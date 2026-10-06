package digital.vmstudio.code.core.ai.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniRouteBackgroundRunnerTest {

    @Test
    fun `shell quoting survives single quotes`() {
        assertEquals("'it'\\''s here'", OmniRouteBackgroundRunner.quote("it's here"))
        assertEquals("'/srv/my app'", OmniRouteBackgroundRunner.quote("/srv/my app"))
    }

    @Test
    fun `the runner script ships with the app at the version the installer expects`() {
        val script = javaClass.getResourceAsStream("/xcodes/xcodes_agent.py")?.bufferedReader()?.readText()
        assertNotNull(script)
        assertTrue(script!!.contains("VERSION = \"4\""))
    }

    @Test
    fun `server-side runs parse into the shared background run model`() {
        val runs = BackgroundRunParser.parse(
            """[{"id":"xo-abcdef12","cwd":"/srv/app","name":"fix it","kind":"background",""" +
                """"status":"running","state":"working","startedAt":1,"pid":42}]""",
        )
        val run = runs.single()
        assertTrue(run.isBackground)
        assertTrue(run.isRunning)
        assertEquals("xo-abcdef12", run.id)
    }
}
