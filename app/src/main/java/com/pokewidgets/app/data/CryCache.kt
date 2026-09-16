package com.pokewidgets.app.data

import java.io.File

/**
 * The on-disk half of cries: where each file lives, whether it is complete, and which ones
 * upstream has told us it will never have.
 *
 * Split out of [SpriteSource] so it needs no `Context` and can be tested on the JVM, and
 * because both of its rules are about taps racing things:
 *
 * - **A file is written whole or not at all.** It lands as `<name>.tmp` and is renamed into
 *   place. The renderer's pre-fetch and a tap used to write the same file at once with a
 *   plain `writeBytes`, and a tap that found the file half-written played a clipped cry, or
 *   nothing, since [cached] accepts any non-empty file.
 * - **A confirmed 404 survives the process.** It used to be remembered in memory only, so
 *   every cold tap on a Gen 6+ Pokémon asked for the legacy cry again and burned a round trip
 *   inside the tap's short window before trying the flavour that exists.
 */
internal class CryCache(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun path(pokemonId: Int, flavour: String): File = File(dir, "$flavour-$pokemonId.ogg")

    fun cached(pokemonId: Int, flavour: String): File? =
        path(pokemonId, flavour).takeIf { it.isFile && it.length() > 0 }

    fun isMissing(pokemonId: Int, flavour: String): Boolean = marker(pokemonId, flavour).exists()

    fun markMissing(pokemonId: Int, flavour: String) {
        runCatching { marker(pokemonId, flavour).createNewFile() }
    }

    /** Writes via a temp file and a rename, so a reader never sees a partial cry. */
    fun write(pokemonId: Int, flavour: String, bytes: ByteArray): File? {
        val target = path(pokemonId, flavour)
        val temp = File(dir, "${target.name}.${Thread.currentThread().id}.tmp")
        return try {
            temp.writeBytes(bytes)
            // renameTo replaces the target on Android's filesystems. If another writer got
            // there first the file is identical anyway, so losing that race is harmless.
            if (temp.renameTo(target) || target.isFile) target else null
        } catch (e: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    /** Only finished cries count; temp files and markers are bookkeeping, not cache. */
    fun sizeBytes(): Long =
        dir.listFiles()?.filter { it.isFile && it.name.endsWith(".ogg") }?.sumOf { it.length() } ?: 0L

    /** Everything, markers included — clearing should let a re-pinned source be re-probed. */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun marker(pokemonId: Int, flavour: String): File = File(dir, "missing-$flavour-$pokemonId")
}
