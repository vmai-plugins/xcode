package digital.vmstudio.code.core.ai.repository

import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.ConversationDao
import digital.vmstudio.code.core.database.dao.MessageDao
import digital.vmstudio.code.core.database.dao.ToolExecutionDao
import digital.vmstudio.code.core.database.entity.ConversationEntity
import digital.vmstudio.code.core.database.entity.MessageEntity
import digital.vmstudio.code.core.database.entity.ToolExecutionEntity
import digital.vmstudio.code.core.database.entity.MessageRole
import digital.vmstudio.code.core.database.entity.MessageStatus
import digital.vmstudio.code.core.database.entity.ToolExecutionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A conversation with an agent, as the UI needs it. */
data class AgentConversation(
    val id: String,
    val title: String,
    val providerId: String,
    val providerSessionId: String?,
    val serverId: String?,
    val workingDirectory: String?,
    val promptTokens: Long,
    val completionTokens: Long,
    val updatedAtMillis: Long,
) {
    /** Whether a follow-up can continue this conversation rather than start fresh. */
    val isResumable: Boolean get() = providerSessionId != null
}

/**
 * Persists agent conversations and the tool calls within them.
 *
 * Two things depend on this rather than on an in-memory transcript. A run's
 * `providerSessionId` is what makes a follow-up continue the same conversation
 * instead of starting cold, so it has to outlive the screen. And the specification's
 * audit requirement means the record of what the agent did must survive the process
 * that did it.
 */
/**
 * One entry of a stored transcript, in storage-neutral terms.
 *
 * The repository maps Room entities into these rather than exposing them, so a
 * feature module never depends on the persistence layer to render a conversation.
 */
sealed interface StoredEntry {
    val timestampMillis: Long

    data class User(
        override val timestampMillis: Long,
        val text: String,
    ) : StoredEntry

    data class Assistant(
        override val timestampMillis: Long,
        val text: String,
    ) : StoredEntry

    data class Reasoning(
        override val timestampMillis: Long,
        val text: String,
    ) : StoredEntry

    data class Tool(
        override val timestampMillis: Long,
        val callId: String,
        val name: String,
        val output: String?,
        val isError: Boolean,
    ) : StoredEntry

    data class Failed(
        override val timestampMillis: Long,
        val summary: String,
    ) : StoredEntry
}

interface AgentConversationRepository {

    fun observeConversations(): Flow<List<AgentConversation>>

    /**
     * The stored transcript, ordered as it happened.
     *
     * Messages and tool calls live in separate tables; this merges them so reopening
     * a conversation shows the same sequence the user watched the first time.
     */
    suspend fun transcript(conversationId: String): List<StoredEntry>

    suspend fun get(conversationId: String): AgentConversation?

    suspend fun create(
        title: String,
        providerId: String,
        modelId: String,
        serverId: String?,
        workingDirectory: String?,
    ): VmResult<String>

    suspend fun appendUserPrompt(conversationId: String, prompt: String): VmResult<Unit>

    /** Writes one streamed event into the transcript. */
    suspend fun record(conversationId: String, event: AgentEvent): VmResult<Unit>

    suspend fun rename(conversationId: String, title: String): VmResult<Unit>

    suspend fun delete(conversationId: String): VmResult<Unit>
}

@Singleton
class DefaultAgentConversationRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val toolExecutionDao: ToolExecutionDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AgentConversationRepository {

    override fun observeConversations(): Flow<List<AgentConversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun transcript(conversationId: String): List<StoredEntry> =
        withContext(ioDispatcher) {
            val messages = messageDao.observeForConversation(conversationId).first()
            val tools = toolExecutionDao.observeForConversation(conversationId).first()

            buildList<StoredEntry> {
                messages.forEach { message ->
                    when {
                        message.status == MessageStatus.FAILED -> add(
                            StoredEntry.Failed(
                                timestampMillis = message.createdAtMillis,
                                summary = message.errorText ?: "The run failed",
                            ),
                        )

                        message.role == MessageRole.USER && message.content.isNotBlank() -> add(
                            StoredEntry.User(message.createdAtMillis, message.content),
                        )

                        !message.reasoning.isNullOrBlank() -> add(
                            StoredEntry.Reasoning(message.createdAtMillis, message.reasoning!!),
                        )

                        message.content.isNotBlank() -> add(
                            StoredEntry.Assistant(message.createdAtMillis, message.content),
                        )
                    }
                }

                tools.forEach { tool ->
                    add(
                        StoredEntry.Tool(
                            timestampMillis = tool.startedAtMillis,
                            callId = tool.providerCallId ?: tool.id,
                            name = tool.toolName,
                            output = tool.resultText ?: tool.errorText,
                            isError = tool.status == ToolExecutionStatus.FAILED,
                        ),
                    )
                }
            }.sortedBy { it.timestampMillis }
        }

    private fun ConversationEntity.toDomain() = AgentConversation(
        id = id,
        title = title,
        providerId = providerId,
        providerSessionId = providerSessionId,
        serverId = serverId,
        workingDirectory = workingDirectory,
        promptTokens = promptTokens,
        completionTokens = completionTokens,
        updatedAtMillis = updatedAtMillis,
    )

