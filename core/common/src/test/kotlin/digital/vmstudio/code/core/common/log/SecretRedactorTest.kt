package digital.vmstudio.code.core.common.log

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {

    @After
    fun tearDown() = SecretRedactor.clearRegisteredSecrets()

    @Test
    fun `registered secret is masked wherever it appears`() {
        SecretRedactor.registerSecret("hunter2-correct-horse")

        val output = SecretRedactor.redact("connecting with hunter2-correct-horse now")

        assertFalse(output.contains("hunter2-correct-horse"))
        assertTrue(output.contains(SecretRedactor.MASK))
    }

    @Test
    fun `short values are not registered to avoid mangling unrelated text`() {
        SecretRedactor.registerSecret("abc")

        assertEquals("abc def", SecretRedactor.redact("abc def"))
    }

    @Test
    fun `api key assignments are redacted by shape`() {
        val output = SecretRedactor.redact("api_key=sk-live-2f9a83bc71e4 sent")

        assertFalse(output.contains("sk-live-2f9a83bc71e4"))
    }

    @Test
    fun `password in json is redacted`() {
        val output = SecretRedactor.redact("""{"password": "s3cr3t-value", "user": "deploy"}""")

        assertFalse(output.contains("s3cr3t-value"))
        assertTrue("non-secret fields survive", output.contains("deploy"))
    }

    @Test
    fun `authorization header is redacted`() {
        val output = SecretRedactor.redact("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.abc.def")

        assertFalse(output.contains("eyJhbGciOiJIUzI1NiJ9"))
    }

    @Test
    fun `credentials embedded in a url are redacted but host is kept`() {
        val output = SecretRedactor.redact("https://deploy:p4ssw0rd-long@git.example.com/repo.git")

        assertFalse(output.contains("p4ssw0rd-long"))
        assertTrue(output.contains("git.example.com"))
        assertTrue(output.contains("deploy"))
    }

    @Test
    fun `private key block body is removed`() {
        val key = buildString {
            appendLine("-----BEGIN OPENSSH PRIVATE KEY-----")
            appendLine("b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAAB")
            appendLine("-----END OPENSSH PRIVATE KEY-----")
        }

        val output = SecretRedactor.redact(key)

        assertFalse(output.contains("b3BlbnNzaC1rZXktdjEA"))
        assertTrue(output.contains(SecretRedactor.MASK))
    }

    @Test
    fun `throwable message and frames are redacted`() {
        SecretRedactor.registerSecret("topsecretvalue")
        val throwable = IllegalStateException("auth failed for topsecretvalue")

        val output = SecretRedactor.redactThrowable(throwable).orEmpty()

        assertFalse(output.contains("topsecretvalue"))
        assertTrue(output.contains("IllegalStateException"))
    }

    @Test
    fun `null and empty inputs are handled`() {
        assertEquals("", SecretRedactor.redact(null))
        assertEquals("", SecretRedactor.redact(""))
    }

    @Test
    fun `unregistering stops masking`() {
        SecretRedactor.registerSecret("temporary-token-value")
        SecretRedactor.unregisterSecret("temporary-token-value")

        assertTrue(SecretRedactor.redact("temporary-token-value").contains("temporary-token-value"))
    }
}
