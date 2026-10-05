package digital.vmstudio.code.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.database.entity.ConnectorEntity
import digital.vmstudio.code.core.database.entity.ConnectorStatus
import digital.vmstudio.code.core.database.entity.CredentialReferenceEntity
import digital.vmstudio.code.core.database.entity.GitRepositoryEntity
import digital.vmstudio.code.core.database.entity.TransferEntity
import digital.vmstudio.code.core.database.entity.TransferStatus
import digital.vmstudio.code.core.database.entity.WorkspaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GitRepositoryDao {

    @Query("SELECT * FROM git_repository WHERE projectId = :projectId")
    fun observeForProject(projectId: String): Flow<GitRepositoryEntity?>

    @Query("SELECT * FROM git_repository WHERE projectId = :projectId")
    suspend fun getForProject(projectId: String): GitRepositoryEntity?

    @Upsert
    suspend fun upsert(repository: GitRepositoryEntity)

    @Query("DELETE FROM git_repository WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}

@Dao
interface ConnectorDao {

    @Query("SELECT * FROM connector ORDER BY displayName ASC")
    fun observeAll(): Flow<List<ConnectorEntity>>

    @Query("SELECT * FROM connector WHERE id = :id")
    suspend fun getById(id: String): ConnectorEntity?

    @Upsert
    suspend fun upsert(connector: ConnectorEntity)

    @Query("UPDATE connector SET status = :status, lastErrorText = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: ConnectorStatus, error: String?)

    @Query("DELETE FROM connector WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfer ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer WHERE status IN (:statuses) ORDER BY createdAtMillis ASC")
    fun observeByStatus(statuses: List<TransferStatus>): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfer WHERE id = :id")
    suspend fun getById(id: String): TransferEntity?

    @Upsert
    suspend fun upsert(transfer: TransferEntity)

    @Query(
        """
        UPDATE transfer
        SET status = :status, errorSummary = :error, resumeOffsetBytes = :resumeOffset,
            updatedAtMillis = :timestamp
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: String,
        status: TransferStatus,
        error: String?,
        resumeOffset: Long,
        timestamp: Long,
    )

    @Query("DELETE FROM transfer WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * A RUNNING transfer cannot survive process death; it is reset to QUEUED so the
     * worker resumes it from [TransferEntity.resumeOffsetBytes] instead of stalling.
     */
    @Query("UPDATE transfer SET status = :queued WHERE status = :running")
    suspend fun requeueInterrupted(
        running: TransferStatus = TransferStatus.RUNNING,
        queued: TransferStatus = TransferStatus.QUEUED,
    )
}

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activity ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>

    @Upsert
    suspend fun insert(activity: ActivityEntity)

    @Query("DELETE FROM activity")
    suspend fun clear()
}

@Dao
interface CredentialReferenceDao {

    @Query("SELECT * FROM credential_reference ORDER BY label ASC")
    fun observeAll(): Flow<List<CredentialReferenceEntity>>

    @Query("SELECT * FROM credential_reference WHERE id = :id")
    suspend fun getById(id: String): CredentialReferenceEntity?

    @Upsert
    suspend fun upsert(reference: CredentialReferenceEntity)

    @Query("DELETE FROM credential_reference WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM credential_reference")
    suspend fun clear()
}

@Dao
interface WorkspaceDao {

    @Query("SELECT * FROM workspace WHERE projectId = :projectId")
    fun observeForProject(projectId: String): Flow<WorkspaceEntity?>

    @Query("SELECT * FROM workspace WHERE projectId = :projectId")
    suspend fun getForProject(projectId: String): WorkspaceEntity?

    @Upsert
    suspend fun upsert(workspace: WorkspaceEntity)

    @Query("DELETE FROM workspace WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}
