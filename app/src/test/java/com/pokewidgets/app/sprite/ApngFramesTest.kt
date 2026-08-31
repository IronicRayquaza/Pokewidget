package com.pokewidgets.app.sprite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * Covers the format sniff, which is the part of the APNG path that can go wrong silently.
 *
 * Decoding itself needs `BitmapFactory` and so belongs to the instrumented tests, but the
 * sniff is pure byte parsing — and it is the decision that matters. An APNG's extension is
 * `.png`, so if [ApngFrames.isApng] ever answered false for one, the sprite would still
 * render: `BitmapFactory` reads the first frame of an animated PNG quite happily. The set
 * would simply lose its animation and be handed a generated idle, with nothing to say so.
 *
 * The real Generation 5 box icons this was written against are 32x32, colour type 6, two
 * frames of 170ms, full-canvas, dispose=background, blend=over. The fixtures below are built
 * to the same shape.
 */
class ApngFramesTest {

    @Test
    fun `an animated png is recognised`() {
        assertTrue(ApngFrames.isApng(png(animated = true)))
    }

    @Test
    fun `a still png is not mistaken for an animated one`() {
        assertFalse(ApngFrames.isApng(png(animated = false)))
    }

    @Test
    fun `a gif is not a png`() {
        val gif = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(32)
        assertFalse(ApngFrames.isApng(gif))
    }

    @Test
    fun `garbage never throws`() {
        // These bytes arrive off the network, so a truncated or corrupt download must be a
        // false rather than an exception escaping into the widget renderer.
        assertFalse(ApngFrames.isApng(ByteArray(0)))
        assertFalse(ApngFrames.isApng(ByteArray(4)))
        assertFalse(ApngFrames.isApng(ByteArray(200) { it.toByte() }))
        // A valid signature and header, then a chunk claiming to be longer than the file.
        val truncated = ByteArrayOutputStream().apply {
            write(SIGNATURE)
            write(chunk("IHDR", ByteArray(13)))
            write(byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F))
            write("acTL".toByteArray(Charsets.US_ASCII))
        }.toByteArray()
        assertFalse("a length running past the buffer must not throw", ApngFrames.isApng(truncated))
    }

    @Test
    fun `acTL after the first IDAT does not count`() {
        // The spec requires acTL before IDAT. A file that breaks that rule is not something
        // the decoder should try to animate, and scanning the whole file for an acTL would
        // also mean walking every still PNG to its end for nothing.
        val bytes = ByteArrayOutputStream().apply {
            write(SIGNATURE)
            write(chunk("IHDR", header()))
            write(chunk("IDAT", ByteArray(8)))
            write(chunk("acTL", actl()))
            write(chunk("IEND", ByteArray(0)))
        }.toByteArray()
        assertFalse(ApngFrames.isApng(bytes))
    }

    // ---- Fixtures ---------------------------------------------------------------

    private fun png(animated: Boolean): ByteArray = ByteArrayOutputStream().apply {
        write(SIGNATURE)
        write(chunk("IHDR", header()))
        if (animated) {
            write(chunk("acTL", actl()))
            write(chunk("fcTL", fctl()))
        }
        write(chunk("IDAT", ByteArray(8)))
        write(chunk("IEND", ByteArray(0)))
    }.toByteArray()

    /** 32x32, 8-bit RGBA, no interlace — the shape the real icons use. */
    private fun header(): ByteArray = ByteArray(13).apply {
        writeInt(this, 0, 32)
        writeInt(this, 4, 32)
        this[8] = 8 // bit depth
        this[9] = 6 // colour type: RGBA
    }

    /** Two frames, looping forever. */
    private fun actl(): ByteArray = ByteArray(8).apply {
        writeInt(this, 0, 2)
        writeInt(this, 4, 0)
    }

    private fun fctl(): ByteArray = ByteArray(26).apply {
        writeInt(this, 4, 32) // width
        writeInt(this, 8, 32) // height
        this[20] = 0
        this[21] = 170.toByte() // delay numerator
        this[22] = 0
        this[23] = 100.toByte() // delay denominator: 170/100 s
        this[24] = 1 // dispose: background
        this[25] = 1 // blend: over
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArray(12 + data.size)
        writeInt(out, 0, data.size)
        for (i in 0 until 4) out[4 + i] = type[i].code.toByte()
        data.copyInto(out, 8)
        writeInt(out, 8 + data.size, CRC32().apply { update(out, 4, 4 + data.size) }.value.toInt())
        return out
    }

    private fun writeInt(b: ByteArray, at: Int, value: Int) {
        b[at] = (value ushr 24).toByte()
        b[at + 1] = (value ushr 16).toByte()
        b[at + 2] = (value ushr 8).toByte()
        b[at + 3] = value.toByte()
    }

    private companion object {
        val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
