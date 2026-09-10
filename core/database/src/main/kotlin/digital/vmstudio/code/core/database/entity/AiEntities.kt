package digital.vmstudio.code.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }

enum class MessageStatus { PENDING, STREAMING, COMPLETE, FAILED, CANCELLED }

@Entity(
    tableName = "conversation",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId"]), Index(value = ["updatedAtMillis"])],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val projectId: String?,
    val title: String,
    val providerId: String,
    /**
     * The backend's own conversation id, used to resume. For the Claude Code CLI
     * this is the session id passed to --resume; for an HTTP provider it is null.
     */
    val providerSessionId: String? = null,
    /** Server the conversation runs against, for CLI-backed providers. */
    val serverId: String? = null,
    /** Absolute working directory on that server. */
    val workingDirectory: String? = null,
    val modelId: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "message",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["conversationId", "createdAtMillis"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: MessageRole,
    val content: String,
    /** Provider-reported reasoning summary, when the model emits one. */
    val reasoning: String? = null,
    val status: MessageStatus = MessageStatus.COMPLETE,
    val errorText: String? = null,
    val tokenCount: Int = 0,
    val modelId: String? = null,
    val createdAtMillis: Long,
    /** Monotonic ordering within a conversation, stable across equal timestamps. */
    val sequence: Long,
)

enum class ToolExecutionStatus {
    PROPOSED,
    AWAITING_APPROVAL,
    APPROVED,
    DENIED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/**
 * One invocation of an agent tool.
 *
 * Persisted separately from the message so the UI can render live progress, the
 * user can audit exactly what the agent did, and a task can be resumed after the
 * process is killed mid-run.
 */
@Entity(
    tableName = "tool_execution",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversationId", "startedAtMillis"]),
        Index(value = ["messageId"]),
        Index(value = ["taskId"]),
    ],
)
data class ToolExecutionEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String?,
    val taskId: String? = null,
    val toolName: String,
    /** Provider-supplied call id, echoed back with the tool result. */
    val providerCallId: String? = null,
    val argumentsJson: String,
    val status: ToolExecutionStatus,
    /** Truncated for storage; the full output lives in the transcript cache. */
    val resultText: String? = null,
    val errorText: String? = null,
    val requiresApproval: Boolean,
    val riskLevel: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long = 0L,
)

enum class AgentTaskStatus {
    TODO,
    PLANNING,
    RUNNING,
    WAITING_APPROVAL,
    TESTING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }

@Entity(
    tableName = "agent_task",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId", "status"]),
        Index(value = ["updatedAtMillis"]),
    ],
)
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val conversationId: String?,
    val title: String,
    val description: String = "",
    val status: AgentTaskStatus = AgentTaskStatus.TODO,
    val priority: TaskPriority = TaskPriority.NORMAL,
    /** JSON array of paths the run has modified, for the diff review screen. */
    val changedFilesJson: String = "[]",
    val iterationCount: Int = 0,
    val commandCount: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val errorText: String? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long = 0L,
)
