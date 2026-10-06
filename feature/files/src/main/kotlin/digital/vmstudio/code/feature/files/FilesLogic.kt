package digital.vmstudio.code.feature.files

import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import java.util.Locale

/*
 * Pure decisions behind the Files screen, kept out of the ViewModel so they can be
 * unit-tested without Android and so the ViewModel stays a thin coordinator.
 */

/** How long the browser waits between quiet re-listings while it is on screen. */
internal const val LIVE_REFRESH_MILLIS = 5_000L

/**
 * Files of unknown type at or under this size are tried as text. Small enough that
 * a misjudged binary costs one quick read, large enough for most configs and logs.
 */
internal const val TEXT_SNIFF_MAX_BYTES = 512L * 1024

/**
 * Hard ceiling for a text preview, even for a known text extension. A preview is a
 * glance; anything larger belongs in the editor or on the phone via Share.
 */
internal const val TEXT_PREVIEW_MAX_BYTES = 2L * 1024 * 1024

/** Images above this are not fetched just to be looked at on a phone screen. */
internal const val IMAGE_PREVIEW_MAX_BYTES = 25L * 1024 * 1024

/** Longest edge an image preview is decoded at, so a huge photo cannot OOM the app. */
internal const val IMAGE_PREVIEW_MAX_EDGE = 2048

/**
 * Longest line a text preview renders before cutting it short. Minified bundles are
 * often one multi-hundred-kilobyte line, which would stall a single Text layout.
 */
internal const val PREVIEW_LINE_MAX_CHARS = 4_000

/** How many "name (n).ext" candidates an upload tries before giving up on a free name. */
internal const val MAX_NAME_CANDIDATES = 100

private const val BYTES_PER_KB = 1L shl 10
private const val BYTES_PER_MB = 1L shl 20
private const val BYTES_PER_GB = 1L shl 30

/** What tapping a file shows. */
enum class PreviewKind { TEXT, IMAGE, NONE }

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp")

private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "rst", "log", "csv", "tsv",
    "json", "jsonc", "yaml", "yml", "toml", "ini", "cfg", "conf", "env", "properties", "xml",
    "html", "htm", "css", "scss", "less", "svg",
    "js", "mjs", "cjs", "jsx", "ts", "tsx", "vue", "svelte",
    "kt", "kts", "java", "gradle", "groovy", "scala", "swift", "m", "c", "h", "cc", "cpp",
    "hpp", "cs", "go", "rs", "py", "rb", "php", "pl", "lua", "r", "dart", "ex", "exs", "erl",
    "sh", "bash", "zsh", "fish", "ps1", "bat", "sql", "graphql", "proto", "tf", "hcl",
    "dockerfile", "makefile", "lock", "gitignore", "editorconfig", "service", "pem", "pub",
)

/** Names with no useful extension that are nonetheless almost always text. */
private val TEXT_FILE_NAMES = setOf(
    "dockerfile", "makefile", "readme", "license", "changelog", "procfile", "gemfile",
    "vagrantfile", "authorized_keys", "known_hosts", "config", "hosts", "crontab",
)

/** Known binaries: never sniffed as text, however small. */
private val BINARY_EXTENSIONS = setOf(
    "zip", "gz", "tgz", "bz2", "xz", "zst", "7z", "rar", "tar", "jar", "war", "apk", "aab",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt",
    "mp3", "mp4", "m4a", "mov", "avi", "mkv", "webm", "wav", "flac", "ogg",
    "bmp", "ico", "tif", "tiff", "heic", "psd",
    "so", "o", "a", "dll", "exe", "bin", "class", "pyc", "dex", "iso", "img", "dmg", "deb", "rpm",
    "db", "sqlite", "sqlite3", "woff", "woff2", "ttf", "otf",
)

/**
 * Decides how a file previews. Known text types preview up to
 * [TEXT_PREVIEW_MAX_BYTES]; unknown types are tried as text only when small, since
 * the read fails cleanly on non-UTF-8 content and that failure is shown as such.
 */
