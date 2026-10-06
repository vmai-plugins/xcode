package digital.vmstudio.code.core.security.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.security.crypto.KeystoreCrypto
import digital.vmstudio.code.core.security.model.CredentialRef
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores each secret as its own Keystore-encrypted file, with a separate encrypted
 * index holding the (non-secret) references.
 *
 * One file per secret rather than a single blob so that a corrupted or
 * undecryptable entry costs the user exactly one credential instead of all of
 * them, and so writes never have to rewrite unrelated secrets.
 *
 * All files live in `filesDir`, which is app-private and excluded from cloud backup
 * by the backup rules shipped with the app.
 */
@Singleton
class FileSecureCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SecureCredentialStore {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val rootDir: File by lazy {
        File(context.filesDir, DIR_NAME).apply { mkdirs() }
    }
    private val indexFile: File by lazy { File(rootDir, INDEX_FILE) }

    private val _credentials = MutableStateFlow<List<CredentialRef>>(emptyList())
    /**
     * Loads the index on first collection: only writes loaded it before, so after a
     * cold start Settings showed "0 stored" and disabled Erase all credentials.
     */
    override val credentials: Flow<List<CredentialRef>> = flow {
        if (!indexLoaded) withContext(ioDispatcher) { mutex.withLock { loadIndexLocked() } }
        emitAll(_credentials.asStateFlow())
    }

    @Volatile
    private var indexLoaded = false

