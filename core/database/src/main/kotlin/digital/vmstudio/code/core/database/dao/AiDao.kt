package digital.vmstudio.code.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.database.entity.ConversationEntity
import digital.vmstudio.code.core.database.entity.MessageEntity
import digital.vmstudio.code.core.database.entity.MessageStatus
import digital.vmstudio.code.core.database.entity.ToolExecutionEntity
import digital.vmstudio.code.core.database.entity.ToolExecutionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversation WHERE isArchived = 0 ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        "SELECT * FROM conversation WHERE projectId = :projectId AND isArchived = 0 ORDER BY updatedAtMillis DESC",
    )
    fun observeForProject(projectId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversation WHERE id = :id")
    fun observeById(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversation WHERE id = :id")
    suspend fun getById(id: String): ConversationEntity?

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)

    @Query(
        "UPDATE conversation SET providerSessionId = :sessionId, updatedAtMillis = :timestamp WHERE id = :id",
    )
    suspend fun updateProviderSession(id: String, sessionId: String?, timestamp: Long)

    @Query("DELETE FROM conversation WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE conversation SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query(
        """
        UPDATE conversation
        SET promptTokens = promptTokens + :prompt,
            completionTokens = completionTokens + :completion,
            updatedAtMillis = :timestamp
        WHERE id = :id
        """,
    )
    suspend fun addTokenUsage(id: String, prompt: Long, completion: Long, timestamp: Long)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY sequence ASC")
    fun observeForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message WHERE conversationId = :conversationId ORDER BY sequence DESC")
    fun pagingForConversation(conversationId: String): PagingSource<Int, MessageEntity>

    /**
     * Most recent messages, oldest-first once reversed. Used by the context engine,
     * which must not load an entire long conversation into memory.
     */
    @Query(
        "SELECT * FROM message WHERE conversationId = :conversationId ORDER BY sequence DESC LIMIT :limit",
    )
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COALESCE(MAX(sequence), 0) FROM message WHERE conversationId = :conversationId")
    suspend fun maxSequence(conversationId: String): Long

    @Upsert
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE message SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContent(id: String, content: String, status: MessageStatus)

    @Query(
        "UPDATE message SET status = :status, errorText = :errorText, tokenCount = :tokenCount WHERE id = :id",
    )
    suspend fun finalize(id: String, status: MessageStatus, errorText: String?, tokenCount: Int)

    @Query("DELETE FROM message WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Recovers from a process death mid-stream: a message left STREAMING can never
     * resume, so it is marked failed rather than shown as perpetually in-flight.
     */
    @Query(
        "UPDATE message SET status = :failed, errorText = :reason WHERE status IN (:stuck)",
    )
    suspend fun failInterrupted(
        stuck: List<MessageStatus> = listOf(MessageStatus.PENDING, MessageStatus.STREAMING),
        failed: MessageStatus = MessageStatus.FAILED,
        reason: String = "Interrupted before the response completed",
    )
}

@Dao
interface ToolExecutionDao {

    @Query("SELECT * FROM tool_execution WHERE conversationId = :conversationId ORDER BY startedAtMillis ASC")
    fun observeForConversation(conversationId: String): Flow<List<ToolExecutionEntity>>

    @Query("SELECT * FROM tool_execution WHERE messageId = :messageId ORDER BY startedAtMillis ASC")
    fun observeForMessage(messageId: String): Flow<List<ToolExecutionEntity>>

    @Query("SELECT * FROM tool_execution WHERE taskId = :taskId ORDER BY startedAtMillis ASC")
    suspend fun getForTask(taskId: String): List<ToolExecutionEntity>

    @Query("SELECT * FROM tool_execution WHERE id = :id")
    suspend fun getById(id: String): ToolExecutionEntity?

    @Query("SELECT * FROM tool_execution WHERE status = :status ORDER BY startedAtMillis ASC")
    fun observeAwaitingApproval(
        status: ToolExecutionStatus = ToolExecutionStatus.AWAITING_APPROVAL,
    ): Flow<List<ToolExecutionEntity>>

    @Upsert
    suspend fun upsert(execution: ToolExecutionEntity)

    @Query(
        """
        UPDATE tool_execution
        SET status = :status, resultText = :result, errorText = :error, finishedAtMillis = :timestamp
        WHERE id = :id
        """,
    )
    suspend fun finish(
        id: String,
        status: ToolExecutionStatus,
        result: String?,
        error: String?,
        timestamp: Long,
    )

    @Query(
        "UPDATE tool_execution SET status = :cancelled WHERE status IN (:unfinished)",
    )
    suspend fun cancelInterrupted(
        unfinished: List<ToolExecutionStatus> = listOf(
            ToolExecutionStatus.RUNNING,
            ToolExecutionStatus.AWAITING_APPROVAL,
            ToolExecutionStatus.APPROVED,
        ),
        cancelled: ToolExecutionStatus = ToolExecutionStatus.CANCELLED,
    )
}

@Dao
interface AgentTaskDao {

    @Query("SELECT * FROM agent_task ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_task WHERE projectId = :projectId ORDER BY updatedAtMillis DESC")
    fun observeForProject(projectId: String): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_task WHERE status IN (:statuses) ORDER BY updatedAtMillis DESC")
    fun observeByStatus(statuses: List<AgentTaskStatus>): Flow<List<AgentTaskEntity>>

    @Query("SELECT * FROM agent_task WHERE id = :id")
    fun observeById(id: String): Flow<AgentTaskEntity?>

    @Query("SELECT * FROM agent_task WHERE id = :id")
    suspend fun getById(id: String): AgentTaskEntity?

    @Upsert
    suspend fun upsert(task: AgentTaskEntity)

    @Query("UPDATE agent_task SET status = :status, updatedAtMillis = :timestamp WHERE id = :id")
    suspend fun updateStatus(id: String, status: AgentTaskStatus, timestamp: Long)

    @Query("DELETE FROM agent_task WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Called at startup: a task cannot survive process death mid-run, so anything
     * still marked in-flight is reconciled to FAILED with an explicit reason rather
     * than left misleading the user.
     */
    @Transaction
    suspend fun reconcileInterrupted(timestamp: Long) {
        val interrupted = listOf(
            AgentTaskStatus.PLANNING,
            AgentTaskStatus.RUNNING,
            AgentTaskStatus.TESTING,
        )
        markInterrupted(interrupted, timestamp)
    }

    @Query(
        """
        UPDATE agent_task
        SET status = 'FAILED',
            errorText = 'Interrupted: the app stopped before the task finished',
            updatedAtMillis = :timestamp
        WHERE status IN (:statuses)
        """,
    )
    suspend fun markInterrupted(statuses: List<AgentTaskStatus>, timestamp: Long)
}
