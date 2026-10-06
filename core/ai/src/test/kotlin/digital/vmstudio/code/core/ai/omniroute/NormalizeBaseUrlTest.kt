package digital.vmstudio.code.core.ai.omniroute

import org.junit.Assert.assertEquals
import org.junit.Test

/** A gateway address typed by hand must never reach OkHttp in a form it throws on. */
class NormalizeBaseUrlTest {

    @Test
    fun `a full url keeps its scheme and loses the trailing v1`() {
        assertEquals("https://ai.example.com", normalizeBaseUrl(" https://ai.example.com/v1/ "))
    }

    @Test
    fun `an address without a scheme gets https`() {
        assertEquals("https://192.168.1.5:20128", normalizeBaseUrl("192.168.1.5:20128"))
    }

    @Test
    fun `an address okhttp cannot parse is treated as unset`() {
        assertEquals("", normalizeBaseUrl("my gateway.local"))
        assertEquals("", normalizeBaseUrl("   "))
    }
}
