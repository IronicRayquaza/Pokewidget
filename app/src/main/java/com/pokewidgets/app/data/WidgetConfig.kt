package com.pokewidgets.app.data

import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleStyle

/** What a tap on the widget does. */
enum class TapAction(val label: String, val description: String) {
    CRY("Play cry", "Plays the Pokémon's cry"),
    SHINY("Toggle shiny", "Swaps between the normal and shiny sprite"),
    FLIP("Turn around", "Swaps between the front and back sprite"),
    EXCITE("Get excited", "Speeds the animation up for a couple of seconds"),
    OPEN_APP("Open PokéWidget", "Opens the app"),
    NONE("Do nothing", "Ignores taps"),
}

/**
 * Animation smoothness. Higher frame rates cost bitmap memory, and the widget memory
 * ceiling is fixed by screen size — so on a large widget with a big sprite the planner
 * may quietly deliver less than requested. It never crashes to get there.
 */
enum class Smoothness(val label: String, val fps: Int) {
    SMOOTH("Smooth", 20),
    BALANCED("Balanced", 12),
    LIGHT("Light", 8),
}

/** How large the sprite is drawn inside whatever space the launcher gives the widget. */
enum class Fill(val label: String, val maxScale: Int) {
    FIT("Fill the widget", 8),
    X4("4×", 4),
    X3("3×", 3),
    X2("2×", 2),
    X1("Original size", 1),
}

data class WidgetConfig(
    val pokemonId: Int = 25,
    val setId: String = "other_showdown",
    val shiny: Boolean = false,
    val back: Boolean = false,
    val female: Boolean = false,
    val style: String? = null,

    val showBackground: Boolean = false,
    val backgroundColor: Int = 0xCC1B1F27.toInt(),
    val cornerRadiusDp: Int = 20,

    val cryEnabled: Boolean = true,
    val legacyCry: Boolean = true,

    val smoothness: Smoothness = Smoothness.BALANCED,
    val fill: Fill = Fill.FIT,
    val tapAction: TapAction = TapAction.CRY,

    /**
     * How a *still* sprite moves. Ignored by sets that ship real animation, and the only
     * thing that gives Emerald, FireRed/LeafGreen, Platinum, HeartGold/SoulSilver and
     * everything from Gen 6 on any movement at all — see [IdleAnimator].
     */
    val idleStyle: IdleStyle = IdleAnimator.DEFAULT,

    /** Set while an "excited" tap burst is running, so the renderer speeds the flip up. */
    val excitedUntilMs: Long = 0L,
) {
    val spriteKey: SpriteKey
        get() = SpriteKey(setId, pokemonId, back = back, shiny = shiny, female = female, style = style)
}
