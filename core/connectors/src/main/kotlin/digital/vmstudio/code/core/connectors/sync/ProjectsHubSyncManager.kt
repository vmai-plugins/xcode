package digital.vmstudio.code.core.connectors.sync

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.asSuccess
import digital.vmstudio.code.core.connectors.api.ProjectsHubClient
import digital.vmstudio.code.core.connectors.model.HubProject
import digital.vmstudio.code.core.connectors.model.HubTask
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.dao.ServerDao
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectLocation
import digital.vmstudio.code.core.database.entity.ServerEntity
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.database.entity.TaskPriority
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class HubSyncStats(
    val projectsSynced: Int = 0,
    val tasksSynced: Int = 0,
    val serversConfigured: Int = 0,
)

data class HubSyncState(
    val isSyncing: Boolean = false,
    val lastSyncedMillis: Long = 0L,
    val lastError: String? = null,
    val stats: HubSyncStats = HubSyncStats(),
)

/**
 * Bi-directional reconciler and synchronizer between the WordPress VM Project Hub
 * API (`https://vmstudio.digital/wp-json/xcodes/v1`) and the local Room database.
 *
 * Ensures:
 * 1. Default VPS profiles (VPS 1 Web/Hub and VPS 2 Android Builder) are provisioned.
 * 2. Remote projects from the Hub are mapped into [ProjectEntity] records.
 * 3. Remote project tasks are mapped into [AgentTaskEntity] records for AI agents.
 */
