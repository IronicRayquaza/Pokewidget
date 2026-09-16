package com.pokewidgets.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * The shiny switch, in the same place and the same words everywhere it appears.
 *
 * It used to be one chip among several further down the setup page, shown only when the
 * current set happened to have a shiny — which is how people who really wanted shinies
 * concluded the app had none. Now it sits by the preview and is always there: when a shiny
 * cannot be shown it is dimmed rather than removed, and tapping it says why.
 */
@Composable
fun ShinyChip(
    on: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    available: Boolean = true,
) {
    PokeChip(
        label = "Shiny",
        selected = on && available,
        onClick = onToggle,
        icon = Icons.Default.AutoAwesome,
        modifier = modifier
            .alpha(if (available) 1f else 0.55f)
            .semantics { if (!available) stateDescription = "Not available for this sprite" },
    )
}

/** Mirrors the sprite left to right, so it can face the other way on the home screen. */
@Composable
fun MirrorChip(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, label: String = "Mirror") {
    PokeChip(label = label, selected = on, onClick = onToggle, icon = Icons.Default.Flip, modifier = modifier)
}
