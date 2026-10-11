package com.pokewidgets.app.catalog

import kotlin.random.Random

/**
 * Which Pokémon a "Surprise me" tap swaps in.
 *
 * The widget keeps one sprite and replaces it: a tap on Pikachu turns the same widget into
 * something else, with that something's cry. The choice is drawn from the person's own picks
 * when they made some, and otherwise from every species — base forms only, 1 to
 * [LAST_SPECIES], because "any Pokémon" turning up Gigantamax Toxtricity three taps running
 * is not what anyone means by it. Either way it is limited to what the widget's sprite set
 * actually drew, so a surprise can never be a missing sprite.
 *
 * Pure Kotlin, like [FormRules], so the rules are tested without a device.
 */
object SurprisePicker {

    /** The last species number; everything above is an alternate form. */
    const val LAST_SPECIES = 1025

    /** Everything [pool] allows that the sprite set [covered]. */
    fun candidates(pool: Set<Int>, covered: (Int) -> Boolean): List<Int> =
        (if (pool.isEmpty()) (1..LAST_SPECIES).toList() else pool.sorted()).filter(covered)

    /**
     * A random candidate other than [current] — or [current] itself when it is the only one
     * there is, and null when there is nothing at all to pick from.
     */
    fun pick(pool: Set<Int>, covered: (Int) -> Boolean, current: Int, random: Random = Random.Default): Int? {
        val all = candidates(pool, covered)
        val others = all.filter { it != current }
        return when {
            others.isNotEmpty() -> others.random(random)
            all.isNotEmpty() -> all.first()
            else -> null
        }
    }

    /** Whether an already chosen [next] still fits — the pool or the set may have changed. */
    fun stillFits(next: Int, pool: Set<Int>, covered: (Int) -> Boolean, current: Int): Boolean {
        if (next <= 0 || next == current) return false
        val allowed = if (pool.isEmpty()) next in 1..LAST_SPECIES else next in pool
        return allowed && covered(next)
    }

    /** DataStore has no set type, so the pool is kept as "25,6,150". */
    fun encode(pool: Set<Int>): String = pool.sorted().joinToString(",")

    fun decode(encoded: String?): Set<Int> =
        encoded?.split(',')?.mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 } }?.toSet().orEmpty()
}
