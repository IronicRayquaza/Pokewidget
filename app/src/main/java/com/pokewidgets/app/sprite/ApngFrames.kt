package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Animated PNG, decoded by hand.
 *
 * Generation 5's box icons — the little sprites from the PC boxes — are the one set of real
 * in-game animation upstream holds that this app could not previously use, because they are
 * APNG rather than GIF and the widget pipeline only spoke GIF.
 *
 * Android *does* have an APNG decoder: `ImageDecoder` plus `AnimatedImageDrawable`. It is
 * unusable here for two independent reasons. It arrived in API 28 and this app supports 26,
 * and it exposes no random access to frames — you get a drawable that animates itself in
 * real time, whereas the widget needs every frame up front as a separate `Bitmap` to hand to
 * a `RemoteViews` `ViewFlipper`. Rebuilding the frames from the chunks is both simpler and
 * available on every supported device.
 *
 * An APNG is an ordinary PNG with three extra chunk types: `acTL` announcing the frame count,
 * one `fcTL` per frame carrying its geometry, delay and compositing rules, and `fdAT` holding
 * the frame's pixels in exactly the format `IDAT` uses. So each frame is recovered by
 * synthesising a small standalone PNG — signature, a rewritten `IHDR` at the frame's size,
 * the original palette chunks, the frame's data as `IDAT`, `IEND` — handing that to
 * `BitmapFactory`, and compositing the result onto a running canvas.
 */
object ApngFrames {

    private const val TAG = "ApngFrames"

    /** Matches `GifFrames.MAX_SOURCE_FRAMES`; a sprite this long is pathological either way. */
    private const val MAX_FRAMES = 200

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /**
     * Chunks that describe how to interpret the pixels, and so must be copied into every
     * synthesised frame. `PLTE` and `tRNS` are the load-bearing ones — these icons are
     * palette images, and a frame without its palette decodes to nothing.
     */
    private val CARRIED = setOf("PLTE", "tRNS", "gAMA", "cHRM", "sRGB", "sBIT", "iCCP")

    private const val DISPOSE_NONE = 0
    private const val DISPOSE_BACKGROUND = 1
    private const val DISPOSE_PREVIOUS = 2
    private const val BLEND_SOURCE = 0

    /**
     * Whether these bytes are an *animated* PNG.
     *
     * Deliberately a content sniff and not a file-extension check: an APNG's extension is
     * `.png`, so a set of them is indistinguishable from a set of stills by name. Getting
     * this wrong is silent rather than loud — `BitmapFactory` reads an APNG's first frame
     * quite happily and simply drops the animation — so the check has to be on the bytes.
     */
    fun isApng(bytes: ByteArray): Boolean {
        if (!hasPngSignature(bytes)) return false
        forEachChunk(bytes) { type, _, _ ->
            // acTL must precede the first IDAT, so a scan that reaches IDAT is done.
            if (type == "acTL") return true
            if (type == "IDAT") return false
        }
        return false
    }

    /** Every frame of an APNG, composited, or null if the bytes are not usable. */
    fun decode(bytes: ByteArray): DecodedSprite? {
        if (!hasPngSignature(bytes)) return null

        var header: ByteArray? = null
        val carried = ArrayList<ByteArray>()
        val frames = ArrayList<RawFrame>()
        var pending: Control? = null
        var sawActl = false

        forEachChunk(bytes) { type, offset, length ->
            val data = bytes.copyOfRange(offset, offset + length)
            when (type) {
                "IHDR" -> header = data
                "acTL" -> sawActl = true

                "fcTL" -> {
                    if (data.size >= 26) pending = Control.parse(data)
                }

                // An IDAT is the first frame's pixels when an fcTL was seen before it; with
                // no preceding fcTL it is a still cover image that the animation excludes.
                "IDAT" -> pending?.let { control ->
                    frames.add(RawFrame(control, mutableListOf(data)))
                    pending = null
                }

                // fdAT is IDAT with a four-byte sequence number bolted on the front. Several
                // may follow one fcTL when a frame's data is split across chunks.
                "fdAT" -> if (data.size > 4) {
                    val payload = data.copyOfRange(4, data.size)
                    val control = pending
                    if (control != null) {
                        frames.add(RawFrame(control, mutableListOf(payload)))
                        pending = null
                    } else {
                        frames.lastOrNull()?.data?.add(payload)
                    }
                }

                else -> if (type in CARRIED) carried.add(chunk(type, data))
            }
        }

        val ihdr = header
        if (!sawActl || ihdr == null || frames.isEmpty()) return null

        val canvasWidth = readInt(ihdr, 0)
        val canvasHeight = readInt(ihdr, 4)
        if (canvasWidth <= 0 || canvasHeight <= 0) return null

        return composite(ihdr, carried, frames.take(MAX_FRAMES), canvasWidth, canvasHeight)
    }

    /**
     * Plays the frames onto a canvas, honouring each one's disposal and blend rules.
     *
     * A frame is usually a small rectangle patching part of the previous picture, so the
     * output is the running canvas after each step rather than the frame's own pixels. Every
     * step is copied out, because the canvas keeps being drawn on.
     */
    private fun composite(
        ihdr: ByteArray,
        carried: List<ByteArray>,
        frames: List<RawFrame>,
        canvasWidth: Int,
        canvasHeight: Int,
    ): DecodedSprite? {
        val canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(canvasBitmap)
        // SOURCE blending replaces the region outright, alpha included, rather than
        // compositing over what is already there.
        val replace = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }

