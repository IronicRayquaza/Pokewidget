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
 * What [SpriteSource.spriteParts] found.
 *
 * [Missing] and [Offline] are deliberately different: one is a fact about upstream that will
 * never change, the other is a fact about right now. The widget shows a different message for
 * each, and only [Offline] invites a retry.
 */
sealed interface SpriteFetch {
    /**
     * @param exact false when the requested variant did not exist for this Pokémon and a
     *   nearer one was used instead — see [com.pokewidgets.app.catalog.SpriteSet.resolveVariant].
     */
    class Ok(val parts: List<ByteArray>, val exact: Boolean) : SpriteFetch
    object Missing : SpriteFetch
    object Offline : SpriteFetch
}

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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            // Renders triggered by a tap or an update run inside a broadcast receiver's
            // goAsync() window, which Android grants roughly ten seconds. An unbounded call
            // outlives it and finishes in a process the system is free to kill, so the work
            // is lost with no error to show. Bounding the whole call means a bad network
            // fails fast enough to report "no connection" — which the tap can now retry.
            .callTimeout(25, TimeUnit.SECONDS)
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
     * Whether *any* art is on disk for this key, without needing the set to learn its
     * extension. Used by the tap handler to tell "the widget is showing a sprite" from
     * "the widget is showing an error", which is the difference between a tap meaning
     * "play the cry" and a tap meaning "please try that again".
     */
    fun isSpriteCached(key: SpriteKey): Boolean =
        SPRITE_EXTS.any { isSpriteCached(key, it) }

    /**
     * Every file this sprite is made of, downloading any that are not cached yet.
     *
     * Usually one element. Sets that store their animation as separate stills — see
     * [SpriteSet.frameDirs] — return one element per frame, in playback order.
     *
     * The two failure modes are kept apart on purpose. [SpriteFetch.Missing] means upstream
     * does not have this sprite and never will, so retrying is pointless; [SpriteFetch.Offline]
     * means the network failed and retrying is exactly the right thing. Collapsing them into
     * one null is what used to put "Tap to retry — no connection" under a sprite that no
     * amount of connection could produce.
     */
    suspend fun spriteParts(set: SpriteSet, key: SpriteKey): SpriteFetch {
        val resolved = set.resolveVariant(key.pokemonId, key.back, key.shiny, key.female, key.style)
            ?: return SpriteFetch.Missing
        val parts = ArrayList<ByteArray>(set.partCount)
        var offline = false
        for (part in 0 until set.partCount) {
            // A composite set is only as animated as its rarest frame. If a later frame is
            // missing, fall back to what we have rather than failing the whole sprite —
            // one frame still renders, and the procedural idle takes over from there.
            when (val fetched = spritePart(set, key, resolved.path, part)) {
                is PartFetch.Ok -> parts.add(fetched.bytes)
                PartFetch.Missing -> break
                PartFetch.Offline -> { offline = true; break }
            }
        }
        if (parts.isNotEmpty()) return SpriteFetch.Ok(parts, resolved.exact)
        return if (offline) SpriteFetch.Offline else SpriteFetch.Missing
    }

    /** Convenience for the single-file case. */
    suspend fun spriteBytes(set: SpriteSet, key: SpriteKey): ByteArray? =
        (spriteParts(set, key) as? SpriteFetch.Ok)?.parts?.firstOrNull()

    private suspend fun spritePart(
        set: SpriteSet,
        key: SpriteKey,
        variant: String,
        part: Int,
    ): PartFetch {
        val file = cachedSpriteFile(key, set.ext, part)
        if (file.isFile && file.length() > 0) return PartFetch.Ok(file.readBytes())

        // The resolved variant is cached under the *requested* key. Sprites are immutable and
        // the tree is SHA-pinned, so recording "this is what that request resolves to" can
        // never go stale.
        val urls = spriteUrls(set, spritePath(set, key, variant, part))
        return when (val fetched = download(urls)) {
            is Fetched.Ok -> {
                runCatching { file.writeBytes(fetched.bytes) }
                    .onFailure { Log.w(TAG, "could not cache ${file.name}", it) }
                PartFetch.Ok(fetched.bytes)
            }
            Fetched.Missing -> PartFetch.Missing
            Fetched.Offline -> PartFetch.Offline
        }
    }

    fun spriteUrl(set: SpriteSet, key: SpriteKey): String? {
        val resolved = set.resolveVariant(key.pokemonId, key.back, key.shiny, key.female, key.style)
            ?: return null
        return spriteUrls(set, spritePath(set, key, resolved.path, part = 0)).first()
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
        if (bytes !is Fetched.Ok) {
            // Only a confirmed absence is remembered; being offline is not evidence that
            // upstream lacks the cry.
            if (bytes is Fetched.Missing) missingCries.add(id)
            return null
        }
        return runCatching { file.also { it.writeBytes(bytes.bytes) } }.getOrNull()
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
        // Clearing the cache should mean *everything*, including what we learned upstream
        // does not have — otherwise a re-pinned SHA could never be re-probed.
        missingSprites.clear()
        missingCries.clear()
    }

    // ---- Transport -------------------------------------------------------------

    private suspend fun download(urls: List<String>): Fetched = withContext(Dispatchers.IO) {
        // A confirmed miss is permanent for this install, so a widget whose tap now retries
        // does not re-request a known 404 every time it is touched. Only a *definite* 404
        // is recorded — never an IOException, which would poison the cache the first time
        // the device happened to be offline.
        if (urls.any { it in missingSprites }) return@withContext Fetched.Missing

        var sawNetworkFailure = false
        for (url in urls) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.bytes()
                        if (body != null && body.isNotEmpty()) return@withContext Fetched.Ok(body)
                    }
                    // A 404 means this variant doesn't exist upstream; the mirror won't
                    // have it either, so don't waste a second round trip.
                    if (response.code == 404) {
                        missingSprites.add(url)
                        return@withContext Fetched.Missing
                    }
                }
            } catch (e: IOException) {
                sawNetworkFailure = true
                Log.d(TAG, "fetch failed for $url: ${e.message}")
            }
        }
        if (sawNetworkFailure) Fetched.Offline else Fetched.Missing
    }

    /** What a single HTTP attempt came back with. */
    private sealed interface Fetched {
        class Ok(val bytes: ByteArray) : Fetched
        object Missing : Fetched
        object Offline : Fetched
    }

    /** Same three outcomes, for one file of a possibly-composite sprite. */
    private sealed interface PartFetch {
        class Ok(val bytes: ByteArray) : PartFetch
        object Missing : PartFetch
        object Offline : PartFetch
    }

    private companion object {
        const val TAG = "SpriteSource"

        /** Every extension any set uses; see `SpriteSet.ext`. */
        val SPRITE_EXTS = listOf("gif", "png")

        /**
         * `"<flavour>/<id>"` pairs upstream has confirmed it does not have.
         *
         * Static, not per-instance: `SpriteSource` is constructed freshly by the widget
         * provider on every tap, so an instance field would forget the miss immediately
         * and re-request it each time.
         */
        val missingCries: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        /** Sprite URLs upstream has answered 404 for. Same reasoning as [missingCries]. */
        val missingSprites: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    }
}
