package com.pokewidgets.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.pokewidgets.app.R
import com.pokewidgets.app.catalog.CatalogRepository
import com.pokewidgets.app.catalog.SpriteSet
import com.pokewidgets.app.data.SpriteSource
import com.pokewidgets.app.data.WidgetConfig
import com.pokewidgets.app.data.WidgetConfigStore
import com.pokewidgets.app.sprite.BitmapOps
import com.pokewidgets.app.sprite.DecodedSprite
import com.pokewidgets.app.sprite.FramePlanner
import com.pokewidgets.app.sprite.GifFrames
import com.pokewidgets.app.sprite.IdleAnimator
import com.pokewidgets.app.sprite.IdleFrame
import com.pokewidgets.app.sprite.IdleStyle
import kotlin.math.roundToInt

/**
 * Turns a [WidgetConfig] into the `RemoteViews` the launcher displays.
 *
 * The animation itself is a `ViewFlipper` running inside the launcher's own process:
 * once the frames are handed over, nothing of ours runs again until the user changes
 * something. No alarms, no services, no wakelocks, and the launcher stops the flipping
 * on its own when the widget scrolls off screen.
 */
class WidgetRenderer(private val context: Context) {

    private val catalog = CatalogRepository.get(context)
    private val source = SpriteSource(context)
    private val configStore = WidgetConfigStore(context)

    suspend fun render(widgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        val config = resolveConfig(widgetId)
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()

        val views = try {
            buildViews(widgetId, config, options)
        } catch (e: Exception) {
            Log.e(TAG, "failed to build widget $widgetId", e)
            statusViews(widgetId, config, "Couldn't load sprite")
        }
        updateSafely(manager, widgetId, views, config)
    }

    /**
     * Settings for this widget, adopting the user's in-app choice if this is a brand new
     * widget they just asked the launcher to place.
     *
     * `requestPinAppWidget` carries no payload and does not run the configuration
     * activity, so without this every "Add Charizard to home screen" would land as the
     * default Pikachu. Doing it here rather than in a pin-callback broadcast means it
     * works regardless of whether a given launcher fires that callback at all.
     */
    private suspend fun resolveConfig(widgetId: Int): WidgetConfig {
        if (configStore.exists(widgetId)) return configStore.get(widgetId)
        val pending = configStore.takePendingPin() ?: return configStore.get(widgetId)
        configStore.put(widgetId, pending)
        return pending
    }

