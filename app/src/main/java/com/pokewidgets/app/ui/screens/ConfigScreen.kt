package com.pokewidgets.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pokewidgets.app.catalog.BattleBackground
import com.pokewidgets.app.catalog.ShinyAvailability
import com.pokewidgets.app.data.Fill
import com.pokewidgets.app.data.Scene
import com.pokewidgets.app.data.Smoothness
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.TrainerPose
import com.pokewidgets.app.data.TrainerSide
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.catalog.FormRules
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.sprite.DrawnScenery
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleStyle
import com.pokewidgets.app.ui.BackgroundMode
import com.pokewidgets.app.ui.ConfigUiState
import com.pokewidgets.app.ui.backgroundMode
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.MirrorChip
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeChip
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.components.ShinyChip
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.TypeChip
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Chalk
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.widget.SceneLayout
import kotlin.math.roundToInt
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.PokeRed
import com.pokewidgets.app.ui.theme.Paper
import com.pokewidgets.app.ui.theme.dottedPaper
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.topRule
import com.pokewidgets.app.ui.theme.Card as CardColor

/** The setup actions that are more than "change one field", grouped to keep the signature short. */
data class ConfigActions(
    val toggleShiny: () -> Unit,
    val selectTrainer: (String?) -> Unit,
    val setBackgroundMode: (BackgroundMode) -> Unit,
    val applyBattleScene: () -> Unit,
)

@Composable
fun ConfigScreen(
    state: ConfigUiState,
    onPickPokemon: () -> Unit,
    onPickSet: () -> Unit,
    onPickTrainer: () -> Unit,
    actions: ConfigActions,
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
            PreviewToggles(state, actions, onUpdate)

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
            AppearanceSection(state, actions, onUpdate)
            TrainerSection(state, actions, onPickTrainer, onUpdate)
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

/**
 * The widget as it will look: background, trainer and Pokémon, laid out by the same
 * [SceneLayout] the widget renderer uses, so the two cannot disagree about where anyone
 * stands.
 */
@Composable
private fun PreviewPanel(state: ConfigUiState) {
    val config = state.config
    val background = state.backgrounds?.background(config.backgroundId)
    val trainerArt = state.trainerArt
    val scene = if (trainerArt == null) Scene.SOLO else config.effectiveScene
    val trainerMirrored = state.trainerIsStandIn != config.trainerFlip
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(config.cornerRadiusDp.dp)

    SpriteStage(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        ballFraction = 0.76f,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(
                    when {
                        background != null -> Modifier.clip(shape)
                        config.showBackground -> Modifier.clip(shape).background(Color(config.backgroundColor))
                        else -> Modifier
                    },
                ),
        ) {
            val w = maxWidth.value.roundToInt()
            val h = maxHeight.value.roundToInt()
            val mirrorScene = scene == Scene.BATTLE && config.trainerSide == TrainerSide.RIGHT
            if (background != null) {
                BattleBackgroundImage(background, Modifier.fillMaxSize(), mirrored = mirrorScene)
            }
            val onScenery = background?.isScenery == true
            val stage = background?.takeIf { !onScenery }?.let { SceneLayout.battleFrame(it, w, h).second }
                ?: SceneLayout.OPEN_STAGE
            val layout = SceneLayout.layout(
                scene, config.trainerSide, w, h, stage,
                onScenery = onScenery,
                onBattlefield = background != null && !onScenery,
            )
            val align = if (layout.anchorBottom) Alignment.BottomCenter else Alignment.Center

            @Composable
            fun Trainer() {
                val box = layout.trainer ?: return
                if (trainerArt == null) return
                val image = remember(trainerArt) { trainerArt.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = state.trainer?.displayName,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomCenter,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier
                        .offset(box.left.dp, box.top.dp)
                        .size(box.width.dp, box.height.dp)
                        .graphicsLayer { scaleX = if (trainerMirrored) -1f else 1f },
                )
            }

            if (!layout.trainerInFront) Trainer()
            val p = layout.pokemon
            SpriteImage(
                url = state.previewUrl,
                contentDescription = state.entry?.displayName,
                alignment = align,
                modifier = Modifier
                    .offset(p.left.dp, p.top.dp)
                    .size(p.width.dp, p.height.dp)
                    .padding(12.dp)
                    .graphicsLayer { scaleX = if (config.flipHorizontal) -1f else 1f },
            )
            if (layout.trainerInFront) Trainer()
        }
    }
}

