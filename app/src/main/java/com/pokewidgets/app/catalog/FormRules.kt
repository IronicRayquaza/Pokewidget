package com.pokewidgets.app.catalog

/**
 * Weather, reduced to the four states a Pokémon sprite can actually express.
 *
 * Open-Meteo reports WMO codes, of which there are about thirty. Castform has three weather
 * forms. Anything finer than this would be a distinction the artwork cannot draw.
 */
enum class Weather {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    ;

    companion object {
        /**
         * WMO code → [Weather]. Fog counts as cloudy, thunderstorms and drizzle as rain,
         * and anything unrecognised as cloudy — the least surprising thing to show when we
         * do not know.
         */
        fun fromWmoCode(code: Int): Weather = when (code) {
            0, 1 -> CLEAR
            2, 3, 45, 48 -> CLOUDY
            in 51..67, in 80..82, in 95..99 -> RAIN
            in 71..77, 85, 86 -> SNOW
            else -> CLOUDY
        }
    }
}

/**
 * What the world outside is doing, as far as a sprite is concerned.
 *
 * @param hour local hour, 0..23. Separate from [isDay] because dusk is neither.
 */
data class WorldState(
    val weather: Weather,
    val isDay: Boolean,
    val hour: Int,
)

/**
 * Pokémon whose form has a real-world trigger, and which form to show.
 *
 * A handful of Pokémon change shape according to something the phone can actually observe —
 * the weather, or the time of day. Because PokeAPI models every form as its own Pokémon with
 * its own id, and [SpriteKey.pokemonId] *is* that id, showing the right one is a substitution
 * and nothing more: no new sprite plumbing, no new artwork, no new decode path.
 *
 * Only families where the trigger is genuinely canon are here. Deerling's seasons and
 * Vivillon's regional patterns would be the obvious additions and are deliberately absent —
 * upstream has no separate entries for them, so there is no sprite to fetch.
 *
 * Pure Kotlin on purpose, like [com.pokewidgets.app.sprite.FramePlanner]: the rules are the
 * part worth testing, and they should not need a device to test.
 */
object FormRules {

    /** Castform reads the weather directly; it is the reason this feature exists. */
    private const val CASTFORM = 351
    private const val CASTFORM_SUNNY = 10013
    private const val CASTFORM_RAINY = 10014
    private const val CASTFORM_SNOWY = 10015

    private const val LYCANROC_MIDDAY = 745
    private const val LYCANROC_MIDNIGHT = 10126
    private const val LYCANROC_DUSK = 10152

    private const val SHAYMIN_LAND = 492
    private const val SHAYMIN_SKY = 10006

    private const val EISCUE_ICE = 875
    private const val EISCUE_NOICE = 10185

    private const val MORPEKO_FULL = 877
    private const val MORPEKO_HANGRY = 10187

    /**
     * One family's rule. [members] is every id in the family, so that a widget already set to
     * Castform Rainy still resolves — the user picked the Pokémon, not the weather.
     */
    private class Rule(
        val label: String,
        val members: Set<Int>,
        val pick: (WorldState) -> Int,
    )

    private val RULES = listOf(
        Rule(
            "follows the weather",
            setOf(CASTFORM, CASTFORM_SUNNY, CASTFORM_RAINY, CASTFORM_SNOWY),
        ) { world ->
            when {
                world.weather == Weather.RAIN -> CASTFORM_RAINY
                world.weather == Weather.SNOW -> CASTFORM_SNOWY
                // Sunny Form comes from harsh sunlight, so a clear sky at midnight is not it.
                world.weather == Weather.CLEAR && world.isDay -> CASTFORM_SUNNY
                else -> CASTFORM
            }
        },
        Rule(
            "follows the time of day",
            setOf(LYCANROC_MIDDAY, LYCANROC_MIDNIGHT, LYCANROC_DUSK),
        ) { world ->
            when {
                // Dusk Form is the one evolved in the evening window, so it gets that window.
                world.hour in 17..18 -> LYCANROC_DUSK
                world.isDay -> LYCANROC_MIDDAY
                else -> LYCANROC_MIDNIGHT
            }
        },
        Rule(
            "flies by day",
            setOf(SHAYMIN_LAND, SHAYMIN_SKY),
        ) { world ->
            // Sky Forme reverts at night and when frozen, exactly as it does in Platinum.
            if (world.isDay && world.weather != Weather.SNOW) SHAYMIN_SKY else SHAYMIN_LAND
        },
        Rule(
            "keeps its ice in the cold",
            setOf(EISCUE_ICE, EISCUE_NOICE),
        ) { world ->
            // Hail restores the Ice Face; harsh sunlight is what breaks it.
            if (world.weather == Weather.CLEAR && world.isDay) EISCUE_NOICE else EISCUE_ICE
        },
        Rule(
            "gets hangry after dark",
            setOf(MORPEKO_FULL, MORPEKO_HANGRY),
        ) { world ->
            if (world.isDay) MORPEKO_FULL else MORPEKO_HANGRY
        },
    )

    private val byMember: Map<Int, Rule> =
        RULES.flatMap { rule -> rule.members.map { it to rule } }.toMap()

    /** Whether this Pokémon has anything to follow. */
    fun appliesTo(pokemonId: Int): Boolean = pokemonId in byMember

    /**
     * Which form of [pokemonId] suits [world], or [pokemonId] itself when it has no rule.
     * Never returns an id outside the Pokémon's own family.
     */
    fun formFor(pokemonId: Int, world: WorldState): Int =
        byMember[pokemonId]?.pick?.invoke(world) ?: pokemonId

    /** Short phrase for the config screen, e.g. "Castform follows the weather". */
    fun describe(pokemonId: Int): String? = byMember[pokemonId]?.label

    /** Every id any rule can produce. Used by tests and by the catalog sanity check. */
    fun allMembers(): Set<Int> = byMember.keys
}
