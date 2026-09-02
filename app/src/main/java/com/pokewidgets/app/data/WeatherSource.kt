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

/** A weather reading, and when it was taken. */
data class Reading(val weather: Weather, val isDay: Boolean, val takenAtMs: Long)

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore("weather")

/**
 * Current weather for one chosen place, from Open-Meteo.
 *
 * Open-Meteo needs no API key, no account and no attribution, which is what makes it usable
 * in a sideloaded fan app: nothing to leak, nothing to expire, no terms for a tester to
 * accept. Location comes from a city the user picks once, *not* from device location —
 * asking for a location permission to decide whether Castform is holding an umbrella is a
 * trade nobody would take.
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
            } else {
                prefs[Keys.PLACE_LABEL] = place.label
                prefs[Keys.LATITUDE] = place.latitude
                prefs[Keys.LONGITUDE] = place.longitude
                // A reading belongs to the place it was taken in, so moving invalidates it.
                prefs.remove(Keys.TAKEN_AT)
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
        )
    }

    /**
     * The world as the form rules should see it: the cached sky, and the phone's own clock.
     *
     * The hour is always local and always current — it costs nothing and can never be stale,
     * so a Lycanroc keeps the right form even if the weather fetch has been failing for days.
     * With no reading at all the sky is [Weather.CLOUDY], which is the form-neutral answer,
     * and daylight is guessed from the clock rather than assumed.
     */
    suspend fun worldState(): WorldState {
        val reading = cachedReading()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return WorldState(
            weather = reading?.weather ?: Weather.CLOUDY,
            isDay = reading?.isDay ?: (hour in 6..18),
            hour = hour,
        )
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
            "&current=weather_code,is_day&timezone=auto"
        val body = get(url) ?: return@withContext false

        val current = runCatching {
            json.parseToJsonElement(body).jsonObject["current"]?.jsonObject
        }.getOrNull() ?: return@withContext false

        val code = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return@withContext false
        val isDay = current["is_day"]?.jsonPrimitive?.content?.toIntOrNull() != 0

        store.edit {
            it[Keys.WEATHER_CODE] = code
            it[Keys.IS_DAY] = isDay
            it[Keys.TAKEN_AT] = System.currentTimeMillis()
        }
        true
    }

    // ---- Choosing a place -------------------------------------------------------

    /**
     * City search, so nobody has to know what a latitude is. Same host family as the forecast
     * endpoint, and equally key-free.
     */
    suspend fun searchPlaces(query: String, limit: Int = 8): List<Place> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) return@withContext emptyList()
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(trimmed, "UTF-8")}&count=$limit&format=json"
            val body = get(url) ?: return@withContext emptyList()

            runCatching {
                val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray
                    ?: return@runCatching emptyList()
                results.mapNotNull { element ->
                    val o = element.jsonObject
                    val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val lat = o["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: return@mapNotNull null
                    val lon = o["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull()
                        ?: return@mapNotNull null
                    // "Kanpur" alone is ambiguous: there is one in India and one in Pakistan.
                    val region = listOfNotNull(
                        o["admin1"]?.jsonPrimitive?.content,
                        o["country"]?.jsonPrimitive?.content,
                    ).joinToString(", ")
                    Place(if (region.isEmpty()) name else "$name, $region", lat, lon)
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
    }

    companion object {
        private const val TAG = "WeatherSource"

        /** Weather does not change fast enough to be worth asking more often than this. */
        const val FRESH_FOR_MS = 60 * 60 * 1000L
    }
}
