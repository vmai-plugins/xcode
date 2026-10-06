package digital.vmstudio.code.feature.files

import android.graphics.Bitmap
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteListingOptions
import java.io.File

/** What a file sheet shows below its header. */
sealed interface PreviewContent {
    /** [fraction] is null while the size is unknown, shown as indeterminate. */
    data class Loading(val fraction: Float? = null) : PreviewContent

    data class Text(val lines: List<String>) : PreviewContent

    data class Image(val bitmap: Bitmap, val file: File) : PreviewContent

    data class Failed(val error: VmError) : PreviewContent
}

/** A running download or upload, shown as a progress strip under the breadcrumbs. */
data class TransferStatus(
    val label: String,
    /** Null while the size is unknown, shown as indeterminate. */
    val fraction: Float? = null,
)

data class FilesUiState(
    /** Empty until a server is chosen, which the drawer route does asynchronously. */
    val serverId: String = "",
    /** Every saved server, for the switcher in the top bar. */
    val servers: List<ServerOption> = emptyList(),
    /** True when there is no saved server to browse at all. */
    val noServer: Boolean = false,
    val path: String = "",
    val entries: List<RemoteFileEntry> = emptyList(),
    val options: RemoteListingOptions = RemoteListingOptions(),
    val isLoading: Boolean = true,
    val error: VmError? = null,
    val selectedPaths: Set<String> = emptySet(),
    /** Non-null while a create/rename dialog or the file sheet is open. */
    val pendingAction: FileAction? = null,
    /** What the open file sheet previews; null when the file has no preview. */
    val preview: PreviewContent? = null,
    val transfer: TransferStatus? = null,
    /** The last live re-listing failed, so what is shown may be out of date. */
    val liveStale: Boolean = false,
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty() && error == null && !noServer

    val inSelectionMode: Boolean get() = selectedPaths.isNotEmpty()

    val currentServer: ServerOption? get() = servers.firstOrNull { it.id == serverId }

    /** Breadcrumb segments from the root down to the current directory. */
    val breadcrumbs: List<Pair<String, String>>
        get() {
            if (path.isEmpty()) return emptyList()
            val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
            var accumulated = ""
            return buildList {
                add("/" to "/")
                segments.forEach { segment ->
                    accumulated = "$accumulated/$segment"
                    add(segment to accumulated)
                }
            }
        }
}

sealed interface FileAction {
    data object CreateFile : FileAction
    data object CreateDirectory : FileAction
    data class Rename(val entry: RemoteFileEntry) : FileAction
    data class ConfirmDelete(val entries: List<RemoteFileEntry>) : FileAction

    /** The file sheet: a preview when the type has one, details always. */
    data class Details(val entry: RemoteFileEntry) : FileAction
    data class Edit(val entry: RemoteFileEntry) : FileAction
}

/**
 * Folds a listing result into the state it was requested for.
 *
 * A result for another server, folder or sort order is stale and dropped. A quiet
 * (live) result never clears what is shown on failure and only replaces entries
 * when they changed, so a background tick cannot blank the list, jump the scroll
 * position, or drop a selection that still exists.
 */
internal fun applyListing(
    current: FilesUiState,
    requested: FilesUiState,
    result: VmResult<List<RemoteFileEntry>>,
    quiet: Boolean,
): FilesUiState {
    val stale = current.serverId != requested.serverId ||
        current.path != requested.path ||
        current.options != requested.options
    if (stale) return current
    return when (result) {
        is VmResult.Success -> {
            val entries = changedListing(current.entries, result.value) ?: current.entries
            current.copy(
                entries = entries,
                isLoading = if (quiet) current.isLoading else false,
                error = if (quiet) current.error else null,
                selectedPaths = pruneSelection(current.selectedPaths, entries),
                liveStale = false,
            )
        }
        is VmResult.Failure -> if (quiet) {
            current.copy(liveStale = true)
        } else {
            current.copy(entries = emptyList(), isLoading = false, error = result.error, liveStale = false)
        }
    }
}
