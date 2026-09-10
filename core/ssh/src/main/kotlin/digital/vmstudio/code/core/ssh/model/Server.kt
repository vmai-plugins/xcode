package digital.vmstudio.code.core.ssh.model

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod

/**
 * A configured SSH host.
 *
 * Carries credential *references* only. Resolving a reference to an actual secret
 * is a deliberate, separate step performed by the connection layer immediately
 * before use, so a `Server` can be held in UI state, logged and copied freely.
 */
data class Server(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val authMethod: SshAuthMethod,
    val passwordCredentialId: String? = null,
    val privateKeyCredentialId: String? = null,
    val passphraseCredentialId: String? = null,
    val groupId: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    val connectTimeoutMillis: Int = 15_000,
    val keepAliveIntervalSeconds: Int = 30,
    val preferredShell: String? = null,
    val defaultDirectory: String? = null,
    val notes: String = "",
    val colorHex: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
    val lastConnectedAtMillis: Long = 0L,
) {
    /** `user@host:port`, with the port omitted when it is the default. */
    val displayTarget: String
        get() = if (port == DEFAULT_SSH_PORT) "$username@$host" else "$username@$host:$port"

    val isProduction: Boolean get() = environment == ServerEnvironment.PRODUCTION

    /** Credential ids this server owns, for cleanup when it is deleted. */
    val credentialIds: List<String>
        get() = listOfNotNull(passwordCredentialId, privateKeyCredentialId, passphraseCredentialId)

    companion object {
        const val DEFAULT_SSH_PORT = 22
    }
}

/** A server group, used purely for organising the server list. */
data class ServerGroup(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

/**
 * User-entered server details before they have been validated and split into a
 * [Server] plus secrets to store.
 *
 * Secrets are held as plain strings here only for the lifetime of the form; they
 * are moved into the credential store and cleared as soon as the draft is saved.
 */
data class ServerDraft(
    val id: String? = null,
    val name: String = "",
    val host: String = "",
    val port: String = Server.DEFAULT_SSH_PORT.toString(),
    val username: String = "",
    val authMethod: SshAuthMethod = SshAuthMethod.PASSWORD,
    val password: String = "",
    val privateKeyPem: String = "",
    val passphrase: String = "",
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    val groupId: String? = null,
    val preferredShell: String = "",
    val defaultDirectory: String = "",
    val notes: String = "",
    val connectTimeoutSeconds: String = "15",
    val keepAliveSeconds: String = "30",
    /** True when editing a saved server whose secret is unchanged. */
    val retainExistingSecret: Boolean = false,
)

/** Field-level validation outcome for a [ServerDraft]. */
data class ServerValidation(
    val errors: Map<ServerField, VmError.Validation> = emptyMap(),
) {
    val isValid: Boolean get() = errors.isEmpty()

    operator fun get(field: ServerField): VmError.Validation? = errors[field]
}

enum class ServerField {
    NAME,
    HOST,
    PORT,
    USERNAME,
    PASSWORD,
    PRIVATE_KEY,
    PASSPHRASE,
    CONNECT_TIMEOUT,
    KEEP_ALIVE,
}
