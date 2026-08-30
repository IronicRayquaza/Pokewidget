package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.catalog.PokemonEntry
import com.pokewidgets.app.ui.components.EmptyHint
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeChip
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.PokeSearchField
import com.pokewidgets.app.ui.theme.Card as CardColor
import com.pokewidgets.app.ui.theme.dottedPaper

/**
 * Search and browse all 1345 catalogued Pokémon and forms.
 *
 * The list, names, types and icons are all bundled, so this screen is fully usable with
 * no network — only the sprite the user finally picks needs downloading.
 */
@Composable
fun PokemonPicker(
    all: List<PokemonEntry>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    var query by remember { mutableStateOf("") }
    var generation by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(all, query, generation) {
        all.filter { it.matches(query) && (generation == null || it.generation == generation) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        PokeHeader("Choose a Pokémon", onBack = onBack)

        PokeSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Name or dex number",
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
                    label = "All gens",
                    selected = generation == null,
                    onClick = { generation = null },
                )
            }
            items((1..9).toList()) { gen ->
                PokeChip(
                    label = "Gen $gen",
                    selected = generation == gen,
                    onClick = { generation = if (generation == gen) null else gen },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            EmptyHint(
                title = "No Pokémon match that",
                detail = "Nothing called “" + query + "” in this generation.",
                action = {
                    PokeButton(
                        text = "Clear filters",
                        onClick = {
                            query = ""
                            generation = null
                        },
                        container = CardColor,
                    )
                },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 116.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    PokemonCell(entry, entry.id == selectedId) { onSelect(entry.id) }
                }
            }
        }
    }
}
