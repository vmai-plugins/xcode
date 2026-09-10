package digital.vmstudio.code.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "git_repository",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId"], unique = true)],
)
data class GitRepositoryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Path to the working tree; remote for SSH projects, local for SAF projects. */
    val workingTreePath: String,
    val remoteName: String = "origin",
    val remoteUrl: String? = null,
    val defaultBranch: String = "main",
    val currentBranch: String? = null,
    /** Reference to a token/SSH key used for fetch and push. Never a token. */
    val credentialId: String? = null,
    val userName: String? = null,
    val userEmail: String? = null,
    val lastFetchedAtMillis: Long = 0L,
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
)

enum class ConnectorType { GOOGLE_DRIVE, GITHUB, GITLAB, BITBUCKET, DROPBOX, ONEDRIVE, CUSTOM }

enum class ConnectorStatus { DISCONNECTED, CONNECTED, NEEDS_REAUTH, ERROR }

@Entity(
    tableName = "connector",
    indices = [Index(value = ["type", "accountLabel"], unique = true)],
)
data class ConnectorEntity(
    @PrimaryKey val id: String,
    val type: ConnectorType,
    val displayName: String,
    /** Account identifier shown in the UI, e.g. an email address. */
    val accountLabel: String,
    val status: ConnectorStatus = ConnectorStatus.DISCONNECTED,
    val accessTokenCredentialId: String? = null,
    val refreshTokenCredentialId: String? = null,
    val grantedScopesJson: String = "[]",
    val lastErrorText: String? = null,
    val connectedAtMillis: Long = 0L,
    val tokenExpiresAtMillis: Long = 0L,
)

enum class TransferDirection { UPLOAD, DOWNLOAD }

enum class TransferStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

@Entity(
    tableName = "transfer",
    indices = [
        Index(value = ["status", "createdAtMillis"]),
        Index(value = ["projectId"]),
        Index(value = ["serverId"]),
    ],
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val projectId: String? = null,
    val serverId: String? = null,
    val connectorId: String? = null,
    val direction: TransferDirection,
    val localPath: String,
    val remotePath: String,
    val displayName: String,
    val sizeBytes: Long = -1L,
    val transferredBytes: Long = 0L,
    val status: TransferStatus = TransferStatus.QUEUED,
    /** Bytes already on the far side, used to resume rather than restart. */
    val resumeOffsetBytes: Long = 0L,
    val bytesPerSecond: Long = 0L,
    val errorSummary: String? = null,
    val attemptCount: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

enum class ActivityCategory {
    SERVER,
    PROJECT,
    AI,
    AGENT,
    GIT,
    TRANSFER,
    FILE,
    TERMINAL,
    CONNECTOR,
    SECURITY,
    ERROR,
}

enum class ActivitySeverity { INFO, SUCCESS, WARNING, ERROR }

/**
 * The user-facing audit timeline. Distinct from [digital.vmstudio.code.core.common.log]
 * diagnostics: this is a curated record of meaningful actions, kept indefinitely and
 * filterable, whereas the log ring buffer is a volatile developer aid.
 */
@Entity(
    tableName = "activity",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["category"]),
        Index(value = ["projectId"]),
        Index(value = ["serverId"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val category: ActivityCategory,
    val severity: ActivitySeverity = ActivitySeverity.INFO,
    val title: String,
    val detail: String? = null,
    val projectId: String? = null,
    val serverId: String? = null,
    val taskId: String? = null,
)

/**
 * Mirror of the credential store's references so queries can join a server or
 * connector to its credential metadata without opening the encrypted store.
 * Contains no secret material.
 */
@Entity(tableName = "credential_reference")
data class CredentialReferenceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val expiresAtMillis: Long = 0L,
)

/**
 * Persisted editor/terminal layout for a project so reopening a project restores
 * the workspace rather than an empty screen.
 */
@Entity(
    tableName = "workspace",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId"], unique = true)],
)
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** JSON array of open editor tabs in display order. */
    val openTabsJson: String = "[]",
    val activeTabPath: String? = null,
    /** JSON array of persisted terminal session descriptors. */
    val terminalSessionsJson: String = "[]",
    /** JSON object of expanded file-tree node paths. */
    val expandedNodesJson: String = "[]",
    val lastOpenedAtMillis: Long,
)
