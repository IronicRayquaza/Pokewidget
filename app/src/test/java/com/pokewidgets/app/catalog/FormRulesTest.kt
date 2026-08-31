package com.pokewidgets.app.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FormRulesTest {

    private fun world(
        weather: Weather = Weather.CLOUDY,
        isDay: Boolean = true,
        hour: Int = 12,
    ) = WorldState(weather, isDay, hour)

    @Test
    fun `Castform takes the form of the weather`() {
        assertEquals(10014, FormRules.formFor(351, world(weather = Weather.RAIN)))
        assertEquals(10015, FormRules.formFor(351, world(weather = Weather.SNOW)))
        assertEquals(10013, FormRules.formFor(351, world(weather = Weather.CLEAR, isDay = true)))
        assertEquals(351, FormRules.formFor(351, world(weather = Weather.CLOUDY)))
    }

    @Test
    fun `a clear sky at midnight is not harsh sunlight`() {
        assertEquals(
            "Sunny Form comes from the sun, which is not up",
            351,
            FormRules.formFor(351, world(weather = Weather.CLEAR, isDay = false, hour = 1)),
        )
    }

    @Test
    fun `any member of a family resolves, not just the default form`() {
        // Someone who chose Castform Rainy and then turned this on still gets a working
        // widget: they picked the Pokémon, not the weather.
        for (member in listOf(351, 10013, 10014, 10015)) {
            assertEquals(10015, FormRules.formFor(member, world(weather = Weather.SNOW)))
        }
    }

    @Test
    fun `Lycanroc follows the clock, with dusk getting the evening window`() {
        assertEquals(10152, FormRules.formFor(745, world(isDay = true, hour = 17)))
        assertEquals(10152, FormRules.formFor(745, world(isDay = true, hour = 18)))
        assertEquals(745, FormRules.formFor(745, world(isDay = true, hour = 12)))
        assertEquals(10126, FormRules.formFor(745, world(isDay = false, hour = 23)))
    }

    @Test
    fun `Shaymin comes down at night and in the snow`() {
        assertEquals(10006, FormRules.formFor(492, world(isDay = true)))
        assertEquals(492, FormRules.formFor(492, world(isDay = false)))
        assertEquals(492, FormRules.formFor(492, world(weather = Weather.SNOW, isDay = true)))
    }

    @Test
    fun `a Pokemon with no rule is returned untouched`() {
        for (id in listOf(1, 25, 150, 493, 1025)) {
            assertFalse(FormRules.appliesTo(id))
            assertEquals(id, FormRules.formFor(id, world(weather = Weather.SNOW)))
        }
    }

    @Test
    fun `a rule never escapes its own family`() {
        val weathers = Weather.entries
        for (member in FormRules.allMembers()) {
            for (w in weathers) {
                for (day in listOf(true, false)) {
                    for (hour in 0..23) {
                        val out = FormRules.formFor(member, WorldState(w, day, hour))
                        assertTrue(
                            "$member turned into $out, which is not in its family",
                            FormRules.appliesTo(out),
                        )
                        assertNotNull(FormRules.describe(out))
                    }
                }
            }
        }
    }

    @Test
    fun `every form a rule can produce exists in the shipped catalog`() {
        // The rules hard-code ids. If a catalog regeneration ever drops one of these forms,
        // this fails here rather than as a blank widget.
        val catalog: Catalog = Json { ignoreUnknownKeys = true }
            .decodeFromString(File("src/main/assets/catalog.json").readText())
        val known = catalog.pokemon.map { it.id }.toHashSet()
        for (id in FormRules.allMembers()) {
            assertTrue("form $id is not in catalog.json", id in known)
        }
    }

    @Test
    fun `every form a rule can produce is drawable by the default animated set`() {
        // Showdown is the app's default and the only set that covers every generation these
        // families span. A rule that resolved to a sprite Showdown lacks would swap a working
        // widget for a broken one.
        val index: SpriteSetIndex = Json { ignoreUnknownKeys = true }
            .decodeFromString(File("src/main/assets/sets.json").readText())
        val showdown = index.sets.first { it.id == "other_showdown" }
        for (id in FormRules.allMembers()) {
            assertTrue(
                "Showdown has no sprite for form $id",
                showdown.covers(id, back = false, shiny = false, female = false, style = null),
            )
        }
    }

    @Test
    fun `WMO codes map to the four states a sprite can express`() {
        assertEquals(Weather.CLEAR, Weather.fromWmoCode(0))
        assertEquals(Weather.CLOUDY, Weather.fromWmoCode(3))
        assertEquals(Weather.CLOUDY, Weather.fromWmoCode(45))
        assertEquals(Weather.RAIN, Weather.fromWmoCode(61))
        assertEquals(Weather.RAIN, Weather.fromWmoCode(95))
        assertEquals(Weather.SNOW, Weather.fromWmoCode(73))
        assertEquals(Weather.SNOW, Weather.fromWmoCode(86))
        // Anything unknown must still be renderable.
        assertEquals(Weather.CLOUDY, Weather.fromWmoCode(-1))
        assertEquals(Weather.CLOUDY, Weather.fromWmoCode(1000))
    }
}
