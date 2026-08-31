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

    /**
     * A uniform swell with a slight rise — the sprite gets bigger, never a different shape.
     *
     * [BREATHE] deliberately distorts, and on a 40x40 pixel sprite that reads as a creature
     * drawing breath. On a Scarlet/Violet or HOME render four hundred pixels wide it reads
     * as the *photograph* being stretched, because a rendered model has no reason to change
     * proportion. Same idea, no shape change. Three bitmaps, six steps.
     */
    SETTLE("Swell", "Grows and sinks without changing shape — best for 3D renders", 170),

    /**
     * Picks between [BREATHE] and [SETTLE] based on how the set was drawn. The default,
     * because the right answer differs across the catalogue and nobody should have to know
     * that Generation 6 stopped drawing sprites.
     */
    AUTO("Auto", "Matches the artwork — a breath for pixel art, a swell for 3D renders", 150),
    ;

    val frames: List<IdleFrame> get() = IdleAnimator.frames(this)
}

object IdleAnimator {

    /** The style used for a still sprite when the user has not chosen one. */
    val DEFAULT = IdleStyle.AUTO

    fun frames(style: IdleStyle): List<IdleFrame> = when (style) {
        IdleStyle.NONE -> STILL
        IdleStyle.BOB -> BOB
        IdleStyle.BREATHE -> BREATHE
        IdleStyle.SWAY -> SWAY
        IdleStyle.HOVER -> HOVER
        IdleStyle.SETTLE -> SETTLE
        // Only reached if AUTO is asked for its frames without a set to resolve against.
        // Pixel art is the larger half of the catalogue, so it is the safer guess.
        IdleStyle.AUTO -> BREATHE
    }

    /**
     * Sets whose art is a rendered 3D model rather than a drawn sprite.
     *
     * Listed rather than derived from `gen`, because generation is the wrong axis: the
     * Generation 7 and 8 *icon* sets are pixel art despite their generation, and would look
     * wrong with a render's idle.
     */
    private val RENDER_SETS = setOf(
        "versions_generation_vi_x_y",
        "versions_generation_vi_omegaruby_alphasapphire",
        "versions_generation_vii_ultra_sun_ultra_moon",
        "versions_generation_viii_brilliant_diamond_shining_pearl",
        "versions_generation_ix_scarlet_violet",
        "versions_generation_ix_champions",
        "other_home",
        "other_official_artwork",
    )

    /** Whether this set's art is a rendered 3D model rather than a drawn sprite. */
    fun isRendered(setId: String): Boolean = setId in RENDER_SETS

    /** Turns [IdleStyle.AUTO] into a concrete style for this set; every other style is itself. */
    fun resolve(style: IdleStyle, setId: String): IdleStyle = when {
        style != IdleStyle.AUTO -> style
        setId in RENDER_SETS -> IdleStyle.SETTLE
        else -> IdleStyle.BREATHE
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

    /**
     * Uniform swell. Shares [BREATHE]'s six-step, three-shape structure — the extremes held
     * for two steps each, which is what gives the ease without interpolation — but scales
     * both axes together, so nothing is ever distorted. The small rise stops the growth
     * reading as the sprite advancing towards the viewer.
     */
    private val SETTLE = listOf(
        IdleFrame(scaleXPermille = 1000, scaleYPermille = 1000, dySource = 0),
        IdleFrame(scaleXPermille = 1010, scaleYPermille = 1010, dySource = -1),
        IdleFrame(scaleXPermille = 1020, scaleYPermille = 1020, dySource = -2),
        IdleFrame(scaleXPermille = 1020, scaleYPermille = 1020, dySource = -2),
        IdleFrame(scaleXPermille = 1010, scaleYPermille = 1010, dySource = -1),
        IdleFrame(scaleXPermille = 1000, scaleYPermille = 1000, dySource = 0),
    )

    /** How many bitmaps a style needs, before any budget clamping. */
    fun distinctShapes(style: IdleStyle): Int = frames(style).distinctBy { it.shapeKey }.size
}
