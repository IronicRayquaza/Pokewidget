package com.pokewidgets.app.data

import android.content.Context
import android.util.Log
import com.pokewidgets.app.BuildConfig
import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.catalog.SpriteProvider
import com.pokewidgets.app.catalog.SpriteSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches sprite and cry bytes, and keeps them on disk forever.
 *
 * "Forever" is safe because sprite art is immutable: PokeAPI URLs are pinned to one
 * commit, and veekun's dump is a finished archive of shipped games. A cached file can
 * never go stale, which is what lets a widget keep animating in airplane mode once its
 * Pokémon has been chosen — and it is why the single-origin veekun sets are acceptable
 * despite having no CDN behind them: each sprite is fetched once, ever.
 */
class SpriteSource(context: Context) {

    private val appContext = context.applicationContext

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val spriteDir = File(appContext.filesDir, "sprites").apply { mkdirs() }
    private val cryDir = File(appContext.filesDir, "cries").apply { mkdirs() }

    // ---- Sprites ---------------------------------------------------------------

    /**
     * @param part index into [SpriteSet.frameDirs] for a composite set; 0 for everything
     *   else. Part of the filename so the two frames of a Gen 4 sprite do not collide.
     */
    fun cachedSpriteFile(key: SpriteKey, ext: String, part: Int = 0): File =
        File(spriteDir, if (part == 0) "${key.cacheName()}.$ext" else "${key.cacheName()}-f$part.$ext")

    fun isSpriteCached(key: SpriteKey, ext: String): Boolean =
        cachedSpriteFile(key, ext).let { it.isFile && it.length() > 0 }

    /**
     * Every file this sprite is made of, downloading any that are not cached yet.
     *
     * Usually one element. Sets that store their animation as separate stills — see
     * [SpriteSet.frameDirs] — return one element per frame, in playback order. Null means
     * the sprite genuinely isn't available: a variant this set doesn't have, or offline
     * with nothing cached.
     */
    suspend fun spriteParts(set: SpriteSet, key: SpriteKey): List<ByteArray>? {
        val variant = set.variantPath(key.back, key.shiny, key.female, key.style) ?: return null
        val parts = ArrayList<ByteArray>(set.partCount)
        for (part in 0 until set.partCount) {
            // A composite set is only as animated as its rarest frame. If a later frame is
            // missing, fall back to what we have rather than failing the whole sprite —
            // one frame still renders, and the procedural idle takes over from there.
            val bytes = spritePart(set, key, variant, part) ?: break
            parts.add(bytes)
        }
        return parts.ifEmpty { null }
    }

    /** Convenience for the single-file case. */
    suspend fun spriteBytes(set: SpriteSet, key: SpriteKey): ByteArray? =
        spriteParts(set, key)?.firstOrNull()

    private suspend fun spritePart(
        set: SpriteSet,
        key: SpriteKey,
        variant: String,
        part: Int,
    ): ByteArray? {
        val file = cachedSpriteFile(key, set.ext, part)
        if (file.isFile && file.length() > 0) return file.readBytes()

        val bytes = download(spriteUrls(set, spritePath(set, key, variant, part))) ?: return null
        runCatching { file.writeBytes(bytes) }
            .onFailure { Log.w(TAG, "could not cache ${file.name}", it) }
        return bytes
    }

    fun spriteUrl(set: SpriteSet, key: SpriteKey): String? {
        val variant = set.variantPath(key.back, key.shiny, key.female, key.style) ?: return null
        return spriteUrls(set, spritePath(set, key, variant, part = 0)).first()
    }

    /**
     * `<set path>/<frame dir>/<variant>/<id>.<ext>`.
     *
     * The frame directory sits above the variant because that is how veekun's tree is
     * laid out: `platinum/frame2/25.png`, not `platinum/25/frame2.png`.
     */
    private fun spritePath(set: SpriteSet, key: SpriteKey, variant: String, part: Int): String =
        buildString {
            append(set.path)
            set.frameDirs.getOrNull(part)?.takeIf { it.isNotEmpty() }?.let { append('/').append(it) }
            if (variant.isNotEmpty()) append('/').append(variant)
            append('/').append(key.pokemonId).append('.').append(set.ext)
        }

