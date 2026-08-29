package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel-art safe bitmap helpers. Nothing here may ever apply bilinear filtering. */
object BitmapOps {

    /**
     * Smallest rectangle containing every non-transparent pixel across all frames.
     *
     * Cropping to this before scaling is what buys the frame budget back: Showdown
     * sprites are padded to a common canvas, so a lot of every frame is empty. Using the
     * *union* across frames (rather than per-frame bounds) keeps the sprite from
     * jittering as its silhouette changes.
     */
    fun unionOpaqueBounds(frames: List<Bitmap>, alphaThreshold: Int = 8): Rect {
        if (frames.isEmpty()) return Rect()
        val w = frames[0].width
        val h = frames[0].height
        var left = w
        var top = h
        var right = -1
        var bottom = -1

        val row = IntArray(w)
        for (frame in frames) {
            if (frame.width != w || frame.height != h) continue
            for (y in 0 until h) {
                frame.getPixels(row, 0, w, 0, y, w, 1)
                var rowLeft = -1
                var rowRight = -1
                for (x in 0 until w) {
                    if (Color.alpha(row[x]) > alphaThreshold) {
                        if (rowLeft < 0) rowLeft = x
                        rowRight = x
                    }
                }
                if (rowLeft >= 0) {
                    if (rowLeft < left) left = rowLeft
                    if (rowRight > right) right = rowRight
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        // A fully transparent animation would produce an inverted rect; fall back to full size.
        return if (right < left || bottom < top) Rect(0, 0, w, h)
        else Rect(left, top, right + 1, bottom + 1)
    }

    /**
     * Maps each frame to the index of the first frame with identical pixels.
     *
     * Sprite idle loops very often play a motion and then reverse it, so the back half of
     * the animation is frequently pixel-for-pixel the front half. Collapsing those onto
     * one shared bitmap is close to free and directly buys sprite size: the widget's
     * memory budget is spent on *distinct* frames, so halving them lets the planner keep
     * a larger upscale.
     *
     * Only the cropped region is compared, since that is all that ends up on screen.
     */
    fun canonicalFrames(frames: List<Bitmap>, bounds: Rect): List<Int> {
        if (frames.size <= 1) return List(frames.size) { it }
        val w = bounds.width()
        val h = bounds.height()
        val buffer = IntArray(w * h)
        val byHash = HashMap<Long, MutableList<Pair<Int, IntArray>>>()
        val canonical = IntArray(frames.size) { it }

        for ((i, frame) in frames.withIndex()) {
            if (frame.width < bounds.right || frame.height < bounds.bottom) continue
            frame.getPixels(buffer, 0, w, bounds.left, bounds.top, w, h)

            var hash = -0x340d631b7bdddcdbL // FNV-1a 64-bit offset basis
            for (p in buffer) {
                hash = (hash xor p.toLong()) * 0x100000001b3L
            }
            val bucket = byHash.getOrPut(hash) { mutableListOf() }
            // Verify on hash match: a collision here would show as a visibly wrong frame.
            val match = bucket.firstOrNull { it.second.contentEquals(buffer) }
            if (match != null) {
                canonical[i] = match.first
            } else {
                bucket.add(i to buffer.copyOf())
            }
        }
        return canonical.toList()
    }

    /**
     * Crops, upscales by an exact integer factor, then shrinks the result to fit the
     * widget's content box when it is still larger than the box.
     *
     * The two directions want opposite treatment. Upscaling is integer-only and
     * nearest-neighbour, because that is what keeps 8-bit art crisp. Downscaling only ever
     * applies to the sets that overflow a widget in the first place — official artwork is
     * a 475 px canvas holding 431x402 of actual art, Pokemon HOME is 512 px,
     * Scarlet/Violet is 256 px — and those are high-resolution renders rather than pixel
     * art, so they are resampled with filtering.
     *
     * Fitting has to happen here because the frame's ImageView is scaleType="center",
     * which never shrinks anything: it clips. Without this, any Pokemon drawn larger than
     * the widget lands on the home screen as a centre crop of its own torso — which is
     * exactly why the artwork, HOME and Scarlet/Violet sets looked fine in the app and
     * blank-to-unrecognisable in the widget.
     *
     * @param boxWidth the widget's content box width in pixels; 0 or less skips fitting.
     * @param boxHeight likewise for height.
     */
    fun cropScaleFit(
        source: Bitmap,
        bounds: Rect,
        scale: Int,
        boxWidth: Int,
        boxHeight: Int,
    ): Bitmap {
        val out = cropAndScale(source, bounds, scale)
        if (boxWidth <= 0 || boxHeight <= 0) return out
        if (out.width <= boxWidth && out.height <= boxHeight) return out

        val ratio = min(boxWidth.toFloat() / out.width, boxHeight.toFloat() / out.height)
        val fitted = Bitmap.createScaledBitmap(
            out,
            (out.width * ratio).roundToInt().coerceAtLeast(1),
            (out.height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (out !== fitted) out.recycle()
        return fitted
    }

    /**
     * Crops then upscales by an exact integer factor with nearest-neighbour sampling.
     *
     * Always returns a bitmap distinct from [source]. That is a correctness requirement,
     * not an optimisation: the renderer recycles the decoded source frames as soon as it
     * has composed the RemoteViews, so handing one of them straight through leaves the
     * launcher holding a recycled bitmap and `updateAppWidget` throws
     * `IllegalStateException: Can't parcel a recycled bitmap` — the widget then never
     * appears at all. It is reachable whenever a sprite's opaque content fills its whole
     * canvas (X/Y and much of Scarlet/Violet ship pre-cropped) and the widget is too small
     * to upscale it, because both `createBitmap` and `createScaledBitmap` return their
     * argument unchanged when the transform is a no-op.
     */
    fun cropAndScale(source: Bitmap, bounds: Rect, scale: Int): Bitmap {
        val cropped = if (bounds.left == 0 && bounds.top == 0 &&
            bounds.width() == source.width && bounds.height() == source.height
        ) {
            source
        } else {
            Bitmap.createBitmap(
                source,
                bounds.left.coerceIn(0, source.width - 1),
                bounds.top.coerceIn(0, source.height - 1),
                bounds.width().coerceAtMost(source.width - bounds.left),
                bounds.height().coerceAtMost(source.height - bounds.top),
            )
        }
        if (scale <= 1) {
            return if (cropped === source) source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
            else cropped
        }

        // filter = false is the whole point: bilinear turns 8-bit art into mush.
        val scaled = Bitmap.createScaledBitmap(
            cropped, cropped.width * scale, cropped.height * scale, false,
        )
        if (cropped !== source && cropped !== scaled) cropped.recycle()
        return scaled
    }

    /**
     * Resizes to an exact pixel size with nearest-neighbour sampling.
     *
     * Unlike [cropAndScale] this permits a non-integer ratio, because the procedural idle
     * animation needs shapes a few percent off natural — a squash of exactly 1x is no
     * squash at all. Sampling stays nearest-neighbour, so a 3% vertical squash drops
     * whole rows rather than blending them, which is precisely what the games do and what
     * keeps the art crisp.
     *
     * Returns [source] itself when the requested size is already the current one, so a
     * loop's natural-shape steps cost no extra bitmap.
     */
    fun resize(source: Bitmap, widthPx: Int, heightPx: Int): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        if (w == source.width && h == source.height) return source
        return Bitmap.createScaledBitmap(source, w, h, false)
    }

    /**
     * The widget's background plate. Drawn as its own bitmap rather than a themed
     * drawable so colour, opacity and corner radius are all freely configurable and the
     * sprite bitmaps stay pure alpha.
     */
    fun roundedPlate(widthPx: Int, heightPx: Int, color: Int, cornerRadiusPx: Float): Bitmap {
        val bmp = Bitmap.createBitmap(
            widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(
            RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()),
            cornerRadiusPx, cornerRadiusPx, paint,
        )
        return bmp
    }

    /** Packs frames left-to-right into one sheet so the disk cache is a single file. */
    fun toSheet(frames: List<Bitmap>): Bitmap {
        require(frames.isNotEmpty()) { "no frames to pack" }
        val w = frames[0].width
        val h = frames[0].height
        val sheet = Bitmap.createBitmap(w * frames.size, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        frames.forEachIndexed { i, frame -> canvas.drawBitmap(frame, (i * w).toFloat(), 0f, null) }
        return sheet
    }

    /** Splits a sheet produced by [toSheet] back into [count] frames. */
    fun fromSheet(sheet: Bitmap, count: Int): List<Bitmap> {
        require(count > 0) { "count must be positive" }
        val w = sheet.width / count
        return (0 until count).map { i ->
            Bitmap.createBitmap(sheet, i * w, 0, w, sheet.height)
        }
    }
}
