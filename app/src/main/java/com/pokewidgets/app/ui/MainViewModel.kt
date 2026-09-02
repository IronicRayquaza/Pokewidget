package com.pokewidgets.app.ui

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.data.CrashLog
import com.pokewidgets.app.data.Place
import com.pokewidgets.app.data.Reading
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.data.WeatherSource
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.data.WidgetConfigStore
import com.pokewidgets.app.widget.CryPlayer
import com.pokewidgets.app.widget.PokemonWidgetProvider
import com.pokewidgets.app.widget.WidgetActions
import com.pokewidgets.app.widget.WeatherRefreshScheduler
import com.pokewidgets.app.widget.WidgetRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One sprite set shown in a Pokémon's detail sheet, with the URL to preview it. */
data class SetPreview(val set: SpriteSet, val url: String?)

/** A widget the user has already placed, so it can be edited from inside the app. */
data class PlacedWidget(
    val widgetId: Int,
    val config: WidgetConfig,
    val entry: PokemonEntry?,
    val set: SpriteSet?,
)

data class MainUiState(
    val loading: Boolean = true,
    val query: String = "",
    val generation: Int? = null,
    val animatedOnly: Boolean = false,
    val results: List<PokemonEntry> = emptyList(),
    val detail: PokemonEntry? = null,
    val detailSets: List<SetPreview> = emptyList(),
    val placed: List<PlacedWidget> = emptyList(),
    val cacheBytes: Long = 0,
    val canPin: Boolean = false,
    /** The city live forms take their weather from, if one has been chosen. */
    val weatherPlace: Place? = null,
    /** The last sky we read for that city. Shown so the feature is visible before a widget redraws. */
    val weatherReading: Reading? = null,
    val checkingWeather: Boolean = false,
    val placeQuery: String = "",
    val placeResults: List<Place> = emptyList(),
    val searchingPlaces: Boolean = false,
    /** A crash recorded on a previous run, for the tester to copy out. */
    val crashReport: String? = null,
    /** Something failed in a way the tester should hear about rather than guess at. */
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val catalog = CatalogRepository.get(app)
    private val source = SpriteSource(app)
    private val store = WidgetConfigStore(app)
    private val weather = WeatherSource(app)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    /** What the results list is currently filtered by. Drives [results] off the UI thread. */
    private data class Filters(
        val query: String = "",
        val generation: Int? = null,
        val animatedOnly: Boolean = false,
    )

    private val filters = MutableStateFlow(Filters())
    private val allPokemon = MutableStateFlow<List<PokemonEntry>>(emptyList())
    private val animatedIds = MutableStateFlow<Set<Int>>(emptySet())

    /** Debounces city search the same way the Pokémon query is debounced. */
    private var placeJob: Job? = null

    init {
        launchSafely("load the catalogue") {
            allPokemon.value = catalog.pokemon()
            animatedIds.value = catalog.animatedPokemonIds()
            _state.update {
                it.copy(
                    loading = false,
                    canPin = AppWidgetManager.getInstance(getApplication())
                        .isRequestPinAppWidgetSupported,
                )
            }
            refreshPlaced()
            refreshCacheSize()
            refreshWeatherPlace()
            refreshCrashReport()
        }
        observeFilters()
    }

    /**
     * Recomputes the results list off the main thread, and only for the keystroke the
     * user actually stopped on.
     *
     * Filtering 1,345 entries is not expensive in isolation, but it was running
     * synchronously inside `setQuery` — so a six-letter search ran it six times on the UI
     * thread, each one competing with the recomposition and the icon decodes it had just
     * invalidated. That is what the dropped frames during search actually were.
     * `mapLatest` throws away the work for every intermediate keystroke.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeFilters() {
        launchSafely("filter the list") {
            combine(allPokemon, animatedIds, filters) { all, ids, f -> Triple(all, ids, f) }
                // Typing gets a short settle; a chip tap is a deliberate single act and
                // should feel instant.
                .debounce { (_, _, f) -> if (f.query.isBlank()) 0L else QUERY_DEBOUNCE_MS }
                .mapLatest { (all, ids, f) ->
                    withContext(Dispatchers.Default) {
                        all.filter { entry ->
                            entry.matches(f.query) &&
                                (f.generation == null || entry.generation == f.generation) &&
                                (!f.animatedOnly || entry.id in ids)
                        }
                    }
                }
                .collect { results -> _state.update { it.copy(results = results) } }
        }
    }

    fun setQuery(q: String) {
        // The text field must echo the keystroke immediately; only the *results* wait.
        _state.update { it.copy(query = q) }
        filters.update { it.copy(query = q) }
    }

    fun setGeneration(gen: Int?) {
        _state.update { it.copy(generation = gen) }
        filters.update { it.copy(generation = gen) }
    }

    fun setAnimatedOnly(only: Boolean) {
        _state.update { it.copy(animatedOnly = only) }
        filters.update { it.copy(animatedOnly = only) }
    }

    /**
     * Plays a Pokémon's cry, cancelling whichever one was still sounding.
     *
     * The cancelling is [CryPlayer]'s own rule now, and process-wide: this used to keep a
     * private `cryJob` while the widget's tap handler kept none, so scrolling the app
     * behaved and tapping a widget three times played three overlapping cries.
     */
    fun playCry(pokemonId: Int) {
        CryPlayer.play(getApplication(), pokemonId, legacy = LEGACY_CRY)
    }

    fun openDetail(entry: PokemonEntry) {
        _state.update { it.copy(detail = entry, detailSets = emptyList()) }
        launchSafely("load that Pokémon") {
            val sets = catalog.setsFor(entry.id)
                .sortedWith(compareByDescending<SpriteSet> { it.animated }.thenBy { it.order })
                .map { set ->
                    SetPreview(set, source.spriteUrl(set, SpriteKey(set.id, entry.id)))
                }
            _state.update { it.copy(detailSets = sets) }
        }
        // Opening a Pokémon announces it. This is the tap the bug report is about: until
        // now, choosing a Pokémon anywhere in the app was completely silent.
        playCry(entry.id)
    }

    fun closeDetail() = _state.update { it.copy(detail = null, detailSets = emptyList()) }

    /**
     * Asks the launcher to place a widget directly, so the user never has to hunt through
     * the system widget tray. Supported since API 26; on launchers that decline, the
     * button is hidden rather than silently doing nothing.
     *
     * The success callback is not optional. These providers declare a configuration
     * activity, which means Android deliberately withholds the first APPWIDGET_UPDATE and
     * expects that activity to draw the widget — but a pinned widget never runs it. With
     * no callback the widget is placed and then simply never rendered, which is why
     * adding a Pokemon from inside the app produced an empty square on the home screen.
     */
    fun requestPin(pokemonId: Int, setId: String) {
        val context = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return

        launchSafely("add the widget") {
            // The pin API carries no payload and never runs the configuration activity,
            // so stash the choice for the provider to adopt when the widget arrives.
            store.putPendingPin(WidgetConfig(pokemonId = pokemonId, setId = setId))

            val provider = ComponentName(context, PokemonWidgetProvider.Medium::class.java)
            val placed = PendingIntent.getBroadcast(
                context,
                PIN_CALLBACK_REQUEST,
                Intent(context, PokemonWidgetProvider.Medium::class.java)
                    .setAction(WidgetActions.ACTION_PINNED),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.requestPinAppWidget(provider, null, placed)
        }
    }

    fun refreshPlaced() {
        launchSafely("read your widgets") {
            val live = WidgetRenderer.allWidgetIds(getApplication()).toSet()
            val placed = store.knownWidgetIds()
                .filter { it in live }
                .map { id ->
                    val config = store.get(id)
                    PlacedWidget(id, config, catalog.entry(config.pokemonId), catalog.set(config.setId))
                }
            _state.update { it.copy(placed = placed) }
        }
    }

    fun refreshCacheSize() {
        launchSafely("measure the cache") {
            val bytes = withContext(Dispatchers.IO) { source.cacheSizeBytes() }
            _state.update { it.copy(cacheBytes = bytes) }
        }
    }

    fun clearCache() {
        launchSafely("clear the cache") {
            withContext(Dispatchers.IO) { source.clearCache() }
            refreshCacheSize()
        }
    }

    // ---- Weather, for live forms -------------------------------------------------

    /**
     * Reads back the chosen city and the last sky, and quietly refreshes the sky if it has
     * gone stale. Opening the app is therefore itself a way to make a live form catch up,
     * which matters because the alarm behind this is inexact and non-waking by design.
     */
    fun refreshWeatherPlace() {
        launchSafely("read the weather settings") {
            _state.update {
                it.copy(weatherPlace = weather.place(), weatherReading = weather.cachedReading())
            }
            if (weather.isStale()) {
                refreshWeather()
                renderLiveFormWidgets()
            }
        }
    }

    fun searchPlaces(query: String) {
        _state.update { it.copy(placeQuery = query) }
        placeJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(placeResults = emptyList(), searchingPlaces = false) }
            return
        }
        placeJob = launchSafely("search for that city") {
            delay(QUERY_DEBOUNCE_MS * 3)
            _state.update { it.copy(searchingPlaces = true) }
            val results = weather.searchPlaces(query)
            _state.update { it.copy(placeResults = results, searchingPlaces = false) }
        }
    }

    /**
     * Choosing a place fetches immediately rather than waiting for the hourly alarm, so the
     * setting visibly does something. Every live-form widget is then re-rendered, because
     * they were drawing against whatever sky was cached for the old place.
     *
     * **The order matters and each step is guarded separately.** This function used to run
     * five unguarded steps in a row — a DataStore write, a network fetch, an alarm, a store
     * read and a full widget render — inside `viewModelScope`, which is the main thread with
     * no exception handler. Anything thrown by any of them closed the app, and the reported
     * symptom was worse than that: the city appeared not to save either. So the write happens
     * first, the UI is told about it before anything that can fail runs, and every later step
     * is allowed to fail on its own without taking the setting or the app with it.
     */
    fun choosePlace(place: Place?) {
        launchSafely("save the city") {
            val saved = runCatching { weather.setPlace(place) }
                .onFailure { Log.e(TAG, "could not store the city", it) }
                .isSuccess

            _state.update {
                it.copy(
                    weatherPlace = if (saved) place else it.weatherPlace,
                    placeQuery = "",
                    placeResults = emptyList(),
                    message = if (saved) null else "Couldn't save that city. Try again?",
                )
            }
            if (!saved) return@launchSafely

            if (place != null) refreshWeather()
            runCatching { WeatherRefreshScheduler.sync(getApplication()) }
                .onFailure { Log.w(TAG, "could not sync the weather alarm", it) }
            renderLiveFormWidgets()
        }
    }

    /** Fetches a reading for the chosen city and shows it. Safe to call with no city set. */
    fun checkWeatherNow() {
        launchSafely("check the weather") {
            _state.update { it.copy(checkingWeather = true) }
            refreshWeather()
            _state.update { it.copy(checkingWeather = false) }
            renderLiveFormWidgets()
        }
    }

    private suspend fun refreshWeather() {
        runCatching { weather.refresh() }
            .onFailure { Log.w(TAG, "weather fetch failed", it) }
        _state.update { it.copy(weatherReading = weather.cachedReading()) }
    }

    /**
     * Redraws the widgets whose form depends on the sky.
     *
     * Filtered by [WidgetRenderer.allWidgetIds] because the config store also remembers
     * widgets that have since been removed from the home screen, and guarded per widget
     * exactly as `PokemonWidgetProvider.refreshLiveForms` already does — one widget that
     * cannot draw is not a reason to abandon the rest, still less to close the app.
     */
    private suspend fun renderLiveFormWidgets() {
        val renderer = WidgetRenderer(getApplication())
        val live = runCatching { WidgetRenderer.allWidgetIds(getApplication()).toSet() }
            .getOrDefault(emptySet())
        for (id in runCatching { store.knownWidgetIds() }.getOrDefault(emptyList())) {
            if (id !in live) continue
            runCatching { if (store.get(id).liveForm) renderer.render(id) }
                .onFailure { Log.e(TAG, "live-form re-render failed for widget $id", it) }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    // ---- Crash diagnostics -------------------------------------------------------

    fun refreshCrashReport() {
        _state.update { it.copy(crashReport = CrashLog.read(getApplication())) }
    }

    fun clearCrashReport() {
        CrashLog.clear(getApplication())
        _state.update { it.copy(crashReport = null) }
    }

    /**
     * `viewModelScope` dispatches on the main thread and has no exception handler, so an
     * unhandled throw in any coroutine launched from here closes the app. For a sideloaded
     * beta that is the worst of both worlds: the tester loses their place *and* learns
     * nothing. Every launch in this class goes through here instead, so the failure becomes
     * a logged line and a message on screen.
     */
    private fun launchSafely(what: String, block: suspend () -> Unit): Job =
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "could not $what", e)
                _state.update { it.copy(message = "Couldn't $what. ${e.javaClass.simpleName}") }
            }
        }

    private companion object {
        const val TAG = "MainViewModel"

        /**
         * Long enough to skip the intermediate states of a fast typist, short enough that
         * the grid still feels like it is keeping up.
         */
        const val QUERY_DEBOUNCE_MS = 120L

        /**
         * Prefer the GBA-era cry in the app, matching the widget's default so a Pokémon
         * sounds the same in both places. [SpriteSource.cryFile] falls back on its own
         * for the Pokémon that have no legacy recording.
         */
        const val LEGACY_CRY = true

        /** Request code for the pin callback; distinct from any widget id. */
        const val PIN_CALLBACK_REQUEST = 0x9101
    }
}
