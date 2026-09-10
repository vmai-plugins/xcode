package digital.vmstudio.code.core.security.model

import kotlinx.serialization.Serializable

/**
 * What a stored secret is for. Kept explicit so the credential store can apply
 * type-specific policy (for example: private keys are never exportable, OAuth
 * tokens carry an expiry).
 */
enum class SecretType {
    SSH_PASSWORD,
    SSH_PRIVATE_KEY,
    SSH_KEY_PASSPHRASE,
    AI_API_KEY,
    OAUTH_ACCESS_TOKEN,
    OAUTH_REFRESH_TOKEN,
    GIT_HTTP_TOKEN,
    GENERIC,
}

/**
 * A handle to a secret. This — never the secret itself — is what gets stored in
 * Room, passed between modules, and written to backups.
 *
 * The [id] is an opaque random identifier; it carries no information about the
 * secret and is safe to log.
 */
@Serializable
data class CredentialRef(
    val id: String,
    val type: SecretType,
    /** Human-facing label, e.g. "Production VPS password". Never the secret. */
    val label: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    /** For OAuth tokens; 0 when not applicable. */
    val expiresAtMillis: Long = 0L,
) {
    val isExpired: Boolean
        get() = expiresAtMillis > 0L && System.currentTimeMillis() >= expiresAtMillis

    companion object {
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}
