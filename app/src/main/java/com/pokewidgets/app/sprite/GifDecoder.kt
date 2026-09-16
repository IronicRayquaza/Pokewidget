package com.pokewidgets.app.sprite

/**
 * A small GIF decoder that composites frames the way browsers do.
 *
 * It replaced Glide's `StandardGifDecoder`, which gets one disposal pattern wrong. Glide
 * only refreshes its "restore to previous" snapshot after frames with disposal 0 or 1, so a
 * disposal-3 frame that follows a disposal-2 clear restores a canvas from *before* the clear.
 * Showdown's Fidough (disposal sequence `…3 1 2 3 3…`) hit exactly that: from frame 17 on,
 * each frame was drawn over a ghost of frame 14, and the widget showed two Fidoughs stacked
 * on top of each other. The ghost also widened the crop and defeated frame dedupe, so the
 * sprite came out smaller as well as doubled. Glide's frame fields are package-private, so
 * the bookkeeping could not be corrected from outside it.
 *
 * The rule here is the spec's: a disposal-3 frame snapshots the canvas *immediately before
 * it is drawn*, and restores that snapshot once it has been shown.
 *
 * No Android types, so it runs — and is tested — on the plain JVM.
 */
object GifDecoder {

    const val DISPOSE_NONE = 1
    const val DISPOSE_BACKGROUND = 2
    const val DISPOSE_PREVIOUS = 3

    /** LZW codes are at most 12 bits. */
    private const val MAX_CODES = 4096

    class Info(val width: Int, val height: Int, val frameCount: Int)

