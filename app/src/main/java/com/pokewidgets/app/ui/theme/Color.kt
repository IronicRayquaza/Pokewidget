package com.pokewidgets.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Poké Ball red, used as the seed for the non-dynamic palette. */
val PokeRed = Color(0xFFEE1515)
val PokeRedDark = Color(0xFFB80D0D)
val PokeInk = Color(0xFF17191E)
val PokeSteel = Color(0xFF3B4252)

/** The classic GBA screen tint, used for the sprite preview panel. */
val GbaScreen = Color(0xFF9BBC0F)
val GbaScreenDark = Color(0xFF2E4B12)

/**
 * Canonical Pokémon type colours, used for chips in the picker.
 * Keyed by the lowercase type name emitted by the catalog generator.
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
