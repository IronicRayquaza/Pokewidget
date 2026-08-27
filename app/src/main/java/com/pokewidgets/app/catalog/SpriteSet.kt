package com.pokewidgets.app.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
) {
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
