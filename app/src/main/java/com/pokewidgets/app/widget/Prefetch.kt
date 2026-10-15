package com.pokewidgets.app.widget

import android.content.Context
import android.util.Log
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.FormRules
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Downloads, ahead of any tap, everything a widget can show without anyone choosing again:
 * each Pokémon in its "Only my picks" list, each form "Live form" can choose, and the shiny or
 * back sprite a "Toggle shiny" or "Turn around" tap switches to.
 *
 * All of it is short — ten picks at most, four forms at most, two looks — so all of it is
 * fetched, sprite and cry, and the first tap after a pick, a change of sky or a toggle waits
 * on nothing but the redraw. "Any Pokémon" is not covered here: a thousand sprites is not a download to
 * make on someone's behalf, so that keeps the one-ahead choice in [WidgetRenderer].
 *
 * Runs on its own scope rather than the caller's, because the caller is often a tap's
 * receiver with a few seconds to live; whatever is not finished when the process goes is
 * picked up by the next render. Everything already on disk costs a file check and no more.
 */
object Prefetch {

    private const val TAG = "Prefetch"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One run per widget look at a time; a render arriving mid-run has nothing to add. */
    private val running = HashSet<String>()

    /** A sprite variant a tap can show: from behind or not, shiny or not. */
    data class Look(val back: Boolean, val shiny: Boolean)

    /** Who [config] can turn into without anyone choosing again. Pure, so it is tested. */
    fun idsFor(config: WidgetConfig): Set<Int> = buildSet {
        if (config.tapAction == TapAction.SURPRISE) addAll(config.surprisePool)
        if (config.liveForm && FormRules.appliesTo(config.pokemonId)) addAll(FormRules.familyOf(config.pokemonId))
    }

    /**
     * Every look a tap can give the widget: the one it has, and the other side of whatever
     * its tap action toggles. "Toggle shiny" on a back sprite needs the shiny back, not the
     * shiny front, so the toggle keeps the rest of the look as it is.
     */
    fun looksFor(config: WidgetConfig): List<Look> {
        val now = Look(config.back, config.shiny)
        return when (config.tapAction) {
            TapAction.SHINY -> listOf(now, now.copy(shiny = !now.shiny))
            TapAction.FLIP -> listOf(now, now.copy(back = !now.back))
            else -> listOf(now)
        }
    }

    fun start(context: Context, config: WidgetConfig) {
        val ids = idsFor(config) + config.pokemonId
        val looks = looksFor(config)
        // The widget's own Pokémon in its own look is what the render is already fetching.
        if (ids.size == 1 && looks.size == 1) return
        val key = "${config.setId}/${looks}/${config.cryEnabled}/${config.legacyCry}/" +
            ids.sorted().joinToString(",")
        synchronized(running) { if (!running.add(key)) return }
        val appContext = context.applicationContext
        scope.launch {
            try {
                fetch(appContext, config, ids, looks)
            } finally {
                synchronized(running) { running.remove(key) }
            }
        }
    }

    private suspend fun fetch(context: Context, config: WidgetConfig, ids: Set<Int>, looks: List<Look>) {
        val set = CatalogRepository.get(context).set(config.setId) ?: return
        val source = SpriteSource(context)
        for (id in ids) {
            for (look in looks) {
                // The widget's own Pokémon keeps its style and female choice; surprises and
                // forms drop both, as the tap and the renderer do.
                val own = id == config.pokemonId
                val female = own && config.female
                val style = if (own) config.style else null
                if (!set.covers(id, look.back, look.shiny, female, style)) continue
                val key = config.spriteKey.copy(
                    pokemonId = id, back = look.back, shiny = look.shiny, female = female, style = style,
                )
                if (!source.isSpriteCached(key, set.ext)) {
                    runCatching { source.spriteParts(set, key) }
                        .onFailure { Log.d(TAG, "could not pre-fetch the sprite for $id", it) }
                }
            }
            // Both flavours are settled here, not at the tap: a Pokémon with no Game Boy cry
            // has its 404 remembered and the modern cry on disk before anyone asks for it.
            // On disk only: decoding ten cries into the sample pool would push out the ones
            // the widgets on screen are using. The tap decodes from disk in a moment.
            if (config.cryEnabled && source.cachedCry(id, config.legacyCry) == null) {
                runCatching { source.cryFile(id, config.legacyCry) }
                    .onFailure { Log.d(TAG, "could not pre-fetch the cry for $id", it) }
            }
        }
    }
}
