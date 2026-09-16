package com.pokewidgets.app.widget

import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig

/**
 * The part of a widget's settings a tap needs *before* anything else happens, carried in the
 * tap's own intent extras.
 *
 * A tap used to open DataStore first. On a cold process that is a disk read and a parse of
 * every widget's preferences before the cry could even be looked up — tens of milliseconds of
 * silence at best. The renderer already rebuilds the tap intent on every config change, so
 * the extras can never be staler than the widget the user is looking at.
 *
 * Plain values in and out through two lambdas, so it round-trips on the JVM without an
 * `Intent` (which is a stub in unit tests).
 */
data class TapPayload(
    val pokemonId: Int,
    val cryEnabled: Boolean,
    val legacyCry: Boolean,
    val tapAction: TapAction,
) {
    /** Whether this tap should make a sound at all. */
    val cries: Boolean
        get() = cryEnabled && (tapAction == TapAction.CRY || tapAction == TapAction.EXCITE)

    fun write(putInt: (String, Int) -> Unit, putString: (String, String) -> Unit) {
        putInt(KEY_POKEMON, pokemonId)
        putString(KEY_FLAGS, "${cryEnabled.bit}${legacyCry.bit}")
        putString(KEY_ACTION, tapAction.name)
    }

    companion object {
        private const val KEY_POKEMON = "pokewidget.tap.pokemon"
        private const val KEY_FLAGS = "pokewidget.tap.flags"
        private const val KEY_ACTION = "pokewidget.tap.action"

        fun of(config: WidgetConfig) =
            TapPayload(config.pokemonId, config.cryEnabled, config.legacyCry, config.tapAction)

        /**
         * Null when the intent predates this payload — a widget last drawn by 1.4 — so the
         * caller knows to fall back to the store rather than guess.
         */
        fun read(getInt: (String) -> Int?, getString: (String) -> String?): TapPayload? {
            val id = getInt(KEY_POKEMON)?.takeIf { it > 0 } ?: return null
            val flags = getString(KEY_FLAGS)?.takeIf { it.length == 2 } ?: return null
            val action = getString(KEY_ACTION)
                ?.let { runCatching { TapAction.valueOf(it) }.getOrNull() } ?: return null
            return TapPayload(id, flags[0] == '1', flags[1] == '1', action)
        }

        private val Boolean.bit get() = if (this) '1' else '0'
    }
}
