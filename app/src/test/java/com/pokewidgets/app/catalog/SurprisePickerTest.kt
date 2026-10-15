package com.pokewidgets.app.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SurprisePickerTest {

    private val everything = { _: Int -> true }

    @Test
    fun `a surprise is never the Pokemon already on screen`() {
        val random = Random(7)
        repeat(500) {
            assertNotEquals(25, SurprisePicker.pick(setOf(25, 133), everything, current = 25, random = random))
        }
    }

    @Test
    fun `any Pokemon means every species, and no alternate forms`() {
        val random = Random(1)
        repeat(2_000) {
            val id = SurprisePicker.pick(emptySet(), everything, current = 25, random = random)!!
            assertTrue(id in 1..SurprisePicker.LAST_SPECIES)
        }
    }

    @Test
    fun `only what the sprite set drew is offered`() {
        // A Gen 1 set: nothing past Mew.
        val gen1 = { id: Int -> id <= 151 }
        val random = Random(3)
        repeat(500) {
            assertTrue(SurprisePicker.pick(emptySet(), gen1, current = 25, random = random)!! <= 151)
        }
        assertEquals(listOf(6), SurprisePicker.candidates(setOf(6, 700), gen1))
    }

    @Test
    fun `a pool of one swaps to it, and stays put once there`() {
        assertEquals(133, SurprisePicker.pick(setOf(133), everything, current = 25))
        assertEquals(133, SurprisePicker.pick(setOf(133), everything, current = 133))
        assertNull(SurprisePicker.pick(setOf(9999), { it < 2000 }, current = 25))
    }

    @Test
    fun `a chosen surprise is dropped once it no longer fits`() {
        assertTrue(SurprisePicker.stillFits(133, emptySet(), everything, current = 25))
        assertFalse(SurprisePicker.stillFits(0, emptySet(), everything, current = 25))
        assertFalse(SurprisePicker.stillFits(25, emptySet(), everything, current = 25))
        assertFalse("taken out of the pool", SurprisePicker.stillFits(133, setOf(6), everything, current = 25))
        assertFalse("an alternate form under 'any'", SurprisePicker.stillFits(10_034, emptySet(), everything, 25))
        assertTrue("an alternate form the person picked", SurprisePicker.stillFits(10_034, setOf(10_034), everything, 25))
        assertFalse("a set that never drew it", SurprisePicker.stillFits(700, emptySet(), { it <= 151 }, 25))
    }

    @Test
    fun `the pool survives the round trip through DataStore`() {
        assertEquals(setOf(6, 25, 150), SurprisePicker.decode(SurprisePicker.encode(setOf(150, 6, 25))))
        assertEquals(emptySet<Int>(), SurprisePicker.decode(""))
        assertEquals(emptySet<Int>(), SurprisePicker.decode(null))
        assertEquals(setOf(4), SurprisePicker.decode("4,x,-2,"))
    }

    @Test
    fun `picks stop at ten, and taking one out always works`() {
        val ten = (1..SurprisePicker.MAX_PICKS).toSet()
        assertEquals(setOf(25), SurprisePicker.toggle(emptySet(), 25))
        assertEquals(emptySet<Int>(), SurprisePicker.toggle(setOf(25), 25))
        assertEquals("an eleventh is ignored", ten, SurprisePicker.toggle(ten, 99))
        assertEquals(ten - 3, SurprisePicker.toggle(ten, 3))
        val savedBeforeTheCap = (1..12).toSet()
        assertEquals(savedBeforeTheCap - 5, SurprisePicker.toggle(savedBeforeTheCap, 5))
    }
}
