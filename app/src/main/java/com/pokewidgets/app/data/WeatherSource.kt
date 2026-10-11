package com.pokewidgets.app.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pokewidgets.app.catalog.Weather
import com.pokewidgets.app.catalog.WorldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar

/** A place the user chose to take the weather from. */
data class Place(val label: String, val latitude: Double, val longitude: Double)

/**
 * A weather reading, and when it was taken.
 *
 * [utcOffsetSec] and [sun] are the place's own clock — see [WorldClock]. Both are absent on a
 * reading stored by 1.5, which then behaves exactly as it did.
 */
data class Reading(
    val weather: Weather,
    val isDay: Boolean,
    val takenAtMs: Long,
    val utcOffsetSec: Int? = null,
    val sun: List<SunWindow> = emptyList(),
)

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore("weather")

/**
 * Current weather for one chosen place, from Open-Meteo.
 *
 * Open-Meteo needs no API key, no account and no attribution, which is what makes it usable
 * in a sideloaded fan app: nothing to leak, nothing to expire, no terms for a tester to
 * accept. Location comes from a city the user picks, or — only when they tap "Use my
 * location" — one coarse fix of where the phone is, turned into a city and stored like any
 * other. Nothing reads the location in the background.
 *
 * The reading is cached with a timestamp and every render reads the cache, so the widget
 * still draws with no connection; it simply keeps the last sky it knew about.
 */
class WeatherSource(context: Context) {

    private val appContext = context.applicationContext
    private val store = appContext.weatherDataStore

    // Shared, not per-instance: WidgetRenderer builds a WeatherSource on every render of a
    // live-form widget. The timeouts live in [Http] — weather is a nicety, and this can run
    // inside a broadcast receiver window, so a reading that has not arrived in ten seconds is
    // worth less than the render waiting on it.
    private val client: OkHttpClient get() = Http.json

    private val json = Json { ignoreUnknownKeys = true }

    // ---- The chosen place -------------------------------------------------------

    val placeFlow: Flow<Place?> = store.data.map { it.readPlace() }

    suspend fun place(): Place? = store.data.first().readPlace()

    suspend fun setPlace(place: Place?) {
        store.edit { prefs ->
            if (place == null) {
                prefs.remove(Keys.PLACE_LABEL)
                prefs.remove(Keys.LATITUDE)
                prefs.remove(Keys.LONGITUDE)
                prefs.remove(Keys.WEATHER_CODE)
                prefs.remove(Keys.IS_DAY)
                prefs.remove(Keys.TAKEN_AT)
                prefs.remove(Keys.UTC_OFFSET)
                prefs.remove(Keys.SUN)
            } else {
                prefs[Keys.PLACE_LABEL] = place.label
                prefs[Keys.LATITUDE] = place.latitude
                prefs[Keys.LONGITUDE] = place.longitude
                // A reading belongs to the place it was taken in, so moving invalidates it —
                // including its clock, which would otherwise keep the old city's night.
                prefs.remove(Keys.TAKEN_AT)
                prefs.remove(Keys.UTC_OFFSET)
                prefs.remove(Keys.SUN)
            }
        }
    }

    private fun Preferences.readPlace(): Place? {
        val label = this[Keys.PLACE_LABEL] ?: return null
        val lat = this[Keys.LATITUDE] ?: return null
        val lon = this[Keys.LONGITUDE] ?: return null
        return Place(label, lat, lon)
    }

    // ---- The reading ------------------------------------------------------------

    val readingFlow: Flow<Reading?> = store.data.map { it.readReading() }

    suspend fun cachedReading(): Reading? = store.data.first().readReading()

    private fun Preferences.readReading(): Reading? {
        val code = this[Keys.WEATHER_CODE] ?: return null
        return Reading(
            weather = Weather.fromWmoCode(code),
            isDay = this[Keys.IS_DAY] ?: true,
            takenAtMs = this[Keys.TAKEN_AT] ?: 0L,
            utcOffsetSec = this[Keys.UTC_OFFSET],
            sun = WorldClock.decode(this[Keys.SUN]),
        )
    }

