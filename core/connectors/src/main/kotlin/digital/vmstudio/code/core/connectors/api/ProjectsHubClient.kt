package digital.vmstudio.code.core.connectors.api

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.connectors.model.HubActivitiesResponse
import digital.vmstudio.code.core.connectors.model.HubActivity
import digital.vmstudio.code.core.connectors.model.HubGrowthRunRequest
import digital.vmstudio.code.core.connectors.model.HubGrowthRunResponse
import digital.vmstudio.code.core.connectors.model.HubJob
import digital.vmstudio.code.core.connectors.model.HubJobsResponse
import digital.vmstudio.code.core.connectors.model.HubMcpTool
import digital.vmstudio.code.core.connectors.model.HubMcpToolsResponse
import digital.vmstudio.code.core.connectors.model.HubProject
import digital.vmstudio.code.core.connectors.model.HubProjectsResponse
import digital.vmstudio.code.core.connectors.model.HubTask
import digital.vmstudio.code.core.connectors.model.HubTasksResponse
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Universal client for the VM Project Hub ("xcodes/v1") REST API on the WordPress VPS.
 * Authenticates via the [X-VMPJ-Key] header.
 */
@Singleton
class ProjectsHubClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {

    @Volatile
    private var baseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var apiKey: String = DEFAULT_API_KEY

    fun configure(url: String, key: String) {
        baseUrl = url.trimEnd('/')
        apiKey = key.trim()
        VmLog.i(LogCategory.CONNECTOR, TAG, "Projects Hub client configured for $baseUrl")
    }

    suspend fun getProjects(): VmResult<List<HubProject>> {
        val request = newRequestBuilder("$baseUrl/projects").get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                // The endpoint can return either a list of projects directly, or an object {ok: true, projects: [...]}
                parseProjects(body)
            }
    }

    suspend fun getProject(id: Int): VmResult<HubProject> {
        val request = newRequestBuilder("$baseUrl/projects/$id").get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    json.decodeFromString<HubProject>(body)
                }
            }
    }

    suspend fun getTasks(projectId: Int? = null): VmResult<List<HubTask>> {
        val url = if (projectId != null) "$baseUrl/tasks?project_id=$projectId" else "$baseUrl/tasks"
        val request = newRequestBuilder(url).get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    runCatching { json.decodeFromString<HubTasksResponse>(body).tasks }
                        .getOrElse { json.decodeFromString<List<HubTask>>(body) }
                }
            }
    }

    suspend fun getJobs(): VmResult<List<HubJob>> {
        val request = newRequestBuilder("$baseUrl/jobs").get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    runCatching { json.decodeFromString<HubJobsResponse>(body).jobs }
                        .getOrElse { json.decodeFromString<List<HubJob>>(body) }
                }
            }
    }

    suspend fun getActivities(): VmResult<List<HubActivity>> {
        val request = newRequestBuilder("$baseUrl/activities").get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    runCatching { json.decodeFromString<HubActivitiesResponse>(body).activities }
                        .getOrElse { json.decodeFromString<List<HubActivity>>(body) }
                }
            }
    }

    suspend fun triggerGrowthRun(projectId: Int, instructions: String? = null): VmResult<HubGrowthRunResponse> {
        val payload = json.encodeToString(
            HubGrowthRunRequest(
                project_id = projectId,
                instructions = instructions,
                auto_apply = false,
            ),
        )
        val request = newRequestBuilder("$baseUrl/integrations/growth/run")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return httpClient.execute(request, RetryPolicy.NONE)
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    json.decodeFromString<HubGrowthRunResponse>(body)
                }
            }
    }

    suspend fun getMcpTools(): VmResult<List<HubMcpTool>> {
        val request = newRequestBuilder("$baseUrl/mcp/tools").get().build()
        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching(::mapParseError) {
                    runCatching { json.decodeFromString<HubMcpToolsResponse>(body).tools }
                        .getOrElse { json.decodeFromString<List<HubMcpTool>>(body) }
                }
            }
    }

    private fun parseProjects(body: String): VmResult<List<HubProject>> = vmCatching(::mapParseError) {
        runCatching {
            json.decodeFromString<HubProjectsResponse>(body).projects
        }.getOrElse {
            json.decodeFromString<List<HubProject>>(body)
        }
    }

    private fun newRequestBuilder(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header(HEADER_API_KEY, apiKey)
            .header("Accept", "application/json")

    private fun mapParseError(throwable: Throwable): VmError =
        VmError.Unexpected(
            summary = "Could not parse Projects Hub response",
            reason = throwable.message,
            cause = throwable,
        )

    companion object {
        private const val TAG = "ProjectsHubClient"
        const val DEFAULT_BASE_URL = "https://vmstudio.digital/wp-json/xcodes/v1"
        const val DEFAULT_API_KEY = "xc_a081cf117bf790917874c8a0f5dee6634442fa9d"
        const val HEADER_API_KEY = "X-VMPJ-Key"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
