package com.pokewidgets.app.data

import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleStyle

/** What a tap on the widget does. */
enum class TapAction(val label: String, val description: String) {
    CRY("Play cry", "Plays the Pokémon's cry"),
    SHINY("Toggle shiny", "Swaps between the normal and shiny sprite"),
    FLIP("Turn around", "Swaps between the front and back sprite"),
    MIRROR("Face the other way", "Mirrors the sprite left to right"),
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

/**
 * How large the sprite is drawn inside whatever space the launcher gives the widget.
 *
 * These are what the sprite *looks* like on screen, and 1.5 honours them exactly: 4× means
 * each source pixel covers four screen pixels. They used to be caps on a bitmap upscale that
 * the memory budget quietly overrode, which is why "Fill" and "4×" often did nothing.
 * Names are stored, so they must not change.
 */
enum class Fill(val label: String, val description: String, val multiple: Int?) {
    FIT("Fill the widget", "As big as the widget allows", null),
    TRUE_SIZE("True size", "Big Pokémon look big and small ones look small", null),
    X4("4×", "Four screen pixels per sprite pixel", 4),
    X3("3×", "Three screen pixels per sprite pixel", 3),
    X2("2×", "Two screen pixels per sprite pixel", 2),
    X1("Original size", "One screen pixel per sprite pixel", 1),
}

/** Which way round a paired trainer is drawn. */
enum class TrainerPose(val label: String) {
    FRONT("Front"),
    BACK("Back"),
}

/** Which side of the widget a paired trainer stands on. */
enum class TrainerSide(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
}

/**
 * How a Pokémon and its trainer share the widget. Only consulted when a trainer is paired:
 * a widget without one is always [SOLO], whatever is stored here.
 */
enum class Scene(val label: String, val description: String) {
    SOLO("Pokémon only", "Just the Pokémon, as always"),
    SIDE_BY_SIDE("Side by side", "Trainer and Pokémon stand together"),
    BATTLE("Battle", "Seen from behind, the way a battle starts"),
}

data class WidgetConfig(
    val pokemonId: Int = 25,
    val setId: String = "other_showdown",
    val shiny: Boolean = false,
    val back: Boolean = false,
    val female: Boolean = false,
    val style: String? = null,

    /**
     * Mirror the sprite left to right, so it can face the other way on the home screen.
     * Off by default: a widget nobody touched looks exactly as it always did.
     */
    val flipHorizontal: Boolean = false,

    val showBackground: Boolean = false,
    val backgroundColor: Int = 0xCC1B1F27.toInt(),
    val cornerRadiusDp: Int = 20,

    /**
     * A battle background from the game library, drawn instead of the colour plate. Null —
     * the default — leaves the background exactly as [showBackground] says.
     */
    val backgroundId: String? = null,

    /**
     * An optional trainer standing with the Pokémon. Everything trainer-related is off by
     * default and only ever switched on by the person setting the widget up; a widget with
     * no trainer renders exactly as it did before trainers existed.
     */
    val trainerId: String? = null,
    val trainerPose: TrainerPose = TrainerPose.FRONT,
    val trainerSide: TrainerSide = TrainerSide.LEFT,
    val trainerFlip: Boolean = false,
    val scene: Scene = Scene.SOLO,

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

    /**
     * Let the real world choose the form, for the handful of Pokémon that have one to
     * choose — Castform takes the weather's shape, Lycanroc the time of day. Off by
     * default, and only offered when the chosen Pokémon has a rule; see
     * [com.pokewidgets.app.catalog.FormRules].
     *
     * Stored per widget rather than globally so two Castforms can disagree, which is exactly
     * the sort of thing someone puts two Castforms on a home screen to do.
     */
    val liveForm: Boolean = false,

    /** Set while an "excited" tap burst is running, so the renderer speeds the flip up. */
    val excitedUntilMs: Long = 0L,
) {
    val spriteKey: SpriteKey
        get() = SpriteKey(setId, pokemonId, back = back, shiny = shiny, female = female, style = style)

    /** The layout actually drawn: a scene needs a trainer to be anything but solo. */
    val effectiveScene: Scene
        get() = if (trainerId == null) Scene.SOLO else scene
}
