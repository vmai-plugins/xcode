package digital.vmstudio.code.core.project

import digital.vmstudio.code.core.common.dispatcher.IoDispatcher
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectLocation
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
) {
    val isRemote: Boolean get() = serverId != null
}

interface ProjectRepository {

    fun observeAll(): Flow<List<Project>>

    fun observeForServer(serverId: String): Flow<List<Project>>

    suspend fun get(id: String): Project?

    suspend fun create(
        name: String,
        serverId: String,
        remotePath: String,
        description: String = "",
        projectTypeId: String = DEFAULT_TYPE,
    ): VmResult<String>

    suspend fun markOpened(id: String): VmResult<Unit>

    suspend fun setFavorite(id: String, favorite: Boolean): VmResult<Unit>

    suspend fun delete(id: String): VmResult<Unit>

    companion object {
        const val DEFAULT_TYPE = "generic"
    }
}

@Singleton
class DefaultProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProjectRepository {

    override fun observeAll(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toProject() } }

    override fun observeForServer(serverId: String): Flow<List<Project>> =
        projectDao.observeByServer(serverId).map { list -> list.map { it.toProject() } }

    override suspend fun get(id: String): Project? = withContext(ioDispatcher) {
        projectDao.getById(id)?.toProject()
    }

    override suspend fun create(
        name: String,
        serverId: String,
        remotePath: String,
        description: String,
        projectTypeId: String,
    ): VmResult<String> = withContext(ioDispatcher) {
        val trimmedName = name.trim()
        val trimmedPath = remotePath.trim()

        if (trimmedName.isEmpty()) {
            return@withContext VmResult.Failure(
                VmError.Validation(summary = "The project needs a name", fieldName = "name"),
            )
        }

        // Validated here rather than only in the UI: this path becomes the agent's
        // filesystem scope, and a relative one would resolve against whatever
        // directory a non-interactive shell happens to start in.
        if (!trimmedPath.startsWith("/")) {
            return@withContext VmResult.Failure(
                VmError.Validation(
                    summary = "The path must be absolute",
                    fieldName = "remotePath",
                    reason = "A relative path resolves against an unpredictable working " +
                        "directory on the server.",
                    suggestedAction = "Start it with \"/\", for example /root/myapp.",
                ),
            )
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        vmCatching(::mapError) {
            projectDao.upsert(
                ProjectEntity(
                    id = id,
                    name = trimmedName,
                    description = description.trim(),
                    location = ProjectLocation.REMOTE_SSH,
                    serverId = serverId,
                    remotePath = normalisePath(trimmedPath),
                    projectTypeId = projectTypeId,
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

    override suspend fun delete(id: String): VmResult<Unit> = withContext(ioDispatcher) {
        vmCatching(::mapError) { projectDao.deleteById(id) }
    }

    /** Strips a trailing separator, but never turns the root into an empty string. */
    private fun normalisePath(path: String): String =
        path.trimEnd('/').ifEmpty { "/" }

    private fun ProjectEntity.toProject() = Project(
        id = id,
        name = name,
        description = description,
        serverId = serverId,
        // Remote projects always carry a path; the column is nullable only because
        // local (SAF) projects use a tree URI instead.
        remotePath = remotePath ?: "/",
        projectTypeId = projectTypeId,
        isFavorite = isFavorite,
        lastOpenedAtMillis = lastOpenedAtMillis,
    )

    private fun mapError(throwable: Throwable) = VmError.Storage(
        summary = "Could not save the project",
        reason = throwable.message,
        suggestedAction = "Retry; if it persists, check available storage.",
        cause = throwable,
    )
}
