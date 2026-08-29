package com.pokewidgets.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.data.Fill
import com.pokewidgets.app.data.Smoothness
import com.pokewidgets.app.sprite.IdleStyle
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.ui.ConfigUiState
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.theme.GbaScreen
import com.pokewidgets.app.ui.theme.GbaScreenDark
import com.pokewidgets.app.ui.theme.typeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onPickPokemon: () -> Unit,
    onSelectSet: (String) -> Unit,
    onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Set up widget") }) },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    // Edge-to-edge is on, so the bar would otherwise sit under the
                    // system navigation and the Save button would be unreachable.
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add to home screen")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            PreviewPanel(state)

            SectionHeader("Pokémon")
            PokemonRow(state, onPickPokemon)

            SectionHeader("Sprite set")
            SetPicker(state, onSelectSet)

            state.warning?.let { warning ->
                Text(
                    warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            VariantSection(state, onUpdate)
            AppearanceSection(state, onUpdate)
            AnimationSection(state, onUpdate)
            InteractionSection(state, onUpdate)

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A GBA screen the sprite sits on, previewing the real background settings. */
@Composable
private fun PreviewPanel(state: ConfigUiState) {
    val config = state.config
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(listOf(GbaScreenDark, GbaScreen)),
            )
            .border(3.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(180.dp)
                .then(
                    if (config.showBackground) {
                        Modifier
                            .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
                            .background(Color(config.backgroundColor))
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            SpriteImage(
                url = state.previewUrl,
                contentDescription = state.entry?.displayName,
                modifier = Modifier.size(160.dp),
            )
        }
    }
}

@Composable
private fun PokemonRow(state: ConfigUiState, onPick: () -> Unit) {
    Card(
        onClick = onPick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PokemonIcon(state.config.pokemonId, null, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    state.entry?.displayName ?: "Choose a Pokémon",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.entry?.types?.forEach { TypeChip(it) }
                }
            }
            Text("Change", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun TypeChip(type: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(typeColor(type))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            type.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun SetPicker(state: ConfigUiState, onSelect: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(state.availableSets, key = { it.id }) { set ->
            SetCard(set, selected = set.id == state.config.setId) { onSelect(set.id) }
        }
    }
}

@Composable
private fun SetCard(set: SpriteSet, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The badge is the point of the whole picker: it tells you at a glance
                // which of these will actually move on your home screen.
                if (set.animated) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Animated",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    set.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                set.hardware,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!set.animated) {
                Text(
                    "still · generated idle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VariantSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val set = state.availableSets.firstOrNull { it.id == state.config.setId } ?: return
    val c = state.config
    val canShiny = set.supports(c.back, true, c.female, c.style)
    val canBack = set.supports(true, c.shiny, c.female, c.style)
    val canFemale = set.supports(c.back, c.shiny, true, c.style)
    if (!canShiny && !canBack && !canFemale) return

    Column {
        SectionHeader("Variant")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canShiny) {
                FilterChip(
                    selected = c.shiny,
                    onClick = { onUpdate { it.copy(shiny = !it.shiny) } },
                    label = { Text("Shiny") },
                )
            }
            if (canBack) {
                FilterChip(
                    selected = c.back,
                    onClick = { onUpdate { it.copy(back = !it.back) } },
                    label = { Text("Back") },
                )
            }
            if (canFemale) {
                FilterChip(
                    selected = c.female,
                    onClick = { onUpdate { it.copy(female = !it.female) } },
                    label = { Text("Female") },
                )
            }
        }
    }
}

private val BACKGROUND_SWATCHES = listOf(
    0xCC1B1F27.toInt(),
    0xCCFFFFFF.toInt(),
    0xCC2E4B12.toInt(),
    0xCC30435E.toInt(),
    0xCC5B2333.toInt(),
    0x66000000,
)

@Composable
private fun AppearanceSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    Column {
        SectionHeader("Background")
        SettingRow(
            title = "Show a background",
            subtitle = "Off means the sprite floats on your wallpaper",
        ) {
            Switch(
                checked = c.showBackground,
                onCheckedChange = { on -> onUpdate { it.copy(showBackground = on) } },
            )
        }
        AnimatedVisibility(visible = c.showBackground) {
            Column {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BACKGROUND_SWATCHES.forEach { color ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(color))
                                .border(
                                    width = if (c.backgroundColor == color) 3.dp else 1.dp,
                                    color = if (c.backgroundColor == color) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { onUpdate { it.copy(backgroundColor = color) } },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Corner radius · ${c.cornerRadiusDp}dp", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = c.cornerRadiusDp.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(cornerRadiusDp = v.toInt()) } },
                    valueRange = 0f..48f,
                )
            }
        }
    }
}

@Composable
private fun AnimationSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    Column {
        SectionHeader("Animation")
        OptionRow(
            options = Smoothness.entries,
            selected = c.smoothness,
            label = { it.label },
            onSelect = { s -> onUpdate { it.copy(smoothness = s) } },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Home-screen widgets have a fixed memory ceiling. Large sprites on large " +
                "widgets may animate a little slower than requested so the launcher stays stable.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Only meaningful for still art. Showing it against Black/White or Showdown would
        // offer a choice that changes nothing.
        AnimatedVisibility(visible = state.selectedSet?.animated == false) {
            Column {
                Spacer(Modifier.height(16.dp))
                SectionHeader("Idle movement")
                OptionRow(
                    options = IdleStyle.entries,
                    selected = c.idleStyle,
                    label = { it.label },
                    onSelect = { s -> onUpdate { it.copy(idleStyle = s) } },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${c.idleStyle.description}. ${state.selectedSet?.label ?: "This set"} " +
                        "ships as still images — its real in-game animation only exists " +
                        "inside the ROM, so PokéWidget generates the movement instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionHeader("Sprite size")
        OptionRow(
            options = Fill.entries,
            selected = c.fill,
            label = { it.label },
            onSelect = { f -> onUpdate { it.copy(fill = f) } },
        )
    }
}

@Composable
private fun InteractionSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    Column {
        SectionHeader("When tapped")
        OptionRow(
            options = TapAction.entries,
            selected = c.tapAction,
            label = { it.label },
            onSelect = { a -> onUpdate { it.copy(tapAction = a) } },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            c.tapAction.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AnimatedVisibility(visible = c.tapAction == TapAction.CRY || c.tapAction == TapAction.EXCITE) {
            Column {
                Spacer(Modifier.height(12.dp))
                SettingRow(title = "Play the cry", subtitle = "Stays silent when your phone is on silent") {
                    Switch(
                        checked = c.cryEnabled,
                        onCheckedChange = { on -> onUpdate { it.copy(cryEnabled = on) } },
                    )
                }
                AnimatedVisibility(visible = c.cryEnabled) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = c.legacyCry,
                                onClick = { onUpdate { it.copy(legacyCry = true) } },
                                label = { Text("Game Boy cry") },
                            )
                            FilterChip(
                                selected = !c.legacyCry,
                                onClick = { onUpdate { it.copy(legacyCry = false) } },
                                label = { Text("Modern cry") },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Small shared pieces --------------------------------------------------------

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String?, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        control()
    }
}

@Composable
private fun <T> OptionRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { index ->
            val option = options[index]
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option), textAlign = TextAlign.Center) },
            )
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
}
