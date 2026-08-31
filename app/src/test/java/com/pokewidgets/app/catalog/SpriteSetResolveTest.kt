package com.pokewidgets.app.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the fix for widgets that showed "Tap to retry — no connection" forever.
 *
 * The cause was asking whether a set had a `female/` *directory* instead of whether that
 * directory held the Pokémon being asked for. Upstream's `female/` trees hold about forty
 * sprites while covering-the-directory is true for all 1345, and Black/White's `back/` stops
 * at 905 while its front reaches 1025 — so the old check happily produced a URL that 404s.
 *
 * These read the real shipped catalog, so a regenerated `sets.json` that silently changes
 * coverage fails here rather than on somebody's home screen.
 */
class SpriteSetResolveTest {

    private val index: SpriteSetIndex = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/sets.json").readText())

    private fun set(id: String) = index.sets.first { it.id == id }

    private val showdown get() = set("other_showdown")
    private val blackWhite get() = set("versions_generation_v_black_white")

    @Test
    fun `a set that never drew a Pokemon resolves to nothing rather than a broken url`() {
        // Showdown stops short of the last Gen 9 additions: 990-995, 1006, 1008, 1010, 1017
        // and 1022-1025 are absent upstream. All confirmed 404 against the pinned tree.
        for (id in listOf(990, 995, 1006, 1008, 1010, 1017, 1022, 1025)) {
            assertNull(
                "Showdown has no sprite for $id, so nothing should resolve",
                showdown.resolveVariant(id, back = false, shiny = false, female = false, style = null),
            )
            assertFalse(showdown.covers(id, back = false, shiny = false, female = false, style = null))
        }
    }

    @Test
    fun `a Pokemon the set does have still resolves exactly`() {
        for (id in listOf(1, 25, 386, 649, 905, 989, 1000, 1021)) {
            val resolved = showdown
                .resolveVariant(id, back = false, shiny = false, female = false, style = null)
            assertEquals("front art is the empty variant", "", resolved?.path)
            assertTrue("nothing was given up for $id", resolved?.exact == true)
        }
    }

    @Test
    fun `asking for a variant the set lacks for this Pokemon degrades instead of failing`() {
        // Charizard is not one of the ~40 Pokémon with a distinct female sprite.
        val charizard = 6
        assertFalse(
            "the old directory-only check said yes here, and that was the bug",
            showdown.covers(charizard, back = false, shiny = false, female = true, style = null),
        )
        val resolved = showdown
            .resolveVariant(charizard, back = false, shiny = false, female = true, style = null)
        assertEquals("should fall back to plain front art", "", resolved?.path)
        assertFalse("and should admit it did", resolved?.exact ?: true)
    }

    @Test
    fun `shiny survives when female is dropped`() {
        // Pikachu does have a female sprite; Charizard does not. Dropping female must not
        // take shiny down with it — shiny is the far more deliberate choice.
        val resolved = showdown
            .resolveVariant(6, back = false, shiny = true, female = true, style = null)
        assertFalse("female had to go", resolved?.exact ?: true)
        assertTrue("but shiny must be kept", resolved?.path?.contains("shiny") == true)
    }

    @Test
    fun `Black-White back sprites stop at 905 while its front reaches 1025`() {
        val front = blackWhite.idsFor("")
        assertTrue("front covers late Gen 9", 1025 in front)

        val backPath = blackWhite.variantPath(back = true, shiny = false, female = false, style = null)
        assertTrue("the set does have a back directory", backPath != null)
        assertFalse(
            "but it does not contain 1025 — the exact shape of the reported bug",
            blackWhite.covers(1025, back = true, shiny = false, female = false, style = null),
        )
        val resolved = blackWhite
            .resolveVariant(1025, back = true, shiny = false, female = false, style = null)
        assertEquals("so it degrades to the front sprite", "", resolved?.path)
        assertFalse(resolved?.exact ?: true)
    }

    @Test
    fun `every variant a set advertises is backed by at least one id`() {
        for (s in index.sets) {
            for (variant in s.variants.keys) {
                assertTrue(
                    "${s.id} advertises variant '$variant' with no ids behind it",
                    s.idsFor(variant).size > 0,
                )
            }
        }
    }

    @Test
    fun `resolution never returns a variant that does not contain the id`() {
        // The whole point: whatever comes back must be fetchable.
        val ids = listOf(1, 6, 25, 151, 351, 745, 905, 1000, 1025)
        for (s in index.sets) {
            for (id in ids) {
                for (shiny in listOf(false, true)) {
                    for (female in listOf(false, true)) {
                        val r = s.resolveVariant(id, back = false, shiny = shiny, female = female, style = null)
                            ?: continue
                        assertTrue(
                            "${s.id} resolved $id (shiny=$shiny female=$female) to '${r.path}', " +
                                "which does not contain it",
                            id in s.idsFor(r.path),
                        )
                    }
                }
            }
        }
    }
}
