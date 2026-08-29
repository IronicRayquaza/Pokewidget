package com.pokewidgets.app.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where a set's files are served from. Decides how [SpriteSet.path] becomes a URL. */
object SpriteProvider {
    /** PokeAPI/sprites on jsDelivr, pinned to one commit. */
    const val POKEAPI = "pokeapi"

    /**
     * veekun.com's sprite dump — the only public host of the *original* animated battle
     * sprites for Emerald, and of the second animation frame for Diamond/Pearl, Platinum
     * and HeartGold/SoulSilver.
     */
    const val VEEKUN = "veekun"
}

/**
 * One game's sprite set, as emitted by `tools/build-catalog.mjs`.
 *
 * [variants] maps a variant path (`""`, `"shiny"`, `"back/shiny/female"`) to the
 * run-length encoded ids that variant covers, e.g. `"1-1025,10001-10099"`.
 * Upstream is not consistent about segment order — `back/gray` but `transparent/back` —
 * so the app always *looks up* a variant path here instead of synthesising one.
 */
@Serializable
data class SpriteSet(
    val id: String,
    val path: String,
    val label: String,
    val game: String,
    val hardware: String,
    val gen: Int,
    val animated: Boolean,
    val ext: String,
    val order: Int,
    val note: String? = null,
    val variants: Map<String, String>,
    /** Which host serves [path]. See [SpriteProvider]. */
    val provider: String = SpriteProvider.POKEAPI,
    /**
     * Sub-directories to assemble one animation from, when the game's frames are stored
     * as separate still images rather than a single GIF.
     *
     * Generation 4's battle sprites are two-frame loops, and veekun stores the second
     * frame in a parallel `frame2/` tree — so Diamond/Pearl, Platinum and
     * HeartGold/SoulSilver become genuinely animated by fetching `""` and `"frame2"` and
     * playing them in sequence. Empty means the set is a single file per sprite, which is
     * every GIF set and every still one.
     */
    val frameDirs: List<String> = emptyList(),
    /**
     * How long each of [frameDirs] is held, in milliseconds. Same length as [frameDirs].
     * The games hold the resting pose longer than the moved one, so these are uneven.
     */
    val frameDelaysMs: List<Int> = emptyList(),
) {

    /** How many separate files one sprite of this set is assembled from. */
    val partCount: Int get() = frameDirs.size.coerceAtLeast(1)

    /** True when the animation is stitched from several stills rather than one GIF. */
    val isComposite: Boolean get() = frameDirs.size > 1

    /** Ids covered by the plain front-facing variant. Parsed lazily; sets are long-lived. */
    val frontIds: IdRanges by lazy { IdRanges.parse(variants[""].orEmpty()) }

    fun supports(back: Boolean, shiny: Boolean, female: Boolean, style: String?): Boolean =
        variantPath(back, shiny, female, style) != null

    /**
     * Finds the real directory path for a variant combination, or null if this set
     * has no such variant. Tries every plausible segment order because upstream's
     * ordering differs between generations.
     */
    fun variantPath(back: Boolean, shiny: Boolean, female: Boolean, style: String?): String? {
        val parts = buildList {
            if (style != null) add(style)
            if (back) add("back")
            if (shiny) add("shiny")
            if (female) add("female")
        }
        if (parts.isEmpty()) return if (variants.containsKey("")) "" else null
        for (candidate in permutations(parts)) {
            val key = candidate.joinToString("/")
            if (variants.containsKey(key)) return key
        }
        return null
    }

    fun idsFor(variant: String): IdRanges = IdRanges.parse(variants[variant].orEmpty())

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val out = mutableListOf<List<T>>()
        for (i in items.indices) {
            val rest = items.toMutableList().also { it.removeAt(i) }
            for (p in permutations(rest)) out.add(listOf(items[i]) + p)
        }
        return out
    }
}

@Serializable
data class SpriteSetIndex(
    @SerialName("spritesSha") val spritesSha: String,
    val sets: List<SpriteSet>,
)

/**
 * Run-length encoded id set: `"1-151,10001-10010,10033"`.
 * Sprite ids are near-contiguous, so this is a few dozen bytes instead of a few KB.
 */
class IdRanges private constructor(private val ranges: List<IntRange>) {

    operator fun contains(id: Int): Boolean {
        var lo = 0
        var hi = ranges.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val r = ranges[mid]
            when {
                id < r.first -> hi = mid - 1
                id > r.last -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    val size: Int get() = ranges.sumOf { it.last - it.first + 1 }

    fun asSequence(): Sequence<Int> = ranges.asSequence().flatMap { it.asSequence() }

    companion object {
        val EMPTY = IdRanges(emptyList())

        fun parse(encoded: String): IdRanges {
            if (encoded.isBlank()) return EMPTY
            val ranges = encoded.split(',').mapNotNull { part ->
                if (part.isEmpty()) return@mapNotNull null
                val dash = part.indexOf('-')
                if (dash < 0) {
                    val n = part.toInt()
                    n..n
                } else {
                    part.substring(0, dash).toInt()..part.substring(dash + 1).toInt()
                }
            }
            return IdRanges(ranges.sortedBy { it.first })
        }
    }
}