        val out = ArrayList<Bitmap>(frames.size)
        val delays = ArrayList<Int>(frames.size)

        try {
            for (frame in frames) {
                val control = frame.control
                val previous = if (control.disposeOp == DISPOSE_PREVIOUS) {
                    canvasBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    null
                }

                val patch = decodePatch(ihdr, carried, frame)
                if (patch != null) {
                    val target = Rect(
                        control.xOffset,
                        control.yOffset,
                        control.xOffset + patch.width,
                        control.yOffset + patch.height,
                    )
                    if (control.blendOp == BLEND_SOURCE) {
                        canvas.drawBitmap(patch, null, target, replace)
                    } else {
                        canvas.drawBitmap(patch, null, target, null)
                    }
                    patch.recycle()
                }

                out.add(canvasBitmap.copy(Bitmap.Config.ARGB_8888, false))
                delays.add(control.delayMs)

                when (control.disposeOp) {
                    DISPOSE_BACKGROUND -> canvas.drawRect(
                        control.xOffset.toFloat(),
                        control.yOffset.toFloat(),
                        (control.xOffset + control.width).toFloat(),
                        (control.yOffset + control.height).toFloat(),
                        Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
                    )

                    DISPOSE_PREVIOUS -> previous?.let {
                        canvas.drawBitmap(it, 0f, 0f, replace)
                        it.recycle()
                    }

                    DISPOSE_NONE -> Unit
                }
            }
        } finally {
            canvasBitmap.recycle()
        }

        if (out.isEmpty()) return null
        return DecodedSprite(out, delays)
    }

    /** Rebuilds one frame as a standalone PNG and decodes it. */
    private fun decodePatch(
        ihdr: ByteArray,
        carried: List<ByteArray>,
        frame: RawFrame,
    ): Bitmap? {
        val png = ByteArrayOutputStream(1024)
        png.write(SIGNATURE)

        // The frame's own dimensions replace the canvas dimensions; every other field of the
        // header — bit depth, colour type, interlace — is what the frame was encoded with.
        val patchHeader = ihdr.copyOf()
        writeInt(patchHeader, 0, frame.control.width)
        writeInt(patchHeader, 4, frame.control.height)
        png.write(chunk("IHDR", patchHeader))

        carried.forEach(png::write)
        frame.data.forEach { png.write(chunk("IDAT", it)) }
        png.write(chunk("IEND", ByteArray(0)))

        val bytes = png.toByteArray()
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (decoded == null) Log.w(TAG, "APNG frame did not decode")
        return decoded
    }

    // ---- Chunk plumbing ---------------------------------------------------------

    /** [data] is mutable because one frame's pixels may arrive across several `fdAT` chunks. */
    private class RawFrame(val control: Control, val data: MutableList<ByteArray>)

    private class Control(
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val delayMs: Int,
        val disposeOp: Int,
        val blendOp: Int,
    ) {
        companion object {
            fun parse(data: ByteArray): Control {
                val numerator = readShort(data, 20)
                // A zero denominator means 1/100s, per the spec. A zero delay is "as fast as
                // possible", which browsers render as 100ms — matching GifFrames' clamp.
                val denominator = readShort(data, 22).let { if (it == 0) 100 else it }
                val ms = numerator * 1000 / denominator
                return Control(
                    width = readInt(data, 4),
                    height = readInt(data, 8),
                    xOffset = readInt(data, 12),
                    yOffset = readInt(data, 16),
                    delayMs = if (ms <= 10) 100 else ms,
                    disposeOp = data[24].toInt() and 0xFF,
                    blendOp = data[25].toInt() and 0xFF,
                )
            }
        }
    }

    private fun hasPngSignature(bytes: ByteArray): Boolean =
        bytes.size > SIGNATURE.size && SIGNATURE.indices.all { bytes[it] == SIGNATURE[it] }

    /**
     * Walks the chunk list, calling [block] with each chunk's type and the bounds of its data.
     *
     * @return true if [block] returned early via its own `return`; callers use a non-local
     *   return, so this simply runs to the end or to a malformed chunk.
     */
    private inline fun forEachChunk(bytes: ByteArray, block: (String, Int, Int) -> Unit) {
        var pos = SIGNATURE.size
        while (pos + 8 <= bytes.size) {
            val length = readInt(bytes, pos)
            // Guard against a length field that would run past the buffer, which is how a
            // truncated download would otherwise become an exception.
            if (length < 0 || pos + 12 + length > bytes.size) return
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            block(type, pos + 8, length)
            if (type == "IEND") return
            pos += 12 + length
        }
    }

    /** Length, type, data, CRC — the PNG chunk framing. */
    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        for (i in 0 until 4) out[4 + i] = type[i].code.toByte()
        data.copyInto(out, 8)
        val crc = CRC32().apply { update(out, 4, 4 + data.size) }.value
        writeInt(out, 8 + data.size, crc.toInt())
        return out
    }

    private fun readInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or
            ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or
            (b[at + 3].toInt() and 0xFF)

    private fun readShort(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun writeInt(b: ByteArray, at: Int, value: Int) {
        b[at] = (value ushr 24).toByte()
        b[at + 1] = (value ushr 16).toByte()
        b[at + 2] = (value ushr 8).toByte()
        b[at + 3] = value.toByte()
    }
}
