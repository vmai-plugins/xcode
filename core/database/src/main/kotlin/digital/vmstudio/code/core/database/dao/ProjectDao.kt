package digital.vmstudio.code.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import digital.vmstudio.code.core.database.entity.FileCacheEntity
import digital.vmstudio.code.core.database.entity.ProjectCommandEntity
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectEnvVarEntity
import digital.vmstudio.code.core.database.entity.ProjectInstructionEntity
import digital.vmstudio.code.core.database.entity.ProjectInstructionScope
import digital.vmstudio.code.core.database.entity.RecentFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM project ORDER BY isFavorite DESC, lastOpenedAtMillis DESC, name ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM project WHERE id = :id")
    fun observeById(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM project WHERE id = :id")
    suspend fun getById(id: String): ProjectEntity?

    @Query("SELECT * FROM project WHERE serverId = :serverId ORDER BY name ASC")
    fun observeByServer(serverId: String): Flow<List<ProjectEntity>>

    @Query("SELECT COUNT(*) FROM project WHERE serverId = :serverId")
    fun observeCountForServer(serverId: String): Flow<Int>

    @Query(
        "SELECT * FROM project WHERE lastOpenedAtMillis > 0 ORDER BY lastOpenedAtMillis DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<ProjectEntity>>

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("DELETE FROM project WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE project SET lastOpenedAtMillis = :timestamp WHERE id = :id")
    suspend fun markOpened(id: String, timestamp: Long)

    @Query("UPDATE project SET branch = :branch, updatedAtMillis = :timestamp WHERE id = :id")
    suspend fun updateBranch(id: String, branch: String?, timestamp: Long)

    @Query("UPDATE project SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
}

@Dao
interface ProjectCommandDao {

    @Query("SELECT * FROM project_command WHERE projectId = :projectId ORDER BY sortOrder ASC")
    fun observeForProject(projectId: String): Flow<List<ProjectCommandEntity>>

    @Query("SELECT * FROM project_command WHERE projectId = :projectId ORDER BY sortOrder ASC")
    suspend fun getForProject(projectId: String): List<ProjectCommandEntity>

    @Upsert
    suspend fun upsert(command: ProjectCommandEntity)

    @Upsert
    suspend fun upsertAll(commands: List<ProjectCommandEntity>)

    @Query("DELETE FROM project_command WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProjectInstructionDao {

    /**
     * Returns global instructions plus the project's own, ordered so that
     * more specific scopes are applied last when the agent assembles its prompt.
     */
    @Query(
        """
        SELECT * FROM project_instruction
        WHERE isEnabled = 1 AND (projectId = :projectId OR scope = :globalScope)
        ORDER BY scope ASC, path ASC
        """,
    )
    suspend fun getEffectiveFor(
        projectId: String,
        globalScope: ProjectInstructionScope = ProjectInstructionScope.GLOBAL,
    ): List<ProjectInstructionEntity>

    @Query("SELECT * FROM project_instruction WHERE projectId = :projectId ORDER BY scope ASC")
    fun observeForProject(projectId: String): Flow<List<ProjectInstructionEntity>>

    @Upsert
    suspend fun upsert(instruction: ProjectInstructionEntity)

    @Query("DELETE FROM project_instruction WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProjectEnvVarDao {

    @Query("SELECT * FROM project_env_var WHERE projectId = :projectId ORDER BY name ASC")
    fun observeForProject(projectId: String): Flow<List<ProjectEnvVarEntity>>

    @Query("SELECT * FROM project_env_var WHERE projectId = :projectId ORDER BY name ASC")
    suspend fun getForProject(projectId: String): List<ProjectEnvVarEntity>

    @Upsert
    suspend fun upsertAll(vars: List<ProjectEnvVarEntity>)

    @Query("DELETE FROM project_env_var WHERE projectId = :projectId")
    suspend fun clearForProject(projectId: String)
}

@Dao
interface RecentFileDao {

    @Query(
        "SELECT * FROM recent_file WHERE projectId = :projectId ORDER BY isPinned DESC, lastOpenedAtMillis DESC LIMIT :limit",
    )
    fun observeForProject(projectId: String, limit: Int = 50): Flow<List<RecentFileEntity>>

    @Upsert
    suspend fun upsert(file: RecentFileEntity)

    @Query("DELETE FROM recent_file WHERE projectId = :projectId AND path = :path")
    suspend fun remove(projectId: String, path: String)

    /** Keeps the list bounded; pinned entries are never trimmed. */
    @Query(
        """
        DELETE FROM recent_file
        WHERE projectId = :projectId AND isPinned = 0 AND id NOT IN (
            SELECT id FROM recent_file
            WHERE projectId = :projectId AND isPinned = 0
            ORDER BY lastOpenedAtMillis DESC LIMIT :keep
        )
        """,
    )
    suspend fun trim(projectId: String, keep: Int)
}

@Dao
interface FileCacheDao {

    @Query("SELECT * FROM file_cache WHERE projectId = :projectId AND remotePath = :remotePath")
    suspend fun find(projectId: String, remotePath: String): FileCacheEntity?

    @Query("SELECT * FROM file_cache WHERE projectId = :projectId AND hasLocalEdits = 1")
    fun observeDirty(projectId: String): Flow<List<FileCacheEntity>>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM file_cache")
    fun observeTotalBytes(): Flow<Long>

    @Upsert
    suspend fun upsert(entry: FileCacheEntity)

    @Query("UPDATE file_cache SET lastAccessedAtMillis = :timestamp WHERE id = :id")
    suspend fun touch(id: String, timestamp: Long)

    @Query("DELETE FROM file_cache WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM file_cache WHERE projectId = :projectId")
    suspend fun clearForProject(projectId: String)

    /**
     * Selects least-recently-used clean entries for eviction. Dirty entries are
     * excluded: evicting one would silently discard the user's unsaved edits.
     */
    @Query(
        """
        SELECT * FROM file_cache
        WHERE hasLocalEdits = 0
        ORDER BY lastAccessedAtMillis ASC
        LIMIT :limit
        """,
    )
    suspend fun evictionCandidates(limit: Int): List<FileCacheEntity>
}
