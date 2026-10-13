package digital.vmstudio.code.feature.connectors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(
    onNavigateBack: () -> Unit,
    onOpenServers: () -> Unit,
    onOpenGitHubSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    var sheetToken by remember { mutableStateOf("") }
    var spreadsheetId by remember { mutableStateOf("") }
    var sheetRange by remember { mutableStateOf("Sheet1!A1:E5") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cloud & Connectors Hub") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            item(key = "connectors-intro") {
                Text(
                    text = "Manage external integrations, remote cloud execution environments, Google workspace services, and code repositories connected to X-codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // VPS Remote SSH Runner
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
                                status = if (state.servers.isNotEmpty()) {
                                    VmStatus.CONNECTED
                                } else {
                                    VmStatus.IDLE
                                },
                                label = if (state.servers.isNotEmpty()) {
                                    "${state.servers.size} Server${if (state.servers.size > 1) "s" else ""}"
                                } else {
                                    "Inactive"
                                },
                            )
                        }

                        Text(
                            text = "Executes agent CLI commands, shell scripts, and remote terminal sessions on your Linux servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (state.servers.isNotEmpty()) {
                            Column(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                state.servers.take(3).forEach { server ->
                                    Text(
                                        text = "• ${server.name} (${server.username}@${server.host}:${server.port})",
                                        style = VmTheme.code.mono,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }

                        VmButton(
                            text = if (state.servers.isNotEmpty()) "Manage Servers" else "Add Server",
                            onClick = onOpenServers,
                            style = VmButtonStyle.Primary,
                            icon = Icons.Default.Settings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // GitHub Repositories & Codespaces
            item(key = "github-connector") {
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
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = "GitHub Integration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = if (state.githubRepoCount != null) {
                                    VmStatus.CONNECTED
                                } else {
                                    VmStatus.IDLE
                                },
                                label = if (state.githubRepoCount != null) "Connected" else "Unlinked",
                            )
                        }

                        Text(
                            text = state.githubStatusMessage
                                ?: "Connect your GitHub Personal Access Token to browse repositories and launch Codespaces.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        VmButton(
                            text = if (state.githubRepoCount != null) "Configure Token" else "Connect GitHub Account",
                            onClick = onOpenGitHubSettings,
                            style = VmButtonStyle.Secondary,
                            icon = Icons.Default.Settings,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // VM Project Hub (vmstudio.digital/wp-json/xcodes/v1)
            item(key = "vm-project-hub-connector") {
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
                                    text = "VM Project Hub (Universal)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = VmStatus.CONNECTED,
                                label = state.hubProjectCount?.let { "$it Projects" } ?: "Online",
                            )
                        }

                        Text(
                            text = state.hubStatusMessage
                                ?: "Universal Portfolio & Kanban Task Sync via vmstudio.digital/wp-json/xcodes/v1. Automatically maps GitHub repos, partial projects, and existing or future VPS servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        VmButton(
                            text = if (state.isSyncingHub) "Syncing Universal Workspaces…" else "Sync Universal Workspaces & Tasks",
                            onClick = viewModel::syncUniversalHubProjects,
                            enabled = !state.isSyncingHub,
                            style = VmButtonStyle.Secondary,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // VM Studio OmniRoute AI Cloud Gateway
            item(key = "omniroute-cloud-connector") {
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
                                    text = "VM Studio OmniRoute Cloud",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = VmStatus.CONNECTED,
                                label = "Live Synced",
                            )
                        }

                        Text(
                            text = "Connected to https://ai.vmstudio.digital with separated Smart Auto, Free Models (0-cost live sync), and Failover Combos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Google Cloud & Workspace Account
            item(key = "google-cloud-connector") {
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
                                    imageVector = Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = "Google Account & Cloud Sync",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            VmStatusBadge(
                                status = VmStatus.CONNECTED,
                                label = "Authenticated",
                            )
                        }

                        Text(
                            text = "Signed in via Google. Cloud profile and workspace identity are active without requiring manual service-account JSON or OAuth tokens.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
