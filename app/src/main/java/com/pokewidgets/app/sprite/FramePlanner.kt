package com.pokewidgets.app.sprite

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decides how a source animation becomes a set of widget frames.
 *
 * This exists because home-screen widgets impose two hard limits that the raw sprites
 * blow straight through:
 *
 *  1. `ViewFlipper` — the only animation primitive `RemoteViews` allows — has a *single*
 *     flip interval, but real sprites have per-frame delays (Crystal's Bulbasaur ranges
 *     from 10 ms to 990 ms). Every animation must be resampled onto a uniform grid.
 *
 *  2. The system rejects any widget update whose total bitmap memory exceeds
 *     `screenW * screenH * 4 * 1.5` bytes. Showdown's Rayquaza is 95 frames of
 *     142x153 — 7.9 MB at 1:1, against a 5.3 MB ceiling on a 720p phone. Left alone it
 *     crashes the launcher.
 *
 * Everything here is pure arithmetic on frame metadata: no `Bitmap`, no `Context`, so
 * the rules that keep the launcher alive are covered by ordinary JVM unit tests.
 */
object FramePlanner {

    /**
     * Frame rates we're willing to drop to, in order of preference.
     *
     * The bottom two rungs exist for Generation 5. Black/White's battle sprites are long
     * idle loops — five to nine seconds, up to 160 frames — and at 8 fps a nine-second
     * loop still needs 72 uniform steps, more than [MAX_FRAMES] allows. Without a rate
     * below 6 there is nothing left to trade but sprite size.
     */
    val FPS_LADDER = intArrayOf(20, 15, 12, 10, 8, 6, 5, 4)

    /**
     * Below this we stop trading smoothness for size — an 8 fps idle loop still reads as
     * alive, a 6 fps one starts to strobe.
     */
    private const val COMFORTABLE_MIN_FPS = 8

    /**
     * The one rate below [COMFORTABLE_MIN_FPS] we will drop to in order to keep the sprite
     * off 1:1.
     *
     * Rendering at 1:1 means a Black/White Charizard occupying 87 of a widget's 350
     * pixels — a quarter of the width, adrift in the middle of it. That is a far more
     * visible defect than 6 fps on an animation that is, in every one of these sets, a
     * standing idle loop. Without this rung the planner would take the 1:1 deal, because
     * the comfortable ladder is searched at every scale before any slower rate is.
     */
    private const val RESCUE_FPS = 6

    /**
     * Above this upscale, another doubling of bitmap size buys nothing a viewer can see:
     * the "pixels" are already large blocks. Below it, shrinking really is noticeable.
     * So the planner spends scale above this threshold freely and defends it below.
     */
    private const val GENEROUS_SCALE = 4

    /**
     * Each frame is a nested `RemoteViews` in the parcel handed to the launcher. Bitmap
     * memory is budgeted separately; this caps the structural cost of the view tree.
     */
    const val MAX_FRAMES = 60

    const val MAX_SCALE = 8

    /**
     * Metadata for the decoded source animation. One delay per source frame.
     *
     * @param canonical maps each frame to the first frame with identical pixels (see
     *   [BitmapOps.canonicalFrames]). Frames that share a canonical index share one
     *   bitmap, so only canonical frames are charged against the budget. Defaults to
     *   every frame being its own.
     */
    data class Source(
        val contentWidth: Int,
        val contentHeight: Int,
        val delaysMs: List<Int>,
        val canonical: List<Int> = delaysMs.indices.toList(),
    ) {
        init {
            require(contentWidth > 0 && contentHeight > 0) { "content size must be positive" }
            require(delaysMs.isNotEmpty()) { "an animation needs at least one frame" }
            require(canonical.size == delaysMs.size) { "canonical must cover every frame" }
        }

        val loopMs: Int get() = delaysMs.sum().coerceAtLeast(delaysMs.size)

        fun canonicalOf(index: Int): Int = canonical.getOrElse(index) { index }
    }

