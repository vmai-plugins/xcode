package digital.vmstudio.code.feature.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * The sheet a tapped file opens: a read-only preview when its type has one, with
 * Edit and Share one tap away, and the file's details.
 *
 * A preview rather than straight into the editor because most taps on a server are
 * "what is in this?", and the editor's load-and-lock flow is heavier than needed
 * to read a config or glance at a screenshot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileSheet(
    entry: RemoteFileEntry,
    preview: PreviewContent?,
    sharing: Boolean,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = VmTheme.spacing
    // Details are the content when there is no preview, and one tap away otherwise.
    var showDetails by rememberSaveable(entry.path) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = preview != null),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatSize(entry.sizeBytes)}  ${entry.permissions.toRwxString()}",
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (preview !is PreviewContent.Image) {
                    VmButton(
                        text = "Edit",
                        onClick = onEdit,
                        icon = Icons.Default.Edit,
                        style = VmButtonStyle.Primary,
                    )
                }
                VmButton(
                    text = "Share",
                    onClick = onShare,
                    icon = Icons.Default.Share,
                    style = VmButtonStyle.Secondary,
                    loading = sharing,
                )
                if (preview != null) {
                    VmButton(
                        text = if (showDetails) "Preview" else "Details",
                        onClick = { showDetails = !showDetails },
                        icon = Icons.Default.Info,
                        style = VmButtonStyle.Tertiary,
                    )
                }
            }

            if (preview == null || showDetails) {
                FileDetails(entry)
            } else {
                PreviewBody(entry = entry, preview = preview)
            }
        }
    }
}

@Composable
private fun PreviewBody(entry: RemoteFileEntry, preview: PreviewContent) {
    when (preview) {
        is PreviewContent.Loading -> VmProgress(
            progress = preview.fraction,
            label = "Loading preview",
            modifier = Modifier.padding(vertical = VmTheme.spacing.lg),
        )

        is PreviewContent.Failed -> Column(
            verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        ) {
            VmErrorPanel(error = preview.error)
            Text(
                text = "You can still share the file to your phone or see its details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is PreviewContent.Text -> TextPreview(preview.lines)

        is PreviewContent.Image -> {
            val bitmap = remember(preview.bitmap) { preview.bitmap.asImageBitmap() }
            Image(
                bitmap = bitmap,
                contentDescription = entry.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
    }
}

/**
 * Monospace, line-numbered, lazily laid out: a 2 MB log is tens of thousands of
 * lines, which a single Text would measure all at once.
 */
@Composable
private fun TextPreview(lines: List<String>) {
    if (lines.all { it.isEmpty() }) {
        Text(
            text = "This file is empty.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val gutterWidth = remember(lines.size) { (lines.size.toString().length * 9 + 8).dp }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        LazyColumn(modifier = Modifier.padding(vertical = VmTheme.spacing.sm)) {
            itemsIndexed(lines) { index, line ->
                Row(modifier = Modifier.padding(end = VmTheme.spacing.sm)) {
                    Text(
                        text = (index + 1).toString(),
                        style = VmTheme.code.mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .widthIn(min = gutterWidth)
                            .padding(end = VmTheme.spacing.sm),
                    )
                    Text(
                        text = line,
                        style = VmTheme.code.mono,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileDetails(entry: RemoteFileEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs)) {
        DetailRow("Path", entry.path)
        DetailRow("Type", entry.type.name.lowercase())
        DetailRow("Size", formatSize(entry.sizeBytes))
        DetailRow(
            "Permissions",
            "${entry.permissions.toRwxString()} (${entry.permissions.toOctal()})",
        )
        DetailRow("Owner", "uid ${entry.uid} / gid ${entry.gid}")
        if (entry.permissions.isWorldWritable) {
            Text(
                text = "This file is world-writable, which is usually a misconfiguration.",
                style = MaterialTheme.typography.bodySmall,
                color = VmTheme.colors.warning,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = VmTheme.code.mono,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = VmTheme.spacing.md),
        )
    }
}
