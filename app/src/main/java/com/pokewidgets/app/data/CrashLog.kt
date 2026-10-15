package com.pokewidgets.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, written to a file the tester can copy out of Settings.
 *
 * This app is sideloaded to a handful of people with no crash reporting behind it, which
 * means a crash report arrives as "it closed when I tapped the thing" and there is no way
 * back to the stack trace — the tester would need `adb`, a cable and a developer-mode
 * phone. One file and one Copy button turns that into a paste.
 *
 * Deliberately no network, no third party and no automatic sending: the report is written
 * locally, shown to the person who owns the phone, and goes nowhere unless they choose to
 * send it.
 */
object CrashLog {

    private const val TAG = "CrashLog"
    private const val FILE_NAME = "last-crash.txt"
    private const val PREFS = "crash-log"
    private const val KEY_LAST_ANR = "lastAnrAt"

    /**
     * Reports are truncated to this many characters, keeping the head.
     *
     * The exception, its message and the first frames are what identify a crash; a deep
     * Compose stack is mostly framework noise, and a report nobody can paste into a chat
     * message is not a report.
     */
    const val MAX_CHARS = 6_000

    /**
     * Installs the handler. Chains to whatever was there before — Android's own default
     * handler is what shows the crash dialog and ends the process, so replacing it rather
     * than wrapping it would leave the app hung on a dead thread.
     */
    fun install(context: Context, versionName: String) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing must never be the reason the process fails to die properly.
            runCatching { file(appContext).writeText(report(error, thread.name, versionName)) }
                .onFailure { Log.w(TAG, "could not record the crash", it) }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Keeps the newest "app not responding" as the report, if it is one not kept before.
     *
     * An ANR never reaches the handler above: the system ends the process from outside. So it
     * is read back on the next launch from [ApplicationExitInfo], which only Android 11 and
     * later keep. Reads a file, so call it off the main thread.
     */
    fun recordLastAnr(context: Context, versionName: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val appContext = context.applicationContext
        runCatching {
            val activity = appContext.getSystemService(ActivityManager::class.java) ?: return
            val exit = activity.getHistoricalProcessExitReasons(appContext.packageName, 0, 5)
                .firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR } ?: return
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Android keeps exit records for a while; one freeze is reported once, so a
            // cleared report stays cleared.
            if (exit.timestamp <= prefs.getLong(KEY_LAST_ANR, 0L)) return
            prefs.edit().putLong(KEY_LAST_ANR, exit.timestamp).apply()
            val trace = exit.traceInputStream?.bufferedReader()?.use { it.readText() }
            file(appContext).writeText(anrReport(trace, exit.description, versionName, at = exit.timestamp))
        }.onFailure { Log.w(TAG, "could not record the last ANR", it) }
    }

    /** The stored report, or null when nothing has crashed since the last [clear]. */
    fun read(context: Context): String? =
        runCatching { file(context).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() } }
            .getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    /**
     * The report text. Pure, so the shape that actually gets pasted into a bug report is
     * unit-testable without a device.
     *
     * @param at overridable only so tests are not time-dependent.
     */
    fun report(
        error: Throwable,
        threadName: String,
        versionName: String,
        device: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        sdk: Int = android.os.Build.VERSION.SDK_INT,
        at: Long = System.currentTimeMillis(),
    ): String = trim(header(versionName, device, sdk, at, threadName) + error.stackTraceToString())

    /**
     * The report for a freeze: only the main thread out of the system's dump of every thread,
     * because the main thread is the one that stopped answering and the rest would push it
     * past [MAX_CHARS]. Pure, like [report].
     */
    fun anrReport(
        trace: String?,
        description: String?,
        versionName: String,
        device: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        sdk: Int = android.os.Build.VERSION.SDK_INT,
        at: Long = System.currentTimeMillis(),
    ): String {
        val body = buildString {
            description?.takeIf { it.isNotBlank() }?.let { appendLine(it).appendLine() }
            append(trace?.let(::mainThreadOf) ?: "Android kept no stack trace for it.")
        }
        return trim(header(versionName, device, sdk, at, "main (not responding)") + body)
    }

    /** The `"main"` block of a full ANR trace, up to the blank line that ends it. */
    fun mainThreadOf(trace: String): String? {
        val start = trace.indexOf("\"main\"").takeIf { it >= 0 } ?: return null
        val end = trace.indexOf("\n\n", start).takeIf { it >= 0 } ?: trace.length
        return trace.substring(start, end).trimEnd()
    }

    private fun header(versionName: String, device: String, sdk: Int, at: Long, threadName: String) =
        buildString {
            appendLine("PokéWidget $versionName")
            appendLine(TIMESTAMP.format(Date(at)))
            appendLine("$device, Android SDK $sdk")
            appendLine("thread: $threadName")
            appendLine()
        }

    /** Caps [text] at [MAX_CHARS], keeping the head and saying so. */
    fun trim(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val dropped = text.length - MAX_CHARS
        return text.take(MAX_CHARS).trimEnd() + "\n… $dropped more characters"
    }

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
