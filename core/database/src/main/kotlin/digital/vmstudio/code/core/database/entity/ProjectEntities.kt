package digital.vmstudio.code.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a project's files actually live. */
enum class ProjectLocation {
    /** Inside the app sandbox or a SAF tree the user granted. */
    LOCAL,
    /** On a remote host reached over SSH/SFTP. */
    REMOTE_SSH,
    /** In a cloud connector such as Google Drive. */
    CONNECTOR,
}

/**
 * Which command set, file conventions and AI hints apply.
 *
 * Stored as a string id rather than a closed enum so new stacks can be added
 * through the project-type registry without a schema migration or a change to
 * business logic (spec: do not hardcode project types into business logic).
 */
@Entity(
    tableName = "project",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["name"]),
        Index(value = ["lastOpenedAtMillis"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val location: ProjectLocation,
    val serverId: String? = null,
    /** Absolute path on the remote host; the AI's filesystem sandbox root. */
    val remotePath: String? = null,
    /** Persisted SAF tree URI for local projects. */
    val localTreeUri: String? = null,
    val projectTypeId: String,
    val branch: String? = null,
    val environment: ServerEnvironment = ServerEnvironment.UNSPECIFIED,
    /** JSON array of detected technologies, e.g. ["kotlin","gradle"]. */
    val techStackJson: String = "[]",
    /** JSON array of glob patterns excluded from indexing, search and AI context. */
    val excludedPathsJson: String = "[]",
    /** JSON array of globs treated as secret-bearing and withheld from the AI. */
    val sensitivePathsJson: String = "[]",
    val packageManager: String? = null,
    val runtime: String? = null,
    val framework: String? = null,
    val colorHex: String? = null,
    val isFavorite: Boolean = false,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long = 0L,
)

/** What a configured command is for. Drives placement in the UI and AI tool access. */
enum class ProjectCommandKind {
    BUILD,
    TEST,
    LINT,
    RUN,
    DEPLOY,
    MIGRATE,
    INSTALL,
    CUSTOM,
}

@Entity(
    tableName = "project_command",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId"])],
)
data class ProjectCommandEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val kind: ProjectCommandKind,
    val label: String,
    val command: String,
    /** Relative to the project root; null means the root itself. */
    val workingDirectory: String? = null,
    /** Forces an approval prompt even at higher AI autonomy levels. */
    val requiresConfirmation: Boolean = false,
    val sortOrder: Int = 0,
)

/** Precedence for instruction files; higher [ProjectInstructionScope.rank] wins. */
enum class ProjectInstructionScope(val rank: Int) {
    GLOBAL(0),
    WORKSPACE(1),
    PROJECT(2),
    DIRECTORY(3),
}

/**
 * A VMSTUDIO.md (or user-authored) instruction block the agent reads before acting.
 * Cached here so instructions are available offline and cheap to assemble.
 */
@Entity(
    tableName = "project_instruction",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId", "scope", "path"], unique = true)],
)
data class ProjectInstructionEntity(
    @PrimaryKey val id: String,
    /** Null for GLOBAL instructions that apply to every project. */
    val projectId: String?,
    val scope: ProjectInstructionScope,
    /** Source path relative to the project root, e.g. "VMSTUDIO.md". */
    val path: String,
    val content: String,
    val isEnabled: Boolean = true,
    val updatedAtMillis: Long,
)

/**
 * Environment variable *metadata*. Names and descriptions only.
 *
 * Values are deliberately absent: the app must be able to tell the AI that
 * `DATABASE_URL` exists without ever holding its value, and a database dump of
 * this table must not be a credential leak.
 */
@Entity(
    tableName = "project_env_var",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["projectId", "name"], unique = true)],
)
data class ProjectEnvVarEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val description: String = "",
    val isSensitive: Boolean = true,
    /** Which file declares it, e.g. ".env.production". Never its contents. */
    val sourceFile: String? = null,
)

@Entity(
    tableName = "recent_file",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId", "path"], unique = true),
        Index(value = ["lastOpenedAtMillis"]),
    ],
)
data class RecentFileEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val path: String,
    val lastOpenedAtMillis: Long,
    val cursorLine: Int = 0,
    val cursorColumn: Int = 0,
    val scrollOffset: Int = 0,
    val isPinned: Boolean = false,
)

/**
 * Tracks a remote file mirrored into the local cache so it can be read offline and
 * so edits can be reconciled with the remote copy.
 */
@Entity(
    tableName = "file_cache",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["projectId", "remotePath"], unique = true),
        Index(value = ["lastAccessedAtMillis"]),
    ],
)
data class FileCacheEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val remotePath: String,
    /** Path inside the app cache directory. */
    val localPath: String,
    val sizeBytes: Long,
    /** SHA-256 of the content as fetched, used to detect remote changes. */
    val contentHash: String,
    /** Remote mtime in millis, when the server reports one. */
    val remoteModifiedAtMillis: Long = 0L,
    val cachedAtMillis: Long,
    val lastAccessedAtMillis: Long,
    /** True when the local copy has unsaved edits not yet pushed to the remote. */
    val hasLocalEdits: Boolean = false,
)
