package digital.vmstudio.code.core.ai.omniroute.agent

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class WebFetcherTest {

    @Test
    fun `private and loopback hosts are refused`() {
        listOf(
            "http://localhost/x",
            "http://127.0.0.1/",
            "http://10.0.0.5/",
            "http://192.168.1.1/",
            "http://172.20.0.1/",
            "http://169.254.169.254/latest/meta-data",
            "http://printer.local/",
            "http://[::1]/",
        ).forEach { assertTrue(it, isPrivateHost(it.toHttpUrl())) }
    }

    @Test
    fun `public hosts are allowed`() {
        listOf("https://developer.android.com/", "http://93.184.216.34/", "https://172.32.0.1/")
            .forEach { assertFalse(it, isPrivateHost(it.toHttpUrl())) }
    }

    @Test
    fun `html is reduced to readable text with its title`() {
        val html = """
            <html><head><title>Docs &amp; Guides</title><style>p{color:red}</style></head>
            <body><script>alert(1)</script><h1>Intro</h1><p>Hello&nbsp;<b>world</b></p><p>Bye</p></body></html>
        """.trimIndent()
        assertEquals("# Docs & Guides\n\nIntro\nHello world\nBye", readable(html))
    }

    @Test
    fun `non html bodies pass through`() {
        assertEquals("{\"ok\":true}", readable("  {\"ok\":true}\n"))
    }

    @Test
    fun `resolved private addresses are refused`() {
        listOf(
            "127.0.0.1", "10.1.2.3", "192.168.0.10", "172.16.5.5",
            "169.254.1.1", "100.100.1.1", "::1", "fd00::1",
        ).forEach { assertTrue(it, isPrivateAddress(InetAddress.getByName(it))) }
        listOf("93.184.216.34", "1.1.1.1", "2606:4700::1111")
            .forEach { assertFalse(it, isPrivateAddress(InetAddress.getByName(it))) }
    }
}