    /**
     * Decodes [bytes], handing each fully composited frame to [onFrame].
     *
     * The array passed to [onFrame] is the live canvas, `width × height` ARGB, and is reused
     * for the next frame: copy it if you keep it. Returning false stops decoding.
     *
     * A truncated or corrupt file ends early rather than throwing; whatever frames decoded
     * before the damage have already been delivered.
     *
     * @return null if this is not a GIF at all.
     */
    fun decode(
        bytes: ByteArray,
        maxFrames: Int = Int.MAX_VALUE,
        onFrame: (argb: IntArray, width: Int, height: Int, delayMs: Int) -> Boolean,
    ): Info? {
        val r = Reader(bytes)
        val signature = runCatching { String(CharArray(6) { r.u8().toChar() }) }.getOrNull()
        if (signature != "GIF87a" && signature != "GIF89a") return null

        var frames = 0
        var width = 0
        var height = 0
        try {
            width = r.u16()
            height = r.u16()
            if (width <= 0 || height <= 0) return null
            val screenPacked = r.u8()
            r.u8() // background colour index: browsers draw transparent, and so do we
            r.u8() // pixel aspect ratio
            val globalPalette = if (screenPacked and 0x80 != 0) {
                r.palette(2 shl (screenPacked and 0x07))
            } else {
                null
            }

            val canvas = IntArray(width * height)
            var snapshot: IntArray? = null
            val indices = IntArray(width * height)

            // Graphic control state; applies to the next image only.
            var disposal = 0
            var delayCs = 0
            var transparent = -1

            loop@ while (frames < maxFrames) {
                when (r.u8()) {
                    0x21 -> {
                        val label = r.u8()
                        if (label == 0xF9) {
                            val size = r.u8()
                            val packed = r.u8()
                            delayCs = r.u16()
                            val index = r.u8()
                            r.skip(size - 4)
                            disposal = (packed shr 2) and 0x07
                            transparent = if (packed and 0x01 != 0) index else -1
                            r.skipSubBlocks()
                        } else {
                            r.skipSubBlocks()
                        }
                    }

                    0x2C -> {
                        val fx = r.u16()
                        val fy = r.u16()
                        val fw = r.u16()
                        val fh = r.u16()
                        val packed = r.u8()
                        val localPalette = if (packed and 0x80 != 0) {
                            r.palette(2 shl (packed and 0x07))
                        } else {
                            null
                        }
                        val interlaced = packed and 0x40 != 0
                        val minCodeSize = r.u8()
                        val data = r.subBlocks()

                        val palette = localPalette ?: globalPalette
                        val pixelCount = fw * fh
                        val frameIndices = if (pixelCount <= indices.size) indices else IntArray(pixelCount)
                        frameIndices.fill(-1, 0, pixelCount)
                        if (minCodeSize in 1..11) lzw(data, minCodeSize, pixelCount, frameIndices)

                        if (disposal == DISPOSE_PREVIOUS) snapshot = canvas.copyOf()
                        if (palette != null) {
                            draw(canvas, width, height, frameIndices, fx, fy, fw, fh, interlaced, palette, transparent)
                        }

                        frames++
                        val delay = delayCs * 10
                        // Browsers clamp 0 ms and 10 ms delays to 100 ms; sprites authored
                        // against that animate at a crawl if the raw value is taken literally.
                        if (!onFrame(canvas, width, height, if (delay <= 10) 100 else delay)) break@loop

                        when (disposal) {
                            DISPOSE_BACKGROUND -> clear(canvas, width, height, fx, fy, fw, fh)
                            DISPOSE_PREVIOUS -> snapshot?.copyInto(canvas)
                        }
                        disposal = 0
                        delayCs = 0
                        transparent = -1
                    }

                    0x3B -> break@loop
                    else -> break@loop
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            // Truncated: keep what was delivered.
        }
        return Info(width, height, frames)
    }

    private fun draw(
        canvas: IntArray,
        width: Int,
        height: Int,
        indices: IntArray,
        fx: Int,
        fy: Int,
        fw: Int,
        fh: Int,
        interlaced: Boolean,
        palette: IntArray,
        transparent: Int,
    ) {
        // Interlaced images store rows in four passes; map each stored row to its real one.
        val rowOrder = if (interlaced) interlaceOrder(fh) else null
        for (stored in 0 until fh) {
            val row = rowOrder?.get(stored) ?: stored
            val y = fy + row
            if (y !in 0 until height) continue
            val src = stored * fw
            val dst = y * width
            for (col in 0 until fw) {
                val x = fx + col
                if (x !in 0 until width) continue
                val index = indices[src + col]
                if (index < 0 || index == transparent || index >= palette.size) continue
                canvas[dst + x] = palette[index]
            }
        }
    }

    private fun interlaceOrder(height: Int): IntArray {
        val order = IntArray(height)
        var i = 0
        for ((start, step) in arrayOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) {
            var row = start
            while (row < height) {
                order[i++] = row
                row += step
            }
        }
        return order
    }

    private fun clear(canvas: IntArray, width: Int, height: Int, fx: Int, fy: Int, fw: Int, fh: Int) {
        for (y in maxOf(fy, 0) until minOf(fy + fh, height)) {
            val from = y * width + maxOf(fx, 0)
            val to = y * width + minOf(fx + fw, width)
            if (from < to) canvas.fill(0, from, to)
        }
    }

    /** Variable-width LZW, as GIF uses it. Stops quietly at the end of the data. */
    private fun lzw(data: ByteArray, minCodeSize: Int, pixelCount: Int, out: IntArray) {
        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var codeMask = (1 shl codeSize) - 1
        var available = clearCode + 2

        val prefix = IntArray(MAX_CODES)
        val suffix = IntArray(MAX_CODES)
        val stack = IntArray(MAX_CODES + 1)
        for (code in 0 until clearCode) suffix[code] = code

        var oldCode = -1
        var first = 0
        var bits = 0
        var datum = 0
        var pos = 0
        var op = 0

        while (op < pixelCount) {
            while (bits < codeSize) {
                if (pos >= data.size) return
                datum = datum or ((data[pos++].toInt() and 0xFF) shl bits)
                bits += 8
            }
            var code = datum and codeMask
            datum = datum ushr codeSize
            bits -= codeSize

            if (code == clearCode) {
                codeSize = minCodeSize + 1
                codeMask = (1 shl codeSize) - 1
                available = clearCode + 2
                oldCode = -1
                continue
            }
            if (code == endCode) return

            if (oldCode == -1) {
                if (code >= clearCode) return
                out[op++] = code
                oldCode = code
                first = code
                continue
            }

            val inCode = code
            var sp = 0
            if (code >= available) {
                if (code > available) return
                stack[sp++] = first
                code = oldCode
            }
            while (code >= clearCode) {
                if (sp >= MAX_CODES) return
                stack[sp++] = suffix[code]
                code = prefix[code]
            }
            first = suffix[code]
            stack[sp++] = first

            if (available < MAX_CODES) {
                prefix[available] = oldCode
                suffix[available] = first
                available++
                if (available and codeMask == 0 && available < MAX_CODES) {
                    codeSize++
                    codeMask += available
                }
            }
            oldCode = inCode
            while (sp > 0 && op < pixelCount) out[op++] = stack[--sp]
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0

        fun u8(): Int = bytes[pos++].toInt() and 0xFF

        fun u16(): Int = u8() or (u8() shl 8)

        fun skip(n: Int) {
            if (n > 0) pos += n
        }

        fun palette(entries: Int): IntArray = IntArray(entries) {
            (0xFF shl 24) or (u8() shl 16) or (u8() shl 8) or u8()
        }

        fun skipSubBlocks() {
            while (true) {
                val size = u8()
                if (size == 0) return
                pos += size
            }
        }

        fun subBlocks(): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            while (pos < bytes.size) {
                val size = u8()
                if (size == 0) break
                // A block that runs past the end is truncated data: keep what is there.
                val end = minOf(pos + size, bytes.size)
                out.write(bytes, pos, end - pos)
                pos = end
            }
            return out.toByteArray()
        }
    }
}
