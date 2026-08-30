package com.pokewidgets.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The app's palette: warm paper, hard black ink, and three loud accents.
 *
 * This is deliberately not a Material dynamic palette. The look is the printed
 * Pokedex-sticker one — cream stock, a heavy ink outline around everything, and flat
 * blocks of colour with no gradients — and dynamic colour would replace exactly the
 * three hues that make it read as Pokemon at all.
 */

/** Warm off-white stock everything sits on. */
val Paper = Color(0xFFFDF6E8)

/** The dot grid printed on [Paper]. Low contrast on purpose: texture, not pattern. */
val PaperDot = Color(0xFFDFD2B8)

/** Cards and sheets, one step brighter than the page so they lift off it. */
val Card = Color(0xFFFFFFFF)

/** Every outline, every piece of body copy. Near-black, never pure black. */
val Ink = Color(0xFF17171A)

/** Secondary copy. Still ink, just quieter. */
val InkSoft = Color(0xFF6E6A63)

/** Poke Ball red — the brand colour, used for the ball, headers and selection. */
val PokeRed = Color(0xFFEE1515)

/** The softer coral the page furniture uses, so the pure red stays special. */
val Coral = Color(0xFFFF5A5A)

/** The call-to-action green-yellow. Used once per screen, never decoratively. */
val Lime = Color(0xFFD8F32B)

/** The screen the sprite is displayed on. */
val Sky = Color(0xFFA9E4F5)

/** Fills behind an outline that needs to read as "off" rather than "empty". */
val Chalk = Color(0xFFF2ECDD)

/**
 * Canonical Pokémon type colours, used for chips in the picker and to tint a Pokémon's
 * own detail page. Keyed by the lowercase type name emitted by the catalog generator.
 */
val TypeColors: Map<String, Color> = mapOf(
    "normal" to Color(0xFF9FA19F),
    "fighting" to Color(0xFFFF8000),
    "flying" to Color(0xFF81B9EF),
    "poison" to Color(0xFF9141CB),
    "ground" to Color(0xFF915121),
    "rock" to Color(0xFFAFA981),
    "bug" to Color(0xFF91A119),
    "ghost" to Color(0xFF704170),
    "steel" to Color(0xFF60A1B8),
    "fire" to Color(0xFFE62829),
    "water" to Color(0xFF2980EF),
    "grass" to Color(0xFF3FA129),
    "electric" to Color(0xFFFAC000),
    "psychic" to Color(0xFFEF4179),
    "ice" to Color(0xFF3DCEF3),
    "dragon" to Color(0xFF5060E1),
    "dark" to Color(0xFF624D4E),
    "fairy" to Color(0xFFEF70EF),
    "stellar" to Color(0xFF40B5A5),
    "unknown" to Color(0xFF68A090),
)

fun typeColor(type: String): Color = TypeColors[type.lowercase()] ?: TypeColors.getValue("unknown")

/**
 * A readable ink colour to put on top of [background].
 *
 * Type colours span Electric's near-yellow and Dark's near-brown, so a fixed white label
 * fails contrast on half of them. Uses the sRGB luminance the WCAG contrast ratio is
 * built on rather than a naive average, which is what makes Electric and Ice come out
 * dark and Poison and Dragon come out light.
 */
fun onColorFor(background: Color): Color {
    val luminance = 0.2126f * channel(background.red) +
        0.7152f * channel(background.green) +
        0.0722f * channel(background.blue)
    return if (luminance > 0.45f) Ink else Color.White
}

private fun channel(value: Float): Float =
    if (value <= 0.03928f) value / 12.92f else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
