package com.pokewidgets.app.sprite

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
        /** Upper bound on integer upscale, from the widget's "fill" setting. */
        val maxScale: Int = MAX_SCALE,
        val desiredFps: Int = 12,
        val budgetBytes: Long,
    )

    /**
     * @param sourceIndices which source frame each uniform step displays. Repeats are
     *   expected and desirable: the renderer hands the *same* `Bitmap` instance to every
     *   step that maps to the same source frame, and `RemoteViews.BitmapCache` dedupes
     *   by object identity, so a long hold costs one bitmap rather than a dozen.
     */
    data class Plan(
        val frameIntervalMs: Int,
        val sourceIndices: List<Int>,
        val scale: Int,
        val outWidth: Int,
        val outHeight: Int,
        val fps: Int,
        val truncated: Boolean,
    ) {
        val stepCount: Int get() = sourceIndices.size

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
     * Fits the animation into the budget by spending whichever resource is cheapest to
     * lose at that moment:
     *
     *  - While the sprite is already drawn at more than [GENEROUS_SCALE], shrink it. The
     *    difference between 8x and 6x pixel art is invisible; the difference between
     *    12 fps and 8 fps is not.
     *  - Once it is down to a modest scale, defend the size and drop frame rate instead —
     *    a tiny sprite on a big widget reads as broken, a slightly choppy one does not.
     *  - Drawing at 1:1 is the last size to give up, not the first: it is worth one rung
     *    of frame rate below the comfortable floor to avoid it. See [RESCUE_FPS].
     *  - Only once nothing fits at any comfortable rate and any size do we accept the
     *    strobier rates — and there size wins again, for the same reason.
     *  - Only after all of that do we truncate the loop.
     */
    fun plan(request: Request): Plan {
        val src = request.source
        val maxScale = fitScale(
            contentWidth = src.contentWidth,
            contentHeight = src.contentHeight,
            targetWidthPx = request.targetWidthPx,
            targetHeightPx = request.targetHeightPx,
            maxScale = request.maxScale,
        )

        val startFps = FPS_LADDER.firstOrNull { it <= request.desiredFps } ?: FPS_LADDER.last()
        val comfortable = FPS_LADDER.filter { it <= startFps && it >= COMFORTABLE_MIN_FPS }
            .ifEmpty { listOf(startFps) }

        // Pass 1: keep the requested frame rate, spending only the "free" scale above
        // GENEROUS_SCALE. This is what stops a 2x2 Pikachu costing 11 MB at 8x.
        for (scale in maxScale downTo min(GENEROUS_SCALE, maxScale)) {
            candidate(src, scale, startFps, request.budgetBytes)?.let { return it }
        }
        // Pass 2: size now matters, so trade frame rate at each remaining scale — but not
        // all the way down to 1:1 yet. See RESCUE_FPS.
        for (scale in min(GENEROUS_SCALE, maxScale) downTo 2) {
            for (fps in comfortable) {
                candidate(src, scale, fps, request.budgetBytes)?.let { return it }
            }
        }
        // Pass 3: one rung below comfort, spent solely on keeping an upscale.
        for (scale in min(GENEROUS_SCALE, maxScale) downTo 2) {
            candidate(src, scale, RESCUE_FPS, request.budgetBytes)?.let { return it }
        }
        // Pass 4: 1:1, at the best rate it can hold.
        for (fps in comfortable) {
            candidate(src, 1, fps, request.budgetBytes)?.let { return it }
        }
        // Pass 5: nothing fits at a comfortable rate, at any size. Smoothness is already
        // conceded, so spend the strobier rates on size rather than dropping straight to
        // 1:1 — which is what used to happen to every Black/White sprite with a loop
        // longer than about seven seconds, leaving a 74px Zapdos marooned in the middle of
        // a 350px widget with four fifths of its budget unspent.
        for (scale in maxScale downTo 1) {
            for (fps in FPS_LADDER.filter { it < COMFORTABLE_MIN_FPS }) {
                candidate(src, scale, fps, request.budgetBytes)?.let { return it }
            }
        }
        // A full loop of this sprite does not fit at all. Truncate it; the renderer
        // degrades to a still image at worst.
        return truncatedPlan(src, request.budgetBytes)
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

    private fun candidate(src: Source, scale: Int, fps: Int, budget: Long): Plan? {
        val raw = resample(src, fps)
        if (raw.size > MAX_FRAMES) return null
        // Collapse onto canonical frames so repeated artwork is charged once.
        val indices = raw.map { src.canonicalOf(it) }
        val outW = src.contentWidth * scale
        val outH = src.contentHeight * scale
        val bytes = indices.distinct().size.toLong() * outW * outH * 4L
        if (bytes > budget) return null
        return Plan(
            frameIntervalMs = intervalFor(src, indices.size),
            sourceIndices = indices,
            scale = scale,
            outWidth = outW,
            outHeight = outH,
            fps = fps,
            truncated = false,
        )
    }

    private fun truncatedPlan(src: Source, budget: Long): Plan {
        val outW = src.contentWidth
        val outH = src.contentHeight
        val perFrame = outW.toLong() * outH * 4L
        val affordable = max(1L, budget / max(1L, perFrame)).toInt()
        val indices = resample(src, FPS_LADDER.last())
            .map { src.canonicalOf(it) }
            .take(min(affordable, MAX_FRAMES))
        return Plan(
            frameIntervalMs = intervalFor(src, indices.size),
            sourceIndices = indices.ifEmpty { listOf(0) },
            scale = 1,
            outWidth = outW,
            outHeight = outH,
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
