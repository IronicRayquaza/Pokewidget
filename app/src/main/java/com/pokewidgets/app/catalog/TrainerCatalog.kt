package com.pokewidgets.app.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

/**
 * A trainer's back sprite, taken from a game's own graphics via pret's decompilations.
 *
 * @param frameWidth the standing pose's size. Emerald and FireRed store the throw animation
 *   as a vertical strip of 64×64 frames; only the first is used.
 * @param palette for Game Boy sprites, which are stored as 2-bit grayscale: the colours
 *   for white, light grey, dark grey and black, in that order.
 */
@Serializable
data class TrainerBack(
    @SerialName("u") val url: String,
    @SerialName("f") val fallbackUrl: String,
    @SerialName("w") val frameWidth: Int,
    @SerialName("h") val frameHeight: Int,
    @SerialName("p") val palette: List<String>? = null,
)

/** One trainer sprite as shipped in `assets/trainers.json`; see `tools/build-trainers.mjs`. */
@Serializable
data class Trainer(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    /** Which game or outfit this sprite is — "Ruby & Sapphire", "Pokémon Masters". */
    @SerialName("v") val variant: String? = null,
    @SerialName("r") val region: String,
    @SerialName("o") val role: String,
    @SerialName("g") val gen: Int = 0,
    @SerialName("b") val back: TrainerBack? = null,
) {
    val hasBack: Boolean get() = back != null

    val displayName: String get() = if (variant.isNullOrBlank()) name else "$name · $variant"
}

@Serializable
data class Labelled(val id: String, val label: String)

@Serializable
data class TrainerIndex(
    val frontBase: String,
    val regions: List<Labelled>,
    val roles: List<Labelled>,
    val trainers: List<Trainer>,
) {
    private val byId: Map<String, Trainer> by lazy { trainers.associateBy { it.id } }

    fun trainer(id: String?): Trainer? = id?.let { byId[it] }

    fun frontUrl(trainer: Trainer): String = frontBase + trainer.id + ".png"

    fun regionLabel(id: String): String = regions.firstOrNull { it.id == id }?.label ?: id

    fun roleLabel(id: String): String = roles.firstOrNull { it.id == id }?.label ?: id

    /**
     * Trainers matching [query] and, when given, [role], in catalogue order (region, then
     * role, then name). The query ignores case and accents, so "pokemon" finds "Pokémon
     * Ranger" and "flannery" finds Flannery; it matches the name, the game and the region.
     */
    fun search(query: String, role: String? = null): List<Trainer> {
        val q = fold(query.trim())
        return trainers.filter { t ->
            (role == null || t.role == role) &&
                (q.isEmpty() ||
                    fold(t.name).contains(q) ||
                    fold(t.variant.orEmpty()).contains(q) ||
                    fold(regionLabel(t.region)).contains(q))
        }
    }

    /**
     * The trainer a new battle scene starts with: this generation's player, with a real
     * back sprite where one exists. Only ever used when the user asks for a battle scene
     * and has not picked anyone themselves.
     */
    fun defaultFor(gen: Int): Trainer? {
        val players = trainers.filter { it.role == "player" }
        return players.firstOrNull { it.gen == gen && it.hasBack }
            ?: players.firstOrNull { it.hasBack }
            ?: players.firstOrNull()
    }
}

// Top level, not a private companion: the serialization plugin reaches a @Serializable
// class's companion from generated code, and a private one fails on Android with
// IllegalAccessError while working fine in JVM unit tests.
private val MARKS = Regex("\\p{Mn}+")

private fun fold(s: String): String =
    MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

/** A battle background as shipped in `assets/backgrounds.json`. */
@Serializable
data class BattleBackground(
    val id: String,
    val label: String,
    val gen: Int,
    val url: String,
    val fallbackUrl: String,
    val w: Int,
    val h: Int,
    /**
     * The scenery inside the image, as `[x, y, width, height]`. Showdown's Gen 3 and Gen 4
     * backgrounds are drawn as a whole battle screen — side panels, a text box — around the
     * actual field; only the field belongs behind a widget. Null means the whole image.
     */
    val crop: List<Int>? = null,
    /** Where the Pokémon stands — the far platform — as `[x, y]` fractions of the scenery. */
    val foe: List<Double>? = null,
    /** Where the trainer stands — the near platform — as `[x, y]` fractions of the scenery. */
    val player: List<Double>? = null,
) {
    /** "Gen 3 · Cave" — backgrounds repeat labels across generations. */
    val displayName: String get() = if (gen <= 4) "Gen $gen · $label" else label
}

@Serializable
data class BackgroundIndex(
    val sha: String,
    val backgrounds: List<BattleBackground>,
) {
    fun background(id: String?): BattleBackground? = id?.let { wanted -> backgrounds.firstOrNull { it.id == wanted } }

    /** The background that matches a sprite set's generation, for the "Battle scene" preset. */
    fun defaultFor(gen: Int): BattleBackground =
        backgrounds.firstOrNull { it.gen == gen && !it.id.contains('-') }
            ?: backgrounds.first { it.id == "route" }
}
