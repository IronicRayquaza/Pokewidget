package com.pokewidgets.app.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Reads the generated catalog out of assets.
 *
 * Everything here is bundled, so browsing and searching the full 1345-Pokémon list works
 * with no network at all — only the sprite the user actually picks needs downloading.
 */
class CatalogRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val loadLock = Mutex()

    @Volatile private var pokemon: List<PokemonEntry>? = null
    @Volatile private var setIndex: SpriteSetIndex? = null
    @Volatile private var iconOffsets: Map<Int, Pair<Int, Int>>? = null
    @Volatile private var iconBlob: ByteArray? = null
    private val iconLock = Mutex()

    /**
     * Decoded box icons, most-recently-used last.
     *
     * The picker grid recomposes constantly while scrolling and asks for the same handful
     * of icons over and over. Without a cache each ask costs an asset read plus a PNG
     * decode, which is what made the grid stutter. An icon is roughly 40x30 ARGB — about
     * 5 KB — so this ceiling is worth a little over a megabyte.
     */
    private val iconCache = object : LruCache<Int, Bitmap>(ICON_CACHE_ENTRIES) {}

    suspend fun pokemon(): List<PokemonEntry> = pokemon ?: loadLock.withLock {
        pokemon ?: withContext(Dispatchers.IO) {
            val text = appContext.assets.open("catalog.json").bufferedReader().use { it.readText() }
            json.decodeFromString<Catalog>(text).pokemon.also { pokemon = it }
        }
    }

    suspend fun sets(): List<SpriteSet> = setIndex()?.sets ?: emptyList()

    suspend fun setIndex(): SpriteSetIndex? = setIndex ?: loadLock.withLock {
        setIndex ?: withContext(Dispatchers.IO) {
            val text = appContext.assets.open("sets.json").bufferedReader().use { it.readText() }
            json.decodeFromString<SpriteSetIndex>(text).also { setIndex = it }
        }
    }

    suspend fun set(id: String): SpriteSet? = sets().firstOrNull { it.id == id }

    suspend fun entry(id: Int): PokemonEntry? = pokemon().firstOrNull { it.id == id }

    /** Sets that can render this Pokémon at all, animated ones first. */
    suspend fun setsFor(pokemonId: Int): List<SpriteSet> =
        sets().filter { pokemonId in it.frontIds }

    /** Ids of every Pokémon at least one animated sprite set can render. */
    suspend fun animatedPokemonIds(): Set<Int> =
        sets().filter { it.animated }
            .flatMap { it.frontIds.asSequence().toList() }
            .toHashSet()

    // ---- Offline picker icons ---------------------------------------------------

    /**
     * Box icons for the grid come from one packed blob rather than ~1100 asset files:
     * fewer file handles, smaller on disk, and noticeably faster to scroll.
     * 1114 of 1345 Pokémon have one; the rest fall back to a CDN thumbnail.
     *
     * Returns a cache hit synchronously so a recomposition of an already-visible cell
     * costs nothing at all; only a genuine miss suspends.
     */
    suspend fun icon(pokemonId: Int): Bitmap? {
        cachedIcon(pokemonId)?.let { return it }
        return withContext(Dispatchers.IO) {
            // Another cell may have decoded the same icon while we were switching threads.
            cachedIcon(pokemonId)?.let { return@withContext it }

            val offsets = iconIndex()
            val (offset, length) = offsets[pokemonId] ?: return@withContext null
            val blob = iconBlob() ?: return@withContext null
            if (offset < 0 || length <= 0 || offset + length > blob.size) return@withContext null

            BitmapFactory.decodeByteArray(blob, offset, length)
                ?.also { iconCache.put(pokemonId, it) }
        }
    }

    /** The already-decoded icon, if there is one. Safe to call from the main thread. */
    fun cachedIcon(pokemonId: Int): Bitmap? = iconCache.get(pokemonId)

    /**
     * The whole icon pack, held in memory.
     *
     * Assets are stored deflated in the APK, so an `InputStream` cannot actually seek —
     * `skip()` has to inflate everything before the offset it is asked to reach. Reading
     * an icon near the end of the blob therefore meant decompressing most of 645 KB, and
     * doing that once per grid cell is what produced the dropped frames. The whole pack
     * costs less than a single screenshot, so it is simply kept resident.
     */
    private suspend fun iconBlob(): ByteArray? = iconBlob ?: iconLock.withLock {
        iconBlob ?: runCatching {
            appContext.assets.open("icons.bin").use { it.readBytes() }
        }.getOrNull()?.also { iconBlob = it }
    }

    private suspend fun iconIndex(): Map<Int, Pair<Int, Int>> = iconOffsets ?: iconLock.withLock {
        iconOffsets ?: buildIconIndex().also { iconOffsets = it }
    }

    private fun buildIconIndex(): Map<Int, Pair<Int, Int>> {
        val map = HashMap<Int, Pair<Int, Int>>(1200)
        runCatching {
            appContext.assets.open("icons.idx").bufferedReader().forEachLine { line ->
                val parts = line.split(':')
                if (parts.size == 3) {
                    val id = parts[0].toIntOrNull()
                    val off = parts[1].toIntOrNull()
                    val len = parts[2].toIntOrNull()
                    if (id != null && off != null && len != null) map[id] = off to len
                }
            }
        }
        return map
    }

    companion object {
        /** Roughly 1.2 MB of decoded icons — a few screenfuls of the grid. */
        private const val ICON_CACHE_ENTRIES = 256

        @Volatile private var instance: CatalogRepository? = null

        fun get(context: Context): CatalogRepository =
            instance ?: synchronized(this) {
                instance ?: CatalogRepository(context).also { instance = it }
            }
    }
}
