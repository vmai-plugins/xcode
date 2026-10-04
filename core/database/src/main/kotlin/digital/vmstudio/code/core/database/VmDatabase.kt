package digital.vmstudio.code.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import digital.vmstudio.code.core.database.dao.ActivityDao
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ConnectorDao
import digital.vmstudio.code.core.database.dao.ConversationDao
import digital.vmstudio.code.core.database.dao.CredentialReferenceDao
import digital.vmstudio.code.core.database.dao.FileCacheDao
import digital.vmstudio.code.core.database.dao.GitRepositoryDao
import digital.vmstudio.code.core.database.dao.KnownHostKeyDao
import digital.vmstudio.code.core.database.dao.MessageDao
import digital.vmstudio.code.core.database.dao.ProjectCommandDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.dao.ProjectEnvVarDao
import digital.vmstudio.code.core.database.dao.ProjectInstructionDao
import digital.vmstudio.code.core.database.dao.RecentFileDao
import digital.vmstudio.code.core.database.dao.ServerDao
import digital.vmstudio.code.core.database.dao.ToolExecutionDao
import digital.vmstudio.code.core.database.dao.TransferDao
import digital.vmstudio.code.core.database.dao.WorkspaceDao
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.ConnectorEntity
import digital.vmstudio.code.core.database.entity.ConversationEntity
import digital.vmstudio.code.core.database.entity.CredentialReferenceEntity
import digital.vmstudio.code.core.database.entity.FileCacheEntity
import digital.vmstudio.code.core.database.entity.GitRepositoryEntity
import digital.vmstudio.code.core.database.entity.KnownHostKeyEntity
import digital.vmstudio.code.core.database.entity.MessageEntity
import digital.vmstudio.code.core.database.entity.ProjectCommandEntity
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectEnvVarEntity
import digital.vmstudio.code.core.database.entity.ProjectInstructionEntity
import digital.vmstudio.code.core.database.entity.RecentFileEntity
import digital.vmstudio.code.core.database.entity.ServerEntity
import digital.vmstudio.code.core.database.entity.ServerGroupEntity
import digital.vmstudio.code.core.database.entity.ToolExecutionEntity
import digital.vmstudio.code.core.database.entity.TransferEntity
import digital.vmstudio.code.core.database.entity.WorkspaceEntity

/**
 * Application database.
 *
 * Holds no secrets: credentials live in the Keystore-backed store and are referenced
 * here only by opaque id (see [CredentialReferenceEntity] and SECURITY.md).
 *
 * Schemas are exported to `core/database/schemas` and checked in, so migrations can
 * be tested against the real historical schema.
 */
@Database(
    entities = [
        ServerEntity::class,
        ServerGroupEntity::class,
        KnownHostKeyEntity::class,
        ProjectEntity::class,
        ProjectCommandEntity::class,
        ProjectInstructionEntity::class,
        ProjectEnvVarEntity::class,
        RecentFileEntity::class,
        FileCacheEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        ToolExecutionEntity::class,
        AgentTaskEntity::class,
        GitRepositoryEntity::class,
        ConnectorEntity::class,
        TransferEntity::class,
        ActivityEntity::class,
        CredentialReferenceEntity::class,
        WorkspaceEntity::class,
    ],
    version = VmDatabase.VERSION,
    exportSchema = true,
)
abstract class VmDatabase : RoomDatabase() {

    abstract fun serverDao(): ServerDao
    abstract fun knownHostKeyDao(): KnownHostKeyDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectCommandDao(): ProjectCommandDao
    abstract fun projectInstructionDao(): ProjectInstructionDao
    abstract fun projectEnvVarDao(): ProjectEnvVarDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun fileCacheDao(): FileCacheDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun toolExecutionDao(): ToolExecutionDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun gitRepositoryDao(): GitRepositoryDao
    abstract fun connectorDao(): ConnectorDao
    abstract fun transferDao(): TransferDao
    abstract fun activityDao(): ActivityDao
    abstract fun credentialReferenceDao(): CredentialReferenceDao
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        const val VERSION = 2
        const val NAME = "vmstudio.db"
    }
}
