package digital.vmstudio.code.core.security.model

import digital.vmstudio.code.core.common.log.SecretRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SecretTest {

    @Test
    fun `toString never reveals the value`() {
        val secret = Secret.of("super-secret-password")

        assertEquals(SecretRedactor.MASK, secret.toString())
        assertFalse("$secret".contains("super-secret-password"))
    }

    @Test
    fun `use exposes the plaintext to the scoped block only`() {
        val secret = Secret.of("value123")

        assertEquals("value123", secret.useAsString { it })
    }

    @Test
    fun `wipe clears the buffer and blocks further reads`() {
        val secret = Secret.of("value123")
        secret.wipe()

        assertTrue(secret.isWiped)
        try {
            secret.useAsString { it }
            fail("Reading a wiped secret must throw")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("wiped"))
        }
    }

    @Test
    fun `wrapping takes ownership so wiping clears the caller's array`() {
        val source = "value123".toByteArray()
        val secret = Secret.wrapping(source)

        secret.wipe()

        assertTrue("backing array must be zeroed", source.all { it == 0.toByte() })
    }

    @Test
    fun `of copies so wiping does not disturb the caller's array`() {
        val source = "value123".toByteArray()
        val secret = Secret.of(source)

        secret.wipe()

        assertEquals("value123", String(source))
    }

    @Test
    fun `equality compares content`() {
        assertEquals(Secret.of("same-value"), Secret.of("same-value"))
        assertNotEquals(Secret.of("same-value"), Secret.of("other-value"))
    }

    @Test
    fun `useAsChars scrubs the temporary char array`() {
        val secret = Secret.of("passphrase")
        val nul = Char(0)
        var leaked: CharArray? = null

        secret.useAsChars { chars ->
            leaked = chars
            assertEquals("passphrase", String(chars))
        }

        assertTrue("chars must be zeroed after use", leaked!!.all { it == nul })
    }

    @Test
    fun `registerForRedaction masks the value in subsequent log text`() {
        try {
            Secret.of("registered-secret-value").registerForRedaction()

            val redacted = SecretRedactor.redact("token registered-secret-value used")

            assertFalse(redacted.contains("registered-secret-value"))
        } finally {
            SecretRedactor.clearRegisteredSecrets()
        }
    }
}
