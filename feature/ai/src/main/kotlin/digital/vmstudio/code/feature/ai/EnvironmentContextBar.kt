package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.project.ClusterNode
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Dynamic Environment Pills displayed in the Chat screen.
 * Shows live target contexts: Active Project/Repo, VPS Server target, Google Cloud Connector,
 * and Live Hardware Cluster / Phone RAM Shadow accelerator.
 */
@Composable
fun EnvironmentContextBar(
    workingDirectory: String,
    serverName: String?,
    githubRepoName: String?,
    clusterNodes: List<ClusterNode> = emptyList(),
    onOpenProjectPicker: () -> Unit,
    onOpenServerPicker: () -> Unit,
    onOpenConnectors: () -> Unit,
    onRefreshCluster: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showClusterDialog by remember { mutableStateOf(false) }

    if (showClusterDialog) {
        ClusterInfoDialog(
            nodes = clusterNodes,
            onDismiss = { showClusterDialog = false },
            onRefresh = onRefreshCluster,
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = VmTheme.spacing.md, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Context Pill 1: GitHub / VM Hub / Project Workspace
            val isGitHub = !githubRepoName.isNullOrBlank() || workingDirectory.startsWith("/github/")
            val isVmHub = workingDirectory.startsWith("/vmhub/")
            val projectLabel = when {
                !githubRepoName.isNullOrBlank() -> "GitHub: $githubRepoName"
                workingDirectory.startsWith("/github/") -> {
                    val slug = workingDirectory.removePrefix("/github/").trimEnd('/')
                    "GitHub: $slug"
                }
                isVmHub -> {
                    val slug = workingDirectory.removePrefix("/vmhub/").trimEnd('/')
                    "VM Hub: $slug"
                }
                workingDirectory.isNotBlank() && workingDirectory != "/" -> {
                    workingDirectory.trimEnd('/').substringAfterLast('/')
                }
                else -> "Workspace: General"
            }
            val (pillIcon, pillTag, pillColor) = when {
                isGitHub -> Triple(Icons.Default.Code, "GITHUB", Color(0xFF6366F1))
                isVmHub -> Triple(Icons.Default.AccountTree, "VM HUB", Color(0xFFEC4899))
                else -> Triple(Icons.Default.Folder, "DIR", MaterialTheme.colorScheme.primary)
            }
            ContextPill(
                icon = pillIcon,
                label = projectLabel,
                tag = pillTag,
                accentColor = pillColor,
                onClick = onOpenProjectPicker,
            )

            // Context Pill 2: Environment Server Target
            val vpsLabel = serverName?.ifBlank { null } ?: "Local / Cloud AI"
            val isLaptopOrPc = serverName != null && (
                serverName.contains("Laptop", ignoreCase = true) ||
                serverName.contains("PC", ignoreCase = true) ||
                serverName.contains("MSI", ignoreCase = true) ||
                serverName.contains("Mac", ignoreCase = true) ||
                serverName.contains("Workstation", ignoreCase = true)
            )
            val isPhone = serverName != null && (
                serverName.contains("Phone", ignoreCase = true) ||
                serverName.contains("Local", ignoreCase = true)
            )
            val (serverTag, serverColor, serverIcon) = when {
                serverName == null -> Triple("CLOUD", Color(0xFF8B5CF6), Icons.Default.Cloud)
                isPhone -> Triple("PHONE", Color(0xFF06B6D4), Icons.Default.Smartphone)
                isLaptopOrPc -> Triple("PC/LAN", Color(0xFFF59E0B), Icons.Default.Computer)
                else -> Triple("VPS", Color(0xFF10B981), Icons.Default.Dns)
            }
            ContextPill(
                icon = serverIcon,
                label = vpsLabel,
                tag = serverTag,
                accentColor = serverColor,
                onClick = onOpenServerPicker,
            )

            // Context Pill 3: Hardware Cluster & Phone Accelerator
            val onlineLaptop = clusterNodes.firstOrNull { it.isOnline && it.id.contains("laptop") }
            val (clusterTag, clusterLabel, clusterColor) = when {
                onlineLaptop != null -> Triple(
                    "ACCEL",
                    "${onlineLaptop.name.substringBefore(" (")} • ${onlineLaptop.latencyMs}ms",
                    Color(0xFF10B981),
                )
                else -> Triple(
                    "PHONE",
                    "RAM Cache • 0ms",
                    Color(0xFF06B6D4),
                )
            }
            ContextPill(
                icon = Icons.Default.AutoAwesome,
                label = clusterLabel,
                tag = clusterTag,
                accentColor = clusterColor,
                onClick = { showClusterDialog = true },
            )

            // Context Pill 4: Google Drive & Connectors
            ContextPill(
                icon = Icons.Default.Cloud,
                label = "G-Suite & Sheets",
                tag = "CONNECT",
                accentColor = Color(0xFF3B82F6),
                onClick = onOpenConnectors,
            )
        }
    }
}

@Composable
private fun ClusterInfoDialog(
    nodes: List<ClusterNode>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFF06B6D4),
                )
                Text("Autonomous Cluster & Nodes")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Guarantee Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                ) {
                    Text(
                        text = "Zero-Laptop Dependency: Phone and VPS are 100% autonomous 24/7. Laptops act as opportunistic local accelerators that drop gracefully without breaking your work.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                // Phone Memory Node (Always Local & Autonomous)
                NodeRow(
                    name = "Phone Memory (Autonomous Engine)",
                    specs = "UFS Storage + RAM Shadow Cache • 0ms read latency",
                    status = "online",
                    statusLabel = "Active",
                    isMaster = true,
                )

                // VPS Master Backbone
                NodeRow(
                    name = "VPS Master (24/7 Backbone)",
                    specs = "200.234.41.231 • Always-On Autonomous Queue",
                    status = "online",
                    statusLabel = "24/7 Live",
                    isMaster = true,
                )

                // Laptops from Cluster Telemetry
                if (nodes.isEmpty()) {
                    Text(
                        "Connecting to VM Project Hub cluster detector...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    nodes.filterNot { it.id == "vps-master" }.forEach { node ->
                        NodeRow(
                            name = node.name,
                            specs = "${node.specs} • ${node.ip}${if (node.latencyMs > 0) " (${node.latencyMs}ms)" else ""}",
                            status = node.status,
                            statusLabel = if (node.isOnline) "Live Wi-Fi" else "Standby (Droppable)",
                            isMaster = false,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onRefresh()
                onDismiss()
            }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Refresh Nodes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun NodeRow(
    name: String,
    specs: String,
    status: String,
    statusLabel: String,
    isMaster: Boolean,
) {
    val isOnline = status.equals("online", ignoreCase = true)
    val dotColor = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = specs,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = dotColor.copy(alpha = 0.15f),
            ) {
                Text(
                    text = statusLabel,
                    color = dotColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ContextPill(
    icon: ImageVector,
    label: String,
    tag: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(
                text = tag,
                color = accentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
