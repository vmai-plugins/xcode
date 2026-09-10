package digital.vmstudio.code.core.ssh.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMetricsParserTest {

    @Test
    fun `parses modern procps-ng output from Debian and Ubuntu hosts`() {
        val output = """
            ---VMS_UPTIME---
             14:32:07 up 42 days,  3:14,  2 users,  load average: 0.15, 0.08, 0.01
            ---VMS_MEM---
                           total        used        free      shared  buff/cache   available
            Mem:           3921        1421         512          58        1987        2211
            Swap:          2047           0        2047
            ---VMS_DISK---
            /dev/vda1       41250724 8364912 30765172  22% /
            ---VMS_CPU---
            %Cpu(s):  1.7 us,  0.7 sy,  0.0 ni, 97.0 id,  0.3 wa,  0.0 hi,  0.3 si,  0.0 st
            ---VMS_PS---
            USER     PID %CPU %MEM COMMAND
            root       1  0.0  0.2 systemd
            www-data 812  2.5  1.1 nginx
            mysql    977  1.3  4.8 mysqld
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, collectedAtMillis = 1_000L)

        assertEquals("42 days,  3:14", metrics.uptime)
        assertEquals(listOf(0.15, 0.08, 0.01), metrics.loadAverage)
        assertEquals(3921, metrics.ramTotalMb)
        assertEquals(1421, metrics.ramUsedMb)
        assertEquals(0.3624f, metrics.ramFraction!!, 0.001f)
        assertEquals(39.34, metrics.diskTotalGb, 0.01)
        assertEquals(7.98, metrics.diskUsedGb, 0.01)
        assertEquals(3.0, metrics.cpuPercent!!, 0.001)
        assertEquals(3, metrics.topProcesses.size)
        assertEquals("nginx", metrics.topProcesses[0].command)
        assertEquals("www-data", metrics.topProcesses[0].user)
        assertEquals(2.5, metrics.topProcesses[0].cpuPercent, 0.001)
        assertEquals(1_000L, metrics.collectedAtMillis)
        assertTrue(metrics.hasData)
    }

    @Test
    fun `parses old procps percent-suffixed cpu line`() {
        val output = """
            ---VMS_CPU---
            Cpu(s):  5.3%us,  1.2%sy,  0.0%ni, 92.5%id,  1.0%wa,  0.0%hi,  0.0%si,  0.0%st
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertEquals(7.5, metrics.cpuPercent!!, 0.001)
    }

    @Test
    fun `parses BusyBox output from Alpine hosts`() {
        val output = """
            ---VMS_UPTIME---
             09:15:02 up 2 days, 1:03, load average: 0.10, 0.14, 0.09
            ---VMS_MEM---
            Mem: 498800 231412 267388
            ---VMS_CPU---
            CPU:  12% usr  5% sys   0% nic  80% idle   3% sirq
            ---VMS_PS---
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertEquals("2 days, 1:03", metrics.uptime)
        assertEquals(listOf(0.10, 0.14, 0.09), metrics.loadAverage)
        assertEquals(498800, metrics.ramTotalMb)
        assertEquals(231412, metrics.ramUsedMb)
        assertEquals(20.0, metrics.cpuPercent!!, 0.001)
        assertTrue(metrics.topProcesses.isEmpty())
        assertTrue(metrics.hasData)
    }

    @Test
    fun `falls back to user plus system when idle is absent`() {
        val output = """
            ---VMS_CPU---
            %Cpu(s):  2.0 us,  3.0 sy
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertEquals(5.0, metrics.cpuPercent!!, 0.001)
    }

    @Test
    fun `normalises decimal-comma locales`() {
        val output = """
            ---VMS_CPU---
            %Cpu(s):  1,7 us,  0,7 sy,  0,0 ni, 97,0 id
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertEquals(3.0, metrics.cpuPercent!!, 0.001)
    }

    @Test
    fun `empty or hostile output yields a metric-less sample instead of throwing`() {
        val metrics = ServerMetricsParser.parse("", 0L)

        assertNull(metrics.cpuPercent)
        assertNull(metrics.uptime)
        assertTrue(metrics.topProcesses.isEmpty())
        assertEquals(0, metrics.ramTotalMb)
        assertTrue(!metrics.hasData)
    }

    @Test
    fun `partial output keeps the sections that did parse`() {
        val output = """
            some login banner noise
            ---VMS_UPTIME---
             14:32:07 up 6 hours,  1 user,  load average: 1.20
            ---VMS_DISK---
            /dev/sda1 1024000 512000 512000 50% /
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertNull(metrics.cpuPercent)
        assertEquals("6 hours", metrics.uptime)
        assertEquals(listOf(1.20), metrics.loadAverage)
        assertEquals(0.49, metrics.diskUsedGb, 0.01)
        assertEquals(0.98, metrics.diskTotalGb, 0.01)
        assertTrue(metrics.hasData)
    }

    @Test
    fun `process rows with non-numeric pid or too few columns are skipped`() {
        val output = """
            ---VMS_PS---
            USER     PID %CPU %MEM COMMAND
            root     412  7.5  0.4 kworker/u8:2+events
            weird    abc  1.0  0.1 broken
            short only-three
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        assertEquals(1, metrics.topProcesses.size)
        assertEquals("412", metrics.topProcesses[0].pid)
        assertEquals("kworker/u8:2+events", metrics.topProcesses[0].command)
    }

    @Test
    fun `wrapped df line fragment is not misparsed as numbers`() {
        val output = """
            ---VMS_DISK---
            /dev/mapper/ubuntu--vg-ubuntu--lv
            41250724 8364912 30765172  22% /
        """.trimIndent()

        val metrics = ServerMetricsParser.parse(output, 0L)

        // The fragment line has fewer than 6 tokens and is skipped; the wrapped
        // continuation is 5 tokens and is also skipped rather than misread.
        assertEquals(0.0, metrics.diskTotalGb, 0.0)
    }
}
