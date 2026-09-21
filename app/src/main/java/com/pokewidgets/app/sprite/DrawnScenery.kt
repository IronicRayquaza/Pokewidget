package com.pokewidgets.app.sprite

import android.graphics.Bitmap
import com.pokewidgets.app.catalog.BattleBackground
import com.pokewidgets.app.catalog.KIND_DRAWN
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Backdrops the app draws itself: skies and open ground in the banded pixel style of the
 * games, with no platform anywhere, so any Pokémon of any size can stand in them.
 *
 * Everything is drawn into a plain pixel array at [W]×[H], the same size as the PokéRogue
 * scenery, so both kinds crop and scale the same way. Drawing is deterministic, so a scene
 * looks identical in the preview and on the home screen, and nothing is downloaded or cached.
 */
object DrawnScenery {
    const val W = 320
    const val H = 180

    /** Where the land meets the sky. Figures stand well below it, on [com.pokewidgets.app.widget.SceneLayout.GROUND]. */
    private const val HORIZON = 104

    private val SCENES: List<Pair<String, String>> = listOf(
        "day" to "Sunny Day",
        "sunset" to "Sunset",
        "night" to "Starry Night",
        "snow" to "Snowfield",
        "seaside" to "Seaside",
        "autumn" to "Autumn",
        "blossom" to "Blossom",
    )

    /** The drawn scenes, as catalog entries; they sit alongside the downloaded ones. */
    val backgrounds: List<BattleBackground> = SCENES.map { (key, label) ->
        BattleBackground(id = "drawn-$key", label = label, gen = 0, url = "", fallbackUrl = "", w = W, h = H, kind = KIND_DRAWN)
    }

    /** The scene as ARGB pixels, row by row, or null for an id this object did not draw. */
    fun pixels(id: String): IntArray? {
        val px = Pixels(W, H)
        when (id.removePrefix("drawn-")) {
            "day" -> day(px)
            "sunset" -> sunset(px)
            "night" -> night(px)
            "snow" -> snow(px)
            "seaside" -> seaside(px)
            "autumn" -> autumn(px)
            "blossom" -> blossom(px)
            else -> return null
        }
        return px.data
    }

    fun bitmap(id: String): Bitmap? =
        pixels(id)?.let { Bitmap.createBitmap(it, W, H, Bitmap.Config.ARGB_8888) }

    // ---- The scenes --------------------------------------------------------------------

    private fun day(p: Pixels) {
        p.bands(0, HORIZON, 0xFF5E9CE6, 0xFF74AEEC, 0xFF8CC0F0, 0xFFA6D2F4, 0xFFC2E2F6)
        p.cloud(58, 28, 11, 0xFFFFFFFF, 0xFFD6E8F6)
        p.cloud(212, 18, 8, 0xFFFFFFFF, 0xFFD6E8F6)
        p.cloud(282, 50, 10, 0xFFFFFFFF, 0xFFD6E8F6)
        p.ridge(HORIZON - 14, 10.0, seed = 1, color = 0xFF8FBFA0)
        p.ridge(HORIZON - 4, 7.0, seed = 2, color = 0xFF6FAE6E)
        p.bands(HORIZON, H, 0xFF74B85A, 0xFF6AAE52, 0xFF62A64C, 0xFF5A9C46)
        p.tufts(HORIZON + 6, H, seed = 3, color = 0xFF4E8A3C, count = 46)
        p.speckle(HORIZON + 10, H, seed = 4, count = 18, 0xFFF4E36A, 0xFFFFFFFF, 0xFFF08CA8)
    }

    private fun sunset(p: Pixels) {
        p.bands(0, HORIZON, 0xFF4A3C78, 0xFF7A4C8A, 0xFFB45C82, 0xFFE07A6C, 0xFFF4A460, 0xFFFAC878)
        p.disc(236, HORIZON - 12, 20, 0xFFFFE6A0)
        p.disc(236, HORIZON - 12, 16, 0xFFFFF4C8)
        p.ridge(HORIZON - 12, 11.0, seed = 5, color = 0xFF6A4470)
        p.ridge(HORIZON - 3, 6.0, seed = 6, color = 0xFF4E3658)
        p.bands(HORIZON, H, 0xFF5A5A3C, 0xFF525236, 0xFF4A4A30, 0xFF42422A)
        p.tufts(HORIZON + 6, H, seed = 7, color = 0xFF383822, count = 40)
    }

    private fun night(p: Pixels) {
        p.bands(0, HORIZON, 0xFF0E1230, 0xFF141A3E, 0xFF1C244E, 0xFF26305E, 0xFF323E6E)
        p.stars(0, HORIZON - 16, seed = 8, count = 70)
        p.crescent(60, 30, 13, 0xFFF4F0D4)
        p.ridge(HORIZON - 12, 10.0, seed = 9, color = 0xFF222C50)
        p.ridge(HORIZON - 3, 6.0, seed = 10, color = 0xFF1A2440)
        p.bands(HORIZON, H, 0xFF24403A, 0xFF203A34, 0xFF1C342E, 0xFF182E28)
        p.tufts(HORIZON + 6, H, seed = 11, color = 0xFF142622, count = 40)
        p.speckle(HORIZON + 8, H, seed = 12, count = 10, 0xFFE8F470) // fireflies
    }

