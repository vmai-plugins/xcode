package digital.vmstudio.code.core.ai.repository

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle status of one agent run, as the Tasks record presents it.
 *
 * The stored schema distinguishes finer in-flight phases (planning, waiting for
 * approval, testing); from the task list they all mean the same thing — work that
 * has not finished — so they collapse to [TaskRunStatus.RUNNING].
 */
enum class TaskRunStatus { RUNNING, COMPLETED, FAILED, CANCELLED }

/**
 * One row of the Tasks record (§28): an agent run against a project.
 *
 * The conversation holds the transcript; this holds the summary a user needs to
 * answer "what did the agent do, where, and did it finish?" without replaying it.
 */
data class AgentTask(
    val id: String,
    val projectId: String?,
    val conversationId: String?,
    val title: String,
    val status: TaskRunStatus,
    /** Paths the run created or modified, feeding the diff review screen. */
    val changedFiles: List<String>,
    val toolCallCount: Int,
    val promptTokens: Long,
    val completionTokens: Long,
    val errorText: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

/**
 * The persisted record of agent runs.
 *
 * Bookkeeping is deliberately non-fatal to callers: a failed task-row update must
 * never abort the run it describes, so writes return [VmResult] and the chat flow
 * is free to ignore failures.
 */
interface AgentTaskRepository {

    fun observeAll(): Flow<List<AgentTask>>

    /**
     * Resolves the project the agent is working in from its server and directory.
     *
     * Tasks are scoped to projects (the schema's foreign key), so a run opened
     * straight from a server — no project matching this directory — has nothing to
     * attach to and is recorded as conversation-only. Returns null in that case.
     */
    suspend fun findProjectId(serverId: String, remotePath: String): String?

    /**
     * The server a task's project lives on, so the task list can open diff review
     * without knowing how projects map to servers. Null when the project is gone.
     */
    suspend fun serverIdFor(projectId: String?): String?

    /** Creates a RUNNING task row; null when [projectId] is null (nothing to attach to). */
    suspend fun start(conversationId: String?, title: String, projectId: String?): String?

    suspend fun complete(taskId: String, promptTokens: Long, completionTokens: Long): VmResult<Unit>

    suspend fun fail(taskId: String, errorText: String): VmResult<Unit>

    suspend fun cancel(taskId: String): VmResult<Unit>

    /** Appends a path to the task's changed-file set, deduplicated and bounded. */
    suspend fun recordChangedFile(taskId: String, path: String): VmResult<Unit>

    /** Counts one tool invocation against the task. */
    suspend fun recordToolCall(taskId: String): VmResult<Unit>

    suspend fun delete(taskId: String): VmResult<Unit>
}

/** JSON array codec for [AgentTaskEntity.changedFilesJson]. */
object ChangedFilesCodec {
    private val json = Json
    private val serializer = ListSerializer(String.serializer())
    private const val MAX_FILES = 50

    fun encode(paths: List<String>): String = json.encodeToString(serializer, paths.take(MAX_FILES))

    fun decode(value: String): List<String> =
        runCatching { json.decodeFromString(serializer, value) }.getOrDefault(emptyList())
}

@Singleton
class DefaultAgentTaskRepository @Inject constructor(
    private val agentTaskDao: AgentTaskDao,
    private val projectDao: ProjectDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AgentTaskRepository {

    override fun observeAll(): Flow<List<AgentTask>> =
        agentTaskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findProjectId(serverId: String, remotePath: String): String? =
        withContext(ioDispatcher) {
            projectDao.observeByServer(serverId).first()
                .firstOrNull { it.remotePath == remotePath }
                ?.id
        }

    override suspend fun serverIdFor(projectId: String?): String? {
        if (projectId == null) return null
        return withContext(ioDispatcher) { projectDao.getById(projectId)?.serverId }
    }

    override suspend fun start(conversationId: String?, title: String, projectId: String?): String? {
        if (projectId == null) return null
        return withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val entity = AgentTaskEntity(
                id = "task-${UUID.randomUUID()}",
                projectId = projectId,
                conversationId = conversationId,
                title = title.lineSequence().firstOrNull().orEmpty()
                    .ifBlank { "Agent run" }
                    .take(MAX_TITLE_LENGTH),
                status = AgentTaskStatus.RUNNING,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            when (val result = store { agentTaskDao.upsert(entity) }) {
                is VmResult.Success -> entity.id
                is VmResult.Failure -> {
                    VmLog.w(LogCategory.AI, TAG, "Could not record task start: ${result.error.summary}")
                    null
                }
            }
        }
    }

    override suspend fun complete(
        taskId: String,
        promptTokens: Long,
        completionTokens: Long,
    ): VmResult<Unit> = update(taskId) {
        copy(
            status = AgentTaskStatus.COMPLETED,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            errorText = null,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun fail(taskId: String, errorText: String): VmResult<Unit> =
        update(taskId) {
            copy(
                status = AgentTaskStatus.FAILED,
                errorText = errorText.take(MAX_ERROR_LENGTH),
                updatedAtMillis = System.currentTimeMillis(),
            )
        }

    override suspend fun cancel(taskId: String): VmResult<Unit> = update(taskId) {
        copy(
            status = AgentTaskStatus.CANCELLED,
            errorText = "Stopped before the run finished",
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    override suspend fun recordChangedFile(taskId: String, path: String): VmResult<Unit> =
        update(taskId) {
            val files = ChangedFilesCodec.decode(changedFilesJson)
            if (path.isBlank() || path in files) {
                this
            } else {
                copy(changedFilesJson = ChangedFilesCodec.encode(files + path))
            }
        }

    override suspend fun recordToolCall(taskId: String): VmResult<Unit> = update(taskId) {
        copy(commandCount = commandCount + 1)
    }

    override suspend fun delete(taskId: String): VmResult<Unit> =
        store { agentTaskDao.deleteById(taskId) }

    /**
     * Read-modify-write on one task row. The load failure and the write failure carry
     * different suggested actions, so they are reported separately.
     */
    private suspend fun update(
        taskId: String,
        transform: AgentTaskEntity.() -> AgentTaskEntity,
    ): VmResult<Unit> = withContext(ioDispatcher) {
        when (val loaded = store { agentTaskDao.getById(taskId) }) {
            is VmResult.Failure -> loaded
            is VmResult.Success ->
                when (val existing = loaded.value) {
                    null -> VmResult.Failure(
                        VmError.Storage(
                            summary = "Task record disappeared",
                            reason = "No agent task with id $taskId",
                            suggestedAction = "The task list can be refreshed or the row deleted.",
                        ),
                    )
                    else -> store { agentTaskDao.upsert(existing.transform()) }
                }
        }
    }

    private inline fun <T> store(block: () -> T): VmResult<T> = vmCatching(
        { throwable ->
            VmError.Storage(
                summary = "Could not update the agent task record",
                reason = throwable.message,
                suggestedAction = "The run itself is unaffected; its task entry may be stale.",
                cause = throwable,
            )
        },
    ) { block() }

    private fun AgentTaskEntity.toDomain(): AgentTask = AgentTask(
        id = id,
        projectId = projectId,
        conversationId = conversationId,
        title = title,
        status = status.toRunStatus(),
        changedFiles = ChangedFilesCodec.decode(changedFilesJson),
        toolCallCount = commandCount,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        errorText = errorText,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )

    private fun AgentTaskStatus.toRunStatus(): TaskRunStatus = when (this) {
        AgentTaskStatus.COMPLETED -> TaskRunStatus.COMPLETED
        AgentTaskStatus.FAILED -> TaskRunStatus.FAILED
        AgentTaskStatus.CANCELLED -> TaskRunStatus.CANCELLED
        // TODO, PLANNING, RUNNING, WAITING_APPROVAL and TESTING all describe work
        // that has not finished; the task list does not need the finer phases.
        else -> TaskRunStatus.RUNNING
    }

    private companion object {
        const val TAG = "AgentTaskRepository"
        const val MAX_TITLE_LENGTH = 80
        const val MAX_ERROR_LENGTH = 500
    }
}
