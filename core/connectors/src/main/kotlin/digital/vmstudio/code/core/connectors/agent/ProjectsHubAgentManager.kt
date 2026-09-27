package digital.vmstudio.code.core.connectors.agent

import digital.vmstudio.code.core.ai.background.BackgroundAgentRunner
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.asSuccess
import digital.vmstudio.code.core.common.result.map
import digital.vmstudio.code.core.connectors.api.ProjectsHubClient
import digital.vmstudio.code.core.connectors.model.HubGrowthRunResponse
import digital.vmstudio.code.core.connectors.sync.ProjectsHubSyncManager
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates multi-project autonomous audits, task generation, and background
 * Claude Code task execution across the VM Studio ecosystem.
 */
@Singleton
class ProjectsHubAgentManager @Inject constructor(
    private val hubClient: ProjectsHubClient,
    private val syncManager: ProjectsHubSyncManager,
    private val projectDao: ProjectDao,
    private val agentTaskDao: AgentTaskDao,
    private val backgroundRunner: BackgroundAgentRunner,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Triggers an autonomous codebase audit and growth plan generation on VM Project Hub
     * for a specific project.
     *
     * @param projectEntityId ID in Room (e.g. "hub_project_12" or "12")
     * @param instructions Optional custom audit focus (e.g. "Security, SEO, performance, UI")
     */
    suspend fun auditProject(
        projectEntityId: String,
        instructions: String? = null,
    ): VmResult<HubGrowthRunResponse> = withContext(ioDispatcher) {
        val numericId = parseHubNumericId(projectEntityId)
            ?: return@withContext VmResult.Failure(
                VmError.Validation(
                    field = "projectId",
                    reason = "Cannot extract numeric Hub ID from $projectEntityId",
                )
            )

        val defaultPrompt = instructions
            ?: "Perform a comprehensive repository audit: code quality, security scan, dependencies, and actionable growth backlog."

        VmLog.i(LogCategory.AI, TAG, "Triggering growth audit for project #$numericId")
        val result = hubClient.triggerGrowthRun(numericId, defaultPrompt)

        // Resync to pick up newly spawned tasks and job records
        syncManager.syncAll()

        result
    }

    /**
     * Executes an agent task in the background on the remote server hosting the project.
     * Starts a detached Claude Code session and monitors progress.
     */
    suspend fun executeTaskInBackground(
        taskId: String,
        permissionMode: AgentPermissionMode = AgentPermissionMode.PLAN,
    ): VmResult<String> = withContext(ioDispatcher) {
        val task = agentTaskDao.getById(taskId)
            ?: return@withContext VmResult.Failure(
                VmError.NotFound(
                    summary = "Task not found",
                    entityId = taskId,
                    entityType = "AgentTask",
                )
            )

        val project = projectDao.getById(task.projectId)
            ?: return@withContext VmResult.Failure(
                VmError.NotFound(
                    summary = "Project not found for task",
                    entityId = task.projectId,
                    entityType = "Project",
                )
            )

        val serverId = project.serverId
            ?: return@withContext VmResult.Failure(
                VmError.Validation(
                    field = "serverId",
                    reason = "Project ${project.name} has no remote server assigned",
                )
            )

        val workDir = project.remotePath
            ?: return@withContext VmResult.Failure(
                VmError.Validation(
                    field = "remotePath",
                    reason = "Project ${project.name} has no remote directory specified",
                )
            )

        val prompt = "Execute task: ${task.title}. Details: ${task.description}"
        val now = System.currentTimeMillis()

        agentTaskDao.updateStatus(taskId, AgentTaskStatus.RUNNING, now)
        VmLog.i(LogCategory.AI, TAG, "Starting background agent for task $taskId on $serverId ($workDir)")

        val startResult = backgroundRunner.start(
            serverId = serverId,
            workingDirectory = workDir,
            prompt = prompt,
            permissionMode = permissionMode,
        )

        when (startResult) {
            is VmResult.Success -> {
                VmLog.i(LogCategory.AI, TAG, "Agent run started successfully: ${startResult.data}")
                startResult
            }
            is VmResult.Failure -> {
                agentTaskDao.updateStatus(taskId, AgentTaskStatus.FAILED, System.currentTimeMillis())
                startResult
            }
        }
    }

    /**
     * Audits all active projects synchronized from VM Project Hub sequentially.
     */
    suspend fun auditAllProjects(): VmResult<Int> = withContext(ioDispatcher) {
        val syncResult = syncManager.syncAll()
        if (syncResult is VmResult.Failure) return@withContext syncResult.map { 0 }

        // Trigger audits across Hub projects
        val projects = hubClient.getProjects()
        var audited = 0
        if (projects is VmResult.Success) {
            for (p in projects.data) {
                if (p.id > 0) {
                    hubClient.triggerGrowthRun(p.id, "Autopilot scheduled project audit and growth evaluation")
                    audited++
                }
            }
        }

        audited.asSuccess()
    }

    private fun parseHubNumericId(id: String): Int? {
        val cleaned = id.removePrefix("hub_project_").trim()
        return cleaned.toIntOrNull()
    }

    companion object {
        private const val TAG = "ProjectsHubAgent"
    }
}
