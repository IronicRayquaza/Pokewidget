package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder

/** A decoded source animation: one bitmap and one delay per frame. */
class DecodedSprite(
    val frames: List<Bitmap>,
    val delaysMs: List<Int>,
) {
    val width: Int get() = frames.first().width
    val height: Int get() = frames.first().height

    fun recycle() = frames.forEach { if (!it.isRecycled) it.recycle() }
}

/**
 * Decodes sprite bytes into frames.
 *
 * GIFs go through Glide's standalone `gifdecoder`, which handles the disposal-method
 * bookkeeping correctly — Gen 5 sprites lean on frame disposal heavily, and a naive
 * decoder leaves smears behind the Pokémon.
 */
object GifFrames {

    private const val TAG = "GifFrames"

    /** Guards against a pathological GIF exhausting memory before the planner sees it. */
    private const val MAX_SOURCE_FRAMES = 200

    fun decode(bytes: ByteArray, isGif: Boolean): DecodedSprite? =
        if (isGif) decodeGif(bytes) else decodeStill(bytes)

    private fun decodeStill(bytes: ByteArray): DecodedSprite? {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return DecodedSprite(listOf(bmp), listOf(100))
    }

    private fun decodeGif(bytes: ByteArray): DecodedSprite? {
        val header = GifHeaderParser().apply { setData(bytes) }.parseHeader()
        if (header.numFrames <= 0) {
            Log.w(TAG, "GIF reported ${header.numFrames} frames")
            return null
        }

        val decoder = StandardGifDecoder(SimpleBitmapProvider).apply {
            setData(header, bytes)
            setDefaultBitmapConfig(Bitmap.Config.ARGB_8888)
        }

        val frames = ArrayList<Bitmap>(header.numFrames)
        val delays = ArrayList<Int>(header.numFrames)
        val count = minOf(header.numFrames, MAX_SOURCE_FRAMES)

        for (i in 0 until count) {
            decoder.advance()
            val frame = decoder.nextFrame
            if (frame == null) {
                Log.w(TAG, "GIF frame $i decoded as null; keeping the ${frames.size} decoded so far")
                break
            }
            // The decoder reuses its own buffer between frames, so each one must be copied.
            frames.add(frame.copy(Bitmap.Config.ARGB_8888, false) ?: frame)

            // Browsers clamp 0 ms and 10 ms delays to 100 ms; sprites authored against that
            // behaviour animate at a crawl if the raw value is taken literally.
            val raw = decoder.getDelay(i)
            delays.add(if (raw <= 10) 100 else raw)
        }
        decoder.clear()

        if (frames.isEmpty()) return null
        return DecodedSprite(frames, delays)
    }

    /**
     * Glide's decoder wants a bitmap pool. The widget decodes one sprite at a time and
     * then throws everything away, so plain allocation is simpler and no slower here.
     */
    private object SimpleBitmapProvider : GifDecoder.BitmapProvider {
        override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
            Bitmap.createBitmap(width, height, config)

        override fun release(bitmap: Bitmap) = bitmap.recycle()

        override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)

        override fun release(bytes: ByteArray) = Unit

        override fun obtainIntArray(size: Int): IntArray = IntArray(size)

        override fun release(array: IntArray) = Unit
    }
}
