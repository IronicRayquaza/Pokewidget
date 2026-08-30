package com.pokewidgets.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * One scheme, always light.
 *
 * The app used to follow the system into a dark Material palette and, on Android 12+,
 * into the wallpaper's dynamic colours. Both are dropped deliberately: the paper stock,
 * the ink outline and the three accents *are* the design, and a themed variant of them
 * is just a worse version of the same screen. A widget host app is also seen in short
 * bursts next to a colourful home screen, where the bright stock reads as an object
 * rather than as a window.
 */
private val PokeScheme = lightColorScheme(
    primary = PokeRed,
    onPrimary = Color.White,
    primaryContainer = Coral,
    onPrimaryContainer = Color.White,

    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Lime,
    onSecondaryContainer = Ink,

    tertiary = Sky,
    onTertiary = Ink,

    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Chalk,
    onSurfaceVariant = InkSoft,

    outline = Ink,
    outlineVariant = PaperDot,

    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * Chunky, and concentric by construction.
 *
 * Nested surfaces here are padded by 8.dp, so each step down the scale is 8.dp smaller
 * than the one above it: a [Shapes.large] card holding a [Shapes.medium] panel holding a
 * [Shapes.small] chip all end up with visually parallel corners instead of the pinched
 * look you get from repeating one radius.
 */
val PokeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun PokeWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PokeScheme,
        typography = PokeTypography,
        shapes = PokeShapes,
        content = content,
    )
}
