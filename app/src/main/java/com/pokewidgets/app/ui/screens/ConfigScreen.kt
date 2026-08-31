package com.pokewidgets.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.data.Fill
import com.pokewidgets.app.data.Smoothness
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.catalog.FormRules
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleStyle
import com.pokewidgets.app.ui.ConfigUiState
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeChip
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.TypeChip
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Chalk
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.PokeRed
import com.pokewidgets.app.ui.theme.Paper
import com.pokewidgets.app.ui.theme.dottedPaper
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.topRule
import com.pokewidgets.app.ui.theme.Card as CardColor

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onPickPokemon: () -> Unit,
    onPickSet: () -> Unit,
    onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        PokeHeader("Set up widget")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewPanel(state)

            Panel {
                SectionHeader("Pokémon")
                PokemonRow(state, onPickPokemon)
                Spacer(Modifier.height(16.dp))
                SectionHeader("Sprite set")
                SetRow(state, onPickSet)
                state.warning?.let {
                    Spacer(Modifier.height(10.dp))
                    Caption(it, color = MaterialTheme.colorScheme.error)
                }
            }

            VariantSection(state, onUpdate)
            LiveFormSection(state, onUpdate)
            AppearanceSection(state, onUpdate)
            AnimationSection(state, onUpdate)
            InteractionSection(state, onUpdate)

            Spacer(Modifier.height(8.dp))
        }

        // Edge-to-edge is on, so without the navigation-bar inset the Save button would
        // sit under the system gesture area and be unreachable.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Paper)
                .topRule()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            PokeButton(
                text = "Add to home screen",
                onClick = onSave,
                icon = Icons.Default.Check,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A settings group. One outlined card per topic, so the page reads as a stack of cards. */
@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .sticker(shape = MaterialTheme.shapes.medium, fill = CardColor, lift = 4.dp)
            .padding(16.dp),
    ) {
        content()
    }
}

/** The sprite standing on its plate, previewing the real background settings. */
@Composable
private fun PreviewPanel(state: ConfigUiState) {
    val config = state.config
    SpriteStage(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        ballFraction = 0.76f,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (config.showBackground) {
                        Modifier
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(config.cornerRadiusDp.dp))
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
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }
}

@Composable
private fun PokemonRow(state: ConfigUiState, onPick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interactions)
            .sticker(shape = MaterialTheme.shapes.small, fill = Chalk, lift = 3.dp)
            .clickable(interactionSource = interactions, indication = null, onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PokemonIcon(state.config.pokemonId, null, size = 48.dp)
        Column(Modifier.weight(1f)) {
            Text(
                state.entry?.displayName ?: "Choose a Pokémon",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.entry?.types?.forEach { TypeChip(it) }
            }
        }
        Text("CHANGE", style = MaterialTheme.typography.labelSmall, color = PokeRed)
    }
}

/**
 * The chosen sprite set, and the way into the full grid of alternatives.
 *
 * The grid used to be a horizontally-scrolling strip right here, which showed two of a
 * possible twenty sets and made comparing them a matter of memory. It is now its own page.
 */
