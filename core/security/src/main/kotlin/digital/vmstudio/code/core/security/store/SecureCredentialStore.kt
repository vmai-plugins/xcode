package digital.vmstudio.code.core.security.store

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.security.model.CredentialRef
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import kotlinx.coroutines.flow.Flow

/**
 * The only place plaintext secrets are persisted.
 *
 * Room stores [CredentialRef]s; this store holds the ciphertext. Nothing else in
 * the app is permitted to write a secret to disk. See SECURITY.md.
 */
interface SecureCredentialStore {

    /** References only — reading this never decrypts anything. */
    val credentials: Flow<List<CredentialRef>>

    suspend fun put(
        type: SecretType,
        label: String,
        secret: Secret,
        expiresAtMillis: Long = 0L,
    ): VmResult<CredentialRef>

    /** Replaces the secret behind an existing reference, preserving its id. */
    suspend fun replace(
        id: String,
        secret: Secret,
        expiresAtMillis: Long = 0L,
    ): VmResult<CredentialRef>

    /**
     * Decrypts a secret. Callers should [Secret.wipe] the result when finished
     * rather than holding it for the lifetime of a screen.
     */
    suspend fun read(id: String): VmResult<Secret>

    suspend fun reference(id: String): VmResult<CredentialRef>

    suspend fun delete(id: String): VmResult<Unit>

    suspend fun list(): VmResult<List<CredentialRef>>

    /** Wipes every stored secret and the master key. Irreversible. */
    suspend fun eraseAll(): VmResult<Unit>
}
