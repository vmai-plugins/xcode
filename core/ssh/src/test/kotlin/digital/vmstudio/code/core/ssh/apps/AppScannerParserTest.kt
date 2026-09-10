package digital.vmstudio.code.core.ssh.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppScannerParserTest {

    @Test
    fun `parses a live transcript with pm2 state and listening ports`() {
        val output = """
            ---VMS_APPS_BEGIN---
            ---VMS_APP---
            PATH=/home/dev/apps/web-shop
            NAME=web-shop
            FRAMEWORK=nodejs
            BRANCH=main
            REMOTE=https://github.com/acme/web-shop.git
            ---VMS_APP---
            PATH=/home/dev/apps/reporting
            NAME=reporting
            FRAMEWORK=python
            ---VMS_PM2---
            [{"name":"web-shop","pid":4242,
              "pm2_env":{"status":"online","pm_uptime":86400000},
              "monit":{"cpu":1.2,"memory":104857600}}]
            ---VMS_PORTS---
            LISTEN 0 128 0.0.0.0:3000 0.0.0.0:* users:(("node",pid=4242,fd=21))
            LISTEN 0 128 0.0.0.0:8080 0.0.0.0:* users:(("python",pid=9,fd=3))
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals(2, apps.size)
        val webShop = apps[0]
        assertEquals("web-shop", webShop.name)
        assertEquals("/home/dev/apps/web-shop", webShop.path)
        assertEquals(AppFramework.NODEJS, webShop.framework)
        assertEquals("main", webShop.branch)
        assertEquals("https://github.com/acme/web-shop.git", webShop.remoteUrl)
        assertEquals(AppStatus.RUNNING, webShop.status)
        assertTrue(webShop.isRunning)
        assertEquals("4242", webShop.pid)
        assertEquals(3000, webShop.port)
        assertEquals(1.2, webShop.cpuPercent!!, 0.001)
        assertEquals(100, webShop.memoryMb!!)
        assertEquals(86_400_000L, webShop.uptimeMillis!!)
        assertTrue(webShop.pm2Managed)

        val reporting = apps[1]
        assertEquals(AppFramework.PYTHON, reporting.framework)
        assertEquals(AppStatus.UNKNOWN, reporting.status)
        assertNull(reporting.port)
        assertFalse(reporting.pm2Managed)
    }

    @Test
    fun `maps pm2 stopped and errored states`() {
        val output = """
            ---VMS_APP---
            PATH=/apps/one
            NAME=one
            FRAMEWORK=nodejs
            ---VMS_APP---
            PATH=/apps/two
            NAME=two
            FRAMEWORK=rust
            ---VMS_PM2---
            [{"name":"one","pm2_env":{"status":"stopped"}},
             {"name":"two","pm2_env":{"status":"errored"}}]
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals(AppStatus.STOPPED, apps[0].status)
        assertEquals(AppStatus.CRASHED, apps[1].status)
        assertFalse(apps[0].isRunning)
    }

    @Test
    fun `apps appear with unknown status when pm2 is absent`() {
        val output = """
            ---VMS_APPS_BEGIN---
            ---VMS_APP---
            PATH=/srv/static-site
            NAME=static-site
            FRAMEWORK=statichtml
            ---VMS_PORTS---
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals(1, apps.size)
        assertEquals(AppFramework.STATIC_HTML, apps[0].framework)
        assertEquals(AppStatus.UNKNOWN, apps[0].status)
        assertNull(apps[0].pid)
    }

    @Test
    fun `directory names with spaces are captured whole`() {
        val output = """
            ---VMS_APP---
            PATH=/apps/my cool app
            NAME=my cool app
            FRAMEWORK=docker
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals("my cool app", apps[0].name)
        assertEquals("/apps/my cool app", apps[0].path)
        assertEquals(AppFramework.DOCKER, apps[0].framework)
    }

    @Test
    fun `garbage pm2 json degrades to unknown rather than failing the scan`() {
        val output = """
            ---VMS_APP---
            PATH=/apps/one
            NAME=one
            FRAMEWORK=unknown
            ---VMS_PM2---
            not json at all
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals(1, apps.size)
        assertEquals(AppStatus.UNKNOWN, apps[0].status)
        assertFalse(apps[0].pm2Managed)
    }

    @Test
    fun `port rows without a pid cannot be joined to an app`() {
        val output = """
            ---VMS_APP---
            PATH=/apps/one
            NAME=one
            FRAMEWORK=nodejs
            ---VMS_PORTS---
            LISTEN 0 128 0.0.0.0:5000 0.0.0.0:* users:(("node",fd=21))
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertNull(apps[0].port)
    }

    @Test
    fun `empty or hostile output yields no apps`() {
        assertTrue(AppScannerParser.parse("").isEmpty())
        assertTrue(AppScannerParser.parse("login banner noise\nwith no markers\n").isEmpty())
    }

    @Test
    fun `first pid wins when one process listens on several ports`() {
        val output = """
            ---VMS_APP---
            PATH=/apps/one
            NAME=one
            FRAMEWORK=nodejs
            ---VMS_PM2---
            [{"name":"one","pid":77,"pm2_env":{"status":"online"}}]
            ---VMS_PORTS---
            LISTEN 0 128 0.0.0.0:3000 0.0.0.0:* users:(("node",pid=77,fd=10))
            LISTEN 0 128 127.0.0.1:3001 0.0.0.0:* users:(("node",pid=77,fd=11))
        """.trimIndent()

        val apps = AppScannerParser.parse(output)

        assertEquals(3000, apps[0].port)
        assertNotNull(apps[0].pid)
    }
}