    data class Request(
        val source: Source,
        /** Pixel size of the widget's content box. */
        val targetWidthPx: Int,
        val targetHeightPx: Int,
        /**
         * How many screen pixels each source pixel should cover — see [displayScale]. Null
         * means "fill the widget", which is also what every caller before 1.5 meant.
         */
        val displayScale: Double? = null,
        val desiredFps: Int = 12,
        val budgetBytes: Long,
    ) {
        val resolvedDisplayScale: Double
            get() = displayScale ?: displayScale(
                source.contentWidth, source.contentHeight, targetWidthPx, targetHeightPx,
            )
    }

    /**
     * How a plan's bitmaps reach the screen.
     *
     * [SHARP] bitmaps are already the size they are shown at, drawn nearest-neighbour, and
     * sit in a `scaleType="center"` ImageView that never touches them. [SCALED] bitmaps are
     * stored at a smaller whole-number multiple and stretched the rest of the way by a
     * `fitCenter` ImageView — slightly soft, but the same size on screen.
     */
    enum class Tier { SHARP, SCALED }

    /**
     * @param sourceIndices which source frame each uniform step displays. Repeats are
     *   expected and desirable: the renderer hands the *same* `Bitmap` instance to every
     *   step that maps to the same source frame, and `RemoteViews.BitmapCache` dedupes
     *   by object identity, so a long hold costs one bitmap rather than a dozen.
     * @param outWidth size of each stored bitmap — what the memory budget pays for.
     * @param displayWidth size the sprite occupies on screen. Never smaller than asked for.
     */
    data class Plan(
        val frameIntervalMs: Int,
        val sourceIndices: List<Int>,
        val outWidth: Int,
        val outHeight: Int,
        val displayWidth: Int,
        val displayHeight: Int,
        val fps: Int,
        val truncated: Boolean,
    ) {
        val stepCount: Int get() = sourceIndices.size

        val tier: Tier
            get() = if (outWidth == displayWidth && outHeight == displayHeight) Tier.SHARP else Tier.SCALED

        /**
         * The frames that actually need a bitmap. Already canonical, so two steps showing
         * pixel-identical frames appear here once and share a single bitmap.
         */
        val distinctFrames: List<Int> get() = sourceIndices.distinct().sorted()

        /** What the system will actually count, given identity-based bitmap dedupe. */
        val estimatedBytes: Long
            get() = distinctFrames.size.toLong() * outWidth * outHeight * 4L

        /** What it would cost if dedupe ever stopped working. Used only for reporting. */
        val worstCaseBytes: Long
            get() = stepCount.toLong() * outWidth * outHeight * 4L
    }

