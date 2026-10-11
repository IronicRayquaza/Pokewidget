package com.pokewidgets.app.data

import java.text.Normalizer
import java.util.Locale

/** One answer from the geocoder, before it is turned into a [Place]. */
data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
) {
    /** "Kanpur" alone is ambiguous: there is one in India and one in Pakistan. */
    fun toPlace(): Place {
        val region = listOfNotNull(admin1, country).filter { it.isNotBlank() }.joinToString(", ")
        return Place(if (region.isEmpty()) name else "$name, $region", latitude, longitude)
    }
}

/**
 * Turning what someone types into something Open-Meteo's geocoder can answer.
 *
 * The geocoder matches the *name* of a place and nothing else. "Richmond, VA" happens to work,
 * but "Richmond VA" comes back as Richmond Vale in Jamaica and "Richmond Virginia" comes back
 * empty — which is how a tester in Richmond could not find Richmond. So a query is split into
 * a name to ask for and a qualifier (state, province, country) to check the answers against,
 * and the split is tried several ways before the raw text is sent as a last resort.
 *
 * Pure Kotlin, like [com.pokewidgets.app.catalog.FormRules], so it is tested without a device
 * or a network.
 */
object PlaceQuery {

    /** One way of reading the query: ask the geocoder for [name], keep answers in [qualifier]. */
    data class Attempt(val name: String, val qualifier: String?)

    /** The most trailing words treated as a qualifier: "Richmond Virginia United States". */
    private const val MAX_QUALIFIER_WORDS = 3

    /**
     * The readings of [query], most likely first. With a comma the person has already split
     * it for us. Without one, the last one to three words are tried as the qualifier, longest
     * name first, and then the whole text on its own — so "San Francisco" still works, it
     * just costs one more request after "San" + "Francisco" finds nothing in Francisco.
     */
    fun attempts(query: String): List<Attempt> {
        val trimmed = query.trim().replace(Regex("\\s+"), " ")
        if (trimmed.length < 2) return emptyList()

        val comma = trimmed.indexOf(',')
        if (comma >= 0) {
            val name = trimmed.substring(0, comma).trim()
            val qualifier = trimmed.substring(comma + 1).trim().trim(',').trim()
            if (name.length < 2) return emptyList()
            return listOf(Attempt(name, qualifier.ifEmpty { null }))
        }

        val words = trimmed.split(' ')
        val splits = (1..minOf(MAX_QUALIFIER_WORDS, words.size - 1)).map { k ->
            Attempt(words.dropLast(k).joinToString(" "), words.takeLast(k).joinToString(" "))
        }
        return splits + Attempt(trimmed, null)
    }

    /**
     * The answers that fit [qualifier], or null when none do — which tells the caller to try
     * the next reading rather than show a list of Richmonds in the wrong country.
     */
    fun filter(results: List<GeoResult>, qualifier: String?): List<GeoResult>? {
        if (qualifier == null) return results.ifEmpty { null }
        val tokens = qualifier.split(Regex("[\\s,]+")).map(::fold).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return results.ifEmpty { null }
        return results.filter { result -> tokens.all { hits(it, result) } }.ifEmpty { null }
    }

    /** Whether one word of the qualifier describes where [result] is. */
    private fun hits(token: String, result: GeoResult): Boolean {
        val code = result.countryCode?.let(::fold)
        if (code != null && (token == code || COUNTRY_ALIASES[token] == code)) return true

        val admin1 = result.admin1?.let(::fold)
        if (admin1 != null && SUBDIVISIONS[token]?.any { (cc, name) -> cc == code && fold(name) == admin1 } == true) {
            return true
        }

        // Word prefixes, so "virg" finds Virginia and "united" finds United States — but a
        // single letter is too easy a match to mean anything.
        if (token.length < 2) return false
        return listOfNotNull(admin1, result.country?.let(::fold))
            .flatMap { it.split(' ') }
            .any { it.startsWith(token) }
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private val COUNTRY_ALIASES = mapOf(
        "usa" to "us",
        "america" to "us",
        "uk" to "gb",
        "britain" to "gb",
        "england" to "gb",
        "scotland" to "gb",
        "wales" to "gb",
    )

    /**
     * Postal abbreviations people actually type after a city name. A list per abbreviation
     * because "WA" is both Washington and Western Australia; the country code decides.
     */
    private val SUBDIVISIONS: Map<String, List<Pair<String, String>>> = buildList {
        fun us(abbr: String, name: String) = add(abbr to ("us" to name))
        fun ca(abbr: String, name: String) = add(abbr to ("ca" to name))
        fun au(abbr: String, name: String) = add(abbr to ("au" to name))

        us("al", "Alabama"); us("ak", "Alaska"); us("az", "Arizona"); us("ar", "Arkansas")
        us("ca", "California"); us("co", "Colorado"); us("ct", "Connecticut"); us("de", "Delaware")
        us("fl", "Florida"); us("ga", "Georgia"); us("hi", "Hawaii"); us("id", "Idaho")
        us("il", "Illinois"); us("in", "Indiana"); us("ia", "Iowa"); us("ks", "Kansas")
        us("ky", "Kentucky"); us("la", "Louisiana"); us("me", "Maine"); us("md", "Maryland")
        us("ma", "Massachusetts"); us("mi", "Michigan"); us("mn", "Minnesota"); us("ms", "Mississippi")
        us("mo", "Missouri"); us("mt", "Montana"); us("ne", "Nebraska"); us("nv", "Nevada")
        us("nh", "New Hampshire"); us("nj", "New Jersey"); us("nm", "New Mexico"); us("ny", "New York")
        us("nc", "North Carolina"); us("nd", "North Dakota"); us("oh", "Ohio"); us("ok", "Oklahoma")
        us("or", "Oregon"); us("pa", "Pennsylvania"); us("ri", "Rhode Island"); us("sc", "South Carolina")
        us("sd", "South Dakota"); us("tn", "Tennessee"); us("tx", "Texas"); us("ut", "Utah")
        us("vt", "Vermont"); us("va", "Virginia"); us("wa", "Washington"); us("wv", "West Virginia")
        us("wi", "Wisconsin"); us("wy", "Wyoming"); us("dc", "District of Columbia")

        ca("ab", "Alberta"); ca("bc", "British Columbia"); ca("mb", "Manitoba")
        ca("nb", "New Brunswick"); ca("nl", "Newfoundland and Labrador"); ca("ns", "Nova Scotia")
        ca("nt", "Northwest Territories"); ca("nu", "Nunavut"); ca("on", "Ontario")
        ca("pe", "Prince Edward Island"); ca("qc", "Quebec"); ca("sk", "Saskatchewan"); ca("yt", "Yukon")

        au("nsw", "New South Wales"); au("vic", "Victoria"); au("qld", "Queensland")
        au("wa", "Western Australia"); au("sa", "South Australia"); au("tas", "Tasmania")
        au("nt", "Northern Territory"); au("act", "Australian Capital Territory")
    }.groupBy({ it.first }, { it.second })
}
