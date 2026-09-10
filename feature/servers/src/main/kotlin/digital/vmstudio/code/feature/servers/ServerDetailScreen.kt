package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ssh.connection.ServerInfo
import digital.vmstudio.code.core.ssh.connection.SshConnectionState
import digital.vmstudio.code.core.ssh.host.HostKeyVerdict
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenTerminal: (String) -> Unit,
    onOpenFiles: (String) -> Unit,
    onOpenApps: (String) -> Unit,
    onOpenRecipes: (String) -> Unit,
    onOpenAgent: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val server = state.server
    var showMetrics by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(server?.name ?: "Server") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Telemetry connects through the shared manager on demand, so
                    // it works from a cold start as well as mid-session.
                    IconButton(onClick = { showMetrics = true }) {
                        Icon(Icons.Default.Analytics, contentDescription = "Server metrics")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = spacing.screenHorizontal,
                vertical = spacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                VmCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = server?.displayTarget.orEmpty(),
                            style = VmTheme.code.mono,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        VmStatusBadge(
                            status = state.connectionState.toBadgeStatus(),
                            label = state.connectionState.label(),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        if (state.isConnected) {
                            VmButton(
                                text = "Disconnect",
                                onClick = viewModel::disconnect,
                                style = VmButtonStyle.Secondary,
                            )
                        } else {
                            VmButton(
                                text = "Connect",
                                onClick = viewModel::connect,
                                loading = state.isBusy,
                            )
                        }
                        VmButton(
                            text = "Edit",
                            onClick = { server?.let { onEdit(it.id) } },
                            style = VmButtonStyle.Tertiary,
                        )
                    }
                }
            }

            state.error?.let { error ->
                item {
                    VmErrorPanel(
                        error = error,
                        onRetry = viewModel::connect,
                        actions = listOf(
                            digital.vmstudio.code.core.ui.component.VmErrorAction(
                                label = "Edit server",
                                onClick = { server?.let { onEdit(it.id) } },
                            ),
                            digital.vmstudio.code.core.ui.component.VmErrorAction(
                                label = "Dismiss",
                                onClick = viewModel::dismissError,
                            ),
                        ),
                    )
                }
            }

            if (state.isConnected) {
                item { VmSectionHeader(title = "Workspace") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        VmButton(
                            text = "Terminal",
                            icon = Icons.Default.Terminal,
                            onClick = { server?.let { onOpenTerminal(it.id) } },
                        )
                        VmButton(
                            text = "Files",
                            icon = Icons.Default.Folder,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenFiles(it.id) } },
                        )
                        VmButton(
                            text = "Apps",
                            icon = Icons.Default.Apps,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenApps(it.id) } },
                        )
                        VmButton(
                            text = "Recipes",
                            icon = Icons.Default.Inventory2,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenRecipes(it.id) } },
                        )
                        VmButton(
                            text = "AI Agent",
                            icon = Icons.Default.AutoAwesome,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenAgent(it.id) } },
                        )
                    }
                }

                state.serverInfo?.let { info ->
                    item { VmSectionHeader(title = "System") }
                    item { ServerInfoCard(info) }
                }
            }
        }
    }

    if (showMetrics) {
        ServerMetricsSheet(onDismiss = { showMetrics = false })
    }

    state.pendingHostKey?.let { verdict ->
        HostKeyDialog(
            verdict = verdict,
            onTrust = viewModel::trustHostKeyAndConnect,
            onReject = viewModel::rejectHostKey,
        )
    }
}

@Composable
private fun ServerInfoCard(info: ServerInfo) {
    VmCard {
        InfoRow("Shell", info.shellPath)
        info.operatingSystem?.let { InfoRow("OS", it) }
        info.kernelVersion?.let { InfoRow("Kernel", it) }
        info.architecture?.let { InfoRow("Architecture", it) }
        info.hostname?.let { InfoRow("Hostname", it) }
        info.homeDirectory?.let { InfoRow("Home", it) }
        info.sshServerVersion?.let { InfoRow("SSH server", it) }

        if (info.operatingSystem == null) {
            Text(
                // Better to say the probe found nothing than to show a plausible
                // guess the user might rely on.
                text = "The host did not report system details. This is normal on " +
                    "restricted shells and minimal containers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = VmTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VmTheme.spacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = VmTheme.code.mono, maxLines = 1)
    }
}

/**
 * The host-key decision.
 *
 * Deliberately not dismissible by tapping outside, and the accept action is styled
 * as destructive for a changed key: this is the one prompt in the app where habitual
 * confirmation would defeat the entire purpose of host verification.
 */
@Composable
private fun HostKeyDialog(
    verdict: HostKeyVerdict,
    onTrust: () -> Unit,
    onReject: () -> Unit,
) {
    val isMismatch = verdict is HostKeyVerdict.Mismatch
    val candidate = when (verdict) {
        is HostKeyVerdict.Unknown -> verdict.candidate
        is HostKeyVerdict.Mismatch -> verdict.candidate
        else -> return
    }

    VmDialog(
        title = if (isMismatch) "Host key has changed" else "Unrecognised host",
        onDismiss = onReject,
        confirmLabel = if (isMismatch) "Trust the new key" else "Trust and connect",
        onConfirm = onTrust,
        dismissLabel = "Cancel",
        destructive = isMismatch,
        icon = if (isMismatch) Icons.Default.Warning else null,
    ) {
        Text(
            text = if (isMismatch) {
                "The key presented by ${candidate.host} does not match the one you " +
                    "trusted before. This happens after a legitimate server rebuild, " +
                    "but it is also what interception looks like."
            } else {
                "This is the first connection to ${candidate.host}. Check the " +
                    "fingerprint matches your server before trusting it."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        FingerprintBlock(
            label = if (isMismatch) "Presented now" else "Fingerprint",
            keyType = candidate.keyType,
            fingerprint = candidate.fingerprintSha256,
        )

        (verdict as? HostKeyVerdict.Mismatch)?.let { mismatch ->
            FingerprintBlock(
                label = "Previously trusted",
                keyType = mismatch.trusted.keyType,
                fingerprint = mismatch.trusted.fingerprintSha256,
            )
        }

        Text(
            text = "Verify on the server with:\nssh-keygen -lf /etc/ssh/ssh_host_" +
                "${candidate.keyType.removePrefix("ssh-")}_key.pub",
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FingerprintBlock(label: String, keyType: String, fingerprint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(VmTheme.spacing.sm),
    ) {
        Text(
            text = "$label ($keyType)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = fingerprint, style = VmTheme.code.mono)
    }
}

private fun SshConnectionState.toBadgeStatus(): VmStatus = when (this) {
    is SshConnectionState.Connected -> VmStatus.CONNECTED
    is SshConnectionState.Connecting, is SshConnectionState.Reconnecting -> VmStatus.CONNECTING
    is SshConnectionState.Failed -> VmStatus.ERROR
    SshConnectionState.Disconnected -> VmStatus.DISCONNECTED
}

private fun SshConnectionState.label(): String = when (this) {
    is SshConnectionState.Connected -> "Connected"
    is SshConnectionState.Connecting -> "Connecting"
    is SshConnectionState.Reconnecting -> "Reconnecting"
    is SshConnectionState.Failed -> "Failed"
    SshConnectionState.Disconnected -> "Not connected"
}
