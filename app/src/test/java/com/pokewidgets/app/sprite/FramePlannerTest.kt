package com.pokewidgets.app.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers below are measured from the real pinned sprites, not invented — these are
 * the cases that crash a launcher if the planner gets them wrong.
 */
class FramePlannerTest {

    private fun uniform(frames: Int, delayMs: Int, w: Int, h: Int) =
        FramePlanner.Source(w, h, List(frames) { delayMs })

    /** showdown/384.gif — 142x153, 95 frames at 30 ms. The worst case in the whole set. */
    private val rayquazaShowdown = uniform(95, 30, 142, 153)

    /** generation-v/black-white/animated/384.gif — 110x98, 74 frames alternating 60/120 ms. */
    private val rayquazaBlackWhite = FramePlanner.Source(
        110, 98, List(74) { if (it % 2 == 0) 60 else 120 },
    )

    /** generation-ii/crystal/animated/1.gif — 56x56, 14 frames, 10 ms to 990 ms. */
    private val bulbasaurCrystal = FramePlanner.Source(
        56, 56,
        listOf(140, 60, 80, 60, 180, 60, 360, 60, 80, 10, 380, 60, 990, 180),
    )

    private val phone720 = FramePlanner.budgetFor(720, 1280)
    private val phone1080 = FramePlanner.budgetFor(1080, 2400)

    // ---- The invariant that matters -----------------------------------------------

    @Test
    fun `never exceeds the budget, for every sprite on every screen at every widget size`() {
        val sources = listOf(rayquazaShowdown, rayquazaBlackWhite, bulbasaurCrystal)
        val screens = listOf(720 to 1280, 1080 to 2400, 1440 to 3120)
        val widgets = listOf(150 to 150, 320 to 160, 700 to 350, 1000 to 900)

        for (src in sources) {
            for ((sw, sh) in screens) {
                for ((ww, wh) in widgets) {
                    for (fps in FramePlanner.FPS_LADDER) {
                        val budget = FramePlanner.budgetFor(sw, sh)
                        val plan = FramePlanner.plan(
                            FramePlanner.Request(src, ww, wh, desiredFps = fps, budgetBytes = budget),
                        )
                        val where = "${src.contentWidth}x${src.contentHeight} " +
                            "screen=${sw}x$sh widget=${ww}x$wh fps=$fps"
                        assertTrue(
                            "$where used ${plan.estimatedBytes} > budget $budget",
                            plan.estimatedBytes <= budget,
                        )
                        assertTrue(
                            "$where produced ${plan.stepCount} frames",
                            plan.stepCount in 1..FramePlanner.MAX_FRAMES,
                        )
                        assertTrue("$where scale ${plan.scale}", plan.scale >= 1)
                    }
                }
            }
        }
    }

    @Test
    fun `the naive approach would have blown the 720p budget - proving the planner earns its keep`() {
        val naive = rayquazaShowdown.delaysMs.size.toLong() * 142 * 153 * 4L
        assertTrue("naive should exceed the 720p ceiling", naive > FramePlanner.budgetFor(720, 1280))

        val plan = FramePlanner.plan(
            FramePlanner.Request(rayquazaShowdown, 700, 350, budgetBytes = phone720),
        )
        assertTrue(plan.estimatedBytes <= phone720)
    }

    // ---- Resampling ----------------------------------------------------------------

    @Test
    fun `resampling holds a long frame for proportionally many steps`() {
        // The 990 ms frame is index 12 and is 36% of Bulbasaur's 2700 ms loop.
        val indices = FramePlanner.resample(bulbasaurCrystal, fps = 12)
        val held = indices.count { it == 12 }
        val share = held.toDouble() / indices.size
        assertTrue("990ms frame got $held/${indices.size} steps", share in 0.28..0.44)
    }

    @Test
    fun `resampling covers one loop and never runs past the last frame`() {
        for (fps in FramePlanner.FPS_LADDER) {
            for (src in listOf(rayquazaShowdown, rayquazaBlackWhite, bulbasaurCrystal)) {
                val indices = FramePlanner.resample(src, fps)
                assertEquals("first step should be frame 0", 0, indices.first())
                assertTrue(indices.all { it in src.delaysMs.indices })
                // Monotonic: a resampled timeline never goes backwards within one loop.
                assertTrue(indices.zipWithNext().all { (a, b) -> b >= a })
            }
        }
    }

