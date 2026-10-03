package digital.vmstudio.code.feature.connectors

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(
    onNavigateBack: () -> Unit,
    onOpenTerminal: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Connectors & Ecosystem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::syncNow,
                        enabled = !state.syncState.isSyncing,
                    ) {
                        if (state.syncState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync Hub")
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
            // 1. VM Project Hub REST Sync Card
            item(key = "hub-connector") {
                VmCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = "VM Project Hub",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = if (state.syncState.isSyncing) VmStatus.CONNECTING else VmStatus.CONNECTED,
                                label = if (state.syncState.isSyncing) "Syncing" else "Connected",
                            )
                        }

                        Text(
                            text = "Connected to https://vmstudio.digital via REST. Syncs projects, tasks, CI/CD jobs, and automated growth runs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        val stats = state.syncState.stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Projects: ${stats.projectsSynced} | Tasks: ${stats.tasksSynced} | VPS: ${stats.serversConfigured}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (state.syncState.lastSyncedMillis > 0L) {
                                val formatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(state.syncState.lastSyncedMillis))
                                Text(
                                    text = "Synced: $formatted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (state.syncState.lastError != null) {
                            Text(
                                text = state.syncState.lastError ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        VmButton(
                            text = if (state.syncState.isSyncing) "Syncing..." else "Sync Now",
                            onClick = viewModel::syncNow,
                            style = VmButtonStyle.Secondary,
                            icon = Icons.Default.Sync,
                            enabled = !state.syncState.isSyncing,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 2. Host VPS SSH Runner Card
            item(key = "vps-runner") {
                VmCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = "VPS SSH Remote Runner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = if (state.servers.isNotEmpty()) VmStatus.CONNECTED else VmStatus.IDLE,
                                label = if (state.servers.isNotEmpty()) "Configured" else "Inactive",
                            )
                        }

                        Text(
                            text = "Direct non-interactive bash tool execution via workspace_exec_command and interactive terminal sessions on managed VPS instances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        VmButton(
                            text = "Open Terminal",
                            onClick = onOpenTerminal,
                            style = VmButtonStyle.Tertiary,
                            icon = Icons.Default.Terminal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // 3. Header for Discovered MCP Tools
            item(key = "mcp-header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VmSectionHeader(
                        title = "Discovered MCP Tools (${state.mcpTools.size})",
                    )
                    IconButton(
                        onClick = viewModel::loadMcpTools,
                        enabled = !state.isLoadingMcp,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Tools")
                    }
                }
            }

            if (state.isLoadingMcp) {
                item(key = "mcp-loading") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (state.mcpTools.isEmpty()) {
                item(key = "mcp-empty") {
                    VmCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.mcpError ?: "No active MCP tools discovered on VM Project Hub.",
                            modifier = Modifier.padding(spacing.md),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(state.mcpTools, key = { it.name }) { tool ->
                    VmCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(spacing.sm)) {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (!tool.description.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = tool.description ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
