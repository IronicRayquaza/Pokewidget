package com.pokewidgets.app.widget

import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchTest {

    @Test
    fun `a plain widget downloads nothing ahead`() {
        assertTrue(Prefetch.idsFor(WidgetConfig(pokemonId = 25)).isEmpty())
    }

    @Test
    fun `my picks are all downloaded ahead`() {
        val config = WidgetConfig(pokemonId = 25, tapAction = TapAction.SURPRISE, surprisePool = setOf(6, 150, 448))
        assertEquals(setOf(6, 150, 448), Prefetch.idsFor(config))
    }

    @Test
    fun `any Pokemon is left to the one-ahead choice`() {
        assertTrue(Prefetch.idsFor(WidgetConfig(pokemonId = 25, tapAction = TapAction.SURPRISE)).isEmpty())
    }

    @Test
    fun `a pool kept from another tap action is not downloaded`() {
        val config = WidgetConfig(pokemonId = 25, tapAction = TapAction.CRY, surprisePool = setOf(6))
        assertTrue(Prefetch.idsFor(config).isEmpty())
    }

    @Test
    fun `live form downloads every form, whichever one is showing`() {
        val lycanroc = setOf(745, 10126, 10152)
        assertEquals(lycanroc, Prefetch.idsFor(WidgetConfig(pokemonId = 745, liveForm = true)))
        assertEquals(lycanroc, Prefetch.idsFor(WidgetConfig(pokemonId = 10126, liveForm = true)))
        assertTrue("off means off", Prefetch.idsFor(WidgetConfig(pokemonId = 745)).isEmpty())
    }

    @Test
    fun `a toggle downloads the other side of what it toggles, and nothing else`() {
        val look = Prefetch.Look(back = true, shiny = false)
        assertEquals(
            listOf(look, look.copy(shiny = true)),
            Prefetch.looksFor(WidgetConfig(back = true, tapAction = TapAction.SHINY)),
        )
        assertEquals(
            listOf(look, look.copy(back = false)),
            Prefetch.looksFor(WidgetConfig(back = true, tapAction = TapAction.FLIP)),
        )
        assertEquals(listOf(look), Prefetch.looksFor(WidgetConfig(back = true, tapAction = TapAction.CRY)))
        assertEquals("a mirror is drawn, not downloaded", listOf(look), Prefetch.looksFor(WidgetConfig(back = true, tapAction = TapAction.MIRROR)))
    }
}
