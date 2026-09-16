package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.catalog.Trainer
import com.pokewidgets.app.catalog.TrainerIndex
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.EmptyHint
import com.pokewidgets.app.ui.components.PokeChip
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.PokeSearchField
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.components.SpriteImage
import com.pokewidgets.app.ui.components.SpriteStage
import com.pokewidgets.app.ui.components.pressScale
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.InkSoft
import com.pokewidgets.app.ui.theme.Lime
import com.pokewidgets.app.ui.theme.PokeRed
import com.pokewidgets.app.ui.theme.Sky
import com.pokewidgets.app.ui.theme.dottedPaper
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.Card as CardColor

/**
 * Every trainer Showdown has drawn, grouped by region, as a page of its own.
 *
 * Like the other pickers, choosing is one tap and the page closes; the preview behind shows
 * the result. Over a thousand sprites is too many to scroll blind, so the search and the
 * role filter are the main way in, and each region is a heading in the grid.
 */
@Composable
fun TrainerPickerScreen(
    index: TrainerIndex?,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var query by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf<String?>(null) }
    val results = remember(index, query, role) { index?.search(query, role).orEmpty() }
    val grouped = remember(results) { results.groupBy { it.region } }

    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        PokeHeader("Trainer", onBack = onBack)

        PokeSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search trainers or games",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            item { PokeChip("All", role == null, { role = null }) }
            items(index?.roles.orEmpty(), key = { it.id }) { r ->
                PokeChip(r.label, role == r.id, { role = if (role == r.id) null else r.id })
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f).navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Caption("Loading trainers…") }
            } else if (results.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyHint(
                        title = "No trainers match “$query”",
                        detail = "Try a name like Cynthia, a class like Hiker, or a game like Emerald.",
                    )
                }
            }

            for ((region, trainers) in grouped) {
                item(key = "header-$region", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = 8.dp)) {
                        SectionHeader(index?.regionLabel(region) ?: region)
                    }
                }
                items(trainers, key = { it.id }) { trainer ->
                    TrainerCell(
                        trainer = trainer,
                        url = index?.frontUrl(trainer),
                        roleLabel = index?.roleLabel(trainer.role).orEmpty(),
                        selected = trainer.id == selectedId,
                        onClick = {
                            onSelect(trainer.id)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrainerCell(
    trainer: Trainer,
    url: String?,
    roleLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    Column(
        Modifier
            .pressScale(interactions)
            .sticker(
                shape = MaterialTheme.shapes.small,
                fill = if (selected) Lime else CardColor,
                borderWidth = if (selected) 3.dp else 2.dp,
                lift = if (selected) 4.dp else 2.dp,
            )
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(6.dp),
    ) {
        Box {
            SpriteStage(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                fill = Sky,
                shape = MaterialTheme.shapes.extraSmall,
                borderWidth = 2.dp,
                lift = 0.dp,
                inset = 6.dp,
            ) {
                SpriteImage(url, trainer.displayName, Modifier.fillMaxSize())
            }
            if (trainer.hasBack) {
                // Worth flagging: these are the only trainers a battle scene can show
                // genuinely from behind.
                Text(
                    "BACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = PokeRed,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .sticker(shape = MaterialTheme.shapes.extraSmall, fill = CardColor, borderWidth = 1.dp, lift = 0.dp)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            trainer.name,
            style = MaterialTheme.typography.labelMedium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Always exactly two lines, so every card in the grid is the same height whatever the
        // game is called — "Let's Go" and "FireRed, LeafGreen & Emerald" sit side by side.
        Text(
            trainer.variant ?: roleLabel,
            style = MaterialTheme.typography.bodySmall,
            color = InkSoft,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