    @Test
    fun `resampled loop duration stays close to the original`() {
        for (src in listOf(rayquazaShowdown, rayquazaBlackWhite, bulbasaurCrystal)) {
            val plan = FramePlanner.plan(
                FramePlanner.Request(src, 400, 400, budgetBytes = phone1080),
            )
            val planned = plan.frameIntervalMs.toLong() * plan.stepCount
            val drift = kotlin.math.abs(planned - src.loopMs).toDouble() / src.loopMs
            assertTrue(
                "loop drifted ${(drift * 100).toInt()}% (${planned}ms vs ${src.loopMs}ms)",
                drift < 0.05,
            )
        }
    }

    @Test
    fun `a single-frame source produces a single step`() {
        val plan = FramePlanner.plan(
            FramePlanner.Request(uniform(1, 100, 64, 64), 256, 256, budgetBytes = phone1080),
        )
        assertEquals(1, plan.stepCount)
        assertEquals(4, plan.scale)
    }

    // ---- Trade-off ordering --------------------------------------------------------

    @Test
    fun `generous scale is spent before frame rate`() {
        // Pikachu on a 2x2 widget can be drawn at 8x, but 8x costs ~15 MB of bitmaps for
        // no visible gain over 5x. The planner should give that back and keep 12 fps
        // rather than shipping a huge, choppy sprite.
        val pikachu = uniform(33, 40, 60, 60)
        val plan = FramePlanner.plan(
            FramePlanner.Request(pikachu, 525, 525, desiredFps = 12, budgetBytes = phone1080),
        )
        assertEquals("frame rate should have been defended", 12, plan.fps)
        assertTrue("expected a still-generous scale, got ${plan.scale}", plan.scale >= 4)
        assertTrue("expected 8x to have been given up", plan.scale < 8)
    }

    @Test
    fun `frame rate is sacrificed only once the sprite is already small`() {
        // Rayquaza is big enough that a small widget caps it at 2x from the start, so
        // there is no "free" scale to spend — here frame rate is the right thing to give.
        val plan = FramePlanner.plan(
            FramePlanner.Request(rayquazaShowdown, 350, 350, desiredFps = 12, budgetBytes = phone720),
        )
        assertTrue("expected fps to be reduced from 12, got ${plan.fps}", plan.fps < 12)
        assertTrue("scale should have bottomed out, got ${plan.scale}", plan.scale <= 2)
    }

    @Test
    fun `dedupe is what makes the budget work`() {
        val plan = FramePlanner.plan(
            FramePlanner.Request(bulbasaurCrystal, 400, 400, budgetBytes = phone1080),
        )
        assertTrue(
            "expected repeated steps to collapse: ${plan.distinctFrames.size} distinct " +
                "of ${plan.stepCount} steps",
            plan.distinctFrames.size < plan.stepCount,
        )
        assertTrue(plan.estimatedBytes < plan.worstCaseBytes)
    }

    @Test
    fun `identical frames share a bitmap, which buys back sprite size`() {
        // A four-pose idle loop where each pose is held for six frames — the shape of a
        // lot of these sprites, and exactly what naive per-frame accounting overpays for.
        val heldPoses = FramePlanner.Source(
            contentWidth = 130,
            contentHeight = 140,
            delaysMs = List(24) { 40 },
            canonical = (0 until 24).map { (it / 6) * 6 },
        )
        val naive = FramePlanner.Source(130, 140, List(24) { 40 })

        val deduped = FramePlanner.plan(
            FramePlanner.Request(heldPoses, 900, 900, budgetBytes = phone1080),
        )
        val plain = FramePlanner.plan(
            FramePlanner.Request(naive, 900, 900, budgetBytes = phone1080),
        )

        assertTrue(
            "dedupe should need fewer bitmaps: ${deduped.distinctFrames.size} vs ${plain.distinctFrames.size}",
            deduped.distinctFrames.size < plain.distinctFrames.size,
        )
        assertTrue(
            "the saving should be spent on a larger sprite: ${deduped.scale} vs ${plain.scale}",
            deduped.scale > plain.scale,
        )
        // The saving shows up as both a bigger sprite and a smoother one: the naive plan
        // has to drop frame rate to fit, the deduped one keeps what was asked for.
        assertTrue(
            "expected at least as many steps: ${deduped.stepCount} vs ${plain.stepCount}",
            deduped.stepCount >= plain.stepCount,
        )
        assertTrue("frame rate should not be worse", deduped.fps >= plain.fps)
        assertTrue(deduped.estimatedBytes <= phone1080)
    }