    /**
     * Where to look for a file, best host first.
     *
     * PokeAPI's tree is mirrored, so it gets jsDelivr with raw.githubusercontent as a
     * fallback. veekun serves its dump from one origin and is not on any CDN — no GitHub
     * mirror of it exists (`veekun/pokedex-media` holds only a README) — so there is a
     * single URL, and a failed fetch simply leaves the widget on its cached art.
     */
    private fun spriteUrls(set: SpriteSet, path: String): List<String> =
        when (set.provider) {
            SpriteProvider.VEEKUN -> listOf("https://veekun.com/dex/media/$path")
            else -> listOf(
                "https://cdn.jsdelivr.net/gh/PokeAPI/sprites@${BuildConfig.SPRITES_SHA}/$path",
                "https://raw.githubusercontent.com/PokeAPI/sprites/${BuildConfig.SPRITES_SHA}/$path",
            )
        }

    // ---- Cries -----------------------------------------------------------------

    /**
     * The Pokémon's cry, or null if upstream has neither flavour for it.
     *
     * @param legacy prefer the GBA-era cry — harsher and more recognisable to anyone who
     *   played the Game Boy games — over the remastered modern one.
     *
     * The preference is a preference, not a requirement. Upstream only has legacy cries
     * for ids 1–649, so honouring it literally left every Pokémon from Gen 6 on silent,
     * and *every* alternate form with it — forms all live above id 10000, so even
     * Charizard's Mega had nothing to play. The other flavour is always tried before
     * giving up.
     */
    suspend fun cryFile(pokemonId: Int, legacy: Boolean): File? {
        val flavours = if (legacy) listOf("legacy", "latest") else listOf("latest", "legacy")
        for (flavour in flavours) {
            cry(pokemonId, flavour)?.let { return it }
        }
        return null
    }

    private suspend fun cry(pokemonId: Int, flavour: String): File? {
        val file = File(cryDir, "$flavour-$pokemonId.ogg")
        if (file.isFile && file.length() > 0) return file

        val id = "$flavour/$pokemonId"
        // Upstream's coverage is fixed, so a miss is permanent for this install. Recording
        // it stops a repeatedly tapped Gen 9 widget re-requesting a 404 on every tap.
        if (id in missingCries) return null

        val bytes = download(
            listOf(
                "https://cdn.jsdelivr.net/gh/PokeAPI/cries@main/cries/pokemon/$flavour/$pokemonId.ogg",
                "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/$flavour/$pokemonId.ogg",
            ),
        )
        if (bytes == null) {
            missingCries.add(id)
            return null
        }
        return runCatching { file.also { it.writeBytes(bytes) } }.getOrNull()
    }

    // ---- Cache management ------------------------------------------------------

    fun cacheSizeBytes(): Long =
        sequenceOf(spriteDir, cryDir)
            .flatMap { it.walkTopDown() }
            .filter { it.isFile }
            .sumOf { it.length() }

    fun clearCache() {
        spriteDir.listFiles()?.forEach { it.delete() }
        cryDir.listFiles()?.forEach { it.delete() }
    }

    // ---- Transport -------------------------------------------------------------

    private suspend fun download(urls: List<String>): ByteArray? = withContext(Dispatchers.IO) {
        for (url in urls) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.bytes()
                        if (body != null && body.isNotEmpty()) return@withContext body
                    }
                    // A 404 means this variant doesn't exist upstream; the mirror won't
                    // have it either, so don't waste a second round trip.
                    if (response.code == 404) return@withContext null
                }
            } catch (e: IOException) {
                Log.d(TAG, "fetch failed for $url: ${e.message}")
            }
        }
        null
    }

    private companion object {
        const val TAG = "SpriteSource"

        /**
         * `"<flavour>/<id>"` pairs upstream has confirmed it does not have.
         *
         * Static, not per-instance: `SpriteSource` is constructed freshly by the widget
         * provider on every tap, so an instance field would forget the miss immediately
         * and re-request it each time.
         */
        val missingCries: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }
}
