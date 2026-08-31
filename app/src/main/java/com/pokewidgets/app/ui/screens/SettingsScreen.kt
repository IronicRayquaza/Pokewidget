package com.pokewidgets.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.data.Place
import com.pokewidgets.app.ui.components.Caption
import com.pokewidgets.app.ui.components.PokeButton
import com.pokewidgets.app.ui.components.PokeHeader
import com.pokewidgets.app.ui.components.PokeSearchField
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
    weatherPlace: Place? = null,
    placeQuery: String = "",
    placeResults: List<Place> = emptyList(),
    searchingPlaces: Boolean = false,
    onPlaceQuery: (String) -> Unit = {},
    onChoosePlace: (Place?) -> Unit = {},
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
                SectionHeader("Weather")
                Caption(
                    "A few Pokémon change shape with the weather — Castform most obviously. " +
                        "Pick a city and any widget with “Live form” switched on will follow " +
                        "its sky.",
                )
                Spacer(Modifier.height(12.dp))

                if (weatherPlace != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                weatherPlace.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                            )
                            Caption("Checked about once an hour")
                        }
                        Spacer(Modifier.width(12.dp))
                        PokeButton(
                            text = "Change",
                            onClick = { onChoosePlace(null) },
                            container = CardColor,
                        )
                    }
                } else {
                    PokeSearchField(
                        value = placeQuery,
                        onValueChange = onPlaceQuery,
                        placeholder = "Search for a city",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        searchingPlaces -> Caption("Searching…")
                        placeQuery.isNotBlank() && placeResults.isEmpty() ->
                            Caption("No city by that name. Check the spelling, or try a larger one nearby.")
                        else -> placeResults.forEach { place ->
                            PlaceRow(place) { onChoosePlace(place) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        "Your location is never read. Only the city you pick here is stored, " +
                            "and only on this device.",
                    )
                }
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

/** One city from the search, tappable. */
@Composable
private fun PlaceRow(place: Place, onClick: () -> Unit) {
    Text(
        place.label,
        style = MaterialTheme.typography.bodyLarge,
        color = Ink,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            // Comfortably past the 48dp minimum, because these are stacked and mis-taps here
            // silently set the wrong city.
            .padding(vertical = 14.dp, horizontal = 4.dp),
    )
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
