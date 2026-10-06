package digital.vmstudio.code.core.ai.omniroute.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrepCommandTest {

    @Test
    fun `secret files are excluded from a search`() {
        val command = grepCommand("password", "/srv/app", caseSensitive = false)
        assertTrue(command.contains("'--exclude=.env'"))
        assertTrue(command.contains("'--exclude-dir=.ssh'"))
        assertTrue(command.contains("'--exclude=*.pem'"))
    }

    @Test
    fun `a query starting with a dash is not read as an option`() {
        assertTrue(grepCommand("-> Unit", "/srv/app", caseSensitive = true).contains("-e '-> Unit'"))
    }

    @Test
    fun `quotes in the query cannot break out of the command`() {
        val command = grepCommand("it's; rm -rf /", "/srv/app", caseSensitive = false)
        assertTrue(command.contains("-e 'it'\\''s; rm -rf /'"))
        assertFalse(command.contains("{"))
    }
}
