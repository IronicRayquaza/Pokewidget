package com.pokewidgets.app.sprite

import com.pokewidgets.app.widget.SceneLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawnSceneryTest {

    @Test
    fun `every drawn scene is solid, full size and its own picture`() {
        val seen = mutableSetOf<Int>()
        for (bg in DrawnScenery.backgrounds) {
            assertTrue(bg.isDrawn && bg.isScenery)
            val px = DrawnScenery.pixels(bg.id)
            assertNotNull(bg.id, px)
            assertEquals(DrawnScenery.W * DrawnScenery.H, px!!.size)
            // A widget background must never let the wallpaper show through.
            assertTrue("${bg.id} has see-through pixels", px.all { it ushr 24 == 0xFF })
            assertTrue("${bg.id} looks like another scene", seen.add(px.contentHashCode()))
        }
        assertNull(DrawnScenery.pixels("drawn-nowhere"))
    }

    @Test
    fun `drawing is the same every time, so the preview matches the widget`() {
        for (bg in DrawnScenery.backgrounds) {
            assertTrue(DrawnScenery.pixels(bg.id)!!.contentEquals(DrawnScenery.pixels(bg.id)!!))
        }
    }

    @Test
    fun `where figures stand is ground, not sky, at any widget shape`() {
        val day = DrawnScenery.backgrounds.first { it.id == "drawn-day" }
        val px = DrawnScenery.pixels(day.id)!!
        val sky = px[DrawnScenery.W / 2] // the top row is sky
        for ((w, h) in listOf(400 to 400, 1000 to 250, 480 to 800, 800 to 400)) {
            val crop = SceneLayout.sceneryCrop(day, w, h)
            val feetY = crop.top + (crop.height * SceneLayout.GROUND).toInt()
            assertTrue("feet at $feetY in a $w x $h widget are above the horizon", feetY > 110)
            assertTrue(px[feetY * DrawnScenery.W + DrawnScenery.W / 2] != sky)
        }
    }
}
