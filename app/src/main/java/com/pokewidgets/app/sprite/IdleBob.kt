package com.pokewidgets.app.sprite

/**
 * The procedural idle animation for static sprites.
 *
 * Every Game Boy Advance set (Ruby/Sapphire, Emerald, FireRed/LeafGreen), all of Gen 4,
 * and everything from Gen 6 onward exists only as still PNGs — Emerald's real in-game
 * animation lives inside ROM rips, not in any distributable sprite mirror. Rather than
 * leave those sprites sitting dead on the home screen, they get the gentle vertical bob
 * the games themselves use in menus and the party screen.
 *
 * It is free: the renderer puts *the same* bitmap into every frame and varies only the
 * padding, so a bobbing sprite costs exactly one bitmap against the widget memory budget.
 */
object IdleBob {

    /** Slow enough to read as breathing rather than vibrating. */
    const val FRAME_INTERVAL_MS = 260

    /**
     * Vertical offsets in source pixels, scaled to match the sprite's upscale factor so
     * the bob stays proportional. The 0,-1,-2,-1 shape gives a smooth rise and fall with
     * a slight pause at the bottom, which is what the in-game idle looks like.
     */
    private val PATTERN = intArrayOf(0, -1, -2, -1)

    fun offsets(scale: Int): List<Int> {
        val step = scale.coerceAtLeast(1)
        return PATTERN.map { it * step }
    }

    val frameCount: Int get() = PATTERN.size
}
