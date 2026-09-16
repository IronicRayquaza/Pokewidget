package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pokewidgets.app.catalog.TrainerBack

/**
 * Turns downloaded trainer images into drawable sprites.
 *
 * Showdown's fronts are ready to use. Backs come straight from game graphics, which need
 * two things done to them:
 *
 * - **Emerald and FireRed** store the throwing animation as a strip of frames; only the
 *   first, standing pose is kept.
 * - **Game Boy and Game Boy Color** sprites are stored as four shades of grey, with the
 *   colours living elsewhere in the ROM, and their background is plain white. The shades are
 *   mapped onto the game's palette and the white surrounding the figure is made transparent
 *   — only white reachable from the edge, so the whites of the eyes stay white.
 */
object TrainerArt {

    fun decode(bytes: ByteArray, back: TrainerBack?): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (back == null) return decoded

        val w = minOf(back.frameWidth, decoded.width)
        val h = minOf(back.frameHeight, decoded.height)
        val pixels = IntArray(w * h)
        decoded.getPixels(pixels, 0, w, 0, 0, w, h)
        decoded.recycle()

        back.palette?.let { hex ->
            val palette = IntArray(hex.size) { parseColor(hex[it]) }
            recolorShades(pixels, palette)
            clearEdgeBackground(pixels, w, h, palette.first())
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Maps each grey pixel to the palette entry for its shade: white, light, dark, black.
     * Pure, so the mapping is tested without Android.
     */
    fun recolorShades(pixels: IntArray, palette: IntArray) {
        if (palette.isEmpty()) return
        val last = palette.size - 1
        for (i in pixels.indices) {
            val p = pixels[i]
            if (p ushr 24 == 0) continue
            val grey = (p shr 16 and 0xFF) // greyscale: every channel is the same
            val shade = ((255 - grey) * last + 127) / 255
            pixels[i] = palette[shade.coerceIn(0, last)]
        }
    }

    /**
     * Makes [background]-coloured pixels transparent where they touch the edge of the image,
     * following them inward, and leaves enclosed ones alone.
     */
    fun clearEdgeBackground(pixels: IntArray, w: Int, h: Int, background: Int) {
        if (w <= 0 || h <= 0) return
        val stack = IntArray(w * h)
        var top = 0
        fun push(x: Int, y: Int) {
            val i = y * w + x
            if (pixels[i] == background) {
                pixels[i] = 0
                stack[top++] = i
            }
        }
        for (x in 0 until w) {
            push(x, 0)
            push(x, h - 1)
        }
        for (y in 0 until h) {
            push(0, y)
            push(w - 1, y)
        }
        while (top > 0) {
            val i = stack[--top]
            val x = i % w
            val y = i / w
            if (x > 0) push(x - 1, y)
            if (x < w - 1) push(x + 1, y)
            if (y > 0) push(x, y - 1)
            if (y < h - 1) push(x, y + 1)
        }
    }

    private fun parseColor(hex: String): Int =
        (0xFF000000.toInt()) or (hex.removePrefix("#").toLong(16).toInt() and 0xFFFFFF)
}
