package com.pokewidgets.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The three drawing primitives the whole app is built out of.
 *
 * They exist as modifiers rather than as wrapper composables so any layout can adopt the
 * look without another node in the tree, and so a card, a chip and a button can all be
 * demonstrably the same object at different sizes.
 */

/**
 * The outlined "sticker" surface: a flat fill, a hard ink outline, and a solid offset
 * edge beneath it.
 *
 * The offset edge is a hard-edged shape, not a blurred elevation shadow. That is a
 * deliberate departure from the usual "shadows for elevation, borders for structure"
 * rule: here the outline *is* the structure — it is what makes a cream card legible on
 * cream paper — and a soft Material shadow next to a 2dp black keyline reads as a
 * printing error rather than as depth.
 *
 * @param lift how far the solid edge sits below the surface. Scale it with the surface:
 *   3dp under a chip, 4-5dp under a full-width panel.
 */
fun Modifier.sticker(
    shape: Shape,
    fill: Color = Card,
    outline: Color = Ink,
    borderWidth: Dp = 2.dp,
    lift: Dp = 3.dp,
): Modifier = this
    .drawBehind {
        if (lift > 0.dp) {
            translate(top = lift.toPx()) {
                drawOutline(shape.createOutline(size, layoutDirection, this), color = outline)
            }
        }
    }
    .background(fill, shape)
    .border(borderWidth, outline, shape)

/**
 * A hard rule along the top edge.
 *
 * Used where a fixed footer sits over scrolling content. This one really is structure
 * rather than fake elevation — it marks where the scroll region ends, which is exactly
 * the job a divider is for, and a soft shadow next to 2dp keylines everywhere else would
 * be the odd one out.
 */
fun Modifier.topRule(color: Color = Ink, width: Dp = 2.dp): Modifier = drawBehind {
    drawRect(color = color, size = Size(size.width, width.toPx()))
}

/**
 * The printed dot grid the app's pages sit on.
 *
 * Drawn as a repeating 16dp shader tile rather than a loop of `drawCircle` calls: a
 * full-screen page is several thousand dots, and issuing them individually turns every
 * scroll frame into thousands of draw commands for a texture nobody is meant to notice.
 */
@Composable
fun Modifier.dottedPaper(
    background: Color = Paper,
    dot: Color = PaperDot,
): Modifier {
    val density = LocalDensity.current
    val brush = remember(density, dot) { dotBrush(density, dot) }
    return this.background(background).drawBehind { drawRect(brush) }
}

private fun dotBrush(density: Density, dot: Color): ShaderBrush {
    val spacing = with(density) { 16.dp.toPx() }.toInt().coerceAtLeast(2)
    val radius = with(density) { 1.dp.toPx() }
    val tile = ImageBitmap(spacing, spacing)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = androidx.compose.ui.graphics.Canvas(tile),
        size = Size(spacing.toFloat(), spacing.toFloat()),
    ) {
        drawCircle(color = dot, radius = radius, center = Offset(radius, radius))
    }
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/**
 * A Poke Ball, drawn flat and outlined to match everything else.
 *
 * Used as the plate a sprite stands on. Sprites are transparent pixel art of wildly
 * varying silhouette, and on a plain panel a small one reads as a mistake; standing it
 * on a ball gives every Pokemon the same visual footprint regardless of its size.
 */
fun DrawScope.drawPokeBall(
    strokePx: Float,
    top: Color = PokeRed,
    bottom: Color = Color.White,
    outline: Color = Ink,
) {
    val radius = (size.minDimension - strokePx) / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)

    drawCircle(color = bottom, radius = radius, center = centre)
    // The top half is a clipped circle rather than an arc so the fill meets the band
    // exactly, with no seam at the join.
    clipRect(top = 0f, bottom = centre.y) {
        drawCircle(color = top, radius = radius, center = centre)
    }
    drawLine(
        color = outline,
        start = Offset(centre.x - radius, centre.y),
        end = Offset(centre.x + radius, centre.y),
        strokeWidth = strokePx,
    )
    drawCircle(color = bottom, radius = radius * 0.22f, center = centre)
    drawCircle(
        color = outline,
        radius = radius * 0.22f,
        center = centre,
        style = Stroke(width = strokePx),
    )
    drawCircle(color = outline, radius = radius, center = centre, style = Stroke(width = strokePx))
}

