package com.pokewidgets.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.data.WeatherSource
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.data.WidgetConfigStore
import com.pokewidgets.app.widget.WeatherRefreshScheduler
import com.pokewidgets.app.widget.WidgetRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfigUiState(
    val loading: Boolean = true,
    val config: WidgetConfig = WidgetConfig(),
    val entry: PokemonEntry? = null,
    /** Every set that can render the selected Pokémon, animated ones first. */
    val availableSets: List<SpriteSet> = emptyList(),
    /**
     * The same sets, each with a URL that previews *this* Pokémon in it.
     *
     * The picker shows the art rather than describing it: "Platinum" and
     * "HeartGold / SoulSilver" are indistinguishable as words and obvious as pictures.
     */
    val availableSetPreviews: List<SetPreview> = emptyList(),
    /** The set the widget is currently using, resolved from [config]. */
    val selectedSet: SpriteSet? = null,
    val allPokemon: List<PokemonEntry> = emptyList(),
    val previewUrl: String? = null,
    val warning: String? = null,
    /** The city weather is taken from, or null when none has been chosen in Settings. */
    val weatherPlace: String? = null,
)

class ConfigViewModel(app: Application) : AndroidViewModel(app) {

    private val catalog = CatalogRepository.get(app)
    private val source = SpriteSource(app)
    private val store = WidgetConfigStore(app)
    private val weather = WeatherSource(app)

    private val _state = MutableStateFlow(ConfigUiState())
    val state: StateFlow<ConfigUiState> = _state.asStateFlow()

    private var widgetId: Int = -1

    fun load(widgetId: Int) {
        this.widgetId = widgetId
        viewModelScope.launch {
            val all = catalog.pokemon()
            // Some launchers do run this activity for a widget pinned from the app. When
            // they do, the Pokemon the user picked is still sitting in the pending slot,
            // and starting from defaultConfig() would silently replace their choice with
            // Pikachu.
            val existing = when {
                store.exists(widgetId) -> store.get(widgetId)
                else -> store.takePendingPin() ?: defaultConfig()
            }
            _state.update { it.copy(loading = false, allPokemon = all, config = existing) }
            refreshDerived()
        }
    }

    /**
     * Showdown's animated set is the default because it is the only one that covers every
     * Pokémon and moves out of the box — a first-run widget should animate, not sit still.
     */
    private suspend fun defaultConfig(): WidgetConfig {
        val showdown = catalog.sets().firstOrNull { it.animated && 25 in it.frontIds }
        return WidgetConfig(setId = showdown?.id ?: catalog.sets().first().id)
    }

    fun update(transform: (WidgetConfig) -> WidgetConfig) {
        _state.update { it.copy(config = transform(it.config)) }
        viewModelScope.launch { refreshDerived() }
    }

    fun selectPokemon(pokemonId: Int) {
        viewModelScope.launch {
            val sets = catalog.setsFor(pokemonId)
            _state.update { current ->
                // Keep the chosen art style if it can render the new Pokémon; otherwise
                // fall back to the best animated set rather than showing an empty widget.
                val keep = sets.any { it.id == current.config.setId }
                val nextSet = if (keep) {
                    current.config.setId
                } else {
                    (sets.firstOrNull { it.animated } ?: sets.firstOrNull())?.id ?: current.config.setId
                }
                // Variants were carried across a Pokémon change untouched, so turning on
                // Female for a Pokémon that has one and then switching to one that does not
                // left an unrenderable key in the store. Re-check against the new Pokémon.
                val target = sets.firstOrNull { it.id == nextSet }
                val c = current.config
                current.copy(
                    config = c.copy(
                        pokemonId = pokemonId,
                        setId = nextSet,
                        back = c.back && target?.covers(pokemonId, true, c.shiny, c.female, c.style) == true,
                        shiny = c.shiny && target?.covers(pokemonId, c.back, true, c.female, c.style) == true,
                        female = c.female && target?.covers(pokemonId, c.back, c.shiny, true, c.style) == true,
                    ),
                )
            }
            refreshDerived()
        }
    }

    fun selectSet(setId: String) {
        viewModelScope.launch {
            val set = catalog.set(setId) ?: return@launch
            _state.update { current ->
                val c = current.config
                // Variant support differs wildly between sets — Emerald has no back
                // sprites, Scarlet/Violet has no shiny. Drop anything the new set lacks
                // instead of silently rendering the wrong file.
                current.copy(
                    config = c.copy(
                        setId = setId,
                        back = c.back && set.covers(c.pokemonId, true, c.shiny, c.female, c.style),
                        shiny = c.shiny && set.covers(c.pokemonId, c.back, true, c.female, c.style),
                        female = c.female && set.covers(c.pokemonId, c.back, c.shiny, true, c.style),
                        style = c.style?.takeIf { set.variants.keys.any { k -> k.contains(it) } },
                    ),
                )
            }
            refreshDerived()
        }
    }

    private suspend fun refreshDerived() {
        val config = _state.value.config
        val entry = catalog.entry(config.pokemonId)
        val sets = catalog.setsFor(config.pokemonId)
            .sortedWith(compareByDescending<SpriteSet> { it.animated }.thenBy { it.order })
        val set = catalog.set(config.setId)

        val supported =
            set?.covers(config.pokemonId, config.back, config.shiny, config.female, config.style) == true
        // Null means this set never drew this Pokémon in any variant — a different message
        // from "it has no shiny of it", and the only fix is a different set.
        val drawsAtAll = set?.resolveVariant(
            config.pokemonId, config.back, config.shiny, config.female, config.style,
        ) != null
        val url = set?.let { source.spriteUrl(it, config.spriteKey) }

        // Preview each set in the variant the user has chosen where it has one, and in
        // plain front art where it does not — a set with no shiny sprite should still
        // appear in the grid, showing what it *can* draw.
        val previews = sets.map { candidate ->
            val key = config.spriteKey.copy(setId = candidate.id)
            val previewUrl = source.spriteUrl(candidate, key)
                ?: source.spriteUrl(candidate, SpriteKey(candidate.id, config.pokemonId))
            SetPreview(candidate, previewUrl)
        }

        val place = weather.place()?.label

        _state.update {
            it.copy(
                entry = entry,
                weatherPlace = place,
                availableSets = sets,
                availableSetPreviews = previews,
                selectedSet = set,
                previewUrl = url,
                warning = when {
                    set == null -> "That sprite set is no longer available."
                    !drawsAtAll -> "${set.label} never drew this Pokémon — pick another set."
                    !supported ->
                        "${set.label} has no ${variantLabel(config)} sprite for this Pokémon, " +
                            "so the widget will show the standard one."
                    !set.animated -> null
                    else -> null
                },
            )
        }
    }

    private fun variantLabel(config: WidgetConfig): String = buildList {
        if (config.back) add("back")
        if (config.shiny) add("shiny")
        if (config.female) add("female")
    }.joinToString(" ").ifEmpty { "front" }

    /** Persists the config and pushes the first render. */
    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val config = _state.value.config.copy(excitedUntilMs = 0L)
            store.put(widgetId, config)
            // Turning a live form on is what creates the app's only recurring job, and
            // turning the last one off is what removes it.
            WeatherRefreshScheduler.sync(getApplication())
            WidgetRenderer(getApplication()).render(widgetId)
            onDone()
        }
    }
}
