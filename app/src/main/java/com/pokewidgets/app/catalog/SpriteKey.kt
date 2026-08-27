package com.pokewidgets.app.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One Pokémon (or alternate form) as shipped in `assets/catalog.json`. */
@Serializable
data class PokemonEntry(
    @SerialName("i") val id: Int,
    @SerialName("n") val name: String,
    @SerialName("d") val dexNumber: Int,
    @SerialName("g") val generation: Int,
    @SerialName("t") val types: List<String> = emptyList(),
    @SerialName("f") val form: String? = null,
    @SerialName("s") val slug: String = "",
) {
    /** "Deoxys" / "Deoxys (Attack Forme)" */
    val displayName: String get() = if (form.isNullOrBlank()) name else "$name ($form)"

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return name.lowercase().contains(q) ||
            slug.contains(q) ||
            form?.lowercase()?.contains(q) == true ||
            dexNumber.toString() == q
    }
}

@Serializable
data class Catalog(val pokemon: List<PokemonEntry>)

/**
 * Everything needed to locate exactly one sprite file. This is the cache key for
 * downloads and for planned frames, so it must be stable and cheap to stringify.
 */
data class SpriteKey(
    val setId: String,
    val pokemonId: Int,
    val back: Boolean = false,
    val shiny: Boolean = false,
    val female: Boolean = false,
    val style: String? = null,
) {
    /** Filesystem- and preference-safe identifier. */
    fun cacheName(): String = buildString {
        append(setId)
        append('-')
        append(pokemonId)
        if (back) append("-b")
        if (shiny) append("-s")
        if (female) append("-f")
        style?.let { append('-').append(it) }
    }

    override fun toString(): String = cacheName()
}
