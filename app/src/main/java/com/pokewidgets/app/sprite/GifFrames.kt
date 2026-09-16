package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

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
 * GIFs go through [GifDecoder], which follows the spec's disposal rules — Gen 5 and Showdown
 * sprites lean on frame disposal heavily, and getting it wrong leaves smears or a second,
 * ghostly copy of the Pokémon behind the first.
 */
object GifFrames {

    private const val TAG = "GifFrames"

    /** Guards against a pathological GIF exhausting memory before the planner sees it. */
    private const val MAX_SOURCE_FRAMES = 200

    /** Hold time for a composite frame whose set gives no explicit delay. */
    private const val DEFAULT_COMPOSITE_DELAY_MS = 300

    fun decode(bytes: ByteArray, isGif: Boolean): DecodedSprite? =
        if (isGif) decodeGif(bytes) else decodeStill(bytes)

    /**
     * Decodes a sprite that may be spread across several files.
     *
     * Generation 4's battle sprites are two-frame loops whose frames are stored as
     * separate PNGs, so Diamond/Pearl, Platinum and HeartGold/SoulSilver only animate if
     * the parts are stitched back together here. One part behaves exactly as [decode].
     *
     * @param delaysMs how long to hold each part. Falls back to an even split when the
     *   set does not say, and is padded if it is shorter than [parts].
     */
    fun decode(parts: List<ByteArray>, isGif: Boolean, delaysMs: List<Int>): DecodedSprite? {
        if (parts.isEmpty()) return null
        if (parts.size == 1) return decode(parts.first(), isGif)

        val frames = ArrayList<Bitmap>(parts.size)
        val delays = ArrayList<Int>(parts.size)
        for ((index, bytes) in parts.withIndex()) {
            // Each part is one still frame; a composite set is never itself a GIF, and its
            // parts are plain PNGs, so only the first frame of each is wanted even in the
            // theoretical case that one turned out to be animated.
            val decoded = decodeStill(bytes) ?: continue
            decoded.frames.drop(1).forEach { it.recycle() }
            frames.add(decoded.frames.first())
            delays.add(delaysMs.getOrNull(index) ?: DEFAULT_COMPOSITE_DELAY_MS)
        }
        if (frames.isEmpty()) return null

        // Frames must share a canvas: the planner crops to the union of their opaque
        // bounds and scales them by one factor, so a part that decoded at a different
        // size would jump. Upstream keeps a game's sprites on a fixed canvas, so this
        // only fires if a fetch returned something unexpected.
        val width = frames.first().width
        val height = frames.first().height
        if (frames.any { it.width != width || it.height != height }) {
            Log.w(TAG, "composite frames disagree on size; falling back to the first")
            for (i in 1 until frames.size) frames[i].recycle()
            return DecodedSprite(listOf(frames.first()), listOf(delays.first()))
        }
        return DecodedSprite(frames, delays)
    }

    /**
     * A single-file, non-GIF sprite.
     *
     * Sniffs for APNG rather than trusting the set's declared extension, because an APNG's
     * extension *is* `.png`. Deciding by name would fail silently in the worst direction:
     * `BitmapFactory` reads an animated PNG's first frame without complaint, so the sprite
     * would quietly lose its real animation and be given a generated idle instead, with
     * nothing anywhere to say so.
     */
    private fun decodeStill(bytes: ByteArray): DecodedSprite? {
        if (ApngFrames.isApng(bytes)) {
            ApngFrames.decode(bytes)?.let { return it }
            // A malformed APNG still has a valid first frame; one frame beats none.
            Log.w(TAG, "APNG did not decode; falling back to its first frame")
        }
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return DecodedSprite(listOf(bmp), listOf(100))
    }

    private fun decodeGif(bytes: ByteArray): DecodedSprite? {
        val frames = ArrayList<Bitmap>()
        val delays = ArrayList<Int>()
        val info = GifDecoder.decode(bytes, maxFrames = MAX_SOURCE_FRAMES) { argb, width, height, delayMs ->
            // createBitmap copies the pixels, so the decoder is free to reuse its canvas.
            frames.add(Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888))
            delays.add(delayMs)
            true
        }
        if (info == null) {
            Log.w(TAG, "not a GIF")
            return null
        }
        if (frames.isEmpty()) {
            Log.w(TAG, "GIF had no decodable frames")
            return null
        }
        return DecodedSprite(frames, delays)
    }
}