    override suspend fun put(
        type: SecretType,
        label: String,
        secret: Secret,
        expiresAtMillis: Long,
    ): VmResult<CredentialRef> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val ref = CredentialRef(
            id = CredentialRef.newId(),
            type = type,
            label = label,
            createdAtMillis = now,
            updatedAtMillis = now,
            expiresAtMillis = expiresAtMillis,
        )
        mutex.withLock { writeSecretLocked(ref, secret) }
    }

    override suspend fun replace(
        id: String,
        secret: Secret,
        expiresAtMillis: Long,
    ): VmResult<CredentialRef> = withContext(ioDispatcher) {
        mutex.withLock {
            val index = loadIndexLocked().let {
                when (it) {
                    is VmResult.Failure -> return@withLock it
                    is VmResult.Success -> it.value
                }
            }
            val existing = index[id]
                ?: return@withLock VmResult.Failure(missingCredential(id))
            val updated = existing.copy(
                updatedAtMillis = System.currentTimeMillis(),
                expiresAtMillis = expiresAtMillis,
            )
            writeSecretLocked(updated, secret)
        }
    }

    override suspend fun read(id: String): VmResult<Secret> = withContext(ioDispatcher) {
        mutex.withLock {
            val file = secretFileOrNull(id)
                ?: return@withLock VmResult.Failure(rejectedId())
            if (!file.exists()) return@withLock VmResult.Failure(missingCredential(id))
            vmCatching(::mapIoError) { file.readBytes() }
                .flatMap { crypto.decrypt(it) }
                .map { Secret.wrapping(it) }
        }
    }

    override suspend fun reference(id: String): VmResult<CredentialRef> =
        withContext(ioDispatcher) {
            mutex.withLock {
                loadIndexLocked().flatMap { index ->
                    index[id]?.let { VmResult.Success(it) }
                        ?: VmResult.Failure(missingCredential(id))
                }
            }
        }

    override suspend fun list(): VmResult<List<CredentialRef>> = withContext(ioDispatcher) {
        mutex.withLock {
            loadIndexLocked().map { index -> index.values.sortedBy { it.label } }
        }
    }

    override suspend fun delete(id: String): VmResult<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            loadIndexLocked().flatMap { index ->
                val file = secretFileOrNull(id)
                    ?: return@flatMap VmResult.Failure(rejectedId())
                if (file.exists() && !file.delete()) {
                    return@flatMap VmResult.Failure(
                        VmError.Storage(
                            summary = "Could not delete stored credential",
                            reason = "The encrypted credential file could not be removed.",
                            suggestedAction = "Retry; if it persists, clear app storage.",
                        ),
                    )
                }
                val updated = index - id
                persistIndexLocked(updated).map { }
            }
        }
    }

    override suspend fun eraseAll(): VmResult<Unit> = withContext(ioDispatcher) {
        mutex.withLock {
            vmCatching(::mapIoError) {
                val files = rootDir.listFiles()
                if (files == null) {
                    VmLog.w(
                        LogCategory.SECURITY,
                        TAG,
                        "Could not enumerate the credential directory; destroying the " +
                            "master key still renders any residual files unreadable",
                    )
                } else {
                    val undeleted = files.filterNot { it.delete() }
                    if (undeleted.isNotEmpty()) {
                        VmLog.w(
                            LogCategory.SECURITY,
                            TAG,
                            "${undeleted.size} credential file(s) could not be deleted; " +
                                "the master key is being destroyed regardless",
                        )
                    }
                }
            }.flatMap {
                crypto.destroyMasterKey()
            }.map {
                indexLoaded = true
                _credentials.value = emptyList()
                VmLog.w(LogCategory.SECURITY, TAG, "All stored credentials erased")
            }
        }
    }

    // --- internals -------------------------------------------------------------

    private fun writeSecretLocked(ref: CredentialRef, secret: Secret): VmResult<CredentialRef> {
        val plaintext = secret.copyBytes()
        val encrypted = try {
            crypto.encrypt(plaintext)
        } finally {
            java.util.Arrays.fill(plaintext, 0)
        }
        return encrypted.flatMap { bytes ->
            val file = secretFileOrNull(ref.id)
                ?: return@flatMap VmResult.Failure(rejectedId())
            vmCatching(::mapIoError) { writeAtomically(file, bytes) }
        }.flatMap {
            loadIndexLocked()
        }.flatMap { index ->
            persistIndexLocked(index + (ref.id to ref))
        }.map { ref }
    }

    /**
     * Writes via a temp file and rename so a process death mid-write cannot leave a
     * half-written credential that would fail its GCM tag check on next read.
     */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.outputStream().use { it.write(bytes); it.fd.sync() }
        try {
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (ignored: AtomicMoveNotSupportedException) {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (throwable: Throwable) {
            temp.delete()
            throw IOException("Atomic rename failed for ${target.name}", throwable)
        }
    }

    private fun loadIndexLocked(): VmResult<Map<String, CredentialRef>> {
        if (indexLoaded) {
            return VmResult.Success(_credentials.value.associateBy { it.id })
        }
        if (!indexFile.exists()) {
            indexLoaded = true
            _credentials.value = emptyList()
            return VmResult.Success(emptyMap())
        }
        return vmCatching(::mapIoError) { indexFile.readBytes() }
            .flatMap { crypto.decrypt(it) }
            .flatMap { decrypted ->
                vmCatching(
                    { throwable ->
                        VmError.Storage(
                            summary = "Credential index is unreadable",
                            reason = "The stored index could not be parsed.",
                            suggestedAction = "Re-enter your credentials to rebuild it.",
                            cause = throwable,
                        )
                    },
                ) {
                    json.decodeFromString<List<CredentialRef>>(String(decrypted, Charsets.UTF_8))
                }
            }
            .map { refs ->
                indexLoaded = true
                _credentials.value = refs
                refs.associateBy { it.id }
            }
    }

    private fun persistIndexLocked(
        index: Map<String, CredentialRef>,
    ): VmResult<Map<String, CredentialRef>> {
        val payload = json.encodeToString(index.values.toList()).toByteArray(Charsets.UTF_8)
        return crypto.encrypt(payload)
            .flatMap { vmCatching(::mapIoError) { writeAtomically(indexFile, it) } }
            .map {
                indexLoaded = true
                _credentials.value = index.values.sortedBy { ref -> ref.label }
                index
            }
    }

    /**
     * Resolves the file backing [id], or null when the id is not well-formed.
     *
     * Ids are generated UUIDs, but this store must not be trivially turned into an
     * arbitrary-file-read primitive by a malformed id arriving from elsewhere.
     *
     * Returns null rather than throwing: every failure in this app is a
     * `VmResult.Failure`, and an id from a corrupted preference used to escape
     * `read` as a raw `IllegalArgumentException` — a crash rather than an error.
     */
    private fun secretFileOrNull(id: String): File? =
        if (id.matches(SAFE_ID)) File(rootDir, "$id$SECRET_SUFFIX") else null

    /** The offending id is deliberately not echoed back into the message. */
    private fun rejectedId() = VmError.Validation(
        summary = "Credential reference is not valid",
        reason = "The stored reference is not a well-formed credential id.",
        suggestedAction = "Re-enter the credential in Settings.",
        fieldName = "credentialId",
    )

    private fun missingCredential(id: String) = VmError.NotFound(
        summary = "Credential not found",
        reason = "No stored credential with reference $id.",
        suggestedAction = "Re-enter the credential in Settings.",
    )

    private fun mapIoError(throwable: Throwable) = VmError.Storage(
        summary = "Could not access secure storage",
        reason = throwable.message,
        suggestedAction = "Check available device storage and retry.",
        cause = throwable,
    )

    private companion object {
        const val TAG = "FileSecureCredentialStore"
        const val DIR_NAME = "credentials"
        const val INDEX_FILE = "index.bin"
        const val SECRET_SUFFIX = ".bin"
        val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
