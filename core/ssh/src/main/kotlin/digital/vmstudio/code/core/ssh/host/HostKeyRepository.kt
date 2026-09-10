package digital.vmstudio.code.core.ssh.host

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.KnownHostKeyDao
import digital.vmstudio.code.core.database.entity.KnownHostKeyEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's `known_hosts`.
 *
 * Trust-on-first-use, with every transition through it made explicit: a key is
 * stored only when [trust] is called, and [trust] is only ever called from a user
 * action.
 */
interface HostKeyRepository {

    val trustedKeys: Flow<List<TrustedHostKey>>

    suspend fun evaluate(candidate: HostKeyCandidate): HostKeyVerdict

    /**
     * Blocking variant for [TofuHostKeyVerifier], which runs on the sshj transport
     * thread and cannot suspend. Kept here so the DB read happens on Room's own
     * executor rather than hopping through the app's IO dispatcher — a hop that can
     * stall the transport thread when that pool is saturated by other SSH work.
     */
    fun evaluateBlocking(candidate: HostKeyCandidate): HostKeyVerdict

    /**
     * Key algorithms already trusted for a host. sshj prefers these during
     * negotiation so a multi-key server does not offer a type the user has never
     * seen and trigger a spurious mismatch.
     */
    suspend fun trustedKeyTypes(host: String, port: Int): List<String>

    /** Blocking variant of [trustedKeyTypes] for the transport-thread verifier. */
    fun trustedKeyTypesBlocking(host: String, port: Int): List<String>

    /** Records the user's decision to trust [candidate], replacing any prior key. */
    suspend fun trust(candidate: HostKeyCandidate): VmResult<TrustedHostKey>

    suspend fun forget(host: String, port: Int): VmResult<Unit>

    suspend fun forgetAll(): VmResult<Unit>
}

@Singleton
class DefaultHostKeyRepository @Inject constructor(
    private val dao: KnownHostKeyDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HostKeyRepository {

    override val trustedKeys: Flow<List<TrustedHostKey>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun evaluate(candidate: HostKeyCandidate): HostKeyVerdict =
        withContext(ioDispatcher) { evaluateInternal(candidate) }

    override fun evaluateBlocking(candidate: HostKeyCandidate): HostKeyVerdict =
        runBlocking { evaluateInternal(candidate) }

    private suspend fun evaluateInternal(candidate: HostKeyCandidate): HostKeyVerdict {
        val stored = dao.find(candidate.host, candidate.port, candidate.keyType)
        if (stored != null) {
            return if (stored.fingerprintSha256 == candidate.fingerprintSha256) {
                dao.touch(stored.id, System.currentTimeMillis())
                HostKeyVerdict.Trusted(stored.toDomain())
            } else {
                VmLog.w(
                    LogCategory.SECURITY,
                    TAG,
                    "Host key mismatch for ${candidate.host}:${candidate.port} " +
                        "(${candidate.keyType}): stored ${stored.fingerprintSha256}, " +
                        "presented ${candidate.fingerprintSha256}",
                )
                HostKeyVerdict.Mismatch(candidate, stored.toDomain())
            }
        }

        // No key of this *type* is stored. If the host already has trusted keys of
        // other types, a newly-appearing type is a change to warn about — not a
        // first contact. Pinning is per host:port, not per key algorithm, so an
        // attacker cannot sidestep the mismatch prompt by presenting a key of a
        // type the user has never happened to see from this host.
        val otherTypes = dao.findForHost(candidate.host, candidate.port)
        if (otherTypes.isNotEmpty()) {
            VmLog.w(
                LogCategory.SECURITY,
                TAG,
                "Host ${candidate.host}:${candidate.port} presented an untrusted " +
                    "${candidate.keyType} key while ${otherTypes.size} key(s) of other " +
                    "types are already trusted",
            )
            return HostKeyVerdict.Mismatch(candidate, otherTypes.first().toDomain())
        }

        return HostKeyVerdict.Unknown(candidate)
    }

    override suspend fun trustedKeyTypes(host: String, port: Int): List<String> =
        withContext(ioDispatcher) { dao.findForHost(host, port).map { it.keyType } }

    override fun trustedKeyTypesBlocking(host: String, port: Int): List<String> =
        runBlocking { dao.findForHost(host, port).map { it.keyType } }

    override suspend fun trust(candidate: HostKeyCandidate): VmResult<TrustedHostKey> =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val existing = dao.find(candidate.host, candidate.port, candidate.keyType)
            val entity = KnownHostKeyEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                host = candidate.host,
                port = candidate.port,
                keyType = candidate.keyType,
                fingerprintSha256 = candidate.fingerprintSha256,
                publicKeyBase64 = candidate.publicKeyBase64,
                firstTrustedAtMillis = existing?.firstTrustedAtMillis ?: now,
                lastSeenAtMillis = now,
            )
            vmCatching(::mapError) {
                // Transactional so a crash cannot leave the host with no trusted key
                // while the previous one is already gone.
                dao.replaceTrusted(entity)
                VmLog.i(
                    LogCategory.SECURITY,
                    TAG,
                    "Trusted host key for ${entity.host}:${entity.port} (${entity.keyType})",
                )
                entity.toDomain()
            }
        }

    override suspend fun forget(host: String, port: Int): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapError) {
                dao.forget(host, port)
                VmLog.i(LogCategory.SECURITY, TAG, "Forgot host keys for $host:$port")
            }
        }

    override suspend fun forgetAll(): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) {
            dao.forgetAll()
            VmLog.w(LogCategory.SECURITY, TAG, "Forgot all trusted host keys")
        }
    }

    private fun mapError(throwable: Throwable) = VmError.Storage(
        summary = "Could not update trusted host keys",
        reason = throwable.message,
        suggestedAction = "Retry; if it persists, clear known hosts in Settings.",
        cause = throwable,
    )

    private companion object {
        const val TAG = "HostKeyRepository"
    }
}

private fun KnownHostKeyEntity.toDomain() = TrustedHostKey(
    id = id,
    host = host,
    port = port,
    keyType = keyType,
    fingerprintSha256 = fingerprintSha256,
    publicKeyBase64 = publicKeyBase64,
    firstTrustedAtMillis = firstTrustedAtMillis,
    lastSeenAtMillis = lastSeenAtMillis,
)
