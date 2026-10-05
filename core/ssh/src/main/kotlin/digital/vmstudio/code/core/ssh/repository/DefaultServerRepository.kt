package digital.vmstudio.code.core.ssh.repository

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.ServerDao
import digital.vmstudio.code.core.database.entity.ServerEntity
import digital.vmstudio.code.core.database.entity.ServerGroupEntity
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.model.ServerGroup
import digital.vmstudio.code.core.ssh.model.ServerValidator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultServerRepository @Inject constructor(
    private val serverDao: ServerDao,
    private val credentialStore: SecureCredentialStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ServerRepository {

    /**
     * Serializes save() and delete() for the same server against each other.
     *
     * save() reads the existing row, stores credentials, then upserts - a
     * read-modify-write with no transaction around it. A delete() for the same id
     * landing between the read and the upsert would free that row's credential ids
     * from the secure store while save()'s in-flight write still references them,
     * resurrecting a server that points at credentials which are gone or about to
     * be. A single mutex is simpler than a per-id lock table and cheap enough here:
     * server CRUD is a rare, user-initiated action, never a hot path.
     */
    private val writeMutex = Mutex()

    override val servers: Flow<List<Server>> =
        serverDao.observeAll().map { list -> list.map { it.toDomain() } }

    override val groups: Flow<List<ServerGroup>> =
        serverDao.observeGroups().map { list -> list.map { it.toDomain() } }

    override fun observe(serverId: String): Flow<Server?> =
        serverDao.observeById(serverId).map { it?.toDomain() }

    override suspend fun get(serverId: String): VmResult<Server> = withContext(ioDispatcher) {
        serverDao.getById(serverId)?.toDomain()?.let { VmResult.Success(it) }
            ?: VmResult.Failure(
                VmError.NotFound(
                    summary = "Server not found",
                    reason = "This server may have been deleted on another screen.",
                    suggestedAction = "Go back to the server list.",
                ),
            )
    }

    override suspend fun save(draft: ServerDraft): VmResult<Server> = withContext(ioDispatcher) {
        writeMutex.withLock { saveLocked(draft) }
    }

    private suspend fun saveLocked(draft: ServerDraft): VmResult<Server> {
        val validation = ServerValidator.validate(draft)
        if (!validation.isValid) {
            // Report the first field error; the form highlights all of them from
            // its own validation pass.
            return VmResult.Failure(validation.errors.values.first())
        }

        val existing = draft.id?.let { serverDao.getById(it) }
        val now = System.currentTimeMillis()

        // Store secrets first. If this fails, nothing has been written to Room and
        // there is no half-configured server left behind.
        val credentials = when (val stored = storeSecrets(draft, existing)) {
            is VmResult.Failure -> return stored
            is VmResult.Success -> stored.value
        }

        val entity = ServerEntity(
            id = draft.id ?: UUID.randomUUID().toString(),
            name = draft.name.trim(),
            host = ServerValidator.normalizeHost(draft.host),
            port = draft.port.trim().toInt(),
            username = draft.username.trim(),
            authMethod = draft.authMethod,
            passwordCredentialId = credentials.passwordId,
            privateKeyCredentialId = credentials.privateKeyId,
            passphraseCredentialId = credentials.passphraseId,
            groupId = draft.groupId,
            environment = draft.environment,
            connectTimeoutMillis = draft.connectTimeoutSeconds.trim().toInt() * 1_000,
            keepAliveIntervalSeconds = draft.keepAliveSeconds.trim().toInt(),
            preferredShell = draft.preferredShell.trim().ifBlank { null },
            defaultDirectory = draft.defaultDirectory.trim().ifBlank { null },
            notes = draft.notes,
            colorHex = existing?.colorHex,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
            lastConnectedAtMillis = existing?.lastConnectedAtMillis ?: 0L,
        )

        val result = vmCatching(::mapDatabaseError) { serverDao.upsert(entity) }
        return when (result) {
            is VmResult.Failure -> {
                // Roll back any credential written in this call so the store does
                // not accumulate secrets no server references.
                credentials.newlyCreatedIds.forEach { credentialStore.delete(it) }
                result
            }
            is VmResult.Success -> {
                // Credentials the server no longer uses (auth method changed, key
                // replaced) are removed now that the new row is committed.
                credentials.supersededIds.forEach { credentialStore.delete(it) }
                VmLog.i(
                    LogCategory.SSH,
                    TAG,
                    "Saved server ${entity.name} (${entity.host}:${entity.port}) auth=${entity.authMethod}",
                )
                VmResult.Success(entity.toDomain())
            }
        }
    }

    override suspend fun delete(serverId: String): VmResult<Unit> = withContext(ioDispatcher) {
        writeMutex.withLock {
            val credentialIds = serverDao.credentialIdsFor(serverId)
            val deleted = vmCatching(::mapDatabaseError) { serverDao.deleteById(serverId) }
            if (deleted is VmResult.Success) {
                credentialIds.forEach { credentialStore.delete(it) }
                VmLog.i(
                    LogCategory.SSH,
                    TAG,
                    "Deleted server $serverId and ${credentialIds.size} credentials",
                )
            }
            deleted
        }
    }

    override suspend fun markConnected(serverId: String): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapDatabaseError) {
                serverDao.markConnected(serverId, System.currentTimeMillis())
            }
        }

    override suspend fun saveGroup(group: ServerGroup): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapDatabaseError) {
            serverDao.upsertGroup(
                ServerGroupEntity(id = group.id, name = group.name, sortOrder = group.sortOrder),
            )
        }
    }

    override suspend fun deleteGroup(groupId: String): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapDatabaseError) { serverDao.deleteGroup(groupId) }
    }

    override suspend fun draftFor(serverId: String): VmResult<ServerDraft> =
        get(serverId).map { server ->
            ServerDraft(
                id = server.id,
                name = server.name,
                host = server.host,
                port = server.port.toString(),
                username = server.username,
                authMethod = server.authMethod,
                environment = server.environment,
                groupId = server.groupId,
                preferredShell = server.preferredShell.orEmpty(),
                defaultDirectory = server.defaultDirectory.orEmpty(),
                notes = server.notes,
                connectTimeoutSeconds = (server.connectTimeoutMillis / 1_000).toString(),
                keepAliveSeconds = server.keepAliveIntervalSeconds.toString(),
                // Secrets are never read back into the form; the user either keeps
                // the stored one or replaces it outright.
                retainExistingSecret = server.credentialIds.isNotEmpty(),
            )
        }

    // --- secret handling -------------------------------------------------------

    private data class StoredCredentials(
        val passwordId: String? = null,
        val privateKeyId: String? = null,
        val passphraseId: String? = null,
        val newlyCreatedIds: List<String> = emptyList(),
        val supersededIds: List<String> = emptyList(),
    )

    private suspend fun storeSecrets(
        draft: ServerDraft,
        existing: ServerEntity?,
    ): VmResult<StoredCredentials> {
        val created = mutableListOf<String>()
        val superseded = mutableListOf<String>()

        suspend fun store(
            type: SecretType,
            label: String,
            value: String,
            previousId: String?,
        ): VmResult<String?> {
            if (value.isEmpty()) {
                // Keep the stored secret when the user did not retype it.
                return if (draft.retainExistingSecret) {
                    VmResult.Success(previousId)
                } else {
                    previousId?.let { superseded.add(it) }
                    VmResult.Success(null)
                }
            }
            val secret = Secret.of(value)
            return try {
                credentialStore.put(type, label, secret).map { ref ->
                    created.add(ref.id)
                    previousId?.let { superseded.add(it) }
                    ref.id
                }
            } finally {
                secret.wipe()
            }
        }

        val label = draft.name.trim().ifBlank { draft.host.trim() }
        val keepPassword = draft.authMethod == SshAuthMethod.PASSWORD
        val keepKey = draft.authMethod == SshAuthMethod.PRIVATE_KEY ||
            draft.authMethod == SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE
        val keepPassphrase = draft.authMethod == SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE

        // Credentials belonging to an auth method that is no longer selected are
        // deleted rather than left dormant in the store.
        if (!keepPassword) existing?.passwordCredentialId?.let(superseded::add)
        if (!keepKey) existing?.privateKeyCredentialId?.let(superseded::add)
        if (!keepPassphrase) existing?.passphraseCredentialId?.let(superseded::add)

        val passwordId = if (keepPassword) {
            when (
                val r = store(
                    SecretType.SSH_PASSWORD,
                    "$label password",
                    draft.password,
                    existing?.passwordCredentialId,
                )
            ) {
                is VmResult.Failure -> return rollback(created, r)
                is VmResult.Success -> r.value
            }
        } else {
            null
        }

        val privateKeyId = if (keepKey) {
            when (
                val r = store(
                    SecretType.SSH_PRIVATE_KEY,
                    "$label key",
                    draft.privateKeyPem,
                    existing?.privateKeyCredentialId,
                )
            ) {
                is VmResult.Failure -> return rollback(created, r)
                is VmResult.Success -> r.value
            }
        } else {
            null
        }

        val passphraseId = if (keepPassphrase) {
            when (
                val r = store(
                    SecretType.SSH_KEY_PASSPHRASE,
                    "$label passphrase",
                    draft.passphrase,
                    existing?.passphraseCredentialId,
                )
            ) {
                is VmResult.Failure -> return rollback(created, r)
                is VmResult.Success -> r.value
            }
        } else {
            null
        }

        return VmResult.Success(
            StoredCredentials(
                passwordId = passwordId,
                privateKeyId = privateKeyId,
                passphraseId = passphraseId,
                newlyCreatedIds = created,
                supersededIds = superseded.distinct(),
            ),
        )
    }

    private suspend fun rollback(
        created: List<String>,
        failure: VmResult.Failure,
    ): VmResult<StoredCredentials> {
        created.forEach { credentialStore.delete(it) }
        return failure
    }

    private fun mapDatabaseError(throwable: Throwable) = VmError.Storage(
        summary = "Could not save the server",
        reason = throwable.message,
        suggestedAction = "Check that the server name is unique and try again.",
        cause = throwable,
    )

    private companion object {
        const val TAG = "ServerRepository"
    }
}

private fun ServerEntity.toDomain() = Server(
    id = id,
    name = name,
    host = host,
    port = port,
    username = username,
    authMethod = authMethod,
    passwordCredentialId = passwordCredentialId,
    privateKeyCredentialId = privateKeyCredentialId,
    passphraseCredentialId = passphraseCredentialId,
    groupId = groupId,
    environment = environment,
    connectTimeoutMillis = connectTimeoutMillis,
    keepAliveIntervalSeconds = keepAliveIntervalSeconds,
    preferredShell = preferredShell,
    defaultDirectory = defaultDirectory,
    notes = notes,
    colorHex = colorHex,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    lastConnectedAtMillis = lastConnectedAtMillis,
)

private fun ServerGroupEntity.toDomain() = ServerGroup(id = id, name = name, sortOrder = sortOrder)
