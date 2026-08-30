package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.ui.SetPreview
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.DexBadge
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeIconButton
import com.pokewidgets.app.ui.components.PokemonIcon
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.TypeChip
import com.pokewidgets.app.ui.components.cryHop
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.Paper
import com.pokewidgets.app.ui.theme.onColorFor
import com.pokewidgets.app.ui.theme.topRule
import com.pokewidgets.app.ui.theme.typeColor

/**
 * One Pokémon, full screen: who it is at the top, every sprite set it has underneath.
 *
 * This is a page rather than the modal bottom sheet it replaces. A sheet was the wrong
 * container for content the user is meant to browse and compare — it dismissed on the
 * same downward flick that scrolls it — and the whole point of the screen is to sit and
 * look at twenty versions of the same creature.
 */
@Composable
fun PokemonDetailScreen(
    entry: PokemonEntry,
    sets: List<SetPreview>,
    canPin: Boolean,
    onBack: () -> Unit,
    onPin: (Int, String) -> Unit,
    onPlayCry: (Int) -> Unit,
) {
    BackHandler(onBack = onBack)

    val accent = typeColor(entry.types.firstOrNull() ?: "unknown")
    val onAccent = onColorFor(accent)

    // Reset whenever the Pokémon changes, so opening a new one never carries the previous
    // one's choice — and default to something that actually moves.
    var chosenSetId by remember(entry.id) { mutableStateOf<String?>(null) }
    val selected = chosenSetId
        ?: sets.firstOrNull { it.set.animated }?.set?.id
        ?: sets.firstOrNull()?.set?.id

    Column(
        Modifier
            .fillMaxSize()
            .background(accent),
    ) {
        DetailHeader(
            entry = entry,
            sets = sets,
            onAccent = onAccent,
            onBack = onBack,
            onPlayCry = onPlayCry,
        )

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(SheetShape)
                .background(Paper),
        ) {
            SpriteSetGrid(
                sets = sets,
                selectedSetId = selected,
                onSelect = { chosenSetId = it },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
                loading = sets.isEmpty(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SectionHeader("Sprite set")
                        SpriteSetGridCaption(
                            count = sets.size,
                            animated = sets.count { it.set.animated },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            DetailFooter(
                entry = entry,
                canPin = canPin,
                selectedSetId = selected,
                onPin = onPin,
            )
        }
    }
}

private val SheetShape = androidx.compose.foundation.shape.RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
)

@Composable
private fun DetailHeader(
    entry: PokemonEntry,
    sets: List<SetPreview>,
    onAccent: androidx.compose.ui.graphics.Color,
    onBack: () -> Unit,
    onPlayCry: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PokeIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            DexBadge(entry.dexNumber)
        }

        Text(
            entry.name,
            style = MaterialTheme.typography.displaySmall,
            color = onAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entry.form?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = onAccent)
        }

        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entry.types.forEach { TypeChip(it) }
            Spacer(Modifier.weight(1f))
            Text(
                "Gen " + entry.generation,
                style = MaterialTheme.typography.labelMedium,
                color = onAccent,
            )
        }

        Spacer(Modifier.height(16.dp))
        HeroSprite(entry, sets, onPlayCry)
        Spacer(Modifier.height(18.dp))
    }
}

/**
 * The Pokémon itself, big, animated, and tappable.
 *
 * Every tap replays the cry and plays a squash-and-stretch hop, so the gesture lands even
 * with the volume down — a sprite that only makes a noise reads as broken on a muted
 * phone. It prefers an animated set so the sprite is already moving before it is touched.
 */
@Composable
private fun HeroSprite(
    entry: PokemonEntry,
    sets: List<SetPreview>,
    onPlayCry: (Int) -> Unit,
) {
    // Counts taps rather than tracking a boolean, so tapping repeatedly restarts the hop
    // each time instead of only toggling it.
    var hops by remember(entry.id) { mutableStateOf(0) }
    val interactions = remember { MutableInteractionSource() }
    val hero = remember(sets) { sets.firstOrNull { it.set.animated } ?: sets.firstOrNull() }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SpriteStage(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clickable(interactionSource = interactions, indication = null) {
                    hops++
                    onPlayCry(entry.id)
                },
            ballFraction = 0.78f,
        ) {
            if (hero == null) {
                PokemonIcon(entry.id, entry.displayName, size = 96.dp)
            } else {
                SpriteImage(
                    hero.url,
                    entry.displayName,
                    Modifier
                        .fillMaxSize()
                        .pressScale(interactions)
                        .cryHop(hops),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Caption("Tap to hear the cry", color = Ink.copy(alpha = 0.65f))
    }
}

@Composable
private fun DetailFooter(
    entry: PokemonEntry,
    canPin: Boolean,
    selectedSetId: String?,
    onPin: (Int, String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper)
            .topRule()
            .navigationBarsPadding()
            .padding(16.dp),
    ) {
        if (canPin) {
            PokeButton(
                text = "Add " + entry.name + " to home screen",
                onClick = { selectedSetId?.let { onPin(entry.id, it) } },
                icon = Icons.Default.Add,
                enabled = selectedSetId != null,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // Some launchers decline requestPinAppWidget. Saying so beats a button that
            // silently does nothing.
            Caption(
                "This launcher does not support adding widgets from inside an app. " +
                    "Long-press your home screen and pick PokéWidget from the widget list.",
            )
        }
    }
}
