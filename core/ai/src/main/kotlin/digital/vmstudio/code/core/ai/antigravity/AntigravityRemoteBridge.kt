package digital.vmstudio.code.core.ai.antigravity

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.command.CommandLimits
import digital.vmstudio.code.core.ssh.connection.SshConnectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AntigravityRemoteBridge @Inject constructor(
    private val connectionManager: SshConnectionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun listSessions(serverId: String): VmResult<List<AntigravitySessionSummary>> = withContext(ioDispatcher) {
        val command = AntigravityCommandBuilder.discoverSessionsCommand()
        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.QUICK)) {
                is VmResult.Failure -> result
                is VmResult.Success -> {
                    val rawOutput = result.value.combinedOutput().trim()
                    try {
                        val list = parseSessionsJson(rawOutput, serverId)
                        VmLog.i(LogCategory.AI, TAG, "Discovered ${list.size} Antigravity sessions on server $serverId")
                        VmResult.Success(list)
                    } catch (e: Exception) {
                        VmLog.w(LogCategory.AI, TAG, "Failed to parse Antigravity sessions: ${e.message}")
                        VmResult.Success(emptyList())
                    }
                }
            }
        }
    }

    suspend fun executeTurn(
        serverId: String,
        conversationId: String?,
        prompt: String,
        model: String? = null,
    ): VmResult<String> = withContext(ioDispatcher) {
        val command = if (conversationId.isNullOrBlank()) {
            AntigravityCommandBuilder.continueLatestCommand(prompt, model)
        } else {
            AntigravityCommandBuilder.resumeCommand(conversationId, prompt, model)
        }

        connectionManager.withSession(serverId) { session ->
            when (val result = session.execute(command, CommandLimits.LONG_RUNNING)) {
                is VmResult.Failure -> result
                is VmResult.Success -> VmResult.Success(result.value.combinedOutput())
            }
        }
    }

    private fun parseSessionsJson(jsonStr: String, serverId: String): List<AntigravitySessionSummary> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty() || trimmed == "[]" || trimmed == "null") return emptyList()

        val results = mutableListOf<AntigravitySessionSummary>()
        val startIdx = trimmed.indexOfAny(charArrayOf('[', '{'))
        if (startIdx < 0) return emptyList()
        val sub = trimmed.substring(startIdx)

        val jsonArray = if (sub.startsWith("[")) {
            JSONArray(sub)
        } else {
            JSONArray().put(JSONObject(sub))
        }

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val title = obj.optString("title", "Active Conversation")
            val time = obj.optString("time", "")
            if (id.isNotBlank() && id != "tempmediaStorage") {
                results.add(
                    AntigravitySessionSummary(
                        id = id,
                        title = title.ifBlank { "Antigravity Chat ($id)" },
                        timeIso = time,
                        serverId = serverId,
                    ),
                )
            }
        }
        return results
    }

    companion object {
        private const val TAG = "AntigravityRemoteBridge"
    }
}
