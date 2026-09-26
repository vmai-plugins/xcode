package digital.vmstudio.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.omniroute.agent.FileEditApprovalRequest
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * The approval surface for a file write proposed by the OmniRoute tool loop.
 *
 * A sibling to [CommandApprovalHost], not a shared type with it: a file write and a
 * shell command need different context (a diff preview instead of a risk
 * assessment), and this is a new, less-proven feature that should not risk
 * regressions in the hardened command-approval path by being forced through it.
 */
@Composable
fun FileEditApprovalHost(
    modifier: Modifier = Modifier,
    viewModel: FileEditApprovalViewModel = hiltViewModel(),
) {
    val request by viewModel.pending.collectAsStateWithLifecycle()

    request?.let { pending ->
        FileEditApprovalDialog(
            request = pending,
            onAllow = { viewModel.resolve(pending.id, approved = true) },
            onDeny = { viewModel.resolve(pending.id, approved = false) },
            modifier = modifier,
        )
    }
}

@Composable
private fun FileEditApprovalDialog(
    request: FileEditApprovalRequest,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing

    VmDialog(
        title = if (request.isNewFile) "Create this file?" else "Overwrite this file?",
        onDismiss = onDeny,
        confirmLabel = "Write",
        onConfirm = onAllow,
        dismissLabel = "Cancel",
        destructive = !request.isNewFile,
        icon = Icons.Default.Edit,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VmChip(text = request.serverName)
            if (request.isProduction) {
                VmChip(
                    text = "Production",
                    containerColor = VmTheme.colors.warningContainer,
                    contentColor = VmTheme.colors.onWarningContainer,
                )
            }
            VmChip(text = if (request.isNewFile) "New file" else "Overwrites existing")
        }

        Text(
            text = request.path,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = "New content",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = request.newContent.take(MAX_PREVIEW_CHARS).let {
                    if (request.newContent.length > MAX_PREVIEW_CHARS) "$it\n..." else it
                },
                style = VmTheme.code.mono,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PREVIEW_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .clip(MaterialTheme.shapes.small)
                    .background(VmTheme.colors.codeSurface)
                    .padding(spacing.sm),
            )
        }
    }
}

/** Enough to judge the change without turning the dialog into a full diff viewer. */
private const val MAX_PREVIEW_CHARS = 2_000
private val PREVIEW_MAX_HEIGHT = 220.dp
