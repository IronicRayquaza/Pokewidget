package com.pokewidgets.app.catalog

import com.pokewidgets.app.sprite.DrawnScenery
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Reads the real bundled catalogs, so a bad regeneration fails here rather than on a phone. */
class TrainerCatalogTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val trainers: TrainerIndex by lazy {
        json.decodeFromString(File("src/main/assets/trainers.json").readText())
    }

    private val backgrounds: BackgroundIndex by lazy {
        json.decodeFromString(File("src/main/assets/backgrounds.json").readText())
    }

    @Test
    fun `every trainer has an id, a name and a known region and role`() {
        val regions = trainers.regions.map { it.id }.toSet()
        val roles = trainers.roles.map { it.id }.toSet()
        assertTrue("expected hundreds of trainers, got ${trainers.trainers.size}", trainers.trainers.size > 500)
        for (t in trainers.trainers) {
            assertTrue(t.id.isNotBlank() && t.name.isNotBlank())
            assertTrue("${t.id} region ${t.region}", t.region in regions)
            assertTrue("${t.id} role ${t.role}", t.role in roles)
        }
        assertEquals("ids are unique", trainers.trainers.size, trainers.trainers.map { it.id }.toSet().size)
    }

    @Test
    fun `gym leaders from every region are there`() {
        val leaders = trainers.search("", role = "leader").map { it.region }.toSet()
        for (region in listOf("kanto", "johto", "hoenn", "sinnoh", "unova", "kalos", "alola", "galar", "paldea")) {
            assertTrue("no leaders in $region", region in leaders)
        }
    }

    @Test
    fun `back sprites point at pinned game graphics and have a frame size`() {
        val withBacks = trainers.trainers.filter { it.hasBack }
        assertTrue("expected the player characters' backs, got ${withBacks.size}", withBacks.size >= 10)
        for (t in withBacks) {
            val back = t.back!!
            assertTrue(back.url.contains("@") && back.url.startsWith("https://cdn.jsdelivr.net/gh/pret/"))
            assertTrue(back.frameWidth in 16..128 && back.frameHeight in 16..128)
            back.palette?.let { assertEquals("${t.id} palette", 4, it.size) }
        }
        assertNotNull(trainers.trainer("brendan-gen3")?.back)
    }

    @Test
    fun `search ignores case and accents and matches the game`() {
        assertTrue(trainers.search("CYNTHIA").any { it.id.startsWith("cynthia") })
        assertTrue(trainers.search("pokemon ranger").isNotEmpty())
        assertTrue(trainers.search("ruby & sapphire").all { it.variant?.contains("Ruby") == true || it.name.contains("Ruby") })
    }

    @Test
    fun `a battle scene's default trainer can really be seen from behind`() {
        assertTrue(trainers.defaultFor(3)!!.hasBack)
        assertTrue(trainers.defaultFor(9)!!.hasBack)
    }

    @Test
    fun `backgrounds are pinned, sized and have a default for every generation`() {
        assertTrue(backgrounds.backgrounds.size >= 20)
        for (bg in backgrounds.battlefields) {
            assertTrue(bg.url.contains(backgrounds.sha))
            assertTrue(bg.w > 0 && bg.h > 0)
        }
        for (gen in 1..9) assertNotNull(backgrounds.defaultFor(gen))
        assertEquals("gen3", backgrounds.defaultFor(3).id)
    }

    @Test
    fun `scenery is pinned, and never stands in for a battlefield`() {
        val scenery = backgrounds.scenery
        assertTrue("expected a good choice of scenery, got ${scenery.size}", scenery.size >= 30)
        for (bg in scenery) assertTrue(bg.url.contains(backgrounds.pokerogueSha!!))
        assertEquals("scenery-plains", backgrounds.defaultScenery()?.id)
        // With the app's own scenes added, as the catalog serves it, a drawn one leads.
        val served = backgrounds.copy(backgrounds = DrawnScenery.backgrounds + backgrounds.backgrounds)
        assertEquals("drawn-day", served.defaultScenery()?.id)
        assertTrue(served.battlefields.none { it.isDrawn })
        for (gen in 1..9) assertTrue(!backgrounds.defaultFor(gen).isScenery)
        assertTrue(backgrounds.battlefields.none { it.isScenery })
    }
}
