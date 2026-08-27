package com.pokewidgets.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.pokewidgets.app.ui.SetPreview
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SpriteImage

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PokéWidget") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            if (state.placed.isNotEmpty()) {
                PlacedWidgetsRow(state.placed, onEditWidget)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search 1,345 Pokémon and forms") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            Spacer(Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.animatedOnly,
                        onClick = { onAnimatedOnly(!state.animatedOnly) },
                        label = { Text("Animated only") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                    )
                }
                item {
                    FilterChip(
                        selected = state.generation == null,
                        onClick = { onGeneration(null) },
                        label = { Text("All gens") },
                    )
                }
                items((1..9).toList()) { gen ->
                    FilterChip(
                        selected = state.generation == gen,
                        onClick = { onGeneration(if (state.generation == gen) null else gen) },
                        label = { Text("Gen $gen") },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when {
                state.loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.results.isEmpty() -> EmptyHint("Nothing matches that search.")

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.id }) { entry ->
                        Card(
                            modifier = Modifier.clickable { onOpenDetail(entry) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PokemonIcon(entry.id, entry.displayName, size = 56.dp)
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                entry.form?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.detail?.let { entry ->
        ModalBottomSheet(onDismissRequest = onCloseDetail) {
            DetailSheet(entry, state.detailSets, state.canPin, onPin)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsSheet(state.cacheBytes, onClearCache)
        }
    }
}

/**
 * Every sprite set that can render this Pokémon, previewed live side by side — the
 * closest thing the app has to the reference sprite gallery, and the fastest way to see
 * which game's art you actually want on your home screen.
 */
@Composable
private fun DetailSheet(
    entry: PokemonEntry,
    sets: List<SetPreview>,
    canPin: Boolean,
    onPin: (Int, String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text(entry.displayName, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.types.forEach { TypeChip(it) }
            Spacer(Modifier.width(4.dp))
            Text(
                "#${entry.dexNumber} · Gen ${entry.generation}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (sets.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SectionHeader("${sets.size} sprite sets available")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(sets, key = { it.set.id }) { preview ->
                    Column(
                        Modifier.width(120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(104.dp)
                                .clickable { onPin(entry.id, preview.set.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            SpriteImage(preview.url, preview.set.label, Modifier.fillMaxSize())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (preview.set.animated) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Animated",
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                preview.set.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            preview.set.hardware,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (canPin) {
            val best = sets.firstOrNull { it.set.animated } ?: sets.firstOrNull()
            SecondaryButton(
                text = "Add ${entry.name} to home screen",
                onClick = { best?.let { onPin(entry.id, it.set.id) } },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "Long-press your home screen and pick PokéWidget from the widget list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlacedWidgetsRow(placed: List<PlacedWidget>, onEdit: (Int) -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        Text(
            "On your home screen",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(placed, key = { it.widgetId }) { widget ->
                Card(
                    modifier = Modifier.clickable { onEdit(widget.widgetId) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        PokemonIcon(widget.config.pokemonId, null, size = 36.dp)
                        Column {
                            Text(
                                widget.entry?.name ?: "Widget ${widget.widgetId}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                widget.set?.label ?: "—",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSheet(cacheBytes: Long, onClearCache: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 28.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        SectionHeader("Sprite cache")
        Text(
            "%.1f MB of sprites and cries stored on this device.".format(cacheBytes / 1_048_576.0),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Sprites download once and are kept forever — every URL is pinned to a fixed " +
                "revision, so a cached sprite can never go stale. Your widgets keep " +
                "animating with no connection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onClearCache) { Text("Clear cache") }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Credits")
        Text(
            "Sprites and cries come from the community PokéAPI mirrors. Pokémon and all " +
                "related art are trademarks of Nintendo, Creatures Inc. and GAME FREAK inc. " +
                "This is an unofficial fan project with no affiliation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
