package com.pokewidgets.app.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CryCacheTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `a written cry is readable and leaves no temp file behind`() {
        val cache = CryCache(temp.newFolder("cries"))
        val bytes = byteArrayOf(1, 2, 3, 4)

        val file = cache.write(25, "legacy", bytes)

        assertArrayEquals(bytes, file!!.readBytes())
        assertEquals(file, cache.cached(25, "legacy"))
        assertTrue(temp.root.walkTopDown().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `a half-written cry is never served`() {
        val dir = temp.newFolder("cries")
        val cache = CryCache(dir)
        // What an interrupted writer leaves: only the temp file.
        dir.resolve("legacy-25.ogg.7.tmp").writeBytes(byteArrayOf(1, 2))

        assertNull(cache.cached(25, "legacy"))
        assertEquals(0L, cache.sizeBytes())
    }

    @Test
    fun `a confirmed miss survives a new instance, as it must survive a new process`() {
        val dir = temp.newFolder("cries")
        CryCache(dir).markMissing(906, "legacy")

        val fresh = CryCache(dir)
        assertTrue(fresh.isMissing(906, "legacy"))
        assertFalse(fresh.isMissing(906, "latest"))
        assertNull(fresh.cached(906, "legacy"))
    }

    @Test
    fun `clearing forgets both cries and misses`() {
        val dir = temp.newFolder("cries")
        val cache = CryCache(dir)
        cache.write(25, "latest", byteArrayOf(9))
        cache.markMissing(906, "legacy")

        cache.clear()

        assertNull(cache.cached(25, "latest"))
        assertFalse(cache.isMissing(906, "legacy"))
    }
}