@Composable
private fun SetRow(state: ConfigUiState, onPick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val set = state.selectedSet
    Row(
        Modifier
            .fillMaxWidth()
            .pressScale(interactions)
            .sticker(shape = MaterialTheme.shapes.small, fill = Chalk, lift = 3.dp)
            .clickable(interactionSource = interactions, indication = null, onClick = onPick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SpriteStage(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.extraSmall,
            borderWidth = 2.dp,
            lift = 0.dp,
            ballFraction = 0.7f,
            inset = 5.dp,
        ) {
            SpriteImage(state.previewUrl, set?.label, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(
                set?.label ?: "Choose a sprite set",
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(set?.hardware ?: "—")
                if (set?.animated == true) {
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Animated",
                        tint = PokeRed,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Caption("animated", color = PokeRed)
                }
            }
        }
        Text(
            (state.availableSets.size).toString() + " SETS",
            style = MaterialTheme.typography.labelSmall,
            color = PokeRed,
        )
    }
}

/**
 * Games whose battle sprites really do animate in the cartridge, but whose animation nobody
 * has ever published. veekun dumped Emerald and only Emerald, so these two are the app's
 * genuine gaps — everything else either has its animation shipped as a separate set, or has
 * no animation to find.
 */
private val ANIMATED_IN_ROM_ONLY = setOf(
    "versions_generation_iii_ruby_sapphire",
    "versions_generation_iii_firered_leafgreen",
)

/**
 * Why this set doesn't move on its own.
 *
 * There is no single true answer, which is the point: this text used to claim that every
 * still set's "real in-game animation only exists inside the ROM", which is false for Red and
 * Blue — those games never animated anything — and false again for Scarlet and Violet, which
 * animate a 3D model and have no 2D animation in the ROM either.
 */
private fun stillSetExplanation(set: SpriteSet?, available: List<SpriteSet>): String {
    if (set == null) return "This set ships as still images, so PokéWidget adds the movement."

    // Several games are in the app twice — a still dump and an animated one. Saying so is
    // more useful than any explanation, because the real thing is one tap away.
    val animatedSibling = available.firstOrNull { it.animated && it.game == set.game && it.id != set.id }
    return when {
        animatedSibling != null ->
            "${set.game}'s real animation is in the “${animatedSibling.label}” set — " +
                "pick that one for the genuine article."

        IdleAnimator.isRendered(set.id) ->
            "${set.game} animates a 3D model rather than a sprite, so there is no 2D " +
                "animation to fetch — PokéWidget adds the movement instead."

        set.id in ANIMATED_IN_ROM_ONLY ->
            "${set.game} does animate its sprites, but that animation only exists inside " +
                "the cartridge — nobody has published a dump of it, so PokéWidget generates " +
                "the movement instead."

        else ->
            "${set.game} never animated its sprites — this is exactly what the game drew, " +
                "and the movement is PokéWidget's."
    }
}

@Composable
private fun VariantSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val set = state.availableSets.firstOrNull { it.id == state.config.setId } ?: return
    val c = state.config
    // Per *this* Pokémon, not per set. Upstream's `female/` directories hold about forty
    // sprites while the set advertises the directory for all 1345, so asking the set-level
    // question here is what used to offer a Female chip that produced a permanent 404.
    val canShiny = set.covers(c.pokemonId, c.back, true, c.female, c.style)
    val canBack = set.covers(c.pokemonId, true, c.shiny, c.female, c.style)
    val canFemale = set.covers(c.pokemonId, c.back, c.shiny, true, c.style)
    if (!canShiny && !canBack && !canFemale) return

    Panel {
        SectionHeader("Variant")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canShiny) {
                PokeChip("Shiny", c.shiny, { onUpdate { it.copy(shiny = !it.shiny) } })
            }
            if (canBack) {
                PokeChip("Back", c.back, { onUpdate { it.copy(back = !it.back) } })
            }
            if (canFemale) {
                PokeChip("Female", c.female, { onUpdate { it.copy(female = !it.female) } })
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

/**
 * Offered only for the handful of Pokémon that have a real-world trigger, because for
 * everyone else it is a switch that does nothing. See `FormRules`.
 */
@Composable
private fun LiveFormSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    val trigger = FormRules.describe(c.pokemonId) ?: return
    val name = state.entry?.name ?: "This Pokémon"

    Panel {
        SectionHeader("Live form")
        SettingRow(
            title = "Follow the real world",
            subtitle = "$name $trigger",
        ) {
            PokeSwitch(c.liveForm) { on -> onUpdate { it.copy(liveForm = on) } }
        }
        AnimatedVisibility(visible = c.liveForm) {
            Column {
                Spacer(Modifier.height(10.dp))
                Caption(
                    if (state.weatherPlace == null) {
                        "Set a city in Settings and the widget will follow its weather. " +
                            "Until then it follows the clock only."
                    } else {
                        "Following the weather in ${state.weatherPlace}. The widget keeps " +
                            "the Pokémon you chose — only its form changes."
                    },
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    Panel {
        SectionHeader("Background")
        SettingRow(
            title = "Show a background",
            subtitle = "Off means the sprite floats on your wallpaper",
        ) {
            PokeSwitch(c.showBackground) { on -> onUpdate { it.copy(showBackground = on) } }
        }
        AnimatedVisibility(visible = c.showBackground) {
            Column {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BACKGROUND_SWATCHES.forEach { color ->
                        val chosen = c.backgroundColor == color
                        Box(
                            Modifier
                                .size(40.dp)
                                .sticker(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    fill = Color(color),
                                    borderWidth = if (chosen) 3.dp else 2.dp,
                                    lift = if (chosen) 4.dp else 2.dp,
                                )
                                .clickable { onUpdate { it.copy(backgroundColor = color) } },
                            contentAlignment = Alignment.Center,
                        ) {
                            // Selection needs a shape as well as a thicker keyline: six
                            // swatches differing only in outline weight is a difference
                            // nobody spots.
                            if (chosen) {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Caption("Corner radius · " + c.cornerRadiusDp + "dp")
                Slider(
                    value = c.cornerRadiusDp.toFloat(),
                    onValueChange = { v -> onUpdate { it.copy(cornerRadiusDp = v.toInt()) } },
                    valueRange = 0f..48f,
                    colors = SliderDefaults.colors(
                        thumbColor = Ink,
                        activeTrackColor = PokeRed,
                        inactiveTrackColor = Chalk,
                    ),
                )
            }
        }
    }
}

@Composable
private fun AnimationSection(state: ConfigUiState, onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit) {
    val c = state.config
    Panel {
        SectionHeader("Animation")
        OptionRow(
            options = Smoothness.entries,
            selected = c.smoothness,
            label = { it.label },
            onSelect = { s -> onUpdate { it.copy(smoothness = s) } },
        )
        Spacer(Modifier.height(10.dp))
        Caption(
            "Home-screen widgets have a fixed memory ceiling. Large sprites on large " +
                "widgets may animate a little slower than requested so the launcher stays " +
                "stable.",
        )

        // Only meaningful for still art. Showing it against Black/White or Showdown would
        // offer a choice that changes nothing.
        AnimatedVisibility(visible = state.selectedSet?.animated == false) {
            Column {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Idle movement")
                OptionRow(
                    options = IdleStyle.entries,
                    selected = c.idleStyle,
                    label = { it.label },
                    onSelect = { s -> onUpdate { it.copy(idleStyle = s) } },
                )
                Spacer(Modifier.height(10.dp))
                Caption(
                    c.idleStyle.description + ". " +
                        stillSetExplanation(state.selectedSet, state.availableSets),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
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
    Panel {
        SectionHeader("When tapped")
        OptionRow(
            options = TapAction.entries,
            selected = c.tapAction,
            label = { it.label },
            onSelect = { a -> onUpdate { it.copy(tapAction = a) } },
        )
        Spacer(Modifier.height(10.dp))
        Caption(c.tapAction.description)

        AnimatedVisibility(visible = c.tapAction == TapAction.CRY || c.tapAction == TapAction.EXCITE) {
            Column {
                Spacer(Modifier.height(16.dp))
                SettingRow(
                    title = "Play the cry",
                    subtitle = "Silent while your media volume is muted",
                ) {
                    PokeSwitch(c.cryEnabled) { on -> onUpdate { it.copy(cryEnabled = on) } }
                }
                AnimatedVisibility(visible = c.cryEnabled) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PokeChip(
                                "Game Boy cry",
                                c.legacyCry,
                                { onUpdate { it.copy(legacyCry = true) } },
                            )
                            PokeChip(
                                "Modern cry",
                                !c.legacyCry,
                                { onUpdate { it.copy(legacyCry = false) } },
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
private fun PokeSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Ink,
            checkedTrackColor = Lime,
            checkedBorderColor = Ink,
            uncheckedThumbColor = Ink,
            uncheckedTrackColor = CardColor,
            uncheckedBorderColor = Ink,
        ),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String?, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Ink)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Caption(it)
            }
        }
        Spacer(Modifier.width(12.dp))
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            PokeChip(label(option), option == selected, { onSelect(option) })
        }
    }
}
