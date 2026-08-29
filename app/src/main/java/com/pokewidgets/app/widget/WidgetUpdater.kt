package com.pokewidgets.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object WidgetActions {
    const val ACTION_TAP = "com.pokewidgets.app.ACTION_TAP"

    /**
     * Fired by the launcher once it has actually placed a widget the app asked it to pin.
     *
     * Needed because a provider that declares an `android:configure` activity is not sent
     * APPWIDGET_UPDATE when its widget is first placed — Android leaves the first render
     * to the configuration activity, and `requestPinAppWidget` does not run one. Without
     * this callback a widget added from inside the app is bound, never rendered, and sits
     * on `widget_initial` forever: present on the home screen, showing no Pokemon.
     */
    const val ACTION_PINNED = "com.pokewidgets.app.ACTION_PINNED"
}

/**
 * Runs widget renders off the main thread.
 *
 * Deliberately *not* WorkManager, which was the original implementation and caused an
 * endless update storm. WorkManager toggles its own broadcast-receiver components with
 * `PackageManager.setComponentEnabledSetting`, every such toggle fires
 * `ACTION_PACKAGE_CHANGED` for our package, and `AppWidgetServiceImpl` responds to a
 * package change by re-broadcasting `APPWIDGET_UPDATE` to every widget the package owns.
 * That update enqueued more work, which toggled the components again: a widget on the
 * home screen re-rendered roughly once a second, forever.
 *
 * A render is a few hundred milliseconds of decode-and-scale with no need to survive
 * reboots or wait on constraints, so a plain coroutine started from the receiver's
 * `goAsync()` window is both lighter and correct.
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * @param pendingResult keeps the process alive for the render when the call came from
     *   a broadcast; always finished, including on failure.
     */
    fun request(
        context: Context,
        widgetIds: IntArray,
        pendingResult: BroadcastReceiver.PendingResult? = null,
    ) {
        if (widgetIds.isEmpty()) {
            pendingResult?.finish()
            return
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                val renderer = WidgetRenderer(appContext)
                for (id in widgetIds) {
                    runCatching { renderer.render(id) }
                        .onFailure { Log.e(TAG, "render failed for widget $id", it) }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    fun requestAll(context: Context) {
        request(context, WidgetRenderer.allWidgetIds(context))
    }

    /** Used to settle a widget back to its normal speed after a tap's excitement burst. */
    fun requestDelayed(context: Context, widgetId: Int, delayMs: Long) {
        val appContext = context.applicationContext
        mainHandler.postDelayed({ request(appContext, intArrayOf(widgetId)) }, delayMs)
    }
}
