package com.pokewidgets.app.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
     */
    suspend fun icon(pokemonId: Int): Bitmap? = withContext(Dispatchers.IO) {
        val offsets = iconOffsets ?: loadIconIndex()
        val (offset, length) = offsets[pokemonId] ?: return@withContext null
        runCatching {
            appContext.assets.open("icons.bin").use { stream ->
                var skipped = 0L
                while (skipped < offset) {
                    val n = stream.skip(offset - skipped)
                    if (n <= 0) return@use null
                    skipped += n
                }
                val bytes = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = stream.read(bytes, read, length - read)
                    if (n < 0) break
                    read += n
                }
                BitmapFactory.decodeByteArray(bytes, 0, read)
            }
        }.getOrNull()
    }

    private fun loadIconIndex(): Map<Int, Pair<Int, Int>> {
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
        iconOffsets = map
        return map
    }

    companion object {
        @Volatile private var instance: CatalogRepository? = null

        fun get(context: Context): CatalogRepository =
            instance ?: synchronized(this) {
                instance ?: CatalogRepository(context).also { instance = it }
            }
    }
}
