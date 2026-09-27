package digital.vmstudio.code.core.connectors.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HubProject(
    val id: Int = 0,
    val name: String? = null,
    val title: String? = null,
    val slug: String? = null,
    val stack: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val server_path: String? = null,
    val app_path: String? = null,
    val repo_url: String? = null,
    val live_url: String? = null,
    val environment: String? = null,
    val progress: Double? = null,
    val progress_percent: Int? = null,
    val due_date: String? = null,
    val tags: String? = null,
    val seo_keywords: String? = null,
    val seo_status: String? = null,
    val health_score: Int? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val tasks: List<HubTask> = emptyList(),
    val jobs: List<HubJob> = emptyList(),
    val activities: List<HubActivity> = emptyList(),
) {
    val displayName: String get() = name ?: title ?: slug ?: "Project #$id"
    val workingDirectory: String
        get() = server_path?.takeIf { it.isNotBlank() }
            ?: app_path?.takeIf { it.isNotBlank() }
            ?: ""
}

@Serializable
data class HubProjectsResponse(
    val ok: Boolean = false,
    val count: Int = 0,
    val projects: List<HubProject> = emptyList(),
)

@Serializable
data class HubTask(
    val id: Int = 0,
    val project_id: Int? = null,
    val title: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val due_date: String? = null,
    val assigned_to: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class HubTasksResponse(
    val ok: Boolean = false,
    val count: Int = 0,
    val tasks: List<HubTask> = emptyList(),
)

@Serializable
data class HubJob(
    val id: Int = 0,
    val project_id: Int? = null,
    val task_id: Int? = null,
    val prompt: String? = null,
    val status: String? = null,
    val progress: Double? = null,
    val error_message: String? = null,
    val created_at: String? = null,
    val finished_at: String? = null,
)

@Serializable
data class HubJobsResponse(
    val ok: Boolean = false,
    val count: Int = 0,
    val jobs: List<HubJob> = emptyList(),
)

@Serializable
data class HubActivity(
    val id: Int = 0,
    val project_id: Int? = null,
    val action: String? = null,
    val details: String? = null,
    val user_name: String? = null,
    val created_at: String? = null,
)

@Serializable
data class HubActivitiesResponse(
    val ok: Boolean = false,
    val count: Int = 0,
    val activities: List<HubActivity> = emptyList(),
)

@Serializable
data class HubGrowthRunRequest(
    val project_id: Int,
    val instructions: String? = null,
    val auto_apply: Boolean = false,
)

@Serializable
data class HubGrowthRunResponse(
    val ok: Boolean = false,
    val message: String? = null,
    val job_id: Int? = null,
    val status: String? = null,
)

@Serializable
data class HubMcpTool(
    val name: String,
    val description: String? = null,
    val input_schema: JsonElement? = null,
)

@Serializable
data class HubMcpToolsResponse(
    val ok: Boolean = false,
    val tools: List<HubMcpTool> = emptyList(),
)