    /**
     * Makes the sprite as big as the widget asked for, then fits that into the budget by
     * spending whichever resource is cheapest to lose at that moment.
     *
     * **Size on screen is never what gets traded.** Up to 1.4 the only lever was an integer
     * upscale baked into the bitmap, so a big sprite with a long loop — Torterra — dropped to
     * 2x and ended up physically smaller than Psyduck, and "Fill the widget" and "4x" were
     * both quietly ignored. The widget's memory ceiling counts *bitmap* bytes, not pixels on
     * screen, so now the bitmap may be stored smaller and stretched by the ImageView instead:
     *
     *  - Best: bitmaps at the exact display size, pixel-perfect ([Tier.SHARP]).
     *  - While the stored multiple is above [GENEROUS_SCALE], lower it. A 4x-stored sprite
     *    stretched to 8x is barely softer; 12 fps dropping to 8 fps is plainly visible.
     *  - Once down to a modest multiple, defend it and drop frame rate instead.
     *  - Storing at 1:1 — the softest stretch — is the last multiple given up, not the
     *    first: it is worth one rung of frame rate below the comfortable floor. See
     *    [RESCUE_FPS].
     *  - Only once nothing fits at any comfortable rate do we accept strobier rates.
     *  - Only after all of that do we truncate the loop.
     */
    fun plan(request: Request): Plan {
        val src = request.source
        val scale = request.resolvedDisplayScale.coerceAtLeast(MIN_DISPLAY_SCALE)
        val dispW = (src.contentWidth * scale).roundToInt().coerceAtLeast(1)
        val dispH = (src.contentHeight * scale).roundToInt().coerceAtLeast(1)
        val budget = request.budgetBytes

        val startFps = FPS_LADDER.firstOrNull { it <= request.desiredFps } ?: FPS_LADDER.last()
        val comfortable = FPS_LADDER.filter { it <= startFps && it >= COMFORTABLE_MIN_FPS }
            .ifEmpty { listOf(startFps) }
        val strobe = FPS_LADDER.filter { it < COMFORTABLE_MIN_FPS }

        fun at(multiple: Int, fps: Int): Plan? = candidate(
            src, src.contentWidth * multiple, src.contentHeight * multiple, dispW, dispH, fps, budget,
        )

        fun sharp(fps: Int): Plan? = candidate(src, dispW, dispH, dispW, dispH, fps, budget)

        // Pass 1: exactly as asked — full size, pixel-perfect, at the requested rate.
        sharp(startFps)?.let { return it }

        // Art larger than the widget is shrunk to fit, and a shrunk bitmap cannot be stored
        // any smaller without the ImageView stretching it back up. Only frame rate is left.
        val top = floor(scale).toInt()
        if (top < 1) {
            for (fps in comfortable + strobe) sharp(fps)?.let { return it }
            return truncatedPlan(src, dispW, dispH, dispW, dispH, budget)
        }
        val generous = min(GENEROUS_SCALE, top)

        // Pass 2: keep the frame rate, give up sharpness above GENEROUS_SCALE.
        for (multiple in top downTo generous) at(multiple, startFps)?.let { return it }
        // Pass 3: the stored multiple now matters, so trade frame rate at each one — but
        // not all the way down to 1:1 yet.
        for (multiple in generous downTo 2) {
            for (fps in comfortable) at(multiple, fps)?.let { return it }
        }
        // Pass 4: one rung below comfort, spent solely on a crisper stretch.
        for (multiple in generous downTo 2) at(multiple, RESCUE_FPS)?.let { return it }
        // Pass 5: 1:1 storage, at the best rate it can hold.
        for (fps in comfortable) at(1, fps)?.let { return it }
        // Pass 6: nothing fits at a comfortable rate at any multiple. Smoothness is already
        // conceded, so spend the strobier rates on sharpness before truncating.
        for (multiple in top downTo 1) {
            for (fps in strobe) at(multiple, fps)?.let { return it }
        }
        // A full loop does not fit at all. Truncate it; the renderer degrades to a still at
        // worst — still drawn at full size.
        return truncatedPlan(src, src.contentWidth, src.contentHeight, dispW, dispH, budget)
    }

    /** Leaves a little air around a sprite that fills its widget, so it never kisses an edge. */
    const val FIT_MARGIN = 0.92

    /** A guard against a zero-sized plan; no real widget asks for less. */
    private const val MIN_DISPLAY_SCALE = 0.05

    /**
     * How large to draw a sprite: the number of screen pixels per source pixel.
     *
     * @param multiple an exact multiple ("4x"), clamped so the sprite still fits the box.
     * @param referencePx "True size": the source size of a *large* Pokémon in this set (see
     *   `SpriteSet.referencePx`). Every sprite is drawn at the scale that makes a sprite of
     *   that size fill the widget, so relative sizes between species survive. Wins over
     *   [multiple] when both are given.
     *
     * With neither, the sprite fills the widget.
     */
    fun displayScale(
        contentWidth: Int,
        contentHeight: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
        multiple: Int? = null,
        referencePx: Int? = null,
    ): Double {
        if (targetWidthPx <= 0 || targetHeightPx <= 0 || contentWidth <= 0 || contentHeight <= 0) {
            return 1.0
        }
        val fit = min(
            targetWidthPx.toDouble() / contentWidth,
            targetHeightPx.toDouble() / contentHeight,
        ) * FIT_MARGIN
        return when {
            referencePx != null && referencePx > 0 ->
                min(fit, min(targetWidthPx, targetHeightPx) * FIT_MARGIN / referencePx)
            multiple != null -> min(multiple.toDouble(), fit)
            else -> fit
        }
    }