    override suspend fun get(conversationId: String): AgentConversation? =
        withContext(ioDispatcher) {
            conversationDao.getById(conversationId)?.toDomain()
        }

    override suspend fun create(
        title: String,
        providerId: String,
        modelId: String,
        serverId: String?,
        workingDirectory: String?,
    ): VmResult<String> = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        vmCatching(::mapError) {
            conversationDao.upsert(
                ConversationEntity(
                    id = id,
                    // Null until projects exist; the column is nullable for exactly
                    // this reason rather than requiring a placeholder project.
                    projectId = null,
                    title = title,
                    providerId = providerId,
                    serverId = serverId,
                    workingDirectory = workingDirectory,
                    modelId = modelId,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            id
        }
    }

    override suspend fun appendUserPrompt(
        conversationId: String,
        prompt: String,
    ): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) {
            insertMessage(conversationId, MessageRole.USER, prompt)
        }
    }

    override suspend fun record(
        conversationId: String,
        event: AgentEvent,
    ): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) {
            val now = System.currentTimeMillis()
            when (event) {
                is AgentEvent.SessionStarted ->
                    conversationDao.updateProviderSession(conversationId, event.sessionId, now)

                is AgentEvent.AssistantMessage ->
                    insertMessage(conversationId, MessageRole.ASSISTANT, event.text)

                is AgentEvent.Reasoning ->
                    insertMessage(
                        conversationId,
                        MessageRole.ASSISTANT,
                        content = "",
                        reasoning = event.text,
                    )

                is AgentEvent.ToolStarted -> toolExecutionDao.upsert(
                    ToolExecutionEntity(
                        // The provider's call id is the stable key; a generated one
                        // would not match the result event that arrives later.
                        id = toolRowId(conversationId, event.toolUseId),
                        conversationId = conversationId,
                        messageId = null,
                        toolName = event.name,
                        providerCallId = event.toolUseId,
                        argumentsJson = event.argumentsJson,
                        status = ToolExecutionStatus.RUNNING,
                        // The agent's own tools are governed by the provider's
                        // permission mode, not by this app's approval gate.
                        requiresApproval = false,
                        riskLevel = "provider-managed",
                        startedAtMillis = now,
                    ),
                )

                is AgentEvent.ToolFinished -> toolExecutionDao.finish(
                    id = toolRowId(conversationId, event.toolUseId),
                    status = if (event.isError) {
                        ToolExecutionStatus.FAILED
                    } else {
                        ToolExecutionStatus.SUCCEEDED
                    },
                    result = event.output.take(MAX_TOOL_OUTPUT),
                    error = if (event.isError) event.output.take(MAX_TOOL_OUTPUT) else null,
                    timestamp = now,
                )

                is AgentEvent.Completed -> {
                    conversationDao.addTokenUsage(
                        id = conversationId,
                        prompt = event.inputTokens,
                        completion = event.outputTokens,
                        timestamp = now,
                    )
                    event.sessionId?.let {
                        conversationDao.updateProviderSession(conversationId, it, now)
                    }
                }

                is AgentEvent.Failed ->
                    insertMessage(
                        conversationId,
                        MessageRole.ASSISTANT,
                        content = "",
                        status = MessageStatus.FAILED,
                        errorText = event.error.summary,
                    )

                // Deltas are display-only. The complete AssistantMessage that follows is
                // what gets stored; persisting both would duplicate every reply.
                is AgentEvent.AssistantDelta -> Unit

                // Diagnostics are shown live but not persisted: they are transport
                // noise, and keeping them would bloat every stored transcript.
                is AgentEvent.Diagnostic -> Unit
            }
            Unit
        }
    }

    override suspend fun rename(conversationId: String, title: String): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapError) {
                conversationDao.getById(conversationId)?.let {
                    conversationDao.upsert(it.copy(title = title))
                }
                Unit
            }
        }

    override suspend fun delete(conversationId: String): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapError) { conversationDao.deleteById(conversationId) }
        }

    private suspend fun insertMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        reasoning: String? = null,
        status: MessageStatus = MessageStatus.COMPLETE,
        errorText: String? = null,
    ) {
        val now = System.currentTimeMillis()
        // sequence is assigned inside insertWithNextSequence's own transaction, not
        // here, so two concurrent writers to the same conversation cannot read the
        // same max and assign the same sequence.
        messageDao.insertWithNextSequence(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = role,
                content = content,
                reasoning = reasoning,
                status = status,
                errorText = errorText,
                createdAtMillis = now,
                sequence = 0,
            ),
        )
    }

    /** Deterministic row id so the start and finish events address the same row. */
    private fun toolRowId(conversationId: String, toolUseId: String): String =
        if (toolUseId.isBlank()) UUID.randomUUID().toString() else "$conversationId:$toolUseId"

    private fun mapError(throwable: Throwable) = VmError.Storage(
        summary = "Could not save the conversation",
        reason = throwable.message,
        suggestedAction = "The run continues; the transcript may be incomplete.",
        cause = throwable,
    )

    private companion object {
        /** Tool output can be a whole file; the transcript stores a readable head. */
        const val MAX_TOOL_OUTPUT = 8_000
    }
}