internal fun previewKindFor(name: String, sizeBytes: Long): PreviewKind {
    val lower = name.lowercase(Locale.ROOT)
    val extension = lower.substringAfterLast('.', "")
    return when {
        extension in IMAGE_EXTENSIONS ->
            if (sizeBytes in 0..IMAGE_PREVIEW_MAX_BYTES) PreviewKind.IMAGE else PreviewKind.NONE
        extension in BINARY_EXTENSIONS -> PreviewKind.NONE
        extension in TEXT_EXTENSIONS || lower.trimStart('.') in TEXT_FILE_NAMES ->
            if (sizeBytes in 0..TEXT_PREVIEW_MAX_BYTES) PreviewKind.TEXT else PreviewKind.NONE
        sizeBytes in 0..TEXT_SNIFF_MAX_BYTES -> PreviewKind.TEXT
        else -> PreviewKind.NONE
    }
}

internal fun RemoteFileEntry.previewKind(): PreviewKind =
    if (isDirectory) PreviewKind.NONE else previewKindFor(name, sizeBytes)

/** Splits preview text into lines, cutting any overlong one at [PREVIEW_LINE_MAX_CHARS]. */
internal fun previewLines(text: String): List<String> = text.lines().map { line ->
    if (line.length > PREVIEW_LINE_MAX_CHARS) line.take(PREVIEW_LINE_MAX_CHARS) + " …" else line
}

/** Case-insensitive substring match on the name; a blank query shows everything. */
internal fun filterEntries(entries: List<RemoteFileEntry>, query: String): List<RemoteFileEntry> {
    val needle = query.trim()
    if (needle.isEmpty()) return entries
    return entries.filter { it.name.contains(needle, ignoreCase = true) }
}

/**
 * The listing to show after a quiet refresh, or null when nothing changed. Returning
 * null lets the caller skip the state update entirely, so a live tick on an idle
 * folder never recomposes the list or disturbs scrolling.
 */
internal fun changedListing(
    current: List<RemoteFileEntry>,
    fresh: List<RemoteFileEntry>,
): List<RemoteFileEntry>? = if (current == fresh) null else fresh

/** Drops selected paths that vanished from the folder, keeping the rest selected. */
internal fun pruneSelection(selected: Set<String>, entries: List<RemoteFileEntry>): Set<String> {
    if (selected.isEmpty()) return selected
    val present = entries.mapTo(HashSet()) { it.path }
    return selected.filterTo(LinkedHashSet()) { it in present }
}

/** A saved server as the switcher shows it. */
data class ServerOption(
    val id: String,
    val name: String,
    val target: String,
    val lastConnectedAtMillis: Long = 0L,
)

/**
 * The server the drawer's Files entry opens on: the one connected to most recently,
 * else the first saved one (every server ties at 0 before its first connection).
 */
internal fun pickDefaultServerId(servers: List<ServerOption>): String? =
    servers.maxByOrNull { it.lastConnectedAtMillis }?.id

/**
 * Names to try, in order, for a file uploaded as [name]: the name itself, then
 * "name (1).ext", "name (2).ext"... Uploading never silently replaces a server file.
 */
internal fun candidateNames(name: String): Sequence<String> {
    val dot = name.lastIndexOf('.')
    // A leading dot is a hidden file's name, not an extension separator.
    val (base, extension) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    return sequenceOf(name) + (1 until MAX_NAME_CANDIDATES).asSequence().map { "$base ($it)$extension" }
}

/**
 * The power-of-two subsample that brings an image's longest edge to at most
 * [maxEdge]; BitmapFactory only honours powers of two.
 */
internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int = IMAGE_PREVIEW_MAX_EDGE): Int {
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / sample > maxEdge) sample *= 2
    return sample
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "unknown"
    bytes >= BYTES_PER_GB -> String.format(Locale.ROOT, "%.1f GB", bytes.toDouble() / BYTES_PER_GB)
    bytes >= BYTES_PER_MB -> String.format(Locale.ROOT, "%.1f MB", bytes.toDouble() / BYTES_PER_MB)
    bytes >= BYTES_PER_KB -> String.format(Locale.ROOT, "%.1f KB", bytes.toDouble() / BYTES_PER_KB)
    else -> "$bytes B"
}