    /**
     * The world as the form rules should see it: the cached sky, and the clock *of the chosen
     * city*.
     *
     * Hour and daylight are worked out now, from the place's UTC offset and its sunrise and
     * sunset, rather than read off the reading — so they never go stale, and a city on the
     * other side of the world gets its own night. With no reading, or one from before 1.6
     * that has no clock, the phone's clock stands in as it always did. With no reading at all
     * the sky is [Weather.CLOUDY], which is the form-neutral answer.
     */
    suspend fun worldState(nowMs: Long = System.currentTimeMillis()): WorldState {
        val deviceHour = Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.HOUR_OF_DAY)
        return WorldClock.worldState(cachedReading(), nowMs, deviceHour)
    }

    /** True when there is a place to ask about and the last answer is older than [maxAgeMs]. */
    suspend fun isStale(maxAgeMs: Long = FRESH_FOR_MS): Boolean {
        if (place() == null) return false
        val takenAt = cachedReading()?.takenAtMs ?: return true
        return System.currentTimeMillis() - takenAt >= maxAgeMs
    }

    /**
     * Fetches and stores a fresh reading. Returns false when there is nothing to ask about or
     * the network refused; either way the cached reading is left alone rather than cleared,
     * because a stale sky renders and a missing one does not.
     */
    suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val place = place() ?: return@withContext false
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${place.latitude}&longitude=${place.longitude}" +
            "&current=weather_code,is_day&daily=sunrise,sunset&forecast_days=2" +
            "&timezone=auto&timeformat=unixtime"
        val body = get(url) ?: return@withContext false

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext false
        val current = root["current"]?.jsonObject ?: return@withContext false

        val code = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return@withContext false
        val isDay = current["is_day"]?.jsonPrimitive?.content?.toIntOrNull() != 0
        val offset = root["utc_offset_seconds"]?.jsonPrimitive?.content?.toIntOrNull()
        val sun = runCatching {
            val daily = root["daily"]?.jsonObject
            val rises = daily?.get("sunrise")?.jsonArray.orEmpty()
            val sets = daily?.get("sunset")?.jsonArray.orEmpty()
            rises.zip(sets).mapNotNull { (rise, set) ->
                val r = rise.jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
                val s = set.jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
                SunWindow(r, s)
            }
        }.getOrDefault(emptyList())

        store.edit {
            it[Keys.WEATHER_CODE] = code
            it[Keys.IS_DAY] = isDay
            it[Keys.TAKEN_AT] = System.currentTimeMillis()
            if (offset != null) it[Keys.UTC_OFFSET] = offset else it.remove(Keys.UTC_OFFSET)
            if (sun.isNotEmpty()) it[Keys.SUN] = WorldClock.encode(sun) else it.remove(Keys.SUN)
        }
        true
    }

    // ---- Choosing a place -------------------------------------------------------

    /**
     * City search, so nobody has to know what a latitude is. Same host family as the forecast
     * endpoint, and equally key-free.
     *
     * The geocoder only matches place *names*, so the query is read several ways by
     * [PlaceQuery] — "Richmond VA" is asked as "Richmond" and checked against Virginia — and
     * the first reading whose answers fit is used. If none fit, the plainest answer for the
     * first reading that found anything is shown rather than nothing: a list with the wrong
     * Richmond first is still a list the right one might be in.
     */
    suspend fun searchPlaces(query: String, limit: Int = 8): List<Place> =
        withContext(Dispatchers.IO) {
            var fallback: List<GeoResult>? = null
            for (attempt in PlaceQuery.attempts(query)) {
                val results = geocode(attempt.name)
                if (fallback == null && results.isNotEmpty()) fallback = results
                val fitting = PlaceQuery.filter(results, attempt.qualifier)
                if (fitting != null) return@withContext fitting.take(limit).map { it.toPlace() }
            }
            fallback.orEmpty().take(limit).map { it.toPlace() }
        }

    private fun geocode(name: String): List<GeoResult> {
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(name, "UTF-8")}&count=$GEOCODE_COUNT&format=json"
        val body = get(url) ?: return emptyList()
        return runCatching {
            val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                ?: return@runCatching emptyList()
            results.mapNotNull { element ->
                val o = element.jsonObject
                fun text(key: String) = o[key]?.jsonPrimitive?.content
                GeoResult(
                    name = text("name") ?: return@mapNotNull null,
                    latitude = text("latitude")?.toDoubleOrNull() ?: return@mapNotNull null,
                    longitude = text("longitude")?.toDoubleOrNull() ?: return@mapNotNull null,
                    admin1 = text("admin1"),
                    country = text("country"),
                    countryCode = text("country_code"),
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun get(url: String): String? = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    } catch (e: IOException) {
        Log.d(TAG, "weather fetch failed: ${e.message}")
        null
    }

    private object Keys {
        val PLACE_LABEL = stringPreferencesKey("placeLabel")
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val WEATHER_CODE = intPreferencesKey("weatherCode")
        val IS_DAY = booleanPreferencesKey("isDay")
        val TAKEN_AT = longPreferencesKey("takenAt")
        val UTC_OFFSET = intPreferencesKey("utcOffsetSec")
        val SUN = stringPreferencesKey("sunWindows")
    }

    companion object {
        private const val TAG = "WeatherSource"

        /** Weather does not change fast enough to be worth asking more often than this. */
        const val FRESH_FOR_MS = 60 * 60 * 1000L

        /** Enough answers that the right Richmond is among them before the qualifier filters. */
        private const val GEOCODE_COUNT = 20
    }
}
