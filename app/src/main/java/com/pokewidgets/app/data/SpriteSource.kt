package com.pokewidgets.app.data

import android.content.Context
import android.util.Log
import com.pokewidgets.app.BuildConfig
import com.pokewidgets.app.catalog.SpriteKey
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
 * "Forever" is safe because every URL is pinned to one commit of PokeAPI/sprites, so a
 * cached file can never be stale. That is what lets a widget keep animating in airplane
 * mode once its Pokémon has been chosen.
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

    fun cachedSpriteFile(key: SpriteKey, ext: String): File =
        File(spriteDir, "${key.cacheName()}.$ext")

    fun isSpriteCached(key: SpriteKey, ext: String): Boolean =
        cachedSpriteFile(key, ext).let { it.isFile && it.length() > 0 }

    /**
     * Returns the raw sprite bytes, downloading them if this is the first time.
     * Null means the sprite genuinely isn't available (missing variant, or offline with
     * nothing cached).
     */
    suspend fun spriteBytes(set: SpriteSet, key: SpriteKey): ByteArray? {
        val file = cachedSpriteFile(key, set.ext)
        if (file.isFile && file.length() > 0) return file.readBytes()

        val variant = set.variantPath(key.back, key.shiny, key.female, key.style) ?: return null
        val path = buildString {
            append(set.path)
            if (variant.isNotEmpty()) append('/').append(variant)
            append('/').append(key.pokemonId).append('.').append(set.ext)
        }
        val bytes = download(spriteUrls(path)) ?: return null
        runCatching { file.writeBytes(bytes) }
            .onFailure { Log.w(TAG, "could not cache ${file.name}", it) }
        return bytes
    }

    fun spriteUrl(set: SpriteSet, key: SpriteKey): String? {
        val variant = set.variantPath(key.back, key.shiny, key.female, key.style) ?: return null
        val path = buildString {
            append(set.path)
            if (variant.isNotEmpty()) append('/').append(variant)
            append('/').append(key.pokemonId).append('.').append(set.ext)
        }
        return spriteUrls(path).first()
    }

    /** jsDelivr first (fast, cacheable); raw.githubusercontent as the fallback. */
    private fun spriteUrls(path: String) = listOf(
        "https://cdn.jsdelivr.net/gh/PokeAPI/sprites@${BuildConfig.SPRITES_SHA}/$path",
        "https://raw.githubusercontent.com/PokeAPI/sprites/${BuildConfig.SPRITES_SHA}/$path",
    )

    // ---- Cries -----------------------------------------------------------------

    /**
     * @param legacy the GBA-era cry — harsher and more recognisable to anyone who played
     *   the Game Boy games — rather than the remastered modern one.
     */
    suspend fun cryFile(pokemonId: Int, legacy: Boolean): File? {
        val flavour = if (legacy) "legacy" else "latest"
        val file = File(cryDir, "$flavour-$pokemonId.ogg")
        if (file.isFile && file.length() > 0) return file

        val bytes = download(
            listOf(
                "https://cdn.jsdelivr.net/gh/PokeAPI/cries@main/cries/pokemon/$flavour/$pokemonId.ogg",
                "https://raw.githubusercontent.com/PokeAPI/cries/main/cries/pokemon/$flavour/$pokemonId.ogg",
            ),
        ) ?: return null
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
    }
}