    /** Largest integer upscale that still fits the widget's content box. */
    fun fitScale(
        contentWidth: Int,
        contentHeight: Int,
        targetWidthPx: Int,
        targetHeightPx: Int,
        maxScale: Int,
    ): Int {
        if (targetWidthPx <= 0 || targetHeightPx <= 0) return 1
        val byWidth = targetWidthPx / contentWidth
        val byHeight = targetHeightPx / contentHeight
        return min(min(byWidth, byHeight), min(maxScale, MAX_SCALE)).coerceAtLeast(1)
    }

    private fun candidate(
        src: Source,
        outW: Int,
        outH: Int,
        dispW: Int,
        dispH: Int,
        fps: Int,
        budget: Long,
    ): Plan? {
        val raw = resample(src, fps)
        if (raw.size > MAX_FRAMES) return null
        // Collapse onto canonical frames so repeated artwork is charged once.
        val indices = raw.map { src.canonicalOf(it) }
        val bytes = indices.distinct().size.toLong() * outW * outH * 4L
        if (bytes > budget) return null
        return Plan(
            frameIntervalMs = intervalFor(src, indices.size),
            sourceIndices = indices,
            outWidth = outW,
            outHeight = outH,
            displayWidth = dispW,
            displayHeight = dispH,
            fps = fps,
            truncated = false,
        )
    }

    private fun truncatedPlan(
        src: Source,
        outW: Int,
        outH: Int,
        dispW: Int,
        dispH: Int,
        budget: Long,
    ): Plan {
        val perFrame = outW.toLong() * outH * 4L
        val affordable = max(1L, budget / max(1L, perFrame)).toInt()
        val indices = resample(src, FPS_LADDER.last())
            .map { src.canonicalOf(it) }
            .take(min(affordable, MAX_FRAMES))
        return Plan(
            frameIntervalMs = intervalFor(src, indices.size),
            sourceIndices = indices.ifEmpty { listOf(0) },
            outWidth = outW,
            outHeight = outH,
            displayWidth = dispW,
            displayHeight = dispH,
            fps = FPS_LADDER.last(),
            truncated = true,
        )
    }

    /**
     * Samples the animation's timeline at evenly spaced instants across exactly one loop.
     *
     * Sampling over the true loop length (rather than stepping by a fixed interval and
     * letting the tail wrap) means the loop closes cleanly with no drift, which matters
     * for the breathing idle animations these sprites mostly are.
     */
    fun resample(src: Source, fps: Int): List<Int> {
        if (src.delaysMs.size == 1) return listOf(0)
        val loopMs = src.loopMs
        val interval = (1000.0 / fps)
        val steps = max(1, (loopMs / interval).roundToInt())

        // Cumulative end time of each source frame, so a step maps to the frame on screen.
        val ends = IntArray(src.delaysMs.size)
        var acc = 0
        for (i in src.delaysMs.indices) {
            acc += src.delaysMs[i].coerceAtLeast(1)
            ends[i] = acc
        }
        val total = ends.last()

        val out = ArrayList<Int>(steps)
        var cursor = 0
        for (i in 0 until steps) {
            val t = (i.toLong() * total / steps).toInt()
            while (cursor < ends.size - 1 && t >= ends[cursor]) cursor++
            out.add(cursor)
        }
        return out
    }

    private fun intervalFor(src: Source, steps: Int): Int =
        if (steps <= 1) 1000 else (src.loopMs.toDouble() / steps).roundToInt().coerceAtLeast(16)

    /**
     * The system's ceiling is `screenW * screenH * 4 * 1.5`. We budget a fraction of
     * that: the launcher's own views share the allowance, and the check is fatal, not
     * advisory.
     *
     * The default is deliberately conservative. A widget is not a place to spend tens of
     * megabytes — it is one small picture on someone's home screen, and every byte here
     * is held live in the launcher's process for as long as the widget exists.
     */
    fun budgetFor(screenWidthPx: Int, screenHeightPx: Int, fraction: Double = 0.40): Long =
        (screenWidthPx.toLong() * screenHeightPx * 4L * 3L / 2L * fraction).toLong()
}
