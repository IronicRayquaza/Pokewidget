package com.pokewidgets.app.data

import android.content.Context
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
    ): String {
        val header = buildString {
            appendLine("PokéWidget $versionName")
            appendLine(TIMESTAMP.format(Date(at)))
            appendLine("$device, Android SDK $sdk")
            appendLine("thread: $threadName")
            appendLine()
        }
        return trim(header + error.stackTraceToString())
    }

    /** Caps [text] at [MAX_CHARS], keeping the head and saying so. */
    fun trim(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val dropped = text.length - MAX_CHARS
        return text.take(MAX_CHARS).trimEnd() + "\n… $dropped more characters"
    }

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
