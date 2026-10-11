package com.pokewidgets.app.data

import com.pokewidgets.app.catalog.Weather
import com.pokewidgets.app.catalog.WorldState

/** One day's daylight, as epoch seconds. */
data class SunWindow(val riseSec: Long, val setSec: Long)

/**
 * The time of day *in the chosen city*, which is not the time of day on the phone.
 *
 * Live forms used to read the hour off the phone's own clock and take day or night from the
 * last weather reading. Both are wrong for someone who picks a city in another time zone: the
 * hour is the phone's, so Lycanroc's dusk arrived at the phone's dusk, and the day/night flag
 * was as old as the reading — an hour at best, much more when the inexact alarm slept through.
 *
 * So a reading now carries the place's UTC offset and two days of sunrise and sunset, and
 * both answers are worked out fresh at render time from those. Pure Kotlin, so it is tested
 * against fixed instants rather than whatever the clock says when the test runs.
 */
object WorldClock {

    /**
     * What the form rules see at [nowMs]: the reading's sky, the place's hour and daylight.
     * With no reading the sky is [Weather.CLOUDY], the form-neutral answer, and daylight is
     * guessed from the hour rather than assumed.
     */
    fun worldState(reading: Reading?, nowMs: Long, deviceHour: Int): WorldState {
        val hour = hour(nowMs, reading?.utcOffsetSec, deviceHour)
        return WorldState(
            weather = reading?.weather ?: Weather.CLOUDY,
            isDay = isDay(nowMs, reading?.sun.orEmpty(), reading?.isDay ?: (hour in 6..18)),
            hour = hour,
        )
    }

    /**
     * The hour, 0..23, at [nowMs] in a place [utcOffsetSec] from UTC. Falls back to
     * [deviceHour] when the offset is unknown — a reading taken before 1.6, or none at all.
     */
    fun hour(nowMs: Long, utcOffsetSec: Int?, deviceHour: Int): Int {
        if (utcOffsetSec == null) return deviceHour
        val localSec = Math.floorDiv(nowMs, 1000L) + utcOffsetSec
        return Math.floorMod(Math.floorDiv(localSec, 3600L), 24L).toInt()
    }

    /**
     * Whether the sun is up at [nowMs], from the stored windows. [fallback] — the reading's
     * own flag, or the clock's guess — is used when the windows do not reach [nowMs] or make
     * no sense (the polar summer, where Open-Meteo has no sunset to give).
     */
    fun isDay(nowMs: Long, sun: List<SunWindow>, fallback: Boolean): Boolean {
        if (sun.isEmpty() || sun.any { it.setSec <= it.riseSec }) return fallback
        val nowSec = Math.floorDiv(nowMs, 1000L)
        // The windows start at the place's midnight on the day of the reading, so anything
        // before the last sunset is covered: inside a window is day, between them is night.
        if (nowSec >= sun.maxOf { it.setSec }) return fallback
        return sun.any { nowSec >= it.riseSec && nowSec < it.setSec }
    }

    /** "r1,s1,r2,s2" — how the windows are kept in DataStore, which has no list type. */
    fun encode(sun: List<SunWindow>): String = sun.joinToString(",") { "${it.riseSec},${it.setSec}" }

    fun decode(encoded: String?): List<SunWindow> {
        val numbers = encoded?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: return emptyList()
        if (numbers.size % 2 != 0) return emptyList()
        return numbers.chunked(2).map { (rise, set) -> SunWindow(rise, set) }
    }
}