/**
 * The two things people most often want to change, right under the picture of the widget:
 * shiny, and which way the Pokémon faces.
 */
@Composable
private fun PreviewToggles(
    state: ConfigUiState,
    actions: ConfigActions,
    onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit,
) {
    val c = state.config
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ShinyChip(
                on = c.shiny,
                onToggle = actions.toggleShiny,
                available = state.shiny == ShinyAvailability.AVAILABLE ||
                    state.shiny == ShinyAvailability.NOT_WITH_THESE_OPTIONS,
            )
            MirrorChip(on = c.flipHorizontal, onToggle = { onUpdate { it.copy(flipHorizontal = !it.flipHorizontal) } })
        }
        AnimatedVisibility(visible = state.notice != null) {
            Caption(
                state.notice.orEmpty(),
                Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
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
            Caption(set?.hardware ?: "—")
            // Its own line: squeezed in beside a long hardware name like "Game Boy Advance",
            // "animated" used to break mid-word.
            if (set?.animated == true) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = PokeRed,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Animated",
                        style = MaterialTheme.typography.bodySmall,
                        color = PokeRed,
                        maxLines = 1,
                        softWrap = false,
                    )
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
    // Shiny lives by the preview now, where it is always visible.
    val canBack = set.covers(c.pokemonId, true, c.shiny, c.female, c.style)
    val canFemale = set.covers(c.pokemonId, c.back, c.shiny, true, c.style)
    if (!canBack && !canFemale) return

    Panel {
        SectionHeader("Variant")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun AppearanceSection(
    state: ConfigUiState,
    actions: ConfigActions,
    onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit,
) {
    val c = state.config
    val mode = c.backgroundMode(state.backgrounds)
    Panel {
        SectionHeader("Background")
        OptionRow(
            // Battlefields have their platforms painted in, which only make sense in a battle:
            // they are offered with the Battle layout, and kept visible for a widget that
            // already has one so it can still be changed.
            options = BackgroundMode.entries.filter {
                it != BackgroundMode.BATTLE || c.effectiveScene == Scene.BATTLE || mode == BackgroundMode.BATTLE
            },
            selected = mode,
            label = { it.label },
            onSelect = actions.setBackgroundMode,
        )
        Spacer(Modifier.height(6.dp))
        Caption(
            when (mode) {
                BackgroundMode.OFF -> "The sprite floats on your wallpaper."
                BackgroundMode.COLOR -> "A plain plate behind the sprite."
                BackgroundMode.SCENERY -> "Open skies and landscapes. Your Pokémon stands on the ground, wherever it is."
                BackgroundMode.BATTLE -> "A battlefield from the games, with the Pokémon on its far platform."
            },
        )

        AnimatedVisibility(visible = mode == BackgroundMode.COLOR) {
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
            }
        }

        AnimatedVisibility(visible = mode == BackgroundMode.SCENERY || mode == BackgroundMode.BATTLE) {
            Column {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    val choices = if (mode == BackgroundMode.SCENERY) state.backgrounds?.scenery else state.backgrounds?.battlefields
                    items(choices.orEmpty(), key = { it.id }) { bg ->
                        BackgroundThumb(
                            background = bg,
                            selected = bg.id == c.backgroundId,
                            onClick = { onUpdate { it.copy(showBackground = true, backgroundId = bg.id) } },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = mode != BackgroundMode.OFF) {
            Column {
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
private fun BackgroundThumb(background: BattleBackground, selected: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(112.dp)
            .pressScale(interactions)
            .sticker(
                shape = MaterialTheme.shapes.small,
                fill = if (selected) Lime else CardColor,
                borderWidth = if (selected) 3.dp else 2.dp,
                lift = if (selected) 4.dp else 2.dp,
            )
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(6.dp),
    ) {
        BattleBackgroundImage(
            background,
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            background.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The optional trainer. Everything here starts switched off; a widget only ever gets a
 * trainer because someone chose one on this panel.
 */
@Composable
private fun TrainerSection(
    state: ConfigUiState,
    actions: ConfigActions,
    onPickTrainer: () -> Unit,
    onUpdate: ((WidgetConfig) -> WidgetConfig) -> Unit,
) {
    val c = state.config
    val trainer = state.trainer
    Panel {
        SectionHeader("Trainer")
        if (trainer == null) {
            Caption("Pair your Pokémon with a trainer — gym leaders, champions and rivals from every region.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PokeButton(
                    text = "Choose a trainer",
                    onClick = onPickTrainer,
                    container = CardColor,
                    modifier = Modifier.weight(1f),
                )
                PokeButton(
                    text = "Battle scene",
                    onClick = actions.applyBattleScene,
                    modifier = Modifier.weight(1f),
                )
            }
            return@Panel
        }

        TrainerRow(state, onPickTrainer)
        Spacer(Modifier.height(14.dp))

        Caption("Layout")
        OptionRow(
            options = listOf(Scene.SIDE_BY_SIDE, Scene.BATTLE),
            selected = c.effectiveScene,
            label = { it.label },
            onSelect = { s -> onUpdate { it.copy(scene = s) } },
        )
        Caption(c.effectiveScene.description)

        Spacer(Modifier.height(12.dp))
        Caption("Trainer faces")
        OptionRow(
            options = TrainerPose.entries,
            selected = c.trainerPose,
            label = { it.label },
            onSelect = { p -> onUpdate { it.copy(trainerPose = p) } },
        )
        if (state.trainerIsStandIn) {
            // Honest about it: no game ever drew this trainer from behind.
            Caption("No game drew ${trainer.name} from behind, so the front is shown turned around.")
        }

        Spacer(Modifier.height(12.dp))
        Caption("Stands on the")
        OptionRow(
            options = TrainerSide.entries,
            selected = c.trainerSide,
            label = { it.label },
            onSelect = { s -> onUpdate { it.copy(trainerSide = s) } },
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MirrorChip(
                on = c.trainerFlip,
                onToggle = { onUpdate { it.copy(trainerFlip = !it.trainerFlip) } },
                label = "Mirror trainer",
            )
            PokeChip("Remove trainer", false, { actions.selectTrainer(null) })
        }
        if (c.effectiveScene != Scene.BATTLE) {
            Spacer(Modifier.height(12.dp))
            PokeButton(
                text = "Make it a battle scene",
                onClick = actions.applyBattleScene,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrainerRow(state: ConfigUiState, onPick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val trainer = state.trainer ?: return
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
            inset = 4.dp,
        ) {
            SpriteImage(state.trainers?.frontUrl(trainer), trainer.displayName, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(trainer.name, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(6.dp))
            Caption(
                listOfNotNull(
                    state.trainers?.roleLabel(trainer.role),
                    state.trainers?.regionLabel(trainer.region),
                    trainer.variant,
                ).joinToString(" · "),
            )
        }
        Text("CHANGE", style = MaterialTheme.typography.labelSmall, color = PokeRed)
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
        Caption(c.fill.description)
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

/**
 * A battle background showing only its scenery, cropped to fill [modifier]'s box the way the
 * widget crops it. Showdown's Gen 3 and 4 images carry a whole battle screen around the field.
 */
@Composable
private fun BattleBackgroundImage(background: BattleBackground, modifier: Modifier, mirrored: Boolean = false) {
    BoxWithConstraints(modifier.clipToBounds().graphicsLayer { scaleX = if (mirrored) -1f else 1f }) {
        val boxW = maxWidth.value
        val boxH = maxHeight.value
        // The same crop the widget uses, so platforms line up with where figures stand.
        val w = boxW.roundToInt().coerceAtLeast(1)
        val h = boxH.roundToInt().coerceAtLeast(1)
        val crop = if (background.isScenery) SceneLayout.sceneryCrop(background, w, h) else SceneLayout.battleFrame(background, w, h).first
        val scale = maxOf(boxW / crop.width, boxH / crop.height)
        val dx = -crop.left * scale - (crop.width * scale - boxW) / 2
        val dy = -crop.top * scale - (crop.height * scale - boxH) / 2
        val placed = Modifier
            .wrapContentSize(Alignment.TopStart, unbounded = true)
            .offset(dx.dp, dy.dp)
            .requiredSize((background.w * scale).dp, (background.h * scale).dp)
        if (background.isDrawn) {
            val image = remember(background.id) { DrawnScenery.bitmap(background.id)?.asImageBitmap() }
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.None,
                    modifier = placed,
                )
            }
        } else {
            AsyncImage(
                model = background.url,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                // Scenery is pixel art and stays crisp; Showdown's battlefields are painted.
                filterQuality = if (background.isScenery) FilterQuality.None else FilterQuality.Low,
                modifier = placed,
            )
        }
    }
}
