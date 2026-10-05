package digital.vmstudio.code.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReporterTest {

    private fun report(logs: String) = CrashReporter.report(
        versionName = "0.5.0",
        device = "Pixel, Android 15 (SDK 35)",
        threadName = "main",
        error = IllegalStateException("boom"),
        timeMillis = 0L,
        recentLogs = logs,
    )

    @Test
    fun `report names the app, device, thread and the exception`() {
        val text = report("")
        assertTrue(text.contains("App: 0.5.0"))
        assertTrue(text.contains("Device: Pixel"))
        assertTrue(text.contains("Thread: main"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertFalse(text.contains("Recent log"))
    }

    @Test
    fun `recent log is kept to its tail`() {
        val text = report("x".repeat(20_000) + "LAST-LINE")
        assertTrue(text.contains("Recent log"))
        assertTrue(text.endsWith("LAST-LINE\n"))
        assertTrue(text.length < 20_000)
    }
}
