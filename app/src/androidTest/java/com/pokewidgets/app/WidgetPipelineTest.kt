package com.pokewidgets.app

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.ViewFlipper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.SpriteKey
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.sprite.BitmapOps
import com.pokewidgets.app.sprite.FramePlanner
import com.pokewidgets.app.sprite.GifFrames
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleFrame
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end verification of the pipeline that actually renders a widget: fetch a real
 * sprite, decode it, plan its frames, build the `RemoteViews`, and inflate the result.
 *
 * The assertion that matters is on `estimateMemoryUsage()` — the same figure the system
 * checks before accepting a widget update, and the one that throws (taking the launcher
 * down with it) when a naive implementation hands over 95 full-size frames.
 *
 * Needs a network connection on first run; sprites are cached afterwards.
 */
@RunWith(AndroidJUnit4::class)
class WidgetPipelineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The framework's real ceiling, from AppWidgetService. */
    private fun systemCeiling(): Long {
        val m = context.resources.displayMetrics
        return m.widthPixels.toLong() * m.heightPixels * 4L * 3L / 2L
    }

    @Test
    fun rayquazaShowdown_theWorstCase_staysUnderTheSystemCeiling() {
        // 142x153, 95 frames at 30 ms — 7.9 MB of bitmaps if handed over untouched.
        runBlocking { assertPipelineFits(384, "other_showdown", 320 to 160) }
    }

    @Test
    fun rayquazaShowdown_onALargeWidget_stillFits() {
        runBlocking { assertPipelineFits(384, "other_showdown", 350 to 350) }
    }

    @Test
    fun bulbasaurCrystal_theWorstTiming_fitsAndDedupes() {
        // Delays run 10 ms to 990 ms, so uniform resampling produces many repeats.
        runBlocking {
            val result = assertPipelineFits(
                1, "versions_generation_ii_crystal_animated", 160 to 160,
            )
            assertTrue(
                "expected repeated steps to collapse onto shared bitmaps " +
                    "(${result.distinctFrames} bitmaps for ${result.steps} steps)",
                result.distinctFrames < result.steps,
            )
        }
    }

    @Test
    fun blackWhiteAnimated_nonUniformDelays_fits() {
        runBlocking {
            assertPipelineFits(384, "versions_generation_v_black_white_animated", 320 to 160)
        }
    }

    /**
     * Emerald ships as still PNGs, so its movement is generated. The point of the test is
     * that generating it stays cheap: the loop must cost one bitmap per distinct *shape*,
     * never one per step, or a still sprite would price itself out of the widget budget
     * that a real animation fits inside.
     */
    @Test
    fun emeraldStatic_getsAGeneratedIdle_forAlmostNothing() {
        runBlocking {
            val result = assertPipelineFits(384, "versions_generation_iii_emerald", 160 to 160)
            val style = IdleAnimator.DEFAULT
            assertEquals(
                "the generated idle should emit every step of its loop",
                style.frames.size,
                result.steps,
            )
            assertEquals(
                "steps sharing a shape must share one bitmap",
                IdleAnimator.distinctShapes(style),
                result.distinctFrames,
            )
            assertTrue(
                "translation must be free: fewer bitmaps than steps",
                result.distinctFrames < result.steps,
            )
        }
    }

    // ---------------------------------------------------------------------------

    private data class PipelineResult(val steps: Int, val distinctFrames: Int, val bytes: Long)

    private suspend fun assertPipelineFits(
        pokemonId: Int,
        setId: String,
        boxDp: Pair<Int, Int>,
    ): PipelineResult {
        val catalog = CatalogRepository.get(context)
        val set = catalog.set(setId)
        assertNotNull("sprite set $setId missing from the generated catalog", set)
        set!!

        val key = SpriteKey(setId, pokemonId)
        val bytes = SpriteSource(context).spriteBytes(set, key)
        assertNotNull("could not fetch sprite $setId/$pokemonId (is the device online?)", bytes)

        val decoded = GifFrames.decode(bytes!!, isGif = set.ext == "gif")
        assertNotNull("could not decode $setId/$pokemonId", decoded)
        decoded!!

        val metrics = context.resources.displayMetrics
        val boxW = (boxDp.first * metrics.density).toInt()
        val boxH = (boxDp.second * metrics.density).toInt()
        val budget = FramePlanner.budgetFor(metrics.widthPixels, metrics.heightPixels)
        val bounds = BitmapOps.unionOpaqueBounds(decoded.frames)

        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.removeAllViews(R.id.widget_flipper)

        val steps: Int
        val distinct: Int
        val distinctBitmaps = mutableListOf<android.graphics.Bitmap>()

        if (set.animated && decoded.frames.size > 1) {
            // Same construction the renderer uses, canonical mapping included — a test
            // that skipped it would stop testing what actually ships.
            val canonical = BitmapOps.canonicalFrames(decoded.frames, bounds)
            Log.i(
                TAG,
                "$setId/$pokemonId: ${decoded.frames.size} source frames, " +
                    "${canonical.distinct().size} unique after pixel dedupe",
            )
            val plan = FramePlanner.plan(
                FramePlanner.Request(
                    source = FramePlanner.Source(
                        contentWidth = bounds.width(),
                        contentHeight = bounds.height(),
                        delaysMs = decoded.delaysMs,
                        canonical = canonical,
                    ),
                    targetWidthPx = boxW,
                    targetHeightPx = boxH,
                    budgetBytes = budget,
                ),
            )
            val scaled = plan.distinctFrames.associateWith { index ->
                BitmapOps.cropAndScale(decoded.frames[index], bounds, plan.scale)
                    .also { distinctBitmaps.add(it) }
            }
            for (index in plan.sourceIndices) {
                val child = RemoteViews(context.packageName, R.layout.widget_frame)
                child.setImageViewBitmap(R.id.widget_frame_image, scaled.getValue(index))
                views.addView(R.id.widget_flipper, child)
            }
            steps = plan.stepCount
            distinct = plan.distinctFrames.size
        } else {
            val style = IdleAnimator.DEFAULT
            val frames = style.frames
            val widest = frames.maxOf { it.scaleXPermille }
            val tallest = frames.maxOf { it.scaleYPermille }
            val scale = FramePlanner.fitScale(
                bounds.width() * widest / IdleFrame.NATURAL,
                bounds.height() * tallest / IdleFrame.NATURAL,
                boxW,
                boxH,
                8,
            )
            val natural = BitmapOps.cropAndScale(decoded.frames.first(), bounds, scale)
            val byShape = HashMap<Int, android.graphics.Bitmap>()
            for (frame in frames) {
                val bitmap = byShape.getOrPut(frame.shapeKey) {
                    BitmapOps.resize(
                        natural,
                        natural.width * frame.scaleXPermille / IdleFrame.NATURAL,
                        natural.height * frame.scaleYPermille / IdleFrame.NATURAL,
                    ).also { distinctBitmaps.add(it) }
                }
                val plant = (natural.height - bitmap.height) / 2
                val dy = frame.dySource * scale + plant
                val dx = frame.dxSource * scale
                val child = RemoteViews(context.packageName, R.layout.widget_frame)
                child.setImageViewBitmap(R.id.widget_frame_image, bitmap)
                child.setViewPadding(R.id.widget_frame_image, dx, dy, -dx, -dy)
                views.addView(R.id.widget_flipper, child)
            }
            steps = frames.size
            distinct = byShape.size
        }

        // Only the interval is remotable; autoStart lives in the layout XML.
        views.setInt(R.id.widget_flipper, "setFlipInterval", 80)

        // AppWidgetService calls RemoteViews.estimateMemoryUsage() and throws if the
        // result exceeds the ceiling. It's a hidden API, so reach it by reflection —
        // this is the only way to check the *real* number the system will compute,
        // including whether its BitmapCache actually deduped our shared bitmaps.
        val reported = reflectMemoryUsage(views)
        val ourBytes = distinctBitmaps.sumOf { it.allocationByteCount.toLong() }
        val used = reported ?: ourBytes
        val ceiling = systemCeiling()
        Log.i(
            TAG,
            "$setId/$pokemonId @${boxDp.first}x${boxDp.second}dp: $steps steps, " +
                "$distinct bitmaps, system=${reported?.div(1024) ?: -1} KB, " +
                "ours=${ourBytes / 1024} KB, ceiling=${ceiling / 1024} KB",
        )
        assertTrue(
            "$setId/$pokemonId used $used bytes against a $ceiling ceiling",
            used < ceiling,
        )
        assertTrue("expected at least one frame", steps >= 1)

        // The dedupe claim, checked against the framework itself: if RemoteViews counted
        // every step separately instead of collapsing shared instances, its figure would
        // be a multiple of ours. Skipped when the hidden API isn't reachable.
        if (reported != null) {
            assertTrue(
                "RemoteViews reported $reported bytes but we only allocated $ourBytes — " +
                    "bitmap dedupe is not behaving as the planner assumes",
                reported <= ourBytes + BITMAP_ACCOUNTING_SLACK,
            )
        }

        // Inflating proves the view tree is legal RemoteViews and the flipper really
        // received its children — a widget that inflates empty animates nothing.
        var flipperChildren = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            val inflated: View = views.apply(context, parent)
            val flipper = inflated.findViewById<ViewFlipper>(R.id.widget_flipper)
            assertNotNull("widget_flipper missing after inflation", flipper)
            flipperChildren = flipper.childCount
            assertTrue(
                "first flipper child should be an ImageView",
                flipper.getChildAt(0) is ViewGroup || flipper.getChildAt(0) is ImageView,
            )
            // Auto-start is what makes the widget animate without us ever running again.
            // It cannot be pushed through RemoteViews, so verify the layout carries it.
            assertTrue("ViewFlipper must have android:autoStart set", flipper.isAutoStart)
        }
        assertEquals("flipper child count should match the plan", steps, flipperChildren)

        decoded.recycle()
        return PipelineResult(steps, distinct, used)
    }

    /**
     * `RemoteViews.estimateMemoryUsage()` is `@hide`, but it is the exact figure
     * AppWidgetService tests before throwing, so it's worth reaching for. Returns null
     * when the hidden-API policy blocks it, in which case the caller falls back to
     * counting the bitmaps it allocated.
     */
    private fun reflectMemoryUsage(views: RemoteViews): Long? = runCatching {
        val method = RemoteViews::class.java.getDeclaredMethod("estimateMemoryUsage")
        method.isAccessible = true
        (method.invoke(views) as Int).toLong()
    }.getOrNull()

    private companion object {
        const val TAG = "WidgetPipelineTest"

        /** Padding for the cache's own bookkeeping, which is bytes, not megabytes. */
        const val BITMAP_ACCOUNTING_SLACK = 64L * 1024
    }
}
