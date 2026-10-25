package digital.vmstudio.code.core.project

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.dao.ConversationDao
import digital.vmstudio.code.core.database.dao.MessageDao
import digital.vmstudio.code.core.database.entity.ConversationEntity
import digital.vmstudio.code.core.database.entity.MessageEntity
import digital.vmstudio.code.core.database.entity.MessageRole
import digital.vmstudio.code.core.database.entity.MessageStatus
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import digital.vmstudio.code.core.ssh.model.ServerDraft
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
    val evolutionApiBaseUrl: String? = null,
    val evolutionApiKey: String? = null,
    val evolutionApiInstance: String? = null,
    val evolutionApiRecipientNumber: String? = null,
    val evolutionApiNotificationsEnabled: Boolean? = null,
    val googleDriveToken: String? = null,
    val googleDriveEmail: String? = null,
)

@Singleton
class VmProjectHubClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val projectRepository: ProjectRepository,
    private val serverRepository: ServerRepository,
    private val preferencesRepository: digital.vmstudio.code.core.common.preferences.UserPreferencesRepository,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val credentialStore: SecureCredentialStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getEffectiveHubKey(): String? {
        val configured = preferencesRepository.preferences.first().hubApiKey
        return configured?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun getEffectiveHubBaseUrl(): String {
        val configured = preferencesRepository.preferences.first().hubBaseUrl.trim()
        val base = if (configured.isNotBlank()) configured else DEFAULT_HUB_BASE_URL
        return if (!base.contains("/wp-json/xcodes/v1")) {
            "${base.removeSuffix("/")}/wp-json/xcodes/v1"
        } else {
            base.removeSuffix("/")
        }
    }

    suspend fun testHubKey(key: String, customBaseUrl: String? = null): VmResult<Int> = withContext(ioDispatcher) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            return@withContext VmResult.Failure(VmError.Validation("Key cannot be empty"))
        }
        val baseUrl = customBaseUrl?.trim()?.takeIf { it.isNotBlank() } ?: getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/projects")
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

        val baseUrl = getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/projects")
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

        val baseUrl = getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/tasks")
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

        val baseUrl = getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/credentials")
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
                        githubToken = root.str("githubToken") ?: root.str("github_token"),
                        omnirouteKey = root.str("omnirouteKey") ?: root.str("omniroute_key"),
                        omnirouteUrl = root.str("omnirouteUrl") ?: root.str("omniroute_url"),
                        defaultModel = root.str("defaultModel") ?: root.str("default_model"),
                        modelsUrl = root.str("modelsUrl") ?: root.str("models_url"),
                        kaggleUsername = root.str("kaggleUsername") ?: root.str("kaggle_username"),
                        vpsHost = root.str("vpsHost") ?: root.str("vps_host"),
                        vpsUser = root.str("vpsUser") ?: root.str("vps_user"),
                        vpsPort = root.int("vpsPort") ?: root.int("vps_port"),
                        vpsAuthMethod = root.str("vpsAuthMethod") ?: root.str("vps_auth_method"),
                        vpsPassword = root.str("vpsPassword") ?: root.str("vps_password"),
                        vpsPrivateKey = root.str("vpsPrivateKey") ?: root.str("vps_private_key"),
                        vpsName = root.str("vpsName") ?: root.str("vps_name"),
                        evolutionApiBaseUrl = root.str("evolutionApiBaseUrl") ?: root.str("evolution_api_base_url"),
                        evolutionApiKey = root.str("evolutionApiKey") ?: root.str("evolution_api_key"),
                        evolutionApiInstance = root.str("evolutionApiInstance") ?: root.str("evolution_api_instance"),
                        evolutionApiRecipientNumber = root.str("evolutionApiRecipientNumber") ?: root.str("evolution_recipient"),
                        evolutionApiNotificationsEnabled = (root["evolutionApiNotificationsEnabled"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: true,
                        googleDriveToken = root.str("googleDriveToken") ?: root.str("google_drive_token"),
                        googleDriveEmail = root.str("googleDriveEmail") ?: root.str("google_drive_email"),
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
        val baseUrl = getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/tasks/$encodedId")
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
     * Synchronizes credentials and auto-configures the VPS server directly
     * from the VM Project Hub plugin. If the VPS was already provisioned,
     * its credentials and endpoints are kept in sync with the Hub.
     */
    suspend fun syncHubCredentialsAndVps(): VmResult<HubCredentials> = withContext(ioDispatcher) {
        val credsRes = fetchCredentials()
        if (credsRes is VmResult.Failure) return@withContext credsRes
        val creds = (credsRes as VmResult.Success).value

        // 1. Auto-save GitHub Token to Keystore and Preferences
        val ghToken = creds.githubToken?.trim()?.takeIf { it.isNotBlank() }
        if (ghToken != null) {
            val secret = Secret.of(ghToken)
            try {
                val previous = preferencesRepository.preferences.first().githubTokenCredentialId
                when (val putRes = credentialStore.put(SecretType.GIT_HTTP_TOKEN, "Hub GitHub Token", secret)) {
                    is VmResult.Success -> {
                        preferencesRepository.setGithubConnection(putRes.value.id, null)
                        if (previous != null) {
                            credentialStore.delete(previous)
                        }
                    }
                    is VmResult.Failure -> {}
                }
            } finally {
                secret.wipe()
            }
        }

        // 2. Auto-save OmniRoute AI Key, Endpoint URL & Model
        val aiKey = creds.omnirouteKey?.trim()?.takeIf { it.isNotBlank() }
        if (aiKey != null) {
            val secret = Secret.of(aiKey)
            try {
                val previous = preferencesRepository.preferences.first().aiApiKeyCredentialId
                when (val putRes = credentialStore.put(SecretType.AI_API_KEY, "Hub OmniRoute Key", secret)) {
                    is VmResult.Success -> {
                        preferencesRepository.setAiApiKeyCredentialId(putRes.value.id)
                        if (previous != null) {
                            credentialStore.delete(previous)
                        }
                    }
                    is VmResult.Failure -> {}
                }
            } finally {
                secret.wipe()
            }
        }
        creds.defaultModel?.takeIf { it.isNotBlank() }?.let { model ->
            preferencesRepository.setAiModel(model, "custom")
            preferencesRepository.setLastChatModel(model)
        }

        // 3. Auto-save WhatsApp / Evolution API settings & Key
        val evoKey = creds.evolutionApiKey?.trim()?.takeIf { it.isNotBlank() }
        val evoBase = creds.evolutionApiBaseUrl?.takeIf { it.isNotBlank() } ?: "https://wa.vmstudio.digital"
        val evoInst = creds.evolutionApiInstance?.takeIf { it.isNotBlank() } ?: "main"
        val evoRecipient = creds.evolutionApiRecipientNumber.orEmpty()
        val evoNotify = creds.evolutionApiNotificationsEnabled ?: true

        preferencesRepository.setEvolutionApiSettings(
            baseUrl = evoBase,
            instance = evoInst,
            recipientNumber = evoRecipient,
            notificationsEnabled = evoNotify,
        )
        if (evoKey != null) {
            val secret = Secret.of(evoKey)
            try {
                val previous = preferencesRepository.preferences.first().evolutionApiKeyCredentialId
                when (val putRes = credentialStore.put(SecretType.GENERIC, "Hub Evolution API Key", secret)) {
                    is VmResult.Success -> {
                        preferencesRepository.setEvolutionApiKeyCredential(putRes.value.id)
                        if (previous != null) {
                            credentialStore.delete(previous)
                        }
                    }
                    is VmResult.Failure -> {}
                }
            } finally {
                secret.wipe()
            }
        }

        // 4. Auto-save Google Drive Token (webxprodigital@gmail.com)
        val gdriveToken = creds.googleDriveToken?.trim()?.takeIf { it.isNotBlank() }
        if (gdriveToken != null) {
            val secret = Secret.of(gdriveToken)
            try {
                val previous = preferencesRepository.preferences.first().googleDriveTokenCredentialId
                when (val putRes = credentialStore.put(SecretType.OAUTH_ACCESS_TOKEN, "Hub Google Drive Token", secret)) {
                    is VmResult.Success -> {
                        preferencesRepository.setGoogleDriveTokenCredential(putRes.value.id)
                        if (previous != null) {
                            credentialStore.delete(previous)
                        }
                    }
                    is VmResult.Failure -> {}
                }
            } finally {
                secret.wipe()
            }
        }

        // 5. Auto-configure Primary Cloud VPS Server
        val host = creds.vpsHost?.trim()?.takeIf { it.isNotBlank() }
        if (host != null) {
            val existing = serverRepository.servers.first()
            val match = existing.firstOrNull {
                it.host.equals(host, ignoreCase = true) ||
                    (creds.vpsName != null && it.name.equals(creds.vpsName, ignoreCase = true)) ||
                    it.name.contains("VM Studio", ignoreCase = true)
            }

            val authMethod = if (!creds.vpsPrivateKey.isNullOrBlank()) {
                SshAuthMethod.PRIVATE_KEY
            } else {
                SshAuthMethod.PASSWORD
            }
            val serverName = creds.vpsName?.takeIf { it.isNotBlank() } ?: "VM Studio Cloud VPS ($host)"

            val draft = ServerDraft(
                id = match?.id,
                name = serverName,
                host = host,
                port = (creds.vpsPort ?: 22).toString(),
                username = creds.vpsUser?.takeIf { it.isNotBlank() } ?: "root",
                authMethod = authMethod,
                password = creds.vpsPassword.orEmpty(),
                privateKeyPem = creds.vpsPrivateKey.orEmpty(),
                environment = ServerEnvironment.PRODUCTION,
            )

            val saveResult = serverRepository.save(draft)
            if (saveResult is VmResult.Success) {
                preferencesRepository.setAutoConnectServerId(saveResult.value.id)
            }
        }

        VmResult.Success(creds)
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
        val autoConnectId = preferencesRepository.preferences.first().autoConnectServerId
        val primaryServerId = autoConnectId?.let { id -> activeServers.firstOrNull { it.id == id }?.id }
            ?: activeServers.firstOrNull {
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

    /**
     * Uploads/syncs a conversation and all its messages to the VPS companion hub.
     */
    suspend fun syncChatToServer(conversationId: String): VmResult<Unit> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val conv = conversationDao.getById(conversationId)
            ?: return@withContext VmResult.Failure(VmError.NotFound("Conversation $conversationId not found locally"))

        val messages = messageDao.observeForConversation(conversationId).first()

        val convJson = buildJsonObject {
            put("id", conv.id)
            conv.projectId?.let { put("projectId", it) }
            put("title", conv.title)
            put("providerId", conv.providerId)
            conv.providerSessionId?.let { put("providerSessionId", it) }
            conv.serverId?.let { put("serverId", it) }
            conv.workingDirectory?.let { put("workingDirectory", it) }
            put("modelId", conv.modelId)
            put("promptTokens", conv.promptTokens)
            put("completionTokens", conv.completionTokens)
            put("isArchived", conv.isArchived)
            put("createdAtMillis", conv.createdAtMillis)
            put("updatedAtMillis", conv.updatedAtMillis)
        }

        val messagesJson = JsonArray(messages.map { m ->
            buildJsonObject {
                put("id", m.id)
                put("conversationId", m.conversationId)
                put("role", m.role.name)
                put("content", m.content)
                m.reasoning?.let { put("reasoning", it) }
                put("status", m.status.name)
                m.errorText?.let { put("errorText", it) }
                put("tokenCount", m.tokenCount)
                m.modelId?.let { put("modelId", it) }
                put("createdAtMillis", m.createdAtMillis)
                put("sequence", m.sequence)
            }
        })

        val payload = buildJsonObject {
            put("conversation", convJson)
            put("messages", messagesJson)
        }

        val baseUrl = getEffectiveHubBaseUrl()
        val request = Request.Builder()
            .url("$baseUrl/chats/sync")
            .header("X-VMPJ-Key", hubKey)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(Unit)
        }
    }

    /**
     * Downloads and restores all conversations from the VPS companion hub into Room SQLite.
     * Preserves locally modified conversations while hydrating missing ones after a fresh install.
     */
    suspend fun syncAllChatsFromServer(): VmResult<Int> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val baseUrl = getEffectiveHubBaseUrl()
        val listRequest = Request.Builder()
            .url("$baseUrl/chats")
            .header("X-VMPJ-Key", hubKey)
            .header("Accept", "application/json")
            .get()
            .build()

        val listResult = httpClient.execute(listRequest, RetryPolicy(maxAttempts = 2))
        if (listResult is VmResult.Failure) return@withContext listResult

        val jsonArray = runCatching {
            json.parseToJsonElement((listResult as VmResult.Success).value) as? JsonArray
        }.getOrNull() ?: return@withContext VmResult.Success(0)

        var restoredCount = 0

        for (item in jsonArray) {
            val convObj = item as? JsonObject ?: continue
            val convId = convObj.str("id") ?: continue
            val serverUpdatedAt = convObj.long("updatedAtMillis") ?: 0L

            val localConv = conversationDao.getById(convId)
            if (localConv == null || serverUpdatedAt > localConv.updatedAtMillis) {
                val detailRequest = Request.Builder()
                    .url("$HUB_BASE_URL/chats/$convId")
                    .header("X-VMPJ-Key", hubKey)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                when (val detailRes = httpClient.execute(detailRequest, RetryPolicy(maxAttempts = 2))) {
                    is VmResult.Failure -> continue
                    is VmResult.Success -> {
                        val root = runCatching { json.parseToJsonElement(detailRes.value) as? JsonObject }.getOrNull()
                            ?: continue
                        val cObj = root["conversation"] as? JsonObject ?: continue
                        val msgsArray = root["messages"] as? JsonArray ?: JsonArray(emptyList())

                        val entity = ConversationEntity(
                            id = cObj.str("id") ?: convId,
                            projectId = cObj.str("projectId"),
                            title = cObj.str("title") ?: "Chat",
                            providerId = cObj.str("providerId") ?: "omniroute",
                            providerSessionId = cObj.str("providerSessionId"),
                            serverId = cObj.str("serverId"),
                            workingDirectory = cObj.str("workingDirectory"),
                            modelId = cObj.str("modelId") ?: "",
                            promptTokens = cObj.long("promptTokens") ?: 0L,
                            completionTokens = cObj.long("completionTokens") ?: 0L,
                            isArchived = false,
                            createdAtMillis = cObj.long("createdAtMillis") ?: System.currentTimeMillis(),
                            updatedAtMillis = cObj.long("updatedAtMillis") ?: System.currentTimeMillis(),
                        )
                        conversationDao.upsert(entity)

                        for (mEl in msgsArray) {
                            val mObj = mEl as? JsonObject ?: continue
                            val mId = mObj.str("id") ?: continue
                            val roleStr = mObj.str("role") ?: "USER"
                            val role = try { MessageRole.valueOf(roleStr) } catch (_: Exception) { MessageRole.USER }
                            val statusStr = mObj.str("status") ?: "COMPLETE"
                            val status = try { MessageStatus.valueOf(statusStr) } catch (_: Exception) { MessageStatus.COMPLETE }

                            val msgEntity = MessageEntity(
                                id = mId,
                                conversationId = convId,
                                role = role,
                                content = mObj.str("content") ?: "",
                                reasoning = mObj.str("reasoning"),
                                status = status,
                                errorText = mObj.str("errorText"),
                                tokenCount = mObj.int("tokenCount") ?: 0,
                                modelId = mObj.str("modelId"),
                                createdAtMillis = mObj.long("createdAtMillis") ?: System.currentTimeMillis(),
                                sequence = mObj.long("sequence") ?: 0L,
                            )
                            messageDao.upsert(msgEntity)
                        }
                        restoredCount++
                    }
                }
            }
        }

        VmResult.Success(restoredCount)
    }

    /**
     * Deletes a conversation on the VPS companion hub.
     */
    suspend fun deleteChatFromServer(conversationId: String): VmResult<Unit> = withContext(ioDispatcher) {
        val hubKey = getEffectiveHubKey()
            ?: return@withContext VmResult.Failure(VmError.Authentication("Device is not paired with VM Project Hub. Enter your Hub Pairing Key in Connectors."))

        val request = Request.Builder()
            .url("$HUB_BASE_URL/chats/$conversationId")
            .header("X-VMPJ-Key", hubKey)
            .delete()
            .build()

        when (val result = httpClient.execute(request, RetryPolicy(maxAttempts = 2))) {
            is VmResult.Failure -> result
            is VmResult.Success -> VmResult.Success(Unit)
        }
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
        private const val DEFAULT_HUB_BASE_URL = "https://vmstudio.digital/wp-json/xcodes/v1"
private const val SAKET_DHAM_HUB_BASE_URL = "https://saketdhamashram.org/wp-json/xcodes/v1"
const val HUB_BASE_URL = "https://saketdhamashram.org/wp-json/xcodes/v1"
        private const val DEFAULT_HUB_BASE_URL = "https://saketdhamashram.org/wp-json/xcodes/v1"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeUnless { it == "null" }

private fun JsonObject.int(key: String): Int? {
    val raw = (this[key] as? JsonPrimitive)?.content ?: return null
    return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
}

private fun JsonObject.long(key: String): Long? {
    val raw = (this[key] as? JsonPrimitive)?.content ?: return null
    return raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
}
