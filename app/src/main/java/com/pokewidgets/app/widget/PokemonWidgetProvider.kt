package com.pokewidgets.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Three concrete providers so the launcher's widget picker offers size presets. They
 * share every behaviour; only the `appwidget-provider` XML differs, and all three are
 * freely resizable afterwards.
 */
sealed class PokemonWidgetProvider : AppWidgetProvider() {

    class Small : PokemonWidgetProvider()
    class Medium : PokemonWidgetProvider()
    class Large : PokemonWidgetProvider()

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        // goAsync() is safe here: AppWidgetProvider dispatches onUpdate synchronously
        // from onReceive, so we are still inside the receiver's window.
        WidgetUpdater.request(context, widgetIds, goAsync())
    }

    /** Fired when the user resizes the widget — the sprite is re-planned for the new box. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle?,
    ) {
        WidgetUpdater.request(context, intArrayOf(widgetId), goAsync())
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        val store = WidgetConfigStore(context)
        scope.launch { store.remove(widgetIds) }
    }

    /** Widget ids are reassigned after a backup restore; carry the settings across. */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val store = WidgetConfigStore(context)
        scope.launch {
            store.remap(oldWidgetIds, newWidgetIds)
            WidgetUpdater.request(context, newWidgetIds)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetActions.ACTION_TAP) {
            handleTap(context, intent)
            return
        }
        if (intent.action == WidgetActions.ACTION_PINNED) {
            adoptPinned(context, goAsync())
            return
        }
        // Everything else goes through onUpdate, which renders via WidgetRenderer. Note
        // that a *newly placed* widget is not among them: a provider declaring an
        // android:configure activity is deliberately sent no APPWIDGET_UPDATE on
        // placement, which is what ACTION_PINNED above exists to cover.
        super.onReceive(context, intent)
    }

    /**
     * Renders every placed widget that has no settings yet.
     *
     * This is the first render for a widget added by the app's "Add to home screen"
     * button. Because these providers declare a configuration activity, the system sends
     * no APPWIDGET_UPDATE for a freshly placed widget, and `requestPinAppWidget` does not
     * launch that activity — so nothing else in the app would ever draw it, and the
     * pending pin choice would never be consumed.
     *
     * The callback carries no widget id, so the new widget is found by elimination: it is
     * the placed one the config store has never heard of. Rendering it runs
     * [WidgetRenderer.resolveConfig], which adopts the Pokemon the user picked.
     */
    private fun adoptPinned(context: Context, pending: BroadcastReceiver.PendingResult) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                val store = WidgetConfigStore(appContext)
                val renderer = WidgetRenderer(appContext)
                for (id in WidgetRenderer.allWidgetIds(appContext)) {
                    if (store.exists(id)) continue
                    runCatching { renderer.render(id) }
                        .onFailure { Log.e(TAG, "could not render pinned widget $id", it) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "pin callback failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Widgets get a click callback and nothing else — no touch-down, no gestures — so
     * every interaction is funnelled through this one broadcast.
     *
     * `goAsync()` holds the receiver alive for the work; a cry is ~1 s of audio, well
     * inside the ~10 s a broadcast receiver is granted.
     */
    private fun handleTap(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val store = WidgetConfigStore(appContext)
                val config = store.get(widgetId)
                when (config.tapAction) {
                    TapAction.CRY -> {
                        if (config.cryEnabled) CryPlayer.play(appContext, config.pokemonId, config.legacyCry)
                    }

                    TapAction.SHINY -> {
                        store.update(widgetId) { it.copy(shiny = !it.shiny) }
                        WidgetRenderer(appContext).render(widgetId)
                    }

                    TapAction.FLIP -> {
                        store.update(widgetId) { it.copy(back = !it.back) }
                        WidgetRenderer(appContext).render(widgetId)
                    }

                    TapAction.EXCITE -> {
                        val until = System.currentTimeMillis() + EXCITED_DURATION_MS
                        store.update(widgetId) { it.copy(excitedUntilMs = until) }
                        WidgetRenderer(appContext).render(widgetId)
                        if (config.cryEnabled) CryPlayer.play(appContext, config.pokemonId, config.legacyCry)
                        // Settle back to the normal rate once the burst is over.
                        WidgetUpdater.requestDelayed(appContext, widgetId, EXCITED_DURATION_MS)
                    }

                    TapAction.OPEN_APP -> {
                        val launch = appContext.packageManager
                            .getLaunchIntentForPackage(appContext.packageName)
                            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        launch?.let { appContext.startActivity(it) }
                    }

                    TapAction.NONE -> Unit
                }
            } catch (e: Exception) {
                Log.e(TAG, "tap handling failed for widget $widgetId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PokemonWidgetProvider"
        const val EXCITED_DURATION_MS = 2_200L

        /**
         * Broadcast receivers are torn down as soon as they return, so the work has to
         * outlive the instance. `goAsync()` keeps the *process* alive; this scope keeps
         * the coroutine off the main thread while it does.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
