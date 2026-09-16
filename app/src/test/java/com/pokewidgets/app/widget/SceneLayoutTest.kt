package com.pokewidgets.app.widget

import com.pokewidgets.app.data.Scene
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.TrainerPose
import com.pokewidgets.app.data.TrainerSide
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.sprite.TrainerArt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLayoutTest {

    @Test
    fun `a widget nobody touched is a plain Pokemon widget`() {
        // The rule for 1.5: trainers, backgrounds and mirroring are opt-in, and the default
        // widget must render exactly as it did before they existed.
        val config = WidgetConfig()
        assertNull(config.trainerId)
        assertNull(config.backgroundId)
        assertFalse(config.flipHorizontal)
        assertFalse(config.trainerFlip)
        assertEquals(Scene.SOLO, config.scene)
        assertEquals(Scene.SOLO, config.effectiveScene)
        assertEquals(TapAction.CRY, config.tapAction)
    }

    @Test
    fun `a stored scene without a trainer still draws solo`() {
        val config = WidgetConfig(scene = Scene.BATTLE, trainerPose = TrainerPose.BACK)
        assertEquals(Scene.SOLO, config.effectiveScene)
        assertEquals(Scene.BATTLE, config.copy(trainerId = "brendan-gen3").effectiveScene)
    }

    @Test
    fun `solo gives the Pokemon the whole widget, centred`() {
        val layout = SceneLayout.layout(Scene.SOLO, TrainerSide.LEFT, 400, 300)
        assertEquals(SceneLayout.Box(0, 0, 400, 300), layout.pokemon)
        assertNull(layout.trainer)
        assertFalse(layout.anchorBottom)
    }

    @Test
    fun `side by side splits the widget without overlap, and the side swaps cleanly`() {
        for (side in TrainerSide.values()) {
            val layout = SceneLayout.layout(Scene.SIDE_BY_SIDE, side, 400, 300)
            val trainer = layout.trainer!!
            val pokemon = layout.pokemon
            assertTrue("inside the widget", trainer.left >= 0 && trainer.right <= 400 && pokemon.right <= 400)
            assertTrue("no overlap", trainer.right <= pokemon.left || pokemon.right <= trainer.left)
            assertEquals(400, trainer.width + pokemon.width)
            assertEquals(side == TrainerSide.LEFT, trainer.left < pokemon.left)
        }
    }

    @Test
    fun `battle puts the trainer in front, low and to one side`() {
        val left = SceneLayout.layout(Scene.BATTLE, TrainerSide.LEFT, 400, 300)
        val right = SceneLayout.layout(Scene.BATTLE, TrainerSide.RIGHT, 400, 300)
        assertTrue(left.trainerInFront)
        assertEquals(300, left.trainer!!.bottom)
        assertEquals(0, left.trainer!!.left)
        assertEquals(400, right.trainer!!.right)
        assertEquals("mirror images", 400 - left.pokemon.right, right.pokemon.left)
    }

    @Test
    fun `fitting keeps the aspect ratio and stands on the floor when asked`() {
        val box = SceneLayout.Box(10, 20, 100, 200)
        val standing = SceneLayout.fit(64, 64, box, anchorBottom = true)
        assertEquals(100, standing.width)
        assertEquals(100, standing.height)
        assertEquals(box.bottom, standing.bottom)
        assertEquals(10, standing.left)
        val centred = SceneLayout.fit(64, 64, box, anchorBottom = false)
        assertEquals(20 + 50, centred.top)
    }

    @Test
    fun `a background is cropped to the widget's shape, never stretched`() {
        // Showdown backgrounds are 753x500; a wide 4x2 widget keeps the full width.
        val wide = SceneLayout.coverCrop(753, 500, 400, 200)
        assertEquals(753, wide.width)
        assertEquals(377, wide.height)
        assertEquals((500 - 377) / 2, wide.top)
        // A square widget keeps the full height and takes the middle.
        val square = SceneLayout.coverCrop(753, 500, 300, 300)
        assertEquals(500, square.width)
        assertEquals(500, square.height)
        assertEquals((753 - 500) / 2, square.left)
    }

    @Test
    fun `a background plate stays inside its byte allowance`() {
        val (w, h) = SceneLayout.plateSize(1000, 500, 1_200_000L)
        assertTrue(w.toLong() * h * 4 <= 1_200_000L)
        assertEquals("shape kept", 2.0, w.toDouble() / h, 0.02)
        assertEquals(200 to 100, SceneLayout.plateSize(200, 100, 1_200_000L))
    }

    @Test
    fun `Game Boy shades map onto the palette, and only the outer white is cleared`() {
        val white = 0xFFFFFFFF.toInt()
        val light = 0xFFAAAAAA.toInt()
        val dark = 0xFF555555.toInt()
        val black = 0xFF000000.toInt()
        val palette = intArrayOf(0xFFFFFFFF.toInt(), 0xFFCE9463.toInt(), 0xFFB54A29.toInt(), black)

        // A 5x3 figure: an outline of black holding one white eye, on a white field.
        val pixels = intArrayOf(
            white, black, black, black, white,
            white, black, white, dark, white,
            white, black, light, black, white,
        )
        TrainerArt.recolorShades(pixels, palette)
        TrainerArt.clearEdgeBackground(pixels, 5, 3, palette[0])

        assertArrayEquals(
            intArrayOf(
                0, black, black, black, 0,
                0, black, palette[0], palette[2], 0,
                0, black, palette[1], black, 0,
            ),
            pixels,
        )
    }
}
