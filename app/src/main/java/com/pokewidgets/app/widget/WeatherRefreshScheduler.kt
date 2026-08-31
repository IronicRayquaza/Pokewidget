package com.pokewidgets.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.pokewidgets.app.data.WidgetConfigStore

/**
 * The one recurring job in the app: ask Open-Meteo what the sky is doing, and re-render the
 * widgets whose form depends on the answer.
 *
 * **This is `AlarmManager` and not `WorkManager`, on purpose.** WorkManager was the original
 * implementation of widget updates here and had to be torn out: it toggles its own broadcast
 * receiver components with `PackageManager.setComponentEnabledSetting`, every toggle fires
 * `ACTION_PACKAGE_CHANGED` for this package, and `AppWidgetServiceImpl` answers a package
 * change by re-broadcasting `APPWIDGET_UPDATE` to every widget the package owns — which
 * enqueued more work, which toggled the components again. A widget on the home screen
 * re-rendered about once a second, forever. That loop is a property of WorkManager
 * *initialising*, not of the cadence, so it would come back on first use at any interval.
 * See the note in [WidgetUpdater] and the README section of the same name.
 *
 * `setInexactRepeating` costs no permission, is batched with other alarms so it is cheap on
 * battery, and an hour is far finer than weather needs. The alarm is only armed while at
 * least one widget actually wants it, and is re-armed from `onUpdate` — which fires on boot —
 * so no `RECEIVE_BOOT_COMPLETED` is needed either.
 */
object WeatherRefreshScheduler {

    const val ACTION_WEATHER = "com.pokewidgets.app.ACTION_WEATHER"

    /** Weather does not move fast enough to justify anything tighter. */
    private val INTERVAL_MS = AlarmManager.INTERVAL_HOUR

    private const val REQUEST_CODE = 0x5EA5 // "seas[on]", and unique within this app.
    private const val TAG = "WeatherRefresh"

    /**
     * Arms the alarm if any widget uses a live form, cancels it if none does.
     *
     * Safe and cheap to call from anywhere something might have changed — saving a config,
     * choosing a city, a system update pass. Re-arming an existing alarm simply replaces it.
     */
    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        val store = WidgetConfigStore(appContext)
        val wanted = store.knownWidgetIds().any { runCatching { store.get(it).liveForm }.getOrDefault(false) }
        if (wanted) arm(appContext) else cancel(appContext)
    }

    private fun arm(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = pendingIntent(context, create = true) ?: return
        runCatching {
            // ELAPSED_REALTIME rather than RTC, and inexact: this must never wake a sleeping
            // device. The sky can wait for the phone to be in use anyway.
            alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                intent,
            )
        }.onFailure { Log.w(TAG, "could not arm the weather alarm", it) }
    }

    private fun cancel(context: Context) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val existing = pendingIntent(context, create = false) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    /**
     * @param create false to probe for an already-scheduled alarm without creating one,
     *   which is how [cancel] avoids arming the very alarm it is trying to remove.
     */
    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        // Any one of the three providers can carry the tick: it redraws by widget id, not by
        // provider, so a home screen with only small widgets is served just as well.
        val intent = Intent(context, PokemonWidgetProvider.Medium::class.java)
            .setAction(ACTION_WEATHER)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
