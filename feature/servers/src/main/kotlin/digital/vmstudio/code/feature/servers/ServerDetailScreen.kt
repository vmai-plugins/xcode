package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenAgent: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val server = state.server

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
                    if (state.isConnected) {
                        IconButton(onClick = viewModel::refreshTelemetry) {
                            if (state.isLoadingTelemetry) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh telemetry")
                            }
                        }
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

                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.sm))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-connect on launch",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Connects to this VPS automatically when X-Codes opens so you don't have to connect each time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.isAutoConnect,
                            onCheckedChange = viewModel::toggleAutoConnect,
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
                item { VmSectionHeader(title = "Actions") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        VmButton(
                            text = "AI Agent",
                            icon = Icons.Default.AutoAwesome,
                            onClick = { server?.let { onOpenAgent(it.id) } },
                            modifier = Modifier.weight(1f),
                        )
                        VmButton(
                            text = "Terminal",
                            icon = Icons.Default.Terminal,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenTerminal(it.id) } },
                            modifier = Modifier.weight(1f),
                        )
                        VmButton(
                            text = "Files",
                            icon = Icons.Default.Folder,
                            style = VmButtonStyle.Secondary,
                            onClick = { server?.let { onOpenFiles(it.id) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                state.telemetry?.let { tel ->
                    item { VmSectionHeader(title = "Live Resource Telemetry") }
                    item {
                        VmCard {
                            if (tel.uptime.isNotBlank()) {
                                InfoRow("Uptime", tel.uptime)
                                Spacer(modifier = Modifier.height(spacing.xs))
                            }
                            if (tel.ramSummary.isNotBlank()) {
                                Text(
                                    text = "RAM: ${tel.ramSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { tel.ramUsedPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (tel.ramUsedPercent > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(spacing.sm))
                            }
                            if (tel.diskSummary.isNotBlank()) {
                                Text(
                                    text = "Disk (/): ${tel.diskSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { tel.diskUsedPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (tel.diskUsedPercent > 90) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                }

                if (state.hasDocker) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VmSectionHeader(title = "Docker Containers (${state.containers.size})")
                        }
                    }
                    if (state.containers.isEmpty()) {
                        item {
                            VmCard {
                                Text(
                                    text = "Docker is installed on this host, but no containers are currently found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(state.containers, key = { it.name }) { container ->
                            VmCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (container.isRunning) Color(0xFF4CAF50) else Color(0xFFE53935)),
                                        )
                                        Column {
                                            Text(
                                                text = container.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                            )
                                            Text(
                                                text = container.image,
                                                style = VmTheme.code.mono,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp,
                                            )
                                            Text(
                                                text = container.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { viewModel.restartContainer(container.name) }) {
                                            Icon(
                                                Icons.Default.RestartAlt,
                                                contentDescription = "Restart container",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        VmButton(
                                            text = "Logs",
                                            style = VmButtonStyle.Tertiary,
                                            onClick = { viewModel.viewContainerLogs(container.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                state.serverInfo?.let { info ->
                    item { VmSectionHeader(title = "System Details") }
                    item { ServerInfoCard(info) }
                }
            }
        }
    }

    state.containerLogs?.let { (name, logs) ->
        VmDialog(
            title = "Logs: $name",
            onDismiss = viewModel::dismissContainerLogs,
            confirmLabel = "Close",
            onConfirm = viewModel::dismissContainerLogs,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1F22))
                    .padding(8.dp),
            ) {
                Text(
                    text = logs,
                    style = VmTheme.code.mono,
                    color = Color(0xFFE2E2E5),
                    fontSize = 11.sp,
                )
            }
        }
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
        Text(
            text = value,
            style = VmTheme.code.mono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

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