    /**
     * A widget update that busts the system's bitmap ceiling throws, and an uncaught
     * throw here takes the launcher down with it. The planner budgets conservatively so
     * this should never fire — but "should never" is not a thing to bet someone's home
     * screen on, so re-plan against a halved budget and fall back to a still frame.
     */
    private suspend fun updateSafely(
        manager: AppWidgetManager,
        widgetId: Int,
        views: RemoteViews,
        config: WidgetConfig,
    ) {
        try {
            manager.updateAppWidget(widgetId, views)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "widget $widgetId exceeded the bitmap ceiling; retrying smaller", e)
            val retry = runCatching {
                buildViews(widgetId, config, manager.getAppWidgetOptions(widgetId), budgetScale = 0.35)
            }.getOrNull() ?: statusViews(widgetId, config, "Sprite too large")
            runCatching { manager.updateAppWidget(widgetId, retry) }
                .onFailure { Log.e(TAG, "widget $widgetId could not be updated at all", it) }
        }
    }

    private suspend fun buildViews(
        widgetId: Int,
        config: WidgetConfig,
        options: Bundle?,
        budgetScale: Double = 1.0,
    ): RemoteViews {
        val set = catalog.set(config.setId)
            ?: return statusViews(widgetId, config, "Sprite set unavailable")

        val parts = source.spriteParts(set, config.spriteKey)
            ?: return statusViews(
                widgetId,
                config,
                if (source.isSpriteCached(config.spriteKey, set.ext)) {
                    "Couldn't read sprite"
                } else {
                    "Tap to retry — no connection"
                },
            )

        val decoded = GifFrames.decode(parts, isGif = set.ext == "gif", delaysMs = set.frameDelaysMs)
            ?: return statusViews(widgetId, config, "Couldn't decode sprite")

        return try {
            composeViews(widgetId, config, set, decoded, options, budgetScale)
        } finally {
            decoded.recycle()
        }
    }

    private fun composeViews(
        widgetId: Int,
        config: WidgetConfig,
        set: SpriteSet,
        decoded: DecodedSprite,
        options: Bundle?,
        budgetScale: Double,
    ): RemoteViews {
        val metrics = context.resources.displayMetrics
        val (boxW, boxH) = contentBoxPx(options, metrics)
        val budget = (
            FramePlanner.budgetFor(metrics.widthPixels, metrics.heightPixels) * budgetScale
            ).toLong().coerceAtLeast(MIN_BUDGET)

        val bounds = BitmapOps.unionOpaqueBounds(decoded.frames)
        val views = RemoteViews(context.packageName, R.layout.widget_root)

        // Background plate first, so the sprite composites over it.
        if (config.showBackground) {
            val radiusPx = config.cornerRadiusDp * metrics.density
            views.setImageViewBitmap(
                R.id.widget_background,
                BitmapOps.roundedPlate(boxW, boxH, config.backgroundColor, radiusPx),
            )
            views.setViewVisibility(R.id.widget_background, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_background, View.GONE)
        }
        views.setViewVisibility(R.id.widget_status, View.GONE)
        views.setViewVisibility(R.id.widget_flipper, View.VISIBLE)
        views.removeAllViews(R.id.widget_flipper)

        val excited = config.excitedUntilMs > System.currentTimeMillis()
        val speedUp = if (excited) EXCITED_SPEEDUP else 1.0

        if (set.animated && decoded.frames.size > 1) {
            addAnimatedFrames(views, decoded, bounds, config, boxW, boxH, budget, speedUp)
        } else {
            addIdleFrames(views, decoded, bounds, config, boxW, boxH, budget, speedUp)
        }

        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(widgetId))
        return views
    }

    /** The real thing: resampled GIF frames driven by the flipper. */
    private fun addAnimatedFrames(
        views: RemoteViews,
        decoded: DecodedSprite,
        bounds: android.graphics.Rect,
        config: WidgetConfig,
        boxW: Int,
        boxH: Int,
        budget: Long,
        speedUp: Double,
    ) {
        val plan = FramePlanner.plan(
            FramePlanner.Request(
                source = FramePlanner.Source(
                    contentWidth = bounds.width(),
                    contentHeight = bounds.height(),
                    delaysMs = decoded.delaysMs,
                    canonical = BitmapOps.canonicalFrames(decoded.frames, bounds),
                ),
                targetWidthPx = boxW,
                targetHeightPx = boxH,
                maxScale = config.fill.maxScale,
                desiredFps = config.smoothness.fps,
                budgetBytes = budget,
            ),
        )

        // One scaled bitmap per *distinct* source frame, reused across every step that
        // shows it. RemoteViews.BitmapCache dedupes by object identity, so a long-held
        // frame costs one bitmap instead of a dozen — which is what keeps sprites like
        // Crystal's (a 990 ms hold in a 2.7 s loop) inside the budget.
        val scaled = HashMap<Int, Bitmap>(plan.distinctFrames.size)
        for (index in plan.distinctFrames) {
            decoded.frames.getOrNull(index)?.let { frame ->
                scaled[index] = BitmapOps.cropAndScale(frame, bounds, plan.scale)
            }
        }

        for (index in plan.sourceIndices) {
            val bitmap = scaled[index] ?: continue
            val child = RemoteViews(context.packageName, R.layout.widget_frame)
            child.setImageViewBitmap(R.id.widget_frame_image, bitmap)
            views.addView(R.id.widget_flipper, child)
        }

        startFlipping(views, (plan.frameIntervalMs / speedUp).roundToInt())
        Log.d(
            TAG,
            "animated: ${plan.stepCount} steps / ${plan.distinctFrames.size} bitmaps, " +
                "scale ${plan.scale}, ${plan.fps} fps, ${plan.estimatedBytes / 1024} KB of $budget",
        )
    }

    /**
     * Still sprites — every GBA set including Emerald, all of Gen 4, and everything from
     * Gen 6 on — get a generated idle animation instead of sitting dead on the home
     * screen. See [IdleAnimator] for why those games have no animated sprites to fetch.
     *
     * The cost is one bitmap per distinct *shape* in the loop, not one per step:
     * translation is expressed as padding, and `RemoteViews.BitmapCache` dedupes by
     * object identity, so a ten-step sway is still a single bitmap and a six-step breath
     * is three.
     */
    private fun addIdleFrames(
        views: RemoteViews,
        decoded: DecodedSprite,
        bounds: android.graphics.Rect,
        config: WidgetConfig,
        boxW: Int,
        boxH: Int,
        budget: Long,
        speedUp: Double,
    ) {
        val source = decoded.frames.firstOrNull() ?: return
        // Leave room for the widest step, so a breath does not clip at the edges of a
        // snugly-fitted widget.
        val style = config.idleStyle
        val widest = style.frames.maxOfOrNull { it.scaleXPermille } ?: IdleFrame.NATURAL
        val tallest = style.frames.maxOfOrNull { it.scaleYPermille } ?: IdleFrame.NATURAL
        val scale = FramePlanner.fitScale(
            contentWidth = bounds.width() * widest / IdleFrame.NATURAL,
            contentHeight = bounds.height() * tallest / IdleFrame.NATURAL,
            targetWidthPx = boxW,
            targetHeightPx = boxH,
            maxScale = config.fill.maxScale,
        )
        val natural = BitmapOps.cropAndScale(source, bounds, scale)

        val frames = style.frames
        val shapes = frames.distinctBy { it.shapeKey }
        val perBitmap = natural.width.toLong() * natural.height * 4L

        // Shape changes are the only thing here that costs memory, so they are the only
        // thing worth giving up. Drop to pure translation, then to a single still frame.
        val affordable = when {
            perBitmap * shapes.size <= budget -> frames
            perBitmap * 2 <= budget -> IdleStyle.BOB.frames
            perBitmap <= budget -> listOf(IdleFrame())
            else -> {
                // Vanishingly unlikely at sprite sizes, but a still frame beats a crash.
                addStill(views, BitmapOps.cropAndScale(source, bounds, 1))
                return
            }
        }

        val byShape = HashMap<Int, Bitmap>(shapes.size)
        for (frame in affordable) {
            byShape.getOrPut(frame.shapeKey) {
                BitmapOps.resize(
                    natural,
                    natural.width * frame.scaleXPermille / IdleFrame.NATURAL,
                    natural.height * frame.scaleYPermille / IdleFrame.NATURAL,
                )
            }
        }

        for (frame in affordable) {
            val bitmap = byShape[frame.shapeKey] ?: natural
            val child = RemoteViews(context.packageName, R.layout.widget_frame)
            child.setImageViewBitmap(R.id.widget_frame_image, bitmap)

            // The frame's ImageView is scaleType="center", so a shorter bitmap would
            // float upward as it squashes. Pushing down by half the height it lost keeps
            // the sprite's feet planted, which is what makes a squash read as weight
            // rather than as the whole creature shrinking.
            val plant = (natural.height - bitmap.height) / 2
            val dy = frame.dySource * scale + plant
            val dx = frame.dxSource * scale
            // The equal-and-opposite padding on the far side keeps the content box the
            // same size, so nothing reflows between steps.
            child.setViewPadding(R.id.widget_frame_image, dx, dy, -dx, -dy)
            views.addView(R.id.widget_flipper, child)
        }

        if (affordable.size <= 1) return
        startFlipping(views, (style.frameIntervalMs / speedUp).roundToInt())
        Log.d(
            TAG,
            "idle ${style.name.lowercase()}: ${affordable.size} steps / ${byShape.size} " +
                "bitmaps, scale $scale, ${perBitmap * byShape.size / 1024} KB of $budget",
        )
    }

    private fun addStill(views: RemoteViews, bitmap: Bitmap) {
        val child = RemoteViews(context.packageName, R.layout.widget_frame)
        child.setImageViewBitmap(R.id.widget_frame_image, bitmap)
        views.addView(R.id.widget_flipper, child)
    }

    /**
     * The one animation lever `RemoteViews` gives us.
     *
     * Only the interval is settable at runtime: `ViewFlipper.setFlipInterval(int)` is a
     * `@RemotableViewMethod`, but `setAutoStart(boolean)` is not — pushing it throws
     * `ActionException: ViewFlipper can't use method with RemoteViews`. Auto-start is
     * therefore declared as `android:autoStart` in widget_root.xml instead.
     */
    private fun startFlipping(views: RemoteViews, intervalMs: Int) {
        views.setInt(R.id.widget_flipper, "setFlipInterval", intervalMs.coerceIn(16, 5_000))
    }

    private fun statusViews(widgetId: Int, config: WidgetConfig, message: String): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.removeAllViews(R.id.widget_flipper)
        views.setViewVisibility(R.id.widget_flipper, View.GONE)
        views.setViewVisibility(R.id.widget_background, View.GONE)
        views.setViewVisibility(R.id.widget_status, View.VISIBLE)
        views.setTextViewText(R.id.widget_status, message)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(widgetId))
        return views
    }

    private fun tapIntent(widgetId: Int): PendingIntent {
        val intent = Intent(context, PokemonWidgetProvider.Medium::class.java).apply {
            action = WidgetActions.ACTION_TAP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // The extras are not part of a PendingIntent's identity, so without a unique
            // data URI every widget would end up sharing the first one's intent.
            data = android.net.Uri.parse("pokewidget://tap/$widgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The launcher reports the widget's size in dp, and only as a min/max pair. Take the
     * larger figure for each axis so the sprite is planned for the space it will actually
     * occupy in the current orientation.
     */
    private fun contentBoxPx(options: Bundle?, metrics: DisplayMetrics): Pair<Int, Int> {
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
            ?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)?.takeIf { it > 0 }
            ?: DEFAULT_BOX_DP
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            ?.takeIf { it > 0 }
            ?: options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 }
            ?: DEFAULT_BOX_DP

        val w = (widthDp * metrics.density).toInt().coerceIn(48, metrics.widthPixels)
        val h = (heightDp * metrics.density).toInt().coerceIn(48, metrics.heightPixels)
        return w to h
    }

    companion object {
        private const val TAG = "WidgetRenderer"
        private const val DEFAULT_BOX_DP = 110
        private const val MIN_BUDGET = 512L * 1024
        const val EXCITED_SPEEDUP = 2.2

        /** Pushes an update for every placed widget, e.g. after a config change. */
        fun componentNames(context: Context): List<ComponentName> = listOf(
            ComponentName(context, PokemonWidgetProvider.Small::class.java),
            ComponentName(context, PokemonWidgetProvider.Medium::class.java),
            ComponentName(context, PokemonWidgetProvider.Large::class.java),
        )

        fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return componentNames(context)
                .flatMap { manager.getAppWidgetIds(it).toList() }
                .distinct()
                .toIntArray()
        }
    }
}
