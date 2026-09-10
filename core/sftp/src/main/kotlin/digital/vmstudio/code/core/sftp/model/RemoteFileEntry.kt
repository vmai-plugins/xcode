package digital.vmstudio.code.core.sftp.model

/** What kind of thing a directory entry is. */
enum class RemoteFileType { FILE, DIRECTORY, SYMLINK, SPECIAL, UNKNOWN }

/**
 * One entry in a remote directory listing.
 *
 * Ownership is exposed as numeric uid/gid rather than names: resolving those to
 * names needs a round trip per entry against `/etc/passwd`, which would make a
 * thousand-file listing unusable over a mobile connection. The UI shows names only
 * where it has already resolved them.
 */
data class RemoteFileEntry(
    val name: String,
    val path: String,
    val type: RemoteFileType,
    val sizeBytes: Long,
    /** Seconds since epoch, as SFTP reports it; 0 when unknown. */
    val modifiedAtSeconds: Long,
    val permissions: RemotePermissions,
    val uid: Int,
    val gid: Int,
    /** Target of a symlink, when it has been resolved. */
    val symlinkTarget: String? = null,
) {
    val isDirectory: Boolean get() = type == RemoteFileType.DIRECTORY

    val isRegularFile: Boolean get() = type == RemoteFileType.FILE

    val isHidden: Boolean get() = name.startsWith('.')

    val extension: String get() = RemotePath.extension(name)

    /** True for files that conventionally hold secrets. */
    val looksSensitive: Boolean get() = RemotePath.looksSensitive(name)
}

/**
 * POSIX permission bits.
 *
 * Kept as a value class over the raw mode so the UI can render `rwxr-xr-x` without
 * every screen re-deriving the bit arithmetic.
 */
@JvmInline
value class RemotePermissions(val mode: Int) {

    val ownerRead: Boolean get() = mode and 0b100_000_000 != 0
    val ownerWrite: Boolean get() = mode and 0b010_000_000 != 0
    val ownerExecute: Boolean get() = mode and 0b001_000_000 != 0
    val groupRead: Boolean get() = mode and 0b000_100_000 != 0
    val groupWrite: Boolean get() = mode and 0b000_010_000 != 0
    val groupExecute: Boolean get() = mode and 0b000_001_000 != 0
    val otherRead: Boolean get() = mode and 0b000_000_100 != 0
    val otherWrite: Boolean get() = mode and 0b000_000_010 != 0
    val otherExecute: Boolean get() = mode and 0b000_000_001 != 0

    /** World-writable files are a common misconfiguration worth surfacing. */
    val isWorldWritable: Boolean get() = otherWrite

    fun toOctal(): String = (mode and 0xFFF).toString(8).padStart(3, '0')

    fun toRwxString(): String = buildString {
        append(if (ownerRead) 'r' else '-')
        append(if (ownerWrite) 'w' else '-')
        append(if (ownerExecute) 'x' else '-')
        append(if (groupRead) 'r' else '-')
        append(if (groupWrite) 'w' else '-')
        append(if (groupExecute) 'x' else '-')
        append(if (otherRead) 'r' else '-')
        append(if (otherWrite) 'w' else '-')
        append(if (otherExecute) 'x' else '-')
    }

    companion object {
        val DEFAULT_FILE = RemotePermissions(0b110_100_100) // 644
        val DEFAULT_DIRECTORY = RemotePermissions(0b111_101_101) // 755

        fun fromOctal(octal: String): RemotePermissions =
            RemotePermissions(octal.toInt(8))
    }
}

/** How a listing should be ordered. */
enum class RemoteSortOrder { NAME, SIZE, MODIFIED, TYPE }

data class RemoteListingOptions(
    val showHidden: Boolean = false,
    val sortOrder: RemoteSortOrder = RemoteSortOrder.NAME,
    val descending: Boolean = false,
    /** Directories before files, regardless of sort order. */
    val directoriesFirst: Boolean = true,
)

/** Applies display ordering to a raw listing. */
fun List<RemoteFileEntry>.sortedFor(options: RemoteListingOptions): List<RemoteFileEntry> {
    val nonNavigation = filterNot { it.name == "." || it.name == ".." }
    val filtered = if (options.showHidden) nonNavigation else nonNavigation.filterNot { it.isHidden }
    val comparator = when (options.sortOrder) {
        RemoteSortOrder.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        RemoteSortOrder.SIZE -> compareBy<RemoteFileEntry> { it.sizeBytes }
        RemoteSortOrder.MODIFIED -> compareBy<RemoteFileEntry> { it.modifiedAtSeconds }
        RemoteSortOrder.TYPE -> compareBy<RemoteFileEntry> { it.extension.lowercase() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val ordered = if (options.descending) filtered.sortedWith(comparator.reversed()) else {
        filtered.sortedWith(comparator)
    }
    return if (options.directoriesFirst) {
        ordered.sortedByDescending { it.isDirectory }
    } else {
        ordered
    }
}
