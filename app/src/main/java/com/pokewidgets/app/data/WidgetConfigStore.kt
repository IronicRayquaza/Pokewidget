package com.pokewidgets.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore("widgets")

/**
 * Per-widget settings, keyed by `appWidgetId`.
 *
 * Preferences rather than Proto because the schema is a flat handful of scalars and
 * widget ids come and go constantly — the simpler store makes [remove] and [remap],
 * which the AppWidget lifecycle demands, straightforward.
 *
 * Keys are namespaced `"<widgetId>.<field>"`, which is what lets [knownWidgetIds]
 * enumerate placed widgets without a second index.
 */
class WidgetConfigStore(context: Context) {

    private val store = context.applicationContext.widgetDataStore

    fun flow(widgetId: Int): Flow<WidgetConfig> = store.data.map { read(it, widgetId) }

    suspend fun get(widgetId: Int): WidgetConfig = read(store.data.first(), widgetId)

    suspend fun exists(widgetId: Int): Boolean = store.data.first().contains(Keys.pokemon(widgetId))

    suspend fun put(widgetId: Int, config: WidgetConfig) {
        store.edit { prefs ->
            prefs[Keys.pokemon(widgetId)] = config.pokemonId
            prefs[Keys.set(widgetId)] = config.setId
            prefs[Keys.shiny(widgetId)] = config.shiny
            prefs[Keys.back(widgetId)] = config.back
            prefs[Keys.female(widgetId)] = config.female
            val styleKey = Keys.style(widgetId)
            if (config.style != null) prefs[styleKey] = config.style else prefs.remove(styleKey)
            prefs[Keys.showBg(widgetId)] = config.showBackground
            prefs[Keys.bgColor(widgetId)] = config.backgroundColor
            prefs[Keys.corner(widgetId)] = config.cornerRadiusDp
            prefs[Keys.cry(widgetId)] = config.cryEnabled
            prefs[Keys.legacyCry(widgetId)] = config.legacyCry
            prefs[Keys.smoothness(widgetId)] = config.smoothness.name
            prefs[Keys.fill(widgetId)] = config.fill.name
            prefs[Keys.tap(widgetId)] = config.tapAction.name
            prefs[Keys.excited(widgetId)] = config.excitedUntilMs
        }
    }

    suspend fun update(widgetId: Int, transform: (WidgetConfig) -> WidgetConfig) {
        put(widgetId, transform(get(widgetId)))
    }

    suspend fun remove(widgetIds: IntArray) {
        store.edit { prefs ->
            val doomed = widgetIds.map { "$it." }
            for (k in prefs.asMap().keys.toList()) {
                if (doomed.any { k.name.startsWith(it) }) prefs.remove(k)
            }
        }
    }

    /**
     * The launcher hands out fresh widget ids after a backup restore. Without this every
     * restored widget would come back as the default Pikachu.
     */
    suspend fun remap(oldIds: IntArray, newIds: IntArray) {
        val configs = oldIds.map { get(it) }
        newIds.forEachIndexed { i, newId -> configs.getOrNull(i)?.let { put(newId, it) } }
        remove(oldIds)
    }

    suspend fun knownWidgetIds(): List<Int> = store.data.first().asMap().keys
        .mapNotNull { it.name.substringBefore('.').toIntOrNull() }
        .filter { it >= 0 }
        .distinct()
        .sorted()

    /**
     * Holds the choice a user made in the app while the launcher places the widget.
     *
     * `requestPinAppWidget` cannot carry custom data and does not run the configuration
     * activity, so without this, tapping "Add Charizard to home screen" would place a
     * widget showing the default Pikachu. Stashed under a reserved negative id, which no
     * real `appWidgetId` ever uses.
     */
    suspend fun putPendingPin(config: WidgetConfig) {
        put(PENDING_PIN_ID, config)
        store.edit { it[PENDING_PIN_AT] = System.currentTimeMillis() }
    }

    /**
     * Consumes the stashed choice, if one was made recently enough to have been this
     * placement. The freshness window matters: without it, a widget the user later adds
     * by hand from the widget tray would silently inherit a choice they made days ago.
     */
    suspend fun takePendingPin(): WidgetConfig? {
        val prefs = store.data.first()
        if (!prefs.contains(Keys.pokemon(PENDING_PIN_ID))) return null
        val at = prefs[PENDING_PIN_AT] ?: 0L
        val config = read(prefs, PENDING_PIN_ID)
        clearPendingPin()
        return config.takeIf { System.currentTimeMillis() - at <= PENDING_PIN_WINDOW_MS }
    }

    suspend fun clearPendingPin() {
        remove(intArrayOf(PENDING_PIN_ID))
        store.edit { it.remove(PENDING_PIN_AT) }
    }

    private fun read(prefs: Preferences, id: Int): WidgetConfig {
        val d = WidgetConfig()
        return WidgetConfig(
            pokemonId = prefs[Keys.pokemon(id)] ?: d.pokemonId,
            setId = prefs[Keys.set(id)] ?: d.setId,
            shiny = prefs[Keys.shiny(id)] ?: d.shiny,
            back = prefs[Keys.back(id)] ?: d.back,
            female = prefs[Keys.female(id)] ?: d.female,
            style = prefs[Keys.style(id)],
            showBackground = prefs[Keys.showBg(id)] ?: d.showBackground,
            backgroundColor = prefs[Keys.bgColor(id)] ?: d.backgroundColor,
            cornerRadiusDp = prefs[Keys.corner(id)] ?: d.cornerRadiusDp,
            cryEnabled = prefs[Keys.cry(id)] ?: d.cryEnabled,
            legacyCry = prefs[Keys.legacyCry(id)] ?: d.legacyCry,
            smoothness = prefs[Keys.smoothness(id)].toEnum(d.smoothness),
            fill = prefs[Keys.fill(id)].toEnum(d.fill),
            tapAction = prefs[Keys.tap(id)].toEnum(d.tapAction),
            excitedUntilMs = prefs[Keys.excited(id)] ?: 0L,
        )
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private companion object {
        /** Reserved id for [putPendingPin]; real appWidgetIds are always positive. */
        const val PENDING_PIN_ID = -1

        /** How long a stashed pin choice stays applicable. */
        const val PENDING_PIN_WINDOW_MS = 2 * 60 * 1000L

        val PENDING_PIN_AT = longPreferencesKey("pendingPinAt")
    }

    private object Keys {
        fun pokemon(id: Int) = intPreferencesKey("$id.pokemon")
        fun set(id: Int) = stringPreferencesKey("$id.set")
        fun shiny(id: Int) = booleanPreferencesKey("$id.shiny")
        fun back(id: Int) = booleanPreferencesKey("$id.back")
        fun female(id: Int) = booleanPreferencesKey("$id.female")
        fun style(id: Int) = stringPreferencesKey("$id.style")
        fun showBg(id: Int) = booleanPreferencesKey("$id.showBg")
        fun bgColor(id: Int) = intPreferencesKey("$id.bgColor")
        fun corner(id: Int) = intPreferencesKey("$id.corner")
        fun cry(id: Int) = booleanPreferencesKey("$id.cry")
        fun legacyCry(id: Int) = booleanPreferencesKey("$id.legacyCry")
        fun smoothness(id: Int) = stringPreferencesKey("$id.smoothness")
        fun fill(id: Int) = stringPreferencesKey("$id.fill")
        fun tap(id: Int) = stringPreferencesKey("$id.tap")
        fun excited(id: Int) = longPreferencesKey("$id.excited")
    }
}
