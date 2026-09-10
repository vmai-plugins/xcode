package digital.vmstudio.code.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * How a server authenticates. The credential itself is never stored here; the
 * entity holds only opaque references into the Keystore-backed credential store.
 */
enum class SshAuthMethod {
    PASSWORD,
    PRIVATE_KEY,
    /** Key with a passphrase; both references are populated. */
    PRIVATE_KEY_WITH_PASSPHRASE,
    /** Delegated to an ssh-agent style provider; reserved for a later release. */
    AGENT,
}

/**
 * Deployment sensitivity. Drives the extra confirmation the safety layer requires
 * before destructive commands run, and the colour of the context bar.
 */
enum class ServerEnvironment {
    PRODUCTION,
    STAGING,
    DEVELOPMENT,
    LOCAL,
    UNSPECIFIED,
}

@Entity(
    tableName = "server_group",
    indices = [Index(value = ["name"], unique = true)],
)
data class ServerGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "server",
    foreignKeys = [
        ForeignKey(
            entity = ServerGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["host", "port", "username"]),
        Index(value = ["name"], unique = true),
    ],
)
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authMethod: SshAuthMethod,
    /** Reference into SecureCredentialStore for a password. Never a password. */
    val passwordCredentialId: String? = null,
    val privateKeyCredentialId: String? = null,
    val passphraseCredentialId: String? = null,
    val groupId: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    val connectTimeoutMillis: Int = 15_000,
    val keepAliveIntervalSeconds: Int = 30,
    /** Preferred login shell; null means detect on connect. */
    val preferredShell: String? = null,
    val defaultDirectory: String? = null,
    val notes: String = "",
    /** Hex colour used in the UI to distinguish servers at a glance. */
    val colorHex: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastConnectedAtMillis: Long = 0L,
)

/**
 * Trust-on-first-use record for SSH host keys.
 *
 * Fingerprints are not secrets, so they live in Room rather than the credential
 * store. A row is written only after the user explicitly trusts the key.
 */
@Entity(
    tableName = "known_host_key",
    indices = [Index(value = ["host", "port", "keyType"], unique = true)],
)
data class KnownHostKeyEntity(
    @PrimaryKey val id: String,
    val host: String,
    val port: Int,
    val keyType: String,
    /** OpenSSH-style `SHA256:base64` fingerprint. */
    val fingerprintSha256: String,
    val publicKeyBase64: String,
    val firstTrustedAtMillis: Long,
    val lastSeenAtMillis: Long,
)
