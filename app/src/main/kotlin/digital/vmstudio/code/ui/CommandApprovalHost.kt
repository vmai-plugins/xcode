package digital.vmstudio.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.command.CommandApprovalRequest
import digital.vmstudio.code.core.ssh.command.CommandRisk
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * The app's single approval surface.
 *
 * Mounted once above the navigation graph rather than per screen, because the gate
 * is process-wide: a command can be proposed by the terminal, by a project's build
 * step, or — once it exists — by the agent running in the background. One host means
 * the prompt appears wherever the user happens to be, and there is exactly one place
 * that can grant approval.
 */
@Composable
fun CommandApprovalHost(
    modifier: Modifier = Modifier,
    viewModel: CommandApprovalViewModel = hiltViewModel(),
) {
    val request by viewModel.pending.collectAsStateWithLifecycle()

    request?.let { pending ->
        ApprovalDialog(
            request = pending,
            onAllow = { viewModel.resolve(pending.id, approved = true) },
            onDeny = { viewModel.resolve(pending.id, approved = false) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ApprovalDialog(
    request: CommandApprovalRequest,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val destructive = request.assessment.risk >= CommandRisk.DESTRUCTIVE
    val spacing = VmTheme.spacing

    VmDialog(
        title = when {
            request.requestedByAgent -> "The AI agent wants to run a command"
            destructive -> "This command can destroy data"
            else -> "Run this command?"
        },
        onDismiss = onDeny,
        confirmLabel = if (destructive) "Run anyway" else "Run",
        onConfirm = onAllow,
        dismissLabel = "Cancel",
        destructive = destructive,
        icon = when {
            request.requestedByAgent -> Icons.Default.AutoAwesome
            destructive -> Icons.Default.Warning
            else -> null
        },
        modifier = modifier,
    ) {
        Row(
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
            VmChip(text = request.assessment.risk.label())
        }

        // The command itself, verbatim and monospaced: the user is being asked to
        // vouch for this exact text, so it must be readable character by character.
        Text(
            text = request.command,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(VmTheme.colors.codeSurface)
                .padding(spacing.sm),
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            request.assessment.findings.take(MAX_FINDINGS).forEach { finding ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (finding.risk >= CommandRisk.DESTRUCTIVE) {
                            MaterialTheme.colorScheme.error
                        } else {
                            VmTheme.colors.warning
                        },
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = finding.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun CommandRisk.label(): String = when (this) {
    CommandRisk.SAFE -> "Safe"
    CommandRisk.CAUTION -> "Changes state"
    CommandRisk.DESTRUCTIVE -> "Destructive"
    CommandRisk.BLOCKED -> "Blocked"
}

/** Enough to explain the decision without turning the dialog into a report. */
private const val MAX_FINDINGS = 3
