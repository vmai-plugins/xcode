package digital.vmstudio.code.core.ssh.model

import digital.vmstudio.code.core.database.entity.SshAuthMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerValidatorTest {

    private fun validDraft() = ServerDraft(
        name = "Production VPS",
        host = "vps.example.com",
        port = "22",
        username = "deploy",
        authMethod = SshAuthMethod.PASSWORD,
        password = "correct-horse",
    )

    @Test
    fun `a complete draft validates`() {
        assertTrue(ServerValidator.validate(validDraft()).isValid)
    }

    @Test
    fun `blank name is rejected`() {
        val result = ServerValidator.validate(validDraft().copy(name = "  "))

        assertNotNull(result[ServerField.NAME])
        assertFalse(result.isValid)
    }

    @Test
    fun `host pasted as a url is rejected with a specific hint`() {
        val result = ServerValidator.validate(validDraft().copy(host = "ssh://vps.example.com"))

        val error = result[ServerField.HOST]
        assertNotNull(error)
        assertTrue(error!!.summary.contains("scheme", ignoreCase = true))
    }

    @Test
    fun `host pasted with a username is rejected`() {
        val result = ServerValidator.validate(validDraft().copy(host = "deploy@vps.example.com"))

        assertNotNull(result[ServerField.HOST])
    }

    @Test
    fun `ipv4 literal is accepted`() {
        assertTrue(ServerValidator.validate(validDraft().copy(host = "203.0.113.10")).isValid)
    }

    @Test
    fun `out of range ipv4 is rejected`() {
        assertNotNull(ServerValidator.validate(validDraft().copy(host = "203.0.113.999"))[ServerField.HOST])
    }

    @Test
    fun `ipv6 literal is accepted`() {
        assertTrue(ServerValidator.validate(validDraft().copy(host = "2001:db8::1")).isValid)
    }

    @Test
    fun `port must be numeric and in range`() {
        assertNotNull(ServerValidator.validate(validDraft().copy(port = "twenty-two"))[ServerField.PORT])
        assertNotNull(ServerValidator.validate(validDraft().copy(port = "0"))[ServerField.PORT])
        assertNotNull(ServerValidator.validate(validDraft().copy(port = "65536"))[ServerField.PORT])
        assertNull(ServerValidator.validate(validDraft().copy(port = "2222"))[ServerField.PORT])
    }

    @Test
    fun `username with an at sign is rejected`() {
        assertNotNull(
            ServerValidator.validate(validDraft().copy(username = "deploy@host"))[ServerField.USERNAME],
        )
    }

    @Test
    fun `password auth without a password is rejected`() {
        val result = ServerValidator.validate(validDraft().copy(password = ""))

        assertNotNull(result[ServerField.PASSWORD])
    }

    @Test
    fun `key auth requires a pem body`() {
        val draft = validDraft().copy(
            authMethod = SshAuthMethod.PRIVATE_KEY,
            password = "",
            privateKeyPem = "ssh-rsa AAAAB3NzaC1yc2E deploy@laptop",
        )

        val error = ServerValidator.validate(draft)[ServerField.PRIVATE_KEY]

        assertNotNull("a public key must not be accepted as a private key", error)
        assertTrue(error!!.suggestedAction.orEmpty().contains(".pub"))
    }

    @Test
    fun `key auth accepts a pem private key`() {
        val draft = validDraft().copy(
            authMethod = SshAuthMethod.PRIVATE_KEY,
            password = "",
            privateKeyPem = "-----BEGIN OPENSSH PRIVATE KEY-----\nbody\n-----END OPENSSH PRIVATE KEY-----",
        )

        assertTrue(ServerValidator.validate(draft).isValid)
    }

    @Test
    fun `passphrase is required only when the method says so`() {
        val pem = "-----BEGIN OPENSSH PRIVATE KEY-----\nbody\n-----END OPENSSH PRIVATE KEY-----"

        val withoutPassphrase = validDraft().copy(
            authMethod = SshAuthMethod.PRIVATE_KEY,
            password = "",
            privateKeyPem = pem,
        )
        assertNull(ServerValidator.validate(withoutPassphrase)[ServerField.PASSPHRASE])

        val needsPassphrase = withoutPassphrase.copy(
            authMethod = SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE,
        )
        assertNotNull(ServerValidator.validate(needsPassphrase)[ServerField.PASSPHRASE])
    }

    @Test
    fun `editing an existing server does not demand the secret again`() {
        val draft = validDraft().copy(
            id = "existing",
            password = "",
            retainExistingSecret = true,
        )

        assertTrue(ServerValidator.validate(draft).isValid)
    }

    @Test
    fun `keepalive of zero is allowed as an explicit disable`() {
        assertTrue(ServerValidator.validate(validDraft().copy(keepAliveSeconds = "0")).isValid)
    }

    @Test
    fun `connect timeout must be positive`() {
        assertNotNull(
            ServerValidator.validate(validDraft().copy(connectTimeoutSeconds = "0"))[ServerField.CONNECT_TIMEOUT],
        )
    }

    @Test
    fun `every validation error names its field and explains itself`() {
        val result = ServerValidator.validate(
            ServerDraft(name = "", host = "", port = "x", username = "", password = ""),
        )

        assertFalse(result.isValid)
        result.errors.forEach { (field, error) ->
            assertTrue("$field must name its field", !error.fieldName.isNullOrBlank())
            assertTrue("$field must have a summary", error.summary.isNotBlank())
            assertEquals("validation errors are never retryable", false, error.retryable)
        }
    }
}
