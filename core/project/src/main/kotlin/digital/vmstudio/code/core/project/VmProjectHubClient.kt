package digital.vmstudio.code.core.project

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

data class HubProjectItem(
    val id: String,
    val name: String,
    val slug: String,
    val stack: String,
    val status: String,
    val priority: String,
    val repoUrl: String?,
    val appUrls: String?,
    val serverPath: String?,
    val appPath: String?,
    val liveUrl: String?,
    val nextMilestone: String?,
    val progressPercent: Int,
    val tasksDone: Int,
    val tasksTotal: Int,
    val isPartial: Boolean,
) {
    /**
     * Universal workspace resolution:
     * 1. If a GitHub URL exists in repoUrl or appUrls -> Zero-Clone `/github/{owner}/{repo}`
     * 2. If a server/app path exists -> use it (and bind dynamically to any active VPS, or universal local fallback if VPS was lost/not added yet)
     * 3. If partial/unassigned -> `/vmhub/{slug}` universal path so it works anywhere
     */
    fun universalPath(): String {
        extractGithubRepoPath(repoUrl ?: appUrls)?.let { return it }
        val rawPath = serverPath?.takeIf { it.isNotBlank() }
            ?: appPath?.takeIf { it.isNotBlank() }
        if (rawPath != null && (rawPath.startsWith("/") || rawPath.startsWith("~"))) return rawPath
        val safeSlug = slug.ifBlank { name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') }
            .ifBlank { "project-$id" }
        return "/vmhub/$safeSlug"
    }

    val hasGithubRepo: Boolean
        get() = extractGithubRepoPath(repoUrl ?: appUrls) != null

    companion object {
        private val GITHUB_REGEX = Regex("""github\.com[:/]+([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)""")

        fun extractGithubRepoPath(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val match = GITHUB_REGEX.find(raw) ?: return null
            val fullName = match.groupValues[1].removeSuffix(".git").trim('/')
            return if ('/' in fullName) "/github/$fullName" else null
        }
    }
}

data class HubTaskItem(
    val id: String,
    val projectId: String,
    val projectName: String,
    val title: String,
    val detail: String,
    val status: String,
    val priority: String,
    val dueDate: String?,
)

data class HubCredentials(
    val githubToken: String?,
    val omnirouteKey: String?,
    val omnirouteUrl: String?,
    val defaultModel: String?,
    val modelsUrl: String?,
    val kaggleUsername: String?,
    val vpsHost: String?,
    val vpsUser: String? = null,
    val vpsPort: Int? = null,
    val vpsAuthMethod: String? = null,
    val vpsPassword: String? = null,
    val vpsPrivateKey: String? = null,
    val vpsName: String? = null,
)

@Singleton
class VmProjectHubClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val projectRepository: ProjectRepository,
    private val serverRepository: ServerRepository,
    private val preferencesRepository: digital.vmstudio.code.core.common.preferences.UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getEffectiveHubKey(): String? {
        val configured = preferencesRepository.preferences.first().hubApiKey
        return configured?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun testHubKey(key: String): VmResult<Int> = withContext(ioDispatcher) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return@withContext VmResult.Failure(VmError.Validation("Key cannot be empty"))
        }
        val request = Request.Builder()
            .url("$HUB_BASE_URL/projects")
            .header("X-VMPJ-Key", trimmed)
            .header("Accept", "application/json")
            .get()
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 1))) {
            is VmResult.Failure -> result
            is VmResult.Success -> {
                val projects = parseProjects(result.value)
                VmResult.Success(projects.size)
            }
        }
    }

    suspend fun fetchProjects(): VmResult<List<HubProjectItem>> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val request = Request.Builder()
            .url("$HUB_BASE_URL/projects")
            .header("X-VMPJ-Key", hubKey)
            .header("Accept", "application/json")
            .get()
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(parseProjects(result.value))
        }
    }

    suspend fun fetchTasks(): VmResult<List<HubTaskItem>> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val projectsById = (fetchProjects() as? VmResult.Success)?.value
            ?.associateBy({ it.id }, { it.name })
            .orEmpty()

        val request = Request.Builder()
            .url("$HUB_BASE_URL/tasks")
            .header("X-VMPJ-Key", hubKey)
            .header("Accept", "application/json")
            .get()
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(parseTasks(result.value, projectsById))
        }
    }

    suspend fun fetchCredentials(): VmResult<HubCredentials> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val request = Request.Builder()
            .url("$HUB_BASE_URL/credentials")
            .header("X-VMPJ-Key", hubKey)
            .header("Accept", "application/json")
            .get()
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> {
                val root = runCatching { json.parseToJsonElement(result.value) }.getOrNull() as? JsonObject
                if (root == null || (root["ok"] as? JsonPrimitive)?.content == "false") {
                    VmResult.Failure(VmError.Network("Failed to retrieve credentials from Hub"))
                } else {
                    val creds = HubCredentials(
                        githubToken = root.str("github_token"),
                        omnirouteKey = root.str("omniroute_key"),
                        omnirouteUrl = root.str("omniroute_url"),
                        defaultModel = root.str("default_model"),
                        modelsUrl = root.str("models_url"),
                        kaggleUsername = root.str("kaggle_username"),
                        vpsHost = root.str("vps_host"),
                        vpsUser = root.str("vps_user"),
                        vpsPort = root.int("vps_port"),
                        vpsAuthMethod = root.str("vps_auth_method"),
                        vpsPassword = root.str("vps_password"),
                        vpsPrivateKey = root.str("vps_private_key"),
                        vpsName = root.str("vps_name"),
                    )
                    VmResult.Success(creds)
                }
            }
        }
    }

    suspend fun updateTaskStatus(taskId: String, newStatus: String): VmResult<Unit> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val body = buildJsonObject {
            put("status", newStatus)
        }.toString()

        val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
        val request = Request.Builder()
            .url("$HUB_BASE_URL/tasks/$encodedId")
            .header("X-VMPJ-Key", hubKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 1))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(Unit)
        }
    }

    /**
     * Universal sync: imports or updates projects from VM Project Hub into the local
     * ProjectRepository, dynamically binding GitHub repos to Zero-Clone `/github/...`
     * and server paths to the user's current active VPS (or universal local fallback
     * if a previous VPS was lost and a new one hasn't been added yet).
     */
    suspend fun syncUniversalProjects(): VmResult<Int> = withContext(ioDispatcher) {
        val hubProjects = when (val res = fetchProjects()) {
            is VmResult.Failure -> return@withContext res
            is VmResult.Success -> res.value
        }
        val activeServers = serverRepository.servers.first()
        val primaryServerId = activeServers.firstOrNull {
            it.host.contains("vmstudio", ignoreCase = true) || it.host == "200.234.41.231"
        }?.id ?: activeServers.firstOrNull()?.id

        val existing = projectRepository.observeAll().first()
        var syncedCount = 0

        for (hub in hubProjects) {
            val targetPath = hub.universalPath()
            val targetServerId = when {
                targetPath.startsWith("/github/") -> null
                targetPath.startsWith("/vmhub/") -> null
                else -> primaryServerId // Null if no VPS added yet; re-binds when new VPS is added
            }
            val descParts = mutableListOf<String>()
            descParts += "VM Hub #${hub.id}"
            if (hub.stack.isNotBlank()) descParts += "Stack: ${hub.stack}"
            if (hub.priority.isNotBlank()) descParts += "Priority: ${hub.priority.uppercase()}"
            if (hub.isPartial) descParts += "Partial / Universal"
            if (!hub.liveUrl.isNullOrBlank()) descParts += hub.liveUrl
            val description = descParts.joinToString(" · ")

            val match = existing.firstOrNull {
                it.name.equals(hub.name, ignoreCase = true) ||
                    it.remotePath.equals(targetPath, ignoreCase = true) ||
                    it.description.contains("VM Hub #${hub.id}")
            }

            if (match == null) {
                val branch = if (hub.hasGithubRepo) "main" else null
                val created = projectRepository.create(
                    name = hub.name,
                    serverId = targetServerId,
                    remotePath = targetPath,
                    description = description,
                    projectTypeId = hub.stack.ifBlank { ProjectRepository.DEFAULT_TYPE },
                    branch = branch,
                    environment = ServerEnvironment.DEVELOPMENT,
                    runtime = hub.stack.takeIf { it.isNotBlank() } ?: "vmhub",
                    packageManager = if (hub.isPartial) "partial" else hub.priority.takeIf { it.isNotBlank() },
                )
                if (created is VmResult.Success) syncedCount++
            } else {
                val updatedServerId = if (match.serverId == null && targetServerId != null) targetServerId else match.serverId
                val updatedPath = if (match.remotePath.startsWith("/vmhub/") && targetPath.startsWith("/github/")) targetPath else match.remotePath
                val updatedBranch = if (hub.hasGithubRepo) (match.branch ?: "main") else match.branch
                projectRepository.update(
                    match.copy(
                        description = description,
                        serverId = updatedServerId,
                        remotePath = updatedPath,
                        branch = updatedBranch,
                        runtime = hub.stack.takeIf { it.isNotBlank() } ?: match.runtime,
                        packageManager = if (hub.isPartial) "partial" else (hub.priority.takeIf { it.isNotBlank() } ?: match.packageManager),
                    ),
                )
                syncedCount++
            }
        }
        VmResult.Success(syncedCount)
    }

    internal fun parseProjects(rawBody: String): List<HubProjectItem> {
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject
            ?: return emptyList()
        val array = root["projects"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val name = obj.str("name")?.takeIf { it.isNotBlank() } ?: "Project #$id"
            val slug = obj.str("slug").orEmpty()
            val stack = obj.str("stack").orEmpty()
            val status = obj.str("status").orEmpty().ifBlank { "in_progress" }
            val priority = obj.str("priority").orEmpty().ifBlank { "medium" }
            val repoUrl = obj.str("repo_url")?.takeIf { it.isNotBlank() }
            val appUrls = obj.str("app_urls")?.takeIf { it.isNotBlank() }
            val serverPath = obj.str("server_path")?.takeIf { it.isNotBlank() }
            val appPath = obj.str("app_path")?.takeIf { it.isNotBlank() }
            val liveUrl = obj.str("live_url")?.takeIf { it.isNotBlank() }
            val nextMilestone = obj.str("next_milestone")?.takeIf { it.isNotBlank() }
            val progressPercent = obj.int("progress_percent") ?: obj.int("progress") ?: 0
            val taskProgress = obj["task_progress"] as? JsonObject
            val tasksDone = taskProgress?.int("done") ?: 0
            val tasksTotal = taskProgress?.int("total") ?: 0
            val isPartial = repoUrl == null && serverPath == null && appPath == null

            HubProjectItem(
                id = id,
                name = name,
                slug = slug,
                stack = stack,
                status = status,
                priority = priority,
                repoUrl = repoUrl,
                appUrls = appUrls,
                serverPath = serverPath,
                appPath = appPath,
                liveUrl = liveUrl,
                nextMilestone = nextMilestone,
                progressPercent = progressPercent,
                tasksDone = tasksDone,
                tasksTotal = tasksTotal,
                isPartial = isPartial,
            )
        }
    }

    internal fun parseTasks(rawBody: String, projectsById: Map<String, String>): List<HubTaskItem> {
        val root = runCatching { json.parseToJsonElement(rawBody) }.getOrNull() as? JsonObject
            ?: return emptyList()
        val array = root["tasks"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id") ?: return@mapNotNull null
            val projectId = obj.str("project_id").orEmpty()
            val title = obj.str("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            HubTaskItem(
                id = id,
                projectId = projectId,
                projectName = projectsById[projectId] ?: if (projectId.isNotBlank()) "Project #$projectId" else "General",
                title = title,
                detail = obj.str("detail").orEmpty(),
                status = obj.str("status").orEmpty().ifBlank { "todo" },
                priority = obj.str("priority").orEmpty().ifBlank { "medium" },
                dueDate = obj.str("due_date")?.takeIf { it.isNotBlank() },
            )
        }
    }

    companion object {
        const val HUB_BASE_URL = "https://vmstudio.digital/wp-json/xcodes/v1"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeUnless { it == "null" }

private fun JsonObject.int(key: String): Int? {
    val raw = (this[key] as? JsonPrimitive)?.content ?: return null
    return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
}