@Singleton
class ProjectsHubSyncManager @Inject constructor(
    private val hubClient: ProjectsHubClient,
    private val projectDao: ProjectDao,
    private val serverDao: ServerDao,
    private val agentTaskDao: AgentTaskDao,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _syncState = MutableStateFlow(HubSyncState())
    val syncState: StateFlow<HubSyncState> = _syncState.asStateFlow()

    /**
     * Seeds the standard ecosystem VPS profiles if they do not yet exist in the database.
     */
    suspend fun ensureDefaultServers(): Int = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        var created = 0

        if (serverDao.getById(SERVER_VPS1_ID) == null) {
            val vps1 = ServerEntity(
                id = SERVER_VPS1_ID,
                name = "VPS 1 (Production & Hub)",
                host = "31.97.63.239",
                port = 22,
                username = "root",
                authMethod = SshAuthMethod.PASSWORD,
                environment = ServerEnvironment.PRODUCTION,
                defaultDirectory = "/home/vmstudio.digital/public_html",
                colorHex = "#10B981", // Emerald
                notes = "VMStudio Main Production Web Server & VM Project Hub",
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            serverDao.upsert(vps1)
            created++
            VmLog.i(LogCategory.CONNECTOR, TAG, "Provisioned default VPS 1 entity")
        }

        if (serverDao.getById(SERVER_VPS2_ID) == null) {
            val vps2 = ServerEntity(
                id = SERVER_VPS2_ID,
                name = "VPS 2 (Build Agent & Dev)",
                host = "200.234.41.231",
                port = 22,
                username = "root",
                authMethod = SshAuthMethod.PASSWORD,
                environment = ServerEnvironment.DEVELOPMENT,
                defaultDirectory = "/opt/vmstudio/repos",
                colorHex = "#3B82F6", // Royal Blue
                notes = "Dedicated Android Dev, CI/CD Autopilot Builder, and AI Agent VPS",
                createdAtMillis = now,
                updatedAtMillis = now,
            )
            serverDao.upsert(vps2)
            created++
            VmLog.i(LogCategory.CONNECTOR, TAG, "Provisioned default VPS 2 entity")
        }

        created
    }

    /**
     * Performs a complete synchronization run:
     * - Ensures VPS server profiles are present.
     * - Fetches all projects from VM Project Hub REST API.
     * - Upserts projects into [ProjectDao].
     * - Upserts all child tasks into [AgentTaskDao].
     */
    suspend fun syncAll(): VmResult<HubSyncStats> = withContext(ioDispatcher) {
        _syncState.update { it.copy(isSyncing = true, lastError = null) }
        VmLog.i(LogCategory.CONNECTOR, TAG, "Starting full synchronization with VM Project Hub...")

        try {
            val serversConfigured = ensureDefaultServers()

            val projectsResult = hubClient.getProjects()
            val hubProjects = when (projectsResult) {
                is VmResult.Success -> projectsResult.data
                is VmResult.Failure -> {
                    val err = projectsResult.error.message
                    _syncState.update { it.copy(isSyncing = false, lastError = err) }
                    return@withContext projectsResult
                }
            }

            val now = System.currentTimeMillis()
            var projectCount = 0
            var taskCount = 0

            for (hubProj in hubProjects) {
                val entity = mapHubProjectToEntity(hubProj, now)
                projectDao.upsert(entity)
                projectCount++

                // Sync nested tasks if present in project payload
                for (task in hubProj.tasks) {
                    val taskEntity = mapHubTaskToEntity(task, entity.id, now)
                    agentTaskDao.upsert(taskEntity)
                    taskCount++
                }
            }

            // Also check global tasks endpoint for any tasks not nested under projects
            val tasksResult = hubClient.getTasks()
            if (tasksResult is VmResult.Success) {
                for (task in tasksResult.data) {
                    val targetProjId = task.project_id?.let { "hub_project_$it" }
                    if (targetProjId != null) {
                        val taskEntity = mapHubTaskToEntity(task, targetProjId, now)
                        agentTaskDao.upsert(taskEntity)
                        taskCount++
                    }
                }
            }

            val stats = HubSyncStats(
                projectsSynced = projectCount,
                tasksSynced = taskCount,
                serversConfigured = serversConfigured,
            )

            _syncState.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedMillis = now,
                    lastError = null,
                    stats = stats,
                )
            }

            VmLog.i(LogCategory.CONNECTOR, TAG, "Sync complete: $projectCount projects, $taskCount tasks.")
            stats.asSuccess()
        } catch (t: Throwable) {
            val msg = t.message ?: "Unknown sync failure"
            VmLog.e(LogCategory.CONNECTOR, TAG, "Sync failed: $msg", t)
            _syncState.update { it.copy(isSyncing = false, lastError = msg) }
            VmResult.Failure(
                VmError.Unexpected(
                    summary = "Failed to sync with VM Project Hub",
                    reason = msg,
                    cause = t,
                )
            )
        }
    }

    private fun mapHubProjectToEntity(hub: HubProject, now: Long): ProjectEntity {
        val serverId = when {
            hub.workingDirectory.contains("/opt/vmstudio") ||
            hub.slug?.contains("xcode", ignoreCase = true) == true ||
            hub.name?.contains("x-code", ignoreCase = true) == true ||
            hub.stack?.contains("android", ignoreCase = true) == true -> SERVER_VPS2_ID

            else -> SERVER_VPS1_ID
        }

        val env = when (hub.environment?.lowercase()) {
            "production", "prod" -> ServerEnvironment.PRODUCTION
            "staging", "stage" -> ServerEnvironment.STAGING
            "development", "dev" -> ServerEnvironment.DEVELOPMENT
            else -> if (serverId == SERVER_VPS1_ID) ServerEnvironment.PRODUCTION else ServerEnvironment.DEVELOPMENT
        }

        val projectTypeId = when {
            hub.stack?.contains("android", ignoreCase = true) == true -> "android"
            hub.stack?.contains("kotlin", ignoreCase = true) == true -> "kotlin"
            hub.stack?.contains("wordpress", ignoreCase = true) == true || hub.stack?.contains("php", ignoreCase = true) == true -> "wordpress"
            hub.stack?.contains("react", ignoreCase = true) == true || hub.stack?.contains("node", ignoreCase = true) == true -> "nodejs"
            hub.stack?.contains("python", ignoreCase = true) == true -> "python"
            else -> "generic"
        }

        val techList = listOfNotNull(hub.stack)
            .flatMap { it.split(",", ";", " ").filter { s -> s.isNotBlank() } }
        val techStackJson = json.encodeToString(techList)

        val colorHex = when (serverId) {
            SERVER_VPS1_ID -> "#10B981"
            SERVER_VPS2_ID -> "#3B82F6"
            else -> "#8B5CF6"
        }

        return ProjectEntity(
            id = "hub_project_${hub.id}",
            name = hub.displayName,
            description = hub.slug ?: "",
            location = ProjectLocation.REMOTE_SSH,
            serverId = serverId,
            remotePath = hub.workingDirectory.ifBlank { null },
            localTreeUri = null,
            projectTypeId = projectTypeId,
            branch = "main",
            environment = env,
            techStackJson = techStackJson,
            colorHex = colorHex,
            isFavorite = false,
            createdAtMillis = now,
            updatedAtMillis = now,
            lastOpenedAtMillis = 0L,
        )
    }

    private fun mapHubTaskToEntity(task: HubTask, projectId: String, now: Long): AgentTaskEntity {
        val status = when (task.status?.lowercase()) {
            "completed", "done", "closed" -> AgentTaskStatus.COMPLETED
            "running", "in_progress", "in-progress", "active" -> AgentTaskStatus.RUNNING
            "planning" -> AgentTaskStatus.PLANNING
            "testing" -> AgentTaskStatus.TESTING
            "failed" -> AgentTaskStatus.FAILED
            "cancelled" -> AgentTaskStatus.CANCELLED
            else -> AgentTaskStatus.TODO
        }

        val priority = when (task.priority?.lowercase()) {
            "urgent", "critical" -> TaskPriority.URGENT
            "high" -> TaskPriority.HIGH
            "low" -> TaskPriority.LOW
            else -> TaskPriority.NORMAL
        }

        return AgentTaskEntity(
            id = "hub_task_${task.id}",
            projectId = projectId,
            conversationId = null,
            title = task.title ?: "Task #${task.id}",
            description = "Status: ${task.status ?: "pending"} | Due: ${task.due_date ?: "none"}",
            status = status,
            priority = priority,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    companion object {
        private const val TAG = "ProjectsHubSync"
        const val SERVER_VPS1_ID = "vps-1-production"
        const val SERVER_VPS2_ID = "vps-2-build-agent"
    }
}
