package com.pokewidgets.app.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated idle animation is what gives Emerald, FireRed/LeafGreen, Platinum,
 * HeartGold/SoulSilver and every set from Gen 6 on any movement at all, so its two
 * promises are worth holding to in tests: it must stay cheap, and it must loop cleanly.
 *
 * All of it is integer arithmetic over frame metadata, so none of this needs a device.
 */
class IdleAnimatorTest {

    @Test
    fun everyStyleLoopsBackToWhereItStarted() {
        for (style in IdleStyle.entries) {
            val frames = style.frames
            assertTrue("${style.name} must have at least one frame", frames.isNotEmpty())

            // The flipper runs the loop end-to-start with no transition, so a loop that
            // does not return to its first pose visibly snaps once per cycle.
            val first = frames.first()
            val last = frames.last()
            val step = IdleFrame(
                scaleXPermille = last.scaleXPermille - first.scaleXPermille + IdleFrame.NATURAL,
                scaleYPermille = last.scaleYPermille - first.scaleYPermille + IdleFrame.NATURAL,
                dxSource = last.dxSource - first.dxSource,
                dySource = last.dySource - first.dySource,
            )
            assertTrue(
                "${style.name} jumps at the loop point: $last does not lead back to $first",
                kotlin.math.abs(step.dxSource) <= 1 &&
                    kotlin.math.abs(step.dySource) <= 1 &&
                    kotlin.math.abs(step.scaleXPermille - IdleFrame.NATURAL) <= 15 &&
                    kotlin.math.abs(step.scaleYPermille - IdleFrame.NATURAL) <= 15,
            )
        }
    }

    @Test
    fun translationCostsNoExtraBitmap() {
        // Bob, sway and hover move the sprite without reshaping it, so however many steps
        // they have, the renderer must be able to draw them all from a single bitmap.
        for (style in listOf(IdleStyle.BOB, IdleStyle.SWAY, IdleStyle.HOVER)) {
            assertEquals(
                "${style.name} is pure translation and must need exactly one bitmap",
                1,
                IdleAnimator.distinctShapes(style),
            )
            assertTrue(
                "${style.name} should have more steps than bitmaps",
                style.frames.size > 1,
            )
        }
    }

    @Test
    fun breatheReusesItsShapesAcrossSteps() {
        val style = IdleStyle.BREATHE
        assertTrue(
            "breathing must reshape the sprite, or it is just a bob",
            IdleAnimator.distinctShapes(style) > 1,
        )
        assertTrue(
            "breathing must reuse shapes rather than pay for every step " +
                "(${IdleAnimator.distinctShapes(style)} shapes for ${style.frames.size} steps)",
            IdleAnimator.distinctShapes(style) < style.frames.size,
        )
    }

    @Test
    fun breatheConservesVolume() {
        // What the sprite loses in height it gains in width. Without this a "breath" reads
        // as the whole creature shrinking and growing, which looks like a rendering bug.
        for (frame in IdleStyle.BREATHE.frames) {
            val widened = frame.scaleXPermille - IdleFrame.NATURAL
            val flattened = IdleFrame.NATURAL - frame.scaleYPermille
            assertEquals(
                "squash and stretch must be equal and opposite in $frame",
                flattened,
                widened,
            )
        }
    }

    @Test
    fun stillStyleProducesASingleUnmodifiedFrame() {
        val frames = IdleStyle.NONE.frames
        assertEquals(1, frames.size)
        assertTrue("a still sprite must not be transformed", frames.first().isNaturalShape)
        assertEquals(0, frames.first().dxSource)
        assertEquals(0, frames.first().dySource)
    }

    @Test
    fun shapeKeyDistinguishesSquashFromStretch() {
        // The renderer caches bitmaps by shapeKey, so a collision between a 1030x970 and a
        // 970x1030 sprite would silently draw one of them the wrong way round.
        val squashed = IdleFrame(scaleXPermille = 1030, scaleYPermille = 970)
        val stretched = IdleFrame(scaleXPermille = 970, scaleYPermille = 1030)
        assertNotEquals(squashed.shapeKey, stretched.shapeKey)
        assertEquals(
            IdleFrame(scaleXPermille = 1030, scaleYPermille = 970, dySource = -3).shapeKey,
            squashed.shapeKey,
        )
    }

    @Test
    fun everyStyleLoopsInUnderTwoSeconds() {
        // An idle that takes longer than this stops reading as a living thing and starts
        // reading as a widget that has frozen.
        for (style in IdleStyle.entries) {
            if (style == IdleStyle.NONE) continue
            val loopMs = style.frames.size * style.frameIntervalMs
            assertTrue(
                "${style.name} loops in ${loopMs}ms, which is too slow to read as alive",
                loopMs in 400..2_000,
            )
        }
    }
}
