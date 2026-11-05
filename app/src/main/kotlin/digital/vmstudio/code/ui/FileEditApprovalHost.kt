package digital.vmstudio.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

private data class ApprovalDiffLine(
    val prefix: String,
    val text: String,
    val isAdded: Boolean = false,
    val isRemoved: Boolean = false,
)

private fun computeApprovalDiff(oldText: String?, newText: String, maxLines: Int = 50): List<ApprovalDiffLine> {
    if (oldText == null) {
        return newText.lines().take(maxLines).map { ApprovalDiffLine("+", it, isAdded = true) }
    }
    val oldLines = oldText.lines()
    val newLines = newText.lines()
    if (oldText == newText) {
        return newLines.take(maxLines).map { ApprovalDiffLine(" ", it) }
    }
    val diff = mutableListOf<ApprovalDiffLine>()
    var i = 0
    var j = 0
    while (i < oldLines.size || j < newLines.size) {
        if (diff.size >= maxLines) break
        val oldLine = oldLines.getOrNull(i)
        val newLine = newLines.getOrNull(j)
        when {
            oldLine == newLine -> {
                diff.add(ApprovalDiffLine(" ", oldLine.orEmpty()))
                i++
                j++
            }
            newLine != null && (oldLine == null || !oldLines.contains(newLine)) -> {
                diff.add(ApprovalDiffLine("+", newLine, isAdded = true))
                j++
            }
            oldLine != null && (newLine == null || !newLines.contains(oldLine)) -> {
                diff.add(ApprovalDiffLine("-", oldLine, isRemoved = true))
                i++
            }
            else -> {
                if (oldLine != null) {
                    diff.add(ApprovalDiffLine("-", oldLine, isRemoved = true))
                    i++
                }
                if (newLine != null && diff.size < maxLines) {
                    diff.add(ApprovalDiffLine("+", newLine, isAdded = true))
                    j++
                }
            }
        }
    }
    return diff
}

@Composable
private fun FileEditApprovalDialog(
    request: FileEditApprovalRequest,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val fileName = remember(request.path) { request.path.substringAfterLast('/') }
    val diffLines = remember(request.oldContent, request.newContent, request.isNewFile) {
        computeApprovalDiff(if (request.isNewFile) null else request.oldContent, request.newContent)
    }
    val additionsCount = remember(diffLines) { diffLines.count { it.isAdded } }
    val deletionsCount = remember(diffLines) { diffLines.count { it.isRemoved } }

    VmDialog(
        title = if (request.isNewFile) "Create New File?" else "Approve File Edit?",
        onDismiss = onDeny,
        confirmLabel = if (request.isNewFile) "Create File" else "Apply Changes",
        onConfirm = onAllow,
        dismissLabel = "Reject / Skip",
        destructive = false,
        icon = if (request.isNewFile) Icons.Default.AddCircle else Icons.Default.Edit,
        modifier = modifier,
    ) {
        // Explainer for beginners
        Text(
            text = if (request.isNewFile) {
                "The AI agent proposed creating \"$fileName\" to complete your request. Review proposed content below."
            } else {
                "The AI agent proposed modifying \"$fileName\". Review additions and deletions below before saving."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Metadata Chips
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
            if (request.isNewFile) {
                VmChip(
                    text = "New File (+${diffLines.size})",
                    containerColor = Color(0x2E2E7D32),
                    contentColor = Color(0xFF81C784),
                )
            } else {
                if (additionsCount > 0) {
                    VmChip(
                        text = "+$additionsCount",
                        containerColor = Color(0x2E2E7D32),
                        contentColor = Color(0xFF81C784),
                    )
                }
                if (deletionsCount > 0) {
                    VmChip(
                        text = "-$deletionsCount",
                        containerColor = Color(0x2EC62828),
                        contentColor = Color(0xFFE57373),
                    )
                }
            }
        }

        // Monospaced Path
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = request.path,
                style = VmTheme.code.mono,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        // Diff / Code Box
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (request.isNewFile) "Proposed Content" else "Changes Diff (+Added / -Removed)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${diffLines.size} lines preview",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PREVIEW_MAX_HEIGHT)
                    .clip(MaterialTheme.shapes.small)
                    .background(VmTheme.colors.codeSurface)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(spacing.xs),
            ) {
                diffLines.forEach { line ->
                    val bgColor = when {
                        line.isAdded -> Color(0x2E2E7D32)
                        line.isRemoved -> Color(0x2EC62828)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.isAdded -> Color(0xFF81C784)
                        line.isRemoved -> Color(0xFFE57373)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor, RoundedCornerShape(2.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${line.prefix} ${line.text}",
                            style = VmTheme.code.mono,
                            fontSize = 11.sp,
                            color = textColor,
                        )
                    }
                }
            }
        }

        // Beginner Reassurance Guardrail
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Safe guard: Rejecting will skip this write and leave your workspace untouched. You can also rollback later via the Undo button in chat.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val PREVIEW_MAX_HEIGHT = 220.dp