    private fun snow(p: Pixels) {
        p.bands(0, HORIZON, 0xFF9AAEC8, 0xFFAEBFD4, 0xFFC2D0E0, 0xFFD4DEEA, 0xFFE4EAF2)
        p.ridge(HORIZON - 16, 14.0, seed = 13, color = 0xFFAEBED6)
        p.ridge(HORIZON - 5, 7.0, seed = 14, color = 0xFFC8D6E8)
        p.bands(HORIZON, H, 0xFFF4F8FC, 0xFFEAF0F8, 0xFFE0E8F4, 0xFFD6E0EE)
        p.tufts(HORIZON + 6, H, seed = 15, color = 0xFFC4D0E2, count = 30)
        p.speckle(0, H, seed = 16, count = 90, 0xFFFFFFFF)
    }

    private fun seaside(p: Pixels) {
        p.bands(0, HORIZON - 26, 0xFF4AA0E0, 0xFF62B0E8, 0xFF7CC2EE, 0xFF98D2F2)
        p.cloud(90, 22, 9, 0xFFFFFFFF, 0xFFD8ECF8)
        p.cloud(250, 34, 7, 0xFFFFFFFF, 0xFFD8ECF8)
        p.bands(HORIZON - 26, HORIZON + 6, 0xFF2C7CC0, 0xFF3A8ACA, 0xFF4A98D2, 0xFF5CA8DA)
        p.glints(HORIZON - 24, HORIZON + 4, seed = 17, count = 26, color = 0xFFD4ECFA)
        p.rect(0, HORIZON + 6, W, HORIZON + 8, 0xFFF0F4F4) // surf
        p.bands(HORIZON + 8, H, 0xFFF0DCA4, 0xFFE8D29A, 0xFFE0C890, 0xFFD8BE86)
        p.speckle(HORIZON + 14, H, seed = 18, count = 24, 0xFFC8AC74, 0xFFFFF4E0)
    }

    private fun autumn(p: Pixels) {
        p.bands(0, HORIZON, 0xFF7FA8D8, 0xFF96B8DC, 0xFFB0C8DC, 0xFFCCD6D8, 0xFFE8DCC8)
        p.cloud(250, 26, 9, 0xFFFFF8F0, 0xFFE4D8D0)
        p.ridge(HORIZON - 14, 12.0, seed = 19, color = 0xFFC8703C, bumpy = true)
        p.ridge(HORIZON - 4, 8.0, seed = 20, color = 0xFFA8502C, bumpy = true)
        p.bands(HORIZON, H, 0xFFB8963E, 0xFFAC8A38, 0xFFA07E34, 0xFF94722E)
        p.tufts(HORIZON + 6, H, seed = 21, color = 0xFF7C5E26, count = 40)
        p.speckle(0, H, seed = 22, count = 36, 0xFFE0782C, 0xFFC84A28, 0xFFF0B040)
    }

    private fun blossom(p: Pixels) {
        p.bands(0, HORIZON, 0xFF8EC0EC, 0xFFA8CEEE, 0xFFC4DAEE, 0xFFDCE2EE, 0xFFF0E6EE)
        p.cloud(70, 30, 9, 0xFFFFFFFF, 0xFFE8E4F0)
        p.ridge(HORIZON - 14, 12.0, seed = 23, color = 0xFFF0B4C8, bumpy = true)
        p.ridge(HORIZON - 4, 8.0, seed = 24, color = 0xFFE494B0, bumpy = true)
        p.bands(HORIZON, H, 0xFF86C46A, 0xFF7CBA62, 0xFF72B05A, 0xFF68A652)
        p.tufts(HORIZON + 6, H, seed = 25, color = 0xFF58944A, count = 40)
        p.speckle(0, H, seed = 26, count = 60, 0xFFF8C8D8, 0xFFFFE4EC)
    }

    // ---- Drawing -----------------------------------------------------------------------

    /** A pixel canvas with no anti-aliasing anywhere: every pixel is a palette colour. */
    private class Pixels(val w: Int, val h: Int) {
        val data = IntArray(w * h)

        fun set(x: Int, y: Int, color: Long) {
            if (x in 0 until w && y in 0 until h) data[y * w + x] = color.toInt()
        }

        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, color: Long) {
            for (y in maxOf(0, y0) until minOf(h, y1)) for (x in maxOf(0, x0) until minOf(w, x1)) data[y * w + x] = color.toInt()
        }

