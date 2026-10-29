package digital.vmstudio.code.core.ai.execution

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.vmstudio.code.core.ai.background.AgentWakeLockManager
import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages active AI agent executions across conversations in a process-wide singleton scope.
 *
 * This decouples agent execution from transient screen ViewModels so that switching
 * chats, navigating tabs, or backgrounding the app does not abort or interrupt
 * in-flight runs.
 */
@Singleton
class AgentExecutionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversations: AgentConversationRepository,
    private val tasks: AgentTaskRepository,
    private val wakeLockManager: AgentWakeLockManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeRuns = ConcurrentHashMap<String, ActiveRun>()
    private val _runningConversations = MutableStateFlow<Set<String>>(emptySet())
    val runningConversations: StateFlow<Set<String>> = _runningConversations.asStateFlow()

    data class ActiveRun(
        val conversationId: String,
        val taskId: String?,
        val job: Job,
        val events: MutableSharedFlow<AgentEvent>,
        val lastEvent: AtomicReference<AgentEvent?>,
    )

    fun isRunning(conversationId: String): Boolean = activeRuns.containsKey(conversationId)

    fun observeEvents(conversationId: String): SharedFlow<AgentEvent>? =
        activeRuns[conversationId]?.events?.asSharedFlow()

    fun submitRun(
        conversationId: String,
        taskId: String?,
        prompt: String,
        runner: suspend (emitEvent: suspend (AgentEvent) -> Unit) -> Unit,
    ): SharedFlow<AgentEvent> {
        activeRuns[conversationId]?.job?.cancel()

        val eventFlow = MutableSharedFlow<AgentEvent>(
            replay = 50,
            extraBufferCapacity = 500,
            onBufferOverflow = BufferOverflow.SUSPEND,
        )
        val lastEvent = AtomicReference<AgentEvent?>(null)

        wakeLockManager.acquire()
        notifyServiceStart()

        val job = scope.launch {
            try {
                runner { event ->
                    lastEvent.set(event)
                    eventFlow.emit(event)
                    conversations.record(conversationId, event)
                    taskId?.let { applyTaskEvent(it, event) }
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    taskId?.let { tasks.cancel(it) }
                }
                throw cancelled
            } catch (failure: Throwable) {
                VmLog.e(LogCategory.AI, TAG, "Execution failed in conversation $conversationId: ${failure.message}", failure)
                taskId?.let { tasks.fail(it, failure.message ?: "The run failed") }
                val errorEvent = AgentEvent.Failed(
                    VmError.Ai(
                        summary = "The run failed",
                        reason = failure.message,
                        cause = failure,
                    ),
                )
                lastEvent.set(errorEvent)
                eventFlow.emit(errorEvent)
            } finally {
                activeRuns.remove(conversationId)
                updateState()
                wakeLockManager.release()
                if (activeRuns.isEmpty()) {
                    notifyServiceStop()
                }
            }
        }

        activeRuns[conversationId] = ActiveRun(conversationId, taskId, job, eventFlow, lastEvent)
        updateState()
        return eventFlow.asSharedFlow()
    }

    fun stopRun(conversationId: String) {
        val run = activeRuns.remove(conversationId)
        run?.job?.cancel()
        updateState()
        if (activeRuns.isEmpty()) {
            notifyServiceStop()
        }
    }

    fun stopAll() {
        activeRuns.values.forEach { it.job.cancel() }
        activeRuns.clear()
        updateState()
        notifyServiceStop()
    }

    private fun updateState() {
        _runningConversations.value = activeRuns.keys.toSet()
    }

    private suspend fun applyTaskEvent(taskId: String, event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolStarted -> {
                tasks.recordToolCall(taskId)
                event.affectedPath?.let { tasks.recordChangedFile(taskId, it) }
            }
            is AgentEvent.Completed -> {
                if (event.isError) {
                    tasks.fail(taskId, event.resultText ?: "The run ended with an error")
                } else {
                    tasks.complete(taskId, event.inputTokens, event.outputTokens)
                }
            }
            is AgentEvent.Failed -> tasks.fail(taskId, event.error.summary)
            else -> Unit
        }
    }

    private fun notifyServiceStart() {
        runCatching {
            val intent = Intent(ACTION_START_EXECUTION).setPackage(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }.onFailure {
            VmLog.w(LogCategory.AI, TAG, "Could not start foreground execution service: ${it.message}")
        }
    }

    private fun notifyServiceStop() {
        runCatching {
            val intent = Intent(ACTION_STOP_EXECUTION).setPackage(context.packageName)
            context.startService(intent)
        }.onFailure {
            VmLog.w(LogCategory.AI, TAG, "Could not stop foreground execution service: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "AgentExecutionCoordinator"
        const val ACTION_START_EXECUTION = "digital.vmstudio.code.action.START_EXECUTION"
        const val ACTION_STOP_EXECUTION = "digital.vmstudio.code.action.STOP_EXECUTION"
    }
}
