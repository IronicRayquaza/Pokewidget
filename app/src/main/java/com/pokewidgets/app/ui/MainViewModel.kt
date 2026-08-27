package com.pokewidgets.app.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.data.WidgetConfigStore
import com.pokewidgets.app.widget.PokemonWidgetProvider
import com.pokewidgets.app.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val catalog = CatalogRepository.get(app)
    private val source = SpriteSource(app)
    private val store = WidgetConfigStore(app)

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var all: List<PokemonEntry> = emptyList()
    private var animatedIds: Set<Int> = emptySet()

    init {
        viewModelScope.launch {
            all = catalog.pokemon()
            animatedIds = catalog.animatedPokemonIds()
            _state.update {
                it.copy(
                    loading = false,
                    canPin = AppWidgetManager.getInstance(getApplication())
                        .isRequestPinAppWidgetSupported,
                )
            }
            applyFilters()
            refreshPlaced()
            refreshCacheSize()
        }
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q) }
        applyFilters()
    }

    fun setGeneration(gen: Int?) {
        _state.update { it.copy(generation = gen) }
        applyFilters()
    }

    fun setAnimatedOnly(only: Boolean) {
        _state.update { it.copy(animatedOnly = only) }
        applyFilters()
    }

    private fun applyFilters() {
        val s = _state.value
        val results = all.filter { entry ->
            entry.matches(s.query) &&
                (s.generation == null || entry.generation == s.generation) &&
                (!s.animatedOnly || entry.id in animatedIds)
        }
        _state.update { it.copy(results = results) }
    }

    fun openDetail(entry: PokemonEntry) {
        _state.update { it.copy(detail = entry, detailSets = emptyList()) }
        viewModelScope.launch {
            val sets = catalog.setsFor(entry.id)
                .sortedWith(compareByDescending<SpriteSet> { it.animated }.thenBy { it.order })
                .map { set ->
                    SetPreview(set, source.spriteUrl(set, SpriteKey(set.id, entry.id)))
                }
            _state.update { it.copy(detailSets = sets) }
        }
    }

    fun closeDetail() = _state.update { it.copy(detail = null, detailSets = emptyList()) }

    /**
     * Asks the launcher to place a widget directly, so the user never has to hunt through
     * the system widget tray. Supported since API 26; on launchers that decline, the
     * button is hidden rather than silently doing nothing.
     */
    fun requestPin(pokemonId: Int, setId: String) {
        val context = getApplication<Application>()
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return

        viewModelScope.launch {
            // The pin API carries no payload and never runs the configuration activity,
            // so stash the choice for the provider to adopt when the widget arrives.
            store.putPendingPin(WidgetConfig(pokemonId = pokemonId, setId = setId))

            val provider = ComponentName(context, PokemonWidgetProvider.Medium::class.java)
            manager.requestPinAppWidget(provider, null, null)
        }
    }

    fun refreshPlaced() {
        viewModelScope.launch {
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
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { source.cacheSizeBytes() }
            _state.update { it.copy(cacheBytes = bytes) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { source.clearCache() }
            refreshCacheSize()
        }
    }
}
