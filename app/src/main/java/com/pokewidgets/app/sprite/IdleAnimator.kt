package com.pokewidgets.app.sprite

/**
 * Procedural idle animation for sprite sets that ship as still images.
 *
 * Most of the series is still art. Every Game Boy Advance set — Ruby/Sapphire, **Emerald**,
 * FireRed/LeafGreen — all of Generation 4, and everything from Generation 6 onward exists
 * only as single PNGs in every distributable mirror. Emerald's real in-game animation is a
 * two-frame sequence held inside the ROM, and no public CDN carries it; the same is true
 * of Platinum's and HeartGold/SoulSilver's. So a widget showing Emerald Sceptile could
 * only ever sit dead on the home screen, which is the actual reason those games "have no
 * animated sprites".
 *
 * Rather than leave two thirds of the series motionless, the movement is generated. These
 * are the idle motions the games themselves use in menus, the party screen and the
 * Pokédex: a breath, a bob, a sway, a hover.
 *
 * ## Why this stays nearly free
 *
 * A widget's memory ceiling is charged per *distinct* bitmap, and `RemoteViews.BitmapCache`
 * dedupes by object identity. Translation is expressed as view padding, so it costs no
 * bitmap at all — a bob is one bitmap however many steps it has. Only a change of *shape*
 * needs a new bitmap, and the styles here deliberately reuse a handful of shapes across
 * many steps: [BREATHE] is six steps drawn from three bitmaps.
 */

/**
 * One step of an idle loop.
 *
 * @param scaleXPermille horizontal scale of the sprite, 1000 being its natural size.
 *   Thousandths rather than a float so a loop is exactly comparable in tests.
 * @param scaleYPermille vertical scale, same units.
 * @param dxSource horizontal offset in *source* pixels; the renderer multiplies by the
 *   sprite's upscale factor so the motion stays proportional at every widget size.
 * @param dySource vertical offset in source pixels. Negative is up.
 */
data class IdleFrame(
    val scaleXPermille: Int = NATURAL,
    val scaleYPermille: Int = NATURAL,
    val dxSource: Int = 0,
    val dySource: Int = 0,
) {
    /** True when this step draws the sprite at its original shape. */
    val isNaturalShape: Boolean
        get() = scaleXPermille == NATURAL && scaleYPermille == NATURAL

    /** Identifies the bitmap this step needs. Steps sharing a key share one bitmap. */
    val shapeKey: Int get() = scaleXPermille * 10_000 + scaleYPermille

    companion object {
        const val NATURAL = 1000
    }
}

/**
 * How a still sprite should move.
 *
 * @param frameIntervalMs how long each step is held. Tuned per style so every loop runs
 *   about a second: fast enough to read as alive, slow enough not to look agitated.
 */
enum class IdleStyle(
    val label: String,
    val description: String,
    val frameIntervalMs: Int,
) {
    /** Genuinely still, for anyone who wants their home screen quiet. */
    NONE("Still", "No movement at all", 1_000),

    /** The original: a gentle rise and fall. One bitmap. */
    BOB("Bob", "A gentle rise and fall, like the party screen", 260),

    /**
     * Squash and stretch about the sprite's feet, conserving volume — the sprite widens
     * as it settles and narrows as it draws up. Three bitmaps, six steps.
     */
    BREATHE("Breathe", "Squashes and stretches as if breathing", 150),

    /** A slow lean left and right, as if shifting weight. One bitmap. */
    SWAY("Sway", "Rocks slowly from side to side", 170),

    /** A taller, slower float with a little horizontal drift. One bitmap. */
    HOVER("Hover", "Floats, for Pokémon that never touch the ground", 130),
    ;

    val frames: List<IdleFrame> get() = IdleAnimator.frames(this)
}

object IdleAnimator {

    /** The style used for a still sprite when the user has not chosen one. */
    val DEFAULT = IdleStyle.BREATHE

    fun frames(style: IdleStyle): List<IdleFrame> = when (style) {
        IdleStyle.NONE -> STILL
        IdleStyle.BOB -> BOB
        IdleStyle.BREATHE -> BREATHE
        IdleStyle.SWAY -> SWAY
        IdleStyle.HOVER -> HOVER
    }

    private val STILL = listOf(IdleFrame())

    /**
     * The 0,-1,-2,-1 shape rises and falls smoothly with a slight pause at the bottom,
     * which is what the in-game idle looks like.
     */
    private val BOB = listOf(
        IdleFrame(dySource = 0),
        IdleFrame(dySource = -1),
        IdleFrame(dySource = -2),
        IdleFrame(dySource = -1),
    )

    /**
     * Volume-conserving squash and stretch.
     *
     * What the sprite loses in height it gains in width, which is the difference between
     * something breathing and something merely getting smaller. The renderer plants the
     * feet, so the compression reads as weight rather than drift.
     *
     * Six steps over three shapes: the wide and narrow extremes are each held for two,
     * which is what gives the loop its ease-in and ease-out without any interpolation.
     */
    private val BREATHE = listOf(
        IdleFrame(scaleXPermille = 1000, scaleYPermille = 1000),
        IdleFrame(scaleXPermille = 1015, scaleYPermille = 985),
        IdleFrame(scaleXPermille = 1030, scaleYPermille = 970),
        IdleFrame(scaleXPermille = 1030, scaleYPermille = 970),
        IdleFrame(scaleXPermille = 1015, scaleYPermille = 985),
        IdleFrame(scaleXPermille = 1000, scaleYPermille = 1000),
    )

    /**
     * A weight shift. Pure translation, so it is one bitmap however long the loop is —
     * and the loop is long on purpose, because a fast sway reads as a shiver.
     */
    private val SWAY = listOf(
        IdleFrame(dxSource = 0),
        IdleFrame(dxSource = 1),
        IdleFrame(dxSource = 2),
        IdleFrame(dxSource = 2),
        IdleFrame(dxSource = 1),
        IdleFrame(dxSource = 0),
        IdleFrame(dxSource = -1),
        IdleFrame(dxSource = -2),
        IdleFrame(dxSource = -2),
        IdleFrame(dxSource = -1),
    )

    /**
     * A float with a slight drift, so the path is a lazy oval rather than a straight line
     * up and down — which is what stops it looking like a bouncing ball.
     */
    private val HOVER = listOf(
        IdleFrame(dxSource = 0, dySource = 0),
        IdleFrame(dxSource = 1, dySource = -1),
        IdleFrame(dxSource = 1, dySource = -2),
        IdleFrame(dxSource = 0, dySource = -3),
        IdleFrame(dxSource = -1, dySource = -3),
        IdleFrame(dxSource = -1, dySource = -2),
        IdleFrame(dxSource = 0, dySource = -1),
    )

    /** How many bitmaps a style needs, before any budget clamping. */
    fun distinctShapes(style: IdleStyle): Int = frames(style).distinctBy { it.shapeKey }.size
}
