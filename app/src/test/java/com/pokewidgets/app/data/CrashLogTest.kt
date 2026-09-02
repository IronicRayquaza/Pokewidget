package com.pokewidgets.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the report *text*, which is the part that has to be right.
 *
 * Writing and reading the file needs a `Context` and belongs to the instrumented tests, but
 * the text is what a tester actually pastes into a message — and a report that has lost the
 * exception type, the app version or the device is worse than no report, because it looks
 * like evidence while being useless. These assertions are the contract for that.
 */
class CrashLogTest {

    private val error = IllegalStateException("the sky fell in")

    @Test
    fun `the report identifies the build, the device and the failure`() {
        val report = CrashLog.report(
            error = error,
            threadName = "main",
            versionName = "1.3-debug",
            device = "samsung SM-G781B",
            sdk = 34,
            at = 0L,
        )
        assertTrue("version", report.contains("1.3-debug"))
        assertTrue("device", report.contains("samsung SM-G781B"))
        assertTrue("api level", report.contains("34"))
        assertTrue("thread", report.contains("main"))
        assertTrue("exception type", report.contains("IllegalStateException"))
        assertTrue("exception message", report.contains("the sky fell in"))
        // Without a frame there is nothing to act on, only something to be sad about.
        assertTrue("a stack frame", report.contains("at com.pokewidgets.app.data.CrashLogTest"))
    }

    @Test
    fun `a cause is carried along`() {
        // Almost every real crash here will be wrapped — a coroutine failure, a DataStore
        // IOException behind an edit — so the cause is usually the interesting half.
        val wrapped = RuntimeException("outer", IllegalArgumentException("the actual reason"))
        val report = CrashLog.report(wrapped, "main", "1.3-debug", "device", 34, 0L)
        assertTrue(report.contains("the actual reason"))
        assertTrue(report.contains("Caused by"))
    }

    @Test
    fun `a short report is left exactly as it is`() {
        val text = "one\ntwo\nthree"
        assertEquals(text, CrashLog.trim(text))
    }

    @Test
    fun `a long report is cut to something a person can paste`() {
        val trimmed = CrashLog.trim("x".repeat(CrashLog.MAX_CHARS * 2))
        // The cap plus the note about what was dropped, and nothing like the original size.
        assertTrue(trimmed.length < CrashLog.MAX_CHARS + 60)
        assertTrue(trimmed.startsWith("x"))
        assertTrue("says what it dropped", trimmed.contains("more characters"))
    }

    @Test
    fun `trimming keeps the head, because that is where the exception is`() {
        val head = "java.lang.IllegalStateException: the sky fell in\n"
        val trimmed = CrashLog.trim(head + "\tat somewhere\n".repeat(CrashLog.MAX_CHARS))
        assertTrue(trimmed.startsWith(head))
    }
}
