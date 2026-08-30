package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.ui.SetPreview
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.theme.dottedPaper

/**
 * The full grid of sprite sets, as its own page.
 *
 * Choosing is a single tap: the selection is applied immediately and the page pops, which
 * is the behaviour the row it replaced had. There is no separate confirm step because
 * there is nothing to confirm — the widget preview on the screen behind updates to show
 * exactly what was picked.
 */
@Composable
fun SpriteSetPickerScreen(
    pokemonName: String,
    sets: List<SetPreview>,
    selectedSetId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        PokeHeader("Sprite set", onBack = onBack)

        SpriteSetGrid(
            sets = sets,
            selectedSetId = selectedSetId,
            onSelect = { id ->
                onSelect(id)
                onBack()
            },
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            loading = sets.isEmpty(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Caption("Every game that drew " + pokemonName + ".")
                    Spacer(Modifier.height(4.dp))
                    SpriteSetGridCaption(
                        count = sets.size,
                        animated = sets.count { it.set.animated },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