        /**
         * Horizontal bands from [y0] to [y1], one per colour, with a dithered row between each
         * pair: the stepped gradient the games paint their skies and fields with.
         */
        fun bands(y0: Int, y1: Int, vararg colors: Long) {
            val n = colors.size
            for (i in 0 until n) {
                val top = y0 + (y1 - y0) * i / n
                val bottom = y0 + (y1 - y0) * (i + 1) / n
                rect(0, top, w, bottom, colors[i])
                if (i > 0) for (x in 0 until w step 2) set(x, top, colors[i - 1])
            }
        }

        fun disc(cx: Int, cy: Int, r: Int, color: Long) {
            for (y in -r..r) for (x in -r..r) if (x * x + y * y <= r * r + r) set(cx + x, cy + y, color)
        }

        /** A moon lit from the left: a disc with an offset disc taken out of it. */
        fun crescent(cx: Int, cy: Int, r: Int, color: Long) {
            for (y in -r..r) for (x in -r..r) {
                val inMoon = x * x + y * y <= r * r + r
                val sx = x - r / 2
                val sy = y + r / 3
                if (inMoon && sx * sx + sy * sy > r * r) set(cx + x, cy + y, color)
            }
        }

        /** A puffy cloud: three overlapping discs on a flat base, shaded along the bottom. */
        fun cloud(cx: Int, cy: Int, r: Int, light: Long, shade: Long) {
            val parts = listOf(Triple(-r, 2, r * 3 / 4), Triple(0, 0, r), Triple(r, 2, r * 3 / 4))
            for ((dx, dy, pr) in parts) disc(cx + dx, cy + dy + 1, pr, shade)
            for ((dx, dy, pr) in parts) disc(cx + dx, cy + dy, pr, light)
            rect(cx - r * 7 / 4, cy + r / 2, cx + r * 7 / 4 + 1, cy + r - 1, light)
            rect(cx - r * 7 / 4, cy + r - 1, cx + r * 7 / 4 + 1, cy + r, shade)
        }

        /**
         * A line of hills filled down to the ground, rolling or — with [bumpy] — the lumpy
         * outline of a treeline.
         */
        fun ridge(base: Int, amp: Double, seed: Int, color: Long, bumpy: Boolean = false) {
            val rnd = Random(seed)
            val f1 = 1.0 + rnd.nextDouble() * 1.5
            val f2 = 3.0 + rnd.nextDouble() * 3.0
            val ph1 = rnd.nextDouble() * 2 * PI
            val ph2 = rnd.nextDouble() * 2 * PI
            for (x in 0 until w) {
                val t = x.toDouble() / w * 2 * PI
                var lift = sin(t * f1 + ph1) * 0.7 + sin(t * f2 + ph2) * 0.3
                if (bumpy) lift = lift * 0.6 + abs(sin(t * 14 + ph2)) * 0.8
                val top = (base - amp * lift).toInt()
                for (y in top until HORIZON + 1) set(x, y, color)
            }
        }

        /** Small grass tufts — a "v" of darker pixels — scattered over the ground. */
        fun tufts(y0: Int, y1: Int, seed: Int, color: Long, count: Int) {
            val rnd = Random(seed)
            repeat(count) {
                val x = rnd.nextInt(w)
                val y = y0 + rnd.nextInt(maxOf(1, y1 - y0))
                set(x - 1, y - 1, color); set(x, y, color); set(x + 1, y - 1, color); set(x, y - 1, color)
            }
        }

        /** Single pixels in the given colours: flowers, snow, petals, fireflies. */
        fun speckle(y0: Int, y1: Int, seed: Int, count: Int, vararg colors: Long) {
            val rnd = Random(seed)
            repeat(count) {
                val x = rnd.nextInt(w)
                val y = y0 + rnd.nextInt(maxOf(1, y1 - y0))
                val c = colors[rnd.nextInt(colors.size)]
                set(x, y, c)
                if (rnd.nextInt(3) == 0) set(x + 1, y, c)
            }
        }

        fun stars(y0: Int, y1: Int, seed: Int, count: Int) {
            val rnd = Random(seed)
            repeat(count) {
                val x = rnd.nextInt(w)
                val y = y0 + rnd.nextInt(maxOf(1, y1 - y0))
                if (rnd.nextInt(8) == 0) {
                    // A few bright ones get a twinkle.
                    set(x, y, 0xFFFFFFFF); set(x - 1, y, 0xFF8890C0); set(x + 1, y, 0xFF8890C0)
                    set(x, y - 1, 0xFF8890C0); set(x, y + 1, 0xFF8890C0)
                } else {
                    set(x, y, if (rnd.nextBoolean()) 0xFFE8ECFF else 0xFF9CA6D8)
                }
            }
        }

        /** Short horizontal glints on water. */
        fun glints(y0: Int, y1: Int, seed: Int, count: Int, color: Long) {
            val rnd = Random(seed)
            repeat(count) {
                val x = rnd.nextInt(w)
                val y = y0 + rnd.nextInt(maxOf(1, y1 - y0))
                rect(x, y, x + 2 + rnd.nextInt(5), y + 1, color)
            }
        }
    }
}
