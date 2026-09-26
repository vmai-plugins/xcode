package digital.vmstudio.code.core.ai.omniroute.agent

import digital.vmstudio.code.core.database.entity.ServerEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A proposed file write held pending a human decision. */
data class FileEditApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    /** Null when the file does not exist yet - this write creates it. */
    val oldContent: String?,
    val newContent: String,
    val serverId: String,
    val serverName: String,
    val environment: ServerEnvironment,
) {
    val isProduction: Boolean get() = environment == ServerEnvironment.PRODUCTION
    val isNewFile: Boolean get() = oldContent == null
}

/**
 * Suspends a file write until a person approves it.
 *
 * Structurally identical to [digital.vmstudio.code.core.ssh.command.CommandApprovalGate]
 * (one outstanding request, a suspending call, cancellation clears the prompt) but
 * kept as a separate type rather than generalizing that one: a shell command and a
 * file write are different enough in shape - a real diff instead of a command
 * string and a risk assessment - that forcing them through one type would mean
 * either faking a command string or widening a hardened, security-critical type
 * for a brand-new feature.
 */
interface FileEditApprovalGate {

    val pending: StateFlow<FileEditApprovalRequest?>

    /** Suspends until the user decides. Returns true when approved. */
    suspend fun request(request: FileEditApprovalRequest): Boolean

    /** Called by the UI. Ignored if [id] is not the outstanding request. */
    fun resolve(id: String, approved: Boolean)

    /** Denies whatever is outstanding, e.g. when the screen is dismissed. */
    fun denyPending()
}

@Singleton
class DefaultFileEditApprovalGate @Inject constructor() : FileEditApprovalGate {

    private val _pending = MutableStateFlow<FileEditApprovalRequest?>(null)
    override val pending: StateFlow<FileEditApprovalRequest?> = _pending.asStateFlow()

    private val mutex = Mutex()

    @Volatile
    private var outstanding: CompletableDeferred<Boolean>? = null

    override suspend fun request(request: FileEditApprovalRequest): Boolean = mutex.withLock {
        val decision = CompletableDeferred<Boolean>()
        outstanding = decision
        _pending.value = request
        try {
            decision.await()
        } finally {
            _pending.value = null
            outstanding = null
        }
    }

    override fun resolve(id: String, approved: Boolean) {
        if (_pending.value?.id != id) return
        outstanding?.complete(approved)
    }

    override fun denyPending() {
        outstanding?.complete(false)
    }
}
