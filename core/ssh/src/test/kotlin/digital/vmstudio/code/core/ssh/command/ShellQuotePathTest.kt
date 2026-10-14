package digital.vmstudio.code.core.ssh.command

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellQuotePathTest {

    @Test
    fun `plain absolute path is single quoted`() {
        assertEquals("'/var/log/syslog'", shellQuotePath("/var/log/syslog"))
    }

    @Test
    fun `path with single quotes is escaped properly`() {
        assertEquals("'/home/user/it'\\''s complicated'", shellQuotePath("/home/user/it's complicated"))
    }

    @Test
    fun `tilde only is preserved unquoted for shell expansion`() {
        assertEquals("~", shellQuotePath("~"))
    }

    @Test
    fun `tilde slash is quoted so tilde expands while remainder is safely quoted`() {
        assertEquals("~/'projects/my app'", shellQuotePath("~/projects/my app"))
    }

    @Test
    fun `tilde path with quotes and dangerous characters is safely escaped`() {
        assertEquals("~/'test;\$(rm -rf /)'", shellQuotePath("~/test;\$(rm -rf /)"))
    }

    @Test
    fun `empty or blank path is quoted empty`() {
        assertEquals("''", shellQuotePath(""))
    }
}
