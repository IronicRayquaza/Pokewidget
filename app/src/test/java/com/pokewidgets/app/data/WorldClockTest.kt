package com.pokewidgets.app.data

import com.pokewidgets.app.catalog.FormRules
import com.pokewidgets.app.catalog.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {

    // 2026-10-11 in Richmond, VA (UTC-4): sunrise 07:16, sunset 18:41 local.
    private val richmondOffset = -4 * 3600
    private val day1 = SunWindow(riseSec = 1_791_717_360, setSec = 1_791_758_460)
    private val day2 = SunWindow(riseSec = day1.riseSec + 86_400 + 60, setSec = day1.setSec + 86_400 - 90)
    private val sun = listOf(day1, day2)

    /** Epoch millis for [hour]:[minute] on 2026-10-11 in Richmond. */
    private fun richmond(hour: Int, minute: Int = 0): Long =
        (1_791_676_800L + hour * 3600L + minute * 60L - richmondOffset) * 1000L

    @Test
    fun `the hour is the city's, not the phone's`() {
        // 21:00 in Richmond is 06:30 the next morning in India, where the phone is.
        assertEquals(21, WorldClock.hour(richmond(21), richmondOffset, deviceHour = 6))
        assertEquals(0, WorldClock.hour(richmond(0), richmondOffset, deviceHour = 9))
    }

    @Test
    fun `daylight comes from the city's sunrise and sunset, not a stale flag`() {
        assertTrue(WorldClock.isDay(richmond(12), sun, fallback = false))
        assertFalse(WorldClock.isDay(richmond(21), sun, fallback = true))
        assertFalse(WorldClock.isDay(richmond(5), sun, fallback = true))
        assertTrue(WorldClock.isDay(richmond(7, 30), sun, fallback = false))
        assertFalse(WorldClock.isDay(richmond(18, 45), sun, fallback = true))
    }

    @Test
    fun `tomorrow is covered too, so an alarm that slept still gets it right`() {
        assertTrue(WorldClock.isDay(richmond(24 + 12), sun, fallback = false))
        assertFalse(WorldClock.isDay(richmond(24 + 3), sun, fallback = true))
    }

    @Test
    fun `with nothing to go on, the old behaviour stands`() {
        assertEquals(15, WorldClock.hour(richmond(21), null, deviceHour = 15))
        assertTrue(WorldClock.isDay(richmond(21), emptyList(), fallback = true))
        // Past the last stored sunset, the windows say nothing.
        assertTrue(WorldClock.isDay(richmond(48 + 21), sun, fallback = true))
        // The polar summer: no real sunset to work with.
        assertTrue(WorldClock.isDay(richmond(2), listOf(SunWindow(10, 10)), fallback = true))
    }

    @Test
    fun `Lycanroc in a city where it is night while the phone sees noon`() {
        val reading = Reading(Weather.CLEAR, isDay = true, takenAtMs = 0, utcOffsetSec = richmondOffset, sun = sun)
        val world = WorldClock.worldState(reading, richmond(23), deviceHour = 12)
        assertFalse(world.isDay)
        assertEquals(10126, FormRules.formFor(745, world))

        val dusk = WorldClock.worldState(reading, richmond(17, 30), deviceHour = 3)
        assertEquals(10152, FormRules.formFor(745, dusk))
    }

    @Test
    fun `sun windows survive the round trip through DataStore`() {
        assertEquals(sun, WorldClock.decode(WorldClock.encode(sun)))
        assertEquals(emptyList<SunWindow>(), WorldClock.decode("1,2,3"))
        assertEquals(emptyList<SunWindow>(), WorldClock.decode(null))
    }
}
