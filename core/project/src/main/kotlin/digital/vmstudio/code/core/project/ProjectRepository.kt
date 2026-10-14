package digital.vmstudio.code.core.project

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.dao.ServerDao
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectLocation
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A project: a named working directory on a server.
 *
 * The remote path is the point of the type. Before projects existed, the agent took a
 * directory typed by hand on every run — tedious, and the way a typo silently scopes
 * an agent to the wrong tree.
 */
data class Project(
    val id: String,
    val name: String,
    val description: String,
    val serverId: String?,
    val remotePath: String,
    val projectTypeId: String,
    val isFavorite: Boolean,
    val lastOpenedAtMillis: Long,
    val branch: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    val runtime: String? = null,
    val packageManager: String? = null,
    val colorHex: String? = null,
    val location: ProjectLocation = ProjectLocation.LOCAL,
) {
    val isRemote: Boolean get() = serverId != null && serverId != "local"
    val isLocal: Boolean get() = !isRemote
}

interface ProjectRepository {

    fun observeAll(): Flow<List<Project>>

    fun observeForServer(serverId: String): Flow<List<Project>>

    suspend fun get(id: String): Project?

    @Suppress("LongParameterList")
    suspend fun create(
        name: String,
        serverId: String?,
        remotePath: String,
        description: String = "",
        projectTypeId: String = DEFAULT_TYPE,
        branch: String? = null,
        environment: ServerEnvironment = ServerEnvironment.DEVELOPMENT,
        runtime: String? = null,
        packageManager: String? = null,
    ): VmResult<String>

    suspend fun markOpened(id: String): VmResult<Unit>

    suspend fun setFavorite(id: String, favorite: Boolean): VmResult<Unit>

    suspend fun updateColor(id: String, colorHex: String?): VmResult<Unit>

    suspend fun update(project: Project): VmResult<Unit>

    suspend fun delete(id: String): VmResult<Unit>

    companion object {
        const val DEFAULT_TYPE = "generic"
    }
}

@Singleton
class DefaultProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val serverDao: ServerDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProjectRepository {

    override fun observeAll(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toProject() } }

    override fun observeForServer(serverId: String): Flow<List<Project>> =
        projectDao.observeByServer(serverId).map { list -> list.map { it.toProject() } }

    override suspend fun get(id: String): Project? = withContext(ioDispatcher) {
        projectDao.getById(id)?.toProject()
    }

    @Suppress("LongParameterList")
    override suspend fun create(
        name: String,
        serverId: String?,
        remotePath: String,
        description: String,
        projectTypeId: String,
        branch: String?,
        environment: ServerEnvironment,
        runtime: String?,
        packageManager: String?,
    ): VmResult<String> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        val trimmedPath = remotePath.trim()

        if (trimmedName.isEmpty()) {
            return@withContext VmResult.Failure(
                VmError.Validation(summary = "The project needs a name", fieldName = "name"),
            )
        }

        val isLocal = serverId == null ||
            serverId == "local" ||
            serverId == "device" ||
            serverId == "github" ||
            serverId == "vmhub" ||
            trimmedPath.startsWith("/vmhub/") ||
            trimmedPath.startsWith("/github/")
        // SAF workspaces use a persisted content URI rather than a filesystem path.
        // Remote paths still must be absolute because they become the agent's scope.
        if (!trimmedPath.startsWith("/") && trimmedPath != "~" && !trimmedPath.startsWith("~/") &&
            !(isLocal && ProjectPath.isTreeUri(trimmedPath))
        ) {
            return@withContext VmResult.Failure(
                VmError.Validation(
                    summary = "The workspace path must be absolute or a selected phone folder",
                    fieldName = "remotePath",
                    reason = "A relative path resolves against an unpredictable working directory.",
                    suggestedAction = "Start it with \"/\", for example /sdcard/VMStudio or /root/myapp.",
                ),
            )
        }

        val storedServerId = if (isLocal) null else {
            serverId?.takeIf { serverDao.getById(it) != null }
        }
        val location = if (storedServerId != null) ProjectLocation.REMOTE_SSH else ProjectLocation.LOCAL
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        vmCatching(::mapError) {
            projectDao.upsert(
                ProjectEntity(
                    id = id,
                    name = trimmedName,
                    description = description.trim(),
                    location = location,
                    serverId = storedServerId,
                    remotePath = ProjectPath.normalise(trimmedPath),
                    localTreeUri = if (isLocal) ProjectPath.normalise(trimmedPath) else null,
                    projectTypeId = projectTypeId,
                    branch = branch?.trim()?.takeIf { it.isNotBlank() },
                    environment = environment,
                    runtime = runtime?.trim()?.takeIf { it.isNotBlank() },
                    packageManager = packageManager?.trim()?.takeIf { it.isNotBlank() },
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
            id
        }
    }

    override suspend fun markOpened(id: String): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) { projectDao.markOpened(id, System.currentTimeMillis()) }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapError) { projectDao.setFavorite(id, favorite) }
        }

    override suspend fun updateColor(id: String, colorHex: String?): VmResult<Unit> =
        withContext(ioDispatcher) {
            vmCatching(::mapError) { projectDao.updateColor(id, colorHex, System.currentTimeMillis()) }
        }

    override suspend fun update(project: Project): VmResult<Unit> = withContext(ioDispatcher) {
        val existing = projectDao.getById(project.id) ?: return@withContext VmResult.Failure(
            VmError.Validation(summary = "Project not found", fieldName = "id"),
        )
        val isLocal = project.serverId == null ||
            project.serverId == "local" ||
            project.serverId == "device" ||
            project.serverId == "github" ||
            project.serverId == "vmhub" ||
            project.remotePath.startsWith("/vmhub/") ||
            project.remotePath.startsWith("/github/")
        val storedServerId = if (isLocal) null else {
            project.serverId?.takeIf { serverDao.getById(it) != null }
        }
        val location = if (storedServerId != null) ProjectLocation.REMOTE_SSH else ProjectLocation.LOCAL
        vmCatching(::mapError) {
            projectDao.upsert(
                existing.copy(
                    name = project.name.trim(),
                    description = project.description.trim(),
                    location = location,
                    serverId = storedServerId,
                    remotePath = ProjectPath.normalise(project.remotePath.trim()),
                    localTreeUri = if (isLocal) ProjectPath.normalise(project.remotePath.trim()) else existing.localTreeUri,
                    projectTypeId = project.projectTypeId,
                    branch = project.branch?.trim()?.takeIf { it.isNotBlank() },
                    environment = project.environment,
                    runtime = project.runtime?.trim()?.takeIf { it.isNotBlank() },
                    packageManager = project.packageManager?.trim()?.takeIf { it.isNotBlank() },
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun delete(id: String): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) { projectDao.deleteById(id) }
    }

    private fun ProjectEntity.toProject() = Project(
        id = id,
        name = name,
        description = description,
        serverId = serverId ?: if (location == ProjectLocation.LOCAL) "local" else null,
        remotePath = remotePath ?: localTreeUri ?: "/",
        projectTypeId = projectTypeId,
        isFavorite = isFavorite,
        lastOpenedAtMillis = lastOpenedAtMillis,
        branch = branch,
        environment = environment,
        runtime = runtime,
        packageManager = packageManager,
        colorHex = colorHex,
        location = location,
    )

    private fun mapError(throwable: Throwable) = VmError.Storage(
        summary = "Could not save the project",
        reason = throwable.message,
        suggestedAction = "Retry; if it persists, check available storage.",
        cause = throwable,
    )
}

internal object ProjectPath {
    private const val TREE_URI_SEPARATOR = "!/"

    fun isTreeUri(path: String): Boolean =
        path.startsWith("content://") && path.contains(TREE_URI_SEPARATOR)

    /** Trims filesystem separators without ever removing SAF's required `!/` marker. */
    fun normalise(path: String): String {
        if (!isTreeUri(path)) return path.trimEnd('/').ifEmpty { "/" }
        val separator = path.indexOf(TREE_URI_SEPARATOR)
        val tree = path.substring(0, separator + TREE_URI_SEPARATOR.length)
        val relative = path.substring(separator + TREE_URI_SEPARATOR.length).trim('/')
        return if (relative.isEmpty()) tree else tree + relative
    }
}
