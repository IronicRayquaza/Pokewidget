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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.ui.SetPreview
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Chalk
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.InkSoft
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.PokeRed
import com.pokewidgets.app.ui.theme.Sky
import com.pokewidgets.app.ui.theme.onColorFor
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.Card as CardColor

/**
 * Every sprite set that can draw one Pokémon, previewed live, side by side.
 *
 * This replaced a horizontally-scrolling strip inside a modal bottom sheet, which was
 * wrong on two counts. A sheet dismisses on any downward drag, so the flick that scrolls
 * a row of sprites is a hair away from the flick that throws the whole thing away — the
 * picker kept closing while it was being used. And a single row shows three of a possible
 * twenty sets, so choosing between them meant scrolling blind through the other
 * seventeen. A grid on its own page has neither problem: nothing dismisses it by
 * accident, and the comparison the screen exists to support is actually visible.
 */
@Composable
fun SpriteSetGrid(
    sets: List<SetPreview>,
    selectedSetId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    loading: Boolean = false,
    header: LazyGridScope.() -> Unit = {},
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        header()

        if (loading && sets.isEmpty()) {
            // Placeholders that match the real cell's footprint, so the grid does not
            // reflow under the user's thumb the moment the previews resolve.
            items(SKELETON_CELLS) { SpriteSetSkeleton() }
        }

        items(sets, key = { it.set.id }) { preview ->
            SpriteSetCell(
                preview = preview,
                selected = preview.set.id == selectedSetId,
                onClick = { onSelect(preview.set.id) },
            )
        }

        if (!loading && sets.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Caption(
                    "No sprite set covers this Pokémon yet.",
                    Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
    }
}

/** Enough to fill the first screenful of a two-column grid. */
private const val SKELETON_CELLS = 6

/**
 * One set, showing the actual art rather than describing it.
 *
 * The preview is the real sprite from the real set, animated where the set is animated,
 * because "Platinum" and "HeartGold / SoulSilver" are indistinguishable as words and
 * obvious as pictures.
 */
@Composable
private fun SpriteSetCell(preview: SetPreview, selected: Boolean, onClick: () -> Unit) {
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
            // 8dp of padding inside a 20dp corner leaves the inner panel needing 12dp for
            // its corners to run parallel to the card's.
            .padding(8.dp),
    ) {
        Box {
            SpriteStage(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                fill = Sky,
                shape = MaterialTheme.shapes.small,
                borderWidth = 2.dp,
                lift = 0.dp,
                inset = 10.dp,
            ) {
                SpriteImage(preview.url, preview.set.label, Modifier.fillMaxSize())
            }
            if (preview.set.animated) {
                AnimatedBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            if (selected) {
                SelectedTick(Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            preview.set.label,
            style = MaterialTheme.typography.labelMedium,
            color = onColorFor(fill),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            preview.set.hardware,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Ink.copy(alpha = 0.7f) else InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp),
        )
        Spacer(Modifier.height(2.dp))
    }
}

/**
 * Marks the sets that genuinely move on the home screen.
 *
 * This is the single most useful fact on the card: seven of the app's sets ship real
 * animation and the rest are stills that PokéWidget animates procedurally, and the
 * difference is invisible in a static preview grid.
 */
@Composable
private fun AnimatedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .sticker(
                shape = MaterialTheme.shapes.extraSmall,
                fill = PokeRed,
                borderWidth = 2.dp,
                lift = 2.dp,
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = onColorFor(PokeRed),
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.size(3.dp))
        Text("ANIM", style = MaterialTheme.typography.labelSmall, color = onColorFor(PokeRed))
    }
}

@Composable
private fun SelectedTick(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .sticker(
                shape = MaterialTheme.shapes.extraSmall,
                fill = CardColor,
                borderWidth = 2.dp,
                lift = 2.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = "Selected",
            tint = Ink,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SpriteSetSkeleton() {
    Column(
        Modifier
            .sticker(
                shape = MaterialTheme.shapes.medium,
                fill = CardColor,
                borderWidth = 2.dp,
                lift = 3.dp,
            )
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sticker(
                    shape = MaterialTheme.shapes.small,
                    fill = Chalk,
                    borderWidth = 2.dp,
                    lift = 0.dp,
                ),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp)
                .sticker(
                    shape = MaterialTheme.shapes.extraSmall,
                    fill = Chalk,
                    borderWidth = 0.dp,
                    lift = 0.dp,
                ),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth(0.45f)
                .height(10.dp)
                .sticker(
                    shape = MaterialTheme.shapes.extraSmall,
                    fill = Chalk,
                    borderWidth = 0.dp,
                    lift = 0.dp,
                ),
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** A one-line summary of what the grid is showing, used as its header row. */
@Composable
fun SpriteSetGridCaption(count: Int, animated: Int, modifier: Modifier = Modifier) {
    Text(
        text = when {
            count == 0 -> "Loading sprite sets…"
            animated == 0 -> "$count sets · none animate on their own"
            else -> "$count sets · $animated animate on their own"
        },
        style = MaterialTheme.typography.bodySmall,
        color = InkSoft,
        textAlign = TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}
