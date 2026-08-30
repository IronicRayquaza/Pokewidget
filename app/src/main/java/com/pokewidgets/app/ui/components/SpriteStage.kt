package com.pokewidgets.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pokewidgets.app.ui.theme.Sky
import com.pokewidgets.app.ui.theme.drawPokeBall
import com.pokewidgets.app.ui.theme.sticker

/**
 * The panel a sprite is shown on.
 *
 * Sprites are transparent pixel art whose silhouettes range from a 20px Diglett to a
 * 120px Wailord, and floating them on the page makes the small ones read as mistakes.
 * Standing every Pokémon on the same Poké Ball gives them all one visual footprint, which
 * is the whole reason the reference design does it too.
 *
 * @param ballFraction how much of the panel's shorter side the ball occupies. The sprite
 *   deliberately overhangs it, exactly as it does on a Pokédex page.
 */
@Composable
fun SpriteStage(
    modifier: Modifier = Modifier,
    fill: Color = Sky,
    shape: Shape = MaterialTheme.shapes.medium,
    borderWidth: Dp = 3.dp,
    lift: Dp = 5.dp,
    ballFraction: Float = 0.72f,
    inset: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .sticker(shape = shape, fill = fill, borderWidth = borderWidth, lift = lift)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val diameter = size.minDimension * ballFraction
            val strokePx = borderWidth.toPx().coerceAtLeast(1f)
            inSquare(diameter) { drawPokeBall(strokePx = strokePx) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(inset),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Runs [block] in a square drawing space of [side], centred in the current one.
 *
 * [drawPokeBall] draws to fill whatever DrawScope it is given, so it needs a square to
 * land in; without this the ball would be as wide as the panel and clipped top and
 * bottom.
 */
private inline fun DrawScope.inSquare(side: Float, crossinline block: DrawScope.() -> Unit) {
    val dx = (size.width - side) / 2f
    val dy = (size.height - side) / 2f
    inset(left = dx, top = dy, right = dx, bottom = dy) { block() }
}