    @Test
    fun `canonical mapping never invents a frame index`() {
        val src = FramePlanner.Source(
            60, 60, List(20) { 50 },
            canonical = (0 until 20).map { if (it < 10) it else 19 - it },
        )
        val plan = FramePlanner.plan(FramePlanner.Request(src, 300, 300, budgetBytes = phone1080))
        assertTrue(plan.sourceIndices.all { it in 0 until 20 })
        assertTrue(plan.distinctFrames.all { it in 0 until 20 })
    }

    // ---- Generation 5, whose loops are longer than everything else -----------------

    /**
     * generation-v/black-white/animated/143.gif — a 74x75 canvas, 90 frames at 100 ms.
     * Nine seconds of Zapdos, measured from the pinned sprite.
     *
     * Black/White is the only set whose loops run this long, and the length is what made
     * it a problem: at every frame rate the planner considers comfortable, one loop needs
     * more than MAX_FRAMES uniform steps, so none of them can be used at any scale at all.
     */
    private val zapdosBlackWhite = FramePlanner.Source(74, 75, List(90) { 100 })

    @Test
    fun `a nine-second Black-White loop is still drawn big enough to recognise`() {
        val plan = FramePlanner.plan(
            FramePlanner.Request(zapdosBlackWhite, 350, 350, budgetBytes = phone1080),
        )

        // The regression this guards: the last-resort pass used to hard-code scale 1, so
        // a sprite that could not be planned at 8 fps fell all the way to 74x75 px in the
        // middle of a 350 px widget — while spending under a fifth of the budget it had.
        assertTrue(
            "expected an upscale, got ${plan.scale}x using ${plan.estimatedBytes} of $phone1080",
            plan.scale >= 2,
        )
        assertTrue(plan.estimatedBytes <= phone1080)
        assertTrue(plan.stepCount in 1..FramePlanner.MAX_FRAMES)
        assertTrue("should not have had to truncate the loop", !plan.truncated)
    }

    /**
     * generation-v/black-white/animated/6.gif — an 87x89 canvas, 72 frames, 7.3 seconds.
     *
     * The awkward middle case. 8 fps is the slowest comfortable rate, and one loop at
     * 8 fps is 58 uniform steps — just inside MAX_FRAMES, and affordable only at 1:1.
     * The planner used to take that deal and draw Charizard 87 pixels tall on a 350 pixel
     * widget, having spent a quarter of its budget.
     */
    private val charizardBlackWhite =
        FramePlanner.Source(87, 89, List(72) { if (it == 0) 200 else 100 })

    @Test
    fun `1 to 1 is the last size given up, not the first`() {
        val plan = FramePlanner.plan(
            FramePlanner.Request(charizardBlackWhite, 350, 350, budgetBytes = phone1080),
        )
        assertTrue(
            "expected an upscale, got ${plan.scale}x at ${plan.fps} fps using " +
                "${plan.estimatedBytes} of $phone1080",
            plan.scale >= 2,
        )
        assertTrue(plan.estimatedBytes <= phone1080)
        assertTrue(plan.stepCount in 1..FramePlanner.MAX_FRAMES)
    }

    @Test
    fun `dropping below the comfortable rate buys size, never the other way round`() {
        val plan = FramePlanner.plan(
            FramePlanner.Request(zapdosBlackWhite, 350, 350, budgetBytes = phone1080),
        )
        // Anything below 8 fps is only ever reached once nothing at or above it fits, so a
        // plan that gave up frame rate must have spent the saving on scale.
        if (plan.fps < 8) {
            assertTrue("gave up frame rate for nothing", plan.scale > 1)
        }
    }

    // ---- Scale fitting -------------------------------------------------------------

    @Test
    fun `fitScale picks the largest integer multiple that fits both axes`() {
        assertEquals(3, FramePlanner.fitScale(50, 46, 160, 160, FramePlanner.MAX_SCALE))
        assertEquals(1, FramePlanner.fitScale(142, 153, 200, 160, FramePlanner.MAX_SCALE))
        assertEquals(2, FramePlanner.fitScale(60, 60, 200, 200, maxScale = 2))
        // Never returns 0, even when the sprite is larger than the widget.
        assertEquals(1, FramePlanner.fitScale(200, 200, 80, 80, FramePlanner.MAX_SCALE))
    }

    @Test
    fun `budget matches the documented system formula`() {
        // The framework ceiling is screenW * screenH * 4 * 1.5; we take a fraction of it.
        assertEquals(5_529_600L, 720L * 1280 * 4 * 3 / 2)
        assertEquals((5_529_600L * 0.40).toLong(), FramePlanner.budgetFor(720, 1280))
    }
}
