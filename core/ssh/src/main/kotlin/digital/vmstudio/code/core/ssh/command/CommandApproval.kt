package digital.vmstudio.code.core.ssh.command

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

/** A command held pending a human decision. */
data class CommandApprovalRequest(
    val id: String = UUID.randomUUID().toString(),
    val command: String,
    val assessment: CommandAssessment,
    val serverId: String,
    val serverName: String,
    val environment: ServerEnvironment,
    /** True when the AI agent proposed this rather than the user typing it. */
    val requestedByAgent: Boolean,
) {
    val isProduction: Boolean get() = environment == ServerEnvironment.PRODUCTION
}

/**
 * Suspends a command until a person approves it.
 *
 * Modelled as a suspending call rather than a callback so the call site reads as a
 * straight line — assess, wait for the human, run — and so cancelling the caller
 * (leaving the screen, stopping the agent) cancels the pending approval with it
 * rather than leaving an orphaned dialog that can later approve a command nobody
 * is waiting for.
 *
 * One request is outstanding at a time. Queueing several would let a user approve a
 * dialog they think belongs to the command they just typed while it actually belongs
 * to something the agent queued behind it.
 */
interface CommandApprovalGate {

    /** The request currently awaiting a decision, if any. */
    val pending: StateFlow<CommandApprovalRequest?>

    /** Suspends until the user decides. Returns true when approved. */
    suspend fun request(request: CommandApprovalRequest): Boolean

    /** Called by the UI. Ignored if [id] is not the outstanding request. */
    fun resolve(id: String, approved: Boolean)

    /** Denies whatever is outstanding, e.g. when the screen is dismissed. */
    fun denyPending()
}

@Singleton
class DefaultCommandApprovalGate @Inject constructor() : CommandApprovalGate {

    private val _pending = MutableStateFlow<CommandApprovalRequest?>(null)
    override val pending: StateFlow<CommandApprovalRequest?> = _pending.asStateFlow()

    /** Serialises requests so only one dialog is ever outstanding. */
    private val mutex = Mutex()

    @Volatile
    private var outstanding: CompletableDeferred<Boolean>? = null

    override suspend fun request(request: CommandApprovalRequest): Boolean = mutex.withLock {
        val decision = CompletableDeferred<Boolean>()
        outstanding = decision
        _pending.value = request
        try {
            decision.await()
        } finally {
            // Runs on cancellation too, so abandoning the caller always clears the
            // prompt rather than stranding it on screen.
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
