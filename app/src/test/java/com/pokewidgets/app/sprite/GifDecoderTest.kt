package com.pokewidgets.app.sprite

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class GifDecoderTest {

    private val none = 0
    private val red = 0xFFFF0000.toInt()
    private val green = 0xFF00FF00.toInt()
    private val blue = 0xFF0000FF.toInt()

    /** Palette index 3 is the transparent colour in every test GIF. */
    private val palette = intArrayOf(red, green, blue, 0xFF000000.toInt())
    private val t = 3

    private fun decodeAll(bytes: ByteArray): Pair<List<IntArray>, List<Int>> {
        val frames = ArrayList<IntArray>()
        val delays = ArrayList<Int>()
        GifDecoder.decode(bytes) { argb, _, _, delay ->
            frames.add(argb.copyOf())
            delays.add(delay)
            true
        }
        return frames to delays
    }

    @Test
    fun `restore-to-previous after a background clear does not bring back a ghost`() {
        // The Fidough pattern: 3, 1, 2, 3. A 4x1 canvas; each frame paints one pixel.
        val gif = TestGif(4, 1, palette)
            .frame(dispose = 3, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(0)) // red at 0
            .frame(dispose = 1, x = 1, y = 0, w = 1, h = 1, pixels = intArrayOf(1)) // green at 1, kept
            .frame(dispose = 2, x = 1, y = 0, w = 2, h = 1, pixels = intArrayOf(2, 2)) // blue at 1-2, then cleared
            .frame(dispose = 3, x = 3, y = 0, w = 1, h = 1, pixels = intArrayOf(0)) // red at 3
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(t)) // nothing new
            .bytes()

        val (frames, _) = decodeAll(gif)

        assertEquals(5, frames.size)
        assertArrayEquals(intArrayOf(red, none, none, none), frames[0])
        // Frame 0 was restore-to-previous: its red is gone before frame 1 draws.
        assertArrayEquals(intArrayOf(none, green, none, none), frames[1])
        assertArrayEquals(intArrayOf(none, blue, blue, none), frames[2])
        // Frame 2 cleared its rect to transparent, taking the green with it.
        assertArrayEquals(intArrayOf(none, none, none, red), frames[3])
        // Frame 3 restored the canvas as it was *just before it was drawn* — empty. Glide
        // restored its stale snapshot from after frame 1 instead, bringing the green back.
        assertArrayEquals(intArrayOf(none, none, none, none), frames[4])
    }

    @Test
    fun `transparent pixels leave what is underneath`() {
        val gif = TestGif(2, 1, palette)
            .frame(dispose = 1, x = 0, y = 0, w = 2, h = 1, pixels = intArrayOf(0, 1))
            .frame(dispose = 1, x = 0, y = 0, w = 2, h = 1, pixels = intArrayOf(t, 2))
            .bytes()

        val (frames, _) = decodeAll(gif)

        assertArrayEquals(intArrayOf(red, blue), frames[1])
    }

    @Test
    fun `a background clear only touches the frame's own rect`() {
        val gif = TestGif(3, 1, palette)
            .frame(dispose = 1, x = 0, y = 0, w = 3, h = 1, pixels = intArrayOf(0, 0, 0))
            .frame(dispose = 2, x = 1, y = 0, w = 1, h = 1, pixels = intArrayOf(1))
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(t))
            .bytes()

        val (frames, _) = decodeAll(gif)

        assertArrayEquals(intArrayOf(red, green, red), frames[1])
        assertArrayEquals(intArrayOf(red, none, red), frames[2])
    }

    @Test
    fun `interlaced rows land where they belong`() {
        // Five rows, one pixel wide: stored in pass order 0, 4, 2, 1, 3.
        val stored = intArrayOf(0, 2, 1, 0, 2) // rows 0, 4, 2, 1, 3
        val gif = TestGif(1, 5, palette)
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 5, pixels = stored, interlaced = true)
            .bytes()

        val (frames, _) = decodeAll(gif)

        assertArrayEquals(intArrayOf(red, red, green, blue, blue), frames[0])
    }

    @Test
    fun `tiny delays are clamped the way browsers clamp them`() {
        val gif = TestGif(1, 1, palette)
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(0), delayCs = 0)
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(0), delayCs = 1)
            .frame(dispose = 1, x = 0, y = 0, w = 1, h = 1, pixels = intArrayOf(0), delayCs = 7)
            .bytes()

        val (_, delays) = decodeAll(gif)

        assertEquals(listOf(100, 100, 70), delays)
    }

    @Test
    fun `a truncated file keeps the frames that made it and never throws`() {
        val whole = TestGif(2, 2, palette)
            .frame(dispose = 1, x = 0, y = 0, w = 2, h = 2, pixels = intArrayOf(0, 1, 2, 0))
            .frame(dispose = 1, x = 0, y = 0, w = 2, h = 2, pixels = intArrayOf(1, 1, 1, 1))
            .bytes()

        for (cut in 13 until whole.size) {
            val (frames, _) = decodeAll(whole.copyOf(cut))
            assertTrue("cut at $cut gave ${frames.size} frames", frames.size <= 2)
        }
        assertEquals(2, decodeAll(whole).first.size)
    }

    @Test
    fun `something that is not a GIF is rejected`() {
        assertNull(GifDecoder.decode(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte())) { _, _, _, _ -> true })
    }

    /**
     * Writes GIFs for tests. It emits a clear code before every pixel, so the LZW code size
     * never grows and no real compressor is needed — the decoder still has to handle clear
     * codes, code packing and the end code correctly to read it back.
     */
    private class TestGif(val width: Int, val height: Int, val palette: IntArray) {
        private val out = ByteArrayOutputStream()

        init {
            out.write("GIF89a".toByteArray())
            u16(width)
            u16(height)
            out.write(0x80 or 0x01) // global palette, 4 entries
            out.write(0)
            out.write(0)
            for (c in palette) {
                out.write(c shr 16 and 0xFF)
                out.write(c shr 8 and 0xFF)
                out.write(c and 0xFF)
            }
        }

        fun frame(
            dispose: Int,
            x: Int,
            y: Int,
            w: Int,
            h: Int,
            pixels: IntArray,
            delayCs: Int = 10,
            interlaced: Boolean = false,
        ): TestGif {
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write((dispose shl 2) or 0x01) // transparent flag on
            u16(delayCs)
            out.write(3) // transparent index
            out.write(0)

            out.write(0x2C)
            u16(x)
            u16(y)
            u16(w)
            u16(h)
            out.write(if (interlaced) 0x40 else 0)

            val minCodeSize = 2
            out.write(minCodeSize)
            val data = lzw(pixels, minCodeSize)
            var i = 0
            while (i < data.size) {
                val n = minOf(255, data.size - i)
                out.write(n)
                out.write(data, i, n)
                i += n
            }
            out.write(0)
            return this
        }

        fun bytes(): ByteArray = out.toByteArray() + byteArrayOf(0x3B)

        private fun lzw(pixels: IntArray, minCodeSize: Int): ByteArray {
            val clear = 1 shl minCodeSize
            val codeSize = minCodeSize + 1
            val packed = ByteArrayOutputStream()
            var datum = 0
            var bits = 0
            fun emit(code: Int) {
                datum = datum or (code shl bits)
                bits += codeSize
                while (bits >= 8) {
                    packed.write(datum and 0xFF)
                    datum = datum ushr 8
                    bits -= 8
                }
            }
            for (p in pixels) {
                emit(clear)
                emit(p)
            }
            emit(clear + 1)
            if (bits > 0) packed.write(datum and 0xFF)
            return packed.toByteArray()
        }

        private fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write(v shr 8 and 0xFF)
        }
    }
}
