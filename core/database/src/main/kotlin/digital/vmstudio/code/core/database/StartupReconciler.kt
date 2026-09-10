package digital.vmstudio.code.core.database

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.MessageDao
import digital.vmstudio.code.core.database.dao.ToolExecutionDao
import digital.vmstudio.code.core.database.dao.TransferDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repairs state that cannot survive process death.
 *
 * An SSH channel, a streaming AI response and a running agent loop all end when the
 * process does, but the rows describing them do not. Without this pass the user
 * would come back to a message stuck mid-stream and a task that claims to still be
 * running, with no way to tell that nothing is happening.
 *
 * Transfers are the exception: they carry a resume offset, so they are re-queued
 * rather than failed.
 */
@Singleton
class StartupReconciler @Inject constructor(
    private val messageDao: MessageDao,
    private val toolExecutionDao: ToolExecutionDao,
    private val agentTaskDao: AgentTaskDao,
    private val transferDao: TransferDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun reconcile(): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(
            { throwable ->
                VmError.Storage(
                    summary = "Could not restore previous session state",
                    reason = throwable.message,
                    suggestedAction = "Restart the app; in-progress work from last session " +
                        "may still appear as running.",
                    cause = throwable,
                )
            },
        ) {
            messageDao.failInterrupted()
            toolExecutionDao.cancelInterrupted()
            agentTaskDao.reconcileInterrupted(System.currentTimeMillis())
            transferDao.requeueInterrupted()
            VmLog.i(LogCategory.DATABASE, TAG, "Reconciled interrupted work from previous session")
        }
    }

    private companion object {
        const val TAG = "StartupReconciler"
    }
}
