package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.SectionHeader
import com.pokewidgets.app.ui.theme.Ink
import com.pokewidgets.app.ui.theme.dottedPaper
import com.pokewidgets.app.ui.theme.sticker
import com.pokewidgets.app.ui.theme.Card as CardColor

@Composable
fun SettingsScreen(
    cacheBytes: Long,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        Modifier
            .fillMaxSize()
            .dottedPaper()
            .statusBarsPadding(),
    ) {
        PokeHeader("Settings", onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Panel {
                SectionHeader("Sprite cache")
                Text(
                    "%.1f MB".format(cacheBytes / 1_048_576.0),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Ink,
                )
                Spacer(Modifier.height(8.dp))
                Caption(
                    "Sprites and cries download once and are kept forever. Every URL is " +
                        "pinned to a fixed revision, so a cached sprite can never go stale — " +
                        "your widgets keep animating with no connection.",
                )
                Spacer(Modifier.height(14.dp))
                PokeButton(
                    text = "Clear cache",
                    onClick = onClearCache,
                    icon = Icons.Default.Delete,
                    container = CardColor,
                )
            }

            Spacer(Modifier.height(16.dp))

            Panel {
                SectionHeader("Credits")
                Caption(
                    "Sprites and cries come from the community PokéAPI mirrors, and the " +
                        "original animated Emerald and Generation 4 sprites from veekun's " +
                        "archive.",
                )
                Spacer(Modifier.height(10.dp))
                Caption(
                    "Pokémon and all related art are trademarks of Nintendo, Creatures Inc. " +
                        "and GAME FREAK inc. This is an unofficial fan project with no " +
                        "affiliation.",
                )
            }

            Spacer(Modifier.height(32.dp))
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
