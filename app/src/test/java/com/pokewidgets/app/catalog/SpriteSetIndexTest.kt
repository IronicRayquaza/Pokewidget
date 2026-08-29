package com.pokewidgets.app.catalog

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Checks the generated `assets/sets.json` against the model that reads it.
 *
 * `tools/build-catalog.mjs` and [SpriteSet] are two halves of one contract written in two
 * languages, so nothing but a test stops them drifting apart. A mismatch would not fail
 * the build — it would ship, and then show up as a widget that renders one frame of a
 * two-frame animation, or fetches from the wrong host.
 */
class SpriteSetIndexTest {

    private val index: SpriteSetIndex = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/sets.json").readText())

    @Test
    fun catalogParsesAndIsNotEmpty() {
        assertTrue("no sprite sets shipped", index.sets.isNotEmpty())
        assertTrue("spritesSha should pin PokeAPI content", index.spritesSha.length >= 40)
    }

    @Test
    fun setIdsAreUnique() {
        // Ids are the persisted key of every placed widget. A duplicate would make
        // `catalog.set(id)` return whichever came first, silently changing someone's art.
        val duplicates = index.sets.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate set ids: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun everySetHasAFrontVariant() {
        for (set in index.sets) {
            assertTrue(
                "${set.id} has no plain front variant, so nothing can select it",
                set.variants.containsKey(""),
            )
            assertTrue("${set.id} covers no Pokémon", set.frontIds.size > 0)
        }
    }

    @Test
    fun compositeSetsHaveOneDelayPerFrame() {
        val composites = index.sets.filter { it.isComposite }
        assertTrue(
            "expected the Gen 4 two-frame sets to be present",
            composites.isNotEmpty(),
        )
        for (set in composites) {
            assertEquals(
                "${set.id} must hold each frame for a stated time",
                set.frameDirs.size,
                set.frameDelaysMs.size,
            )
            assertTrue(
                "${set.id} declares frames but is not marked animated",
                set.animated,
            )
            assertTrue(
                "${set.id} has a non-positive frame delay",
                set.frameDelaysMs.all { it > 0 },
            )
        }
    }

    @Test
    fun providersAreKnown() {
        val known = setOf(SpriteProvider.POKEAPI, SpriteProvider.VEEKUN)
        for (set in index.sets) {
            assertTrue(
                "${set.id} names an unknown provider '${set.provider}'",
                set.provider in known,
            )
        }
    }

    @Test
    fun veekunSetsCarryTheAnimationPokeapiLacks() {
        // The whole point of the second provider. If these vanish, Emerald and all of
        // Gen 4 quietly fall back to still art with a generated idle, which is exactly
        // the state this was meant to fix.
        val veekun = index.sets.filter { it.provider == SpriteProvider.VEEKUN }
        assertTrue("no veekun sets in the catalog", veekun.isNotEmpty())
        assertTrue("veekun sets should all be animated", veekun.all { it.animated })

        val emerald = veekun.singleOrNull { it.gen == 3 }
        assertTrue("expected an animated Gen 3 set from veekun", emerald != null)
        assertTrue(
            "animated Emerald should cover most of Gen 3, got ${emerald!!.frontIds.size}",
            emerald.frontIds.size > 350,
        )
        assertTrue("animated Emerald should have shiny art", emerald.variants.containsKey("shiny"))

        assertEquals(
            "expected Diamond/Pearl, Platinum and HeartGold/SoulSilver to be animated",
            3,
            veekun.count { it.gen == 4 },
        )
    }

    @Test
    fun everyAnimatedSetIsReachableFromTheDefaultPokemon() {
        // Pikachu is the default widget. Every animated set that claims to cover Gen 1
        // must actually be able to render it, or the picker offers a dead option.
        val gen1Animated = index.sets.filter { it.animated && it.gen <= 5 }
        assertTrue(gen1Animated.isNotEmpty())
        for (set in gen1Animated) {
            assertTrue("${set.id} cannot render Pikachu", 25 in set.frontIds)
        }
    }

    @Test
    fun animatedCoverageBeatsTheOldThreeSets() {
        // Before the second provider there were three animated sets: Showdown, Black and
        // White, and Crystal. Emerald plus the three Gen 4 games more than double that.
        val animated = index.sets.count { it.animated }
        assertTrue("expected more than the original 3 animated sets, got $animated", animated >= 7)
    }
}
