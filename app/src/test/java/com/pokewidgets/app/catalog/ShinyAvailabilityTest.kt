package com.pokewidgets.app.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ShinyAvailabilityTest {

    private val index: SpriteSetIndex = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/sets.json").readText())

    private fun set(id: String) = index.sets.first { it.id == id }

    @Test
    fun `Showdown has a shiny of nearly everyone`() {
        assertEquals(
            ShinyAvailability.AVAILABLE,
            set("other_showdown").shinyAvailability(25, back = false, female = false, style = null),
        )
    }

    @Test
    fun `a game that never drew shinies says so`() {
        assertEquals(
            ShinyAvailability.SET_HAS_NONE,
            set("versions_generation_i_red_blue").shinyAvailability(25, back = false, female = false, style = null),
        )
        assertEquals(
            ShinyAvailability.SET_HAS_NONE,
            set("versions_generation_ix_scarlet_violet").shinyAvailability(25, back = false, female = false, style = null),
        )
    }

    @Test
    fun `a Pokemon outside a set's shiny coverage is told apart from a set with none`() {
        // Showdown has no sprite at all for 1025, shiny or not.
        assertEquals(
            ShinyAvailability.NOT_FOR_THIS_POKEMON,
            set("other_showdown").shinyAvailability(1025, back = false, female = false, style = null),
        )
    }
}
