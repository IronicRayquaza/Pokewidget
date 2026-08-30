package com.pokewidgets.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.ui.MainUiState
import com.pokewidgets.app.ui.PlacedWidget
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.EmptyHint
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeChip
import com.pokewidgets.app.ui.components.PokeIconButton
import com.pokewidgets.app.ui.components.PokeSearchField
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.InkSoft
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.Sky
import com.pokewidgets.app.ui.theme.dottedPaper
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.Card as CardColor

/**
 * The app's home: search every Pokémon, see what is already on the home screen, open one
 * to choose its art.
 *
 * Both of the screens that used to be modal bottom sheets — a Pokémon's sprite sets and
 * the settings — are now full pages. Sheets dismiss on a downward drag, which is the same
 * gesture as scrolling their contents, and both of these are things the user scrolls.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onQuery: (String) -> Unit,
    onGeneration: (Int?) -> Unit,
    onAnimatedOnly: (Boolean) -> Unit,
    onOpenDetail: (PokemonEntry) -> Unit,
    onCloseDetail: () -> Unit,
    onPin: (Int, String) -> Unit,
    onEditWidget: (Int) -> Unit,
    onClearCache: () -> Unit,
    onPlayCry: (Int) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    val detail = state.detail

    when {
        detail != null -> PokemonDetailScreen(
            entry = detail,
            sets = state.detailSets,
            canPin = state.canPin,
            onBack = onCloseDetail,
            onPin = onPin,
            onPlayCry = onPlayCry,
        )

        showSettings -> SettingsScreen(
            cacheBytes = state.cacheBytes,
            onClearCache = onClearCache,
            onBack = { showSettings = false },
        )

        else -> BrowseScreen(
            state = state,
            onQuery = onQuery,
            onGeneration = onGeneration,
            onAnimatedOnly = onAnimatedOnly,
            onOpenDetail = onOpenDetail,
            onEditWidget = onEditWidget,
            onOpenSettings = { showSettings = true },
        )
    }
}

@Composable
private fun BrowseScreen(
    state: MainUiState,
    onQuery: (String) -> Unit,
    onGeneration: (Int?) -> Unit,
    onAnimatedOnly: (Boolean) -> Unit,
    onOpenDetail: (PokemonEntry) -> Unit,
    onEditWidget: (Int) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("PokéWidget", style = MaterialTheme.typography.headlineMedium, color = Ink)
                Spacer(Modifier.height(6.dp))
                Caption("Put a Pokémon on your home screen")
            }
            PokeIconButton(Icons.Default.Settings, "Settings", onOpenSettings)
        }

        if (state.placed.isNotEmpty()) {
            PlacedWidgetsRow(state.placed, onEditWidget)
        }

        PokeSearchField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = "Search 1,345 Pokémon and forms",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                PokeChip(
                    label = "Animated",
                    selected = state.animatedOnly,
                    onClick = { onAnimatedOnly(!state.animatedOnly) },
                    icon = Icons.Default.PlayArrow,
                )
            }
            item {
                PokeChip(
                    label = "All gens",
                    selected = state.generation == null,
                    onClick = { onGeneration(null) },
                )
            }
            items((1..9).toList()) { gen ->
                PokeChip(
                    label = "Gen $gen",
                    selected = state.generation == gen,
                    onClick = { onGeneration(if (state.generation == gen) null else gen) },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> LoadingGrid()

            state.results.isEmpty() -> EmptyHint(
                title = "Nothing matches that",
                detail = if (state.query.isBlank()) {
                    "No Pokémon in this generation has an animated sprite set."
                } else {
                    "No Pokémon called “" + state.query + "” — try a dex number, or clear the filters."
                },
                action = {
                    PokeButton(
                        text = "Clear filters",
                        onClick = {
                            onQuery("")
                            onGeneration(null)
                            onAnimatedOnly(false)
                        },
                        container = CardColor,
                    )
                },
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 116.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(state.results, key = { it.id }) { entry ->
                    PokemonCell(entry, selected = false) { onOpenDetail(entry) }
                }
            }
        }
    }
}

/**
 * One Pokémon in the browse grid.
 *
 * Shared with the widget's own Pokémon picker so the two grids cannot drift apart — this
 * is the cell the user learns on the first screen and meets again while configuring.
 */
@Composable
fun PokemonCell(entry: PokemonEntry, selected: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val fill = if (selected) Lime else CardColor
    Column(
        Modifier
            .pressScale(interactions)
            .sticker(
                shape = MaterialTheme.shapes.medium,
                fill = fill,
                borderWidth = if (selected) 3.dp else 2.dp,
                lift = if (selected) 5.dp else 3.dp,
            )
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpriteStage(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            fill = Sky,
            shape = MaterialTheme.shapes.small,
            borderWidth = 2.dp,
            lift = 0.dp,
            ballFraction = 0.66f,
            inset = 8.dp,
        ) {
            PokemonIcon(entry.id, entry.displayName, size = 64.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            entry.form ?: ("#" + entry.dexNumber.toString().padStart(3, '0')),
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
    }
}

/** Skeleton cells while the bundled catalog is still being parsed off the main thread. */
@Composable
private fun LoadingGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 116.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(12) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .sticker(
                        shape = MaterialTheme.shapes.medium,
                        fill = com.pokewidgets.app.ui.theme.Chalk,
                        borderWidth = 2.dp,
                        lift = 3.dp,
                    ),
            )
        }
    }
}

/** The widgets already on the home screen, as a shortcut back into their settings. */
@Composable
private fun PlacedWidgetsRow(placed: List<PlacedWidget>, onEdit: (Int) -> Unit) {
    Column {
        SectionHeader("On your home screen", Modifier.padding(horizontal = 16.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(placed, key = { it.widgetId }) { widget ->
                val interactions = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .pressScale(interactions)
                        .sticker(shape = MaterialTheme.shapes.small, fill = CardColor, lift = 3.dp)
                        .clickable(
                            interactionSource = interactions,
                            indication = null,
                        ) { onEdit(widget.widgetId) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PokemonIcon(widget.config.pokemonId, null, size = 36.dp)
                    Column {
                        Text(
                            widget.entry?.name ?: ("Widget " + widget.widgetId),
                            style = MaterialTheme.typography.labelMedium,
                            color = Ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Caption(widget.set?.label ?: "—")
                    }
                    Spacer(Modifier.width(2.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}
