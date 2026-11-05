package digital.vmstudio.code.feature.servers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.network.radar.DiscoveredHost
import digital.vmstudio.code.core.network.radar.DiscoveredPort
import digital.vmstudio.code.core.network.radar.ServiceCategory
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun ServersScreen(
    onAddServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ServersContent(
        state = state,
        onAddServer = onAddServer,
        onOpenServer = onOpenServer,
        onEditServer = onEditServer,
        onDeleteServer = viewModel::deleteServer,
        onDismissError = viewModel::dismissError,
        onStartRadarScan = viewModel::startRadarScan,
        onStopRadarScan = viewModel::stopRadarScan,
        onQuickAddServer = viewModel::quickAddServer,
        onGetConnectCode = viewModel::getConnectCode,
        modifier = modifier,
    )
}

internal data class QuickConnectTarget(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
)

@Composable
internal fun ServersContent(
    state: ServersUiState,
    onAddServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    onEditServer: (String) -> Unit,
    onDeleteServer: (String) -> Unit,
    onDismissError: () -> Unit,
    onStartRadarScan: () -> Unit,
    onStopRadarScan: () -> Unit,
    onQuickAddServer: (String, String, Int, String, String, (String) -> Unit) -> Unit,
    onGetConnectCode: (DiscoveredHost, DiscoveredPort) -> String,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    var pendingDeletion by remember { mutableStateOf<Server?>(null) }
    var showRadarDialog by remember { mutableStateOf(false) }
    var quickConnectTarget by remember { mutableStateOf<QuickConnectTarget?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        showRadarDialog = true
                        onStartRadarScan()
                    },
                    icon = { Icon(Icons.Default.Radar, contentDescription = null, tint = Color(0xFF38BDF8)) },
                    text = { Text("Dev Radar") },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                ExtendedFloatingActionButton(
                    onClick = onAddServer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screenHorizontal,
                end = spacing.screenHorizontal,
                top = spacing.sm,
                bottom = spacing.xxxl + spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Cluster Quick Connect Banner with Tailscale Zero-Config IPs
            item(key = "cluster_banner") {
                ClusterNodesBanner(
                    onAddLaptop1 = {
                        val existing = state.servers.firstOrNull {
                            it.host == "100.107.28.68" || it.name.contains("Laptop 1", ignoreCase = true)
                        }
                        if (existing != null) {
                            onOpenServer(existing.id)
                        } else {
                            quickConnectTarget = QuickConnectTarget(
                                name = "Laptop 1 (MSI Orchestrator)",
                                host = "100.107.28.68",
                                port = 22,
                                username = "tripc",
                            )
                        }
                    },
                    onAddLaptop2 = {
                        val existing = state.servers.firstOrNull {
                            it.host == "100.123.215.64" || it.name.contains("Laptop 2", ignoreCase = true)
                        }
                        if (existing != null) {
                            onOpenServer(existing.id)
                        } else {
                            quickConnectTarget = QuickConnectTarget(
                                name = "Laptop 2 (High-Compute GPU)",
                                host = "100.123.215.64",
                                port = 22,
                                username = "vikra",
                            )
                        }
                    },
                    onOpenRadar = {
                        showRadarDialog = true
                        onStartRadarScan()
                    }
                )
            }

            state.error?.let { error ->
                item(key = "error") {
                    VmErrorPanel(
                        error = error,
                        actions = listOf(
                            digital.vmstudio.code.core.ui.component.VmErrorAction(
                                label = "Dismiss",
                                onClick = onDismissError,
                            ),
                        ),
                    )
                }
            }

            if (state.isEmpty) {
                item(key = "empty") {
                    VmEmptyState(
                        icon = Icons.Default.Dns,
                        title = "No custom servers yet",
                        description = "Tap on Laptop 1 or Laptop 2 above to connect instantly with your closed-lid setup, or use Dev Radar to scan nearby devices.",
                        actionLabel = "Add manual server",
                        onAction = onAddServer,
                    )
                }
            } else {
                state.grouped.forEach { (group, servers) ->
                    if (state.groups.isNotEmpty()) {
                        item(key = "group-${group?.id ?: "ungrouped"}") {
                            VmSectionHeader(title = group?.name ?: "Ungrouped")
                        }
                    }
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onClick = { onOpenServer(server.id) },
                            onEdit = { onEditServer(server.id) },
                            onDelete = { pendingDeletion = server },
                        )
                    }
                }
            }
        }
    }

    if (showRadarDialog) {
        RadarScanDialog(
            isScanning = state.isScanningRadar,
            discoveredHosts = state.discoveredHosts,
            onDismiss = {
                showRadarDialog = false
                onStopRadarScan()
            },
            onRescan = onStartRadarScan,
            onQuickAdd = { host, port ->
                val existing = state.servers.firstOrNull { it.host == host.ip }
                if (existing != null) {
                    showRadarDialog = false
                    onOpenServer(existing.id)
                } else {
                    showRadarDialog = false
                    quickConnectTarget = QuickConnectTarget(
                        name = host.hostname ?: "Node ${host.ip}",
                        host = host.ip,
                        port = port.port,
                        username = "tripc",
                    )
                }
            },
            onGetConnectCode = onGetConnectCode
        )
    }

    quickConnectTarget?.let { target ->
        QuickConnectPasswordDialog(
            target = target,
            onDismiss = { quickConnectTarget = null },
            onConnect = { password ->
                onQuickAddServer(
                    target.name,
                    target.host,
                    target.port,
                    target.username,
                    password,
                ) { serverId ->
                    quickConnectTarget = null
                    onOpenServer(serverId)
                }
            },
            onOpenManual = {
                quickConnectTarget = null
                onAddServer()
            },
        )
    }

    pendingDeletion?.let { server ->
        VmDialog(
            title = "Delete ${server.name}?",
            onDismiss = { pendingDeletion = null },
            confirmLabel = "Delete",
            onConfirm = {
                onDeleteServer(server.id)
                pendingDeletion = null
            },
            destructive = true,
        ) {
            Text(
                text = "This removes the server and its stored credentials from this device. " +
                    "Nothing on the server itself is changed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ClusterNodesBanner(
    onAddLaptop1: () -> Unit,
    onAddLaptop2: () -> Unit,
    onOpenRadar: () -> Unit
) {
    VmCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Hardware Cluster Nodes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Lid Closed 24/7",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Autonomous power setup is active on both laptops. Connect instantly:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onAddLaptop1,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Laptop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Laptop 1 (MSI)", fontSize = 12.sp, maxLines = 1)
                }

                FilledTonalButton(
                    onClick = onAddLaptop2,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEAB308))
                    Spacer(Modifier.width(6.dp))
                    Text("Laptop 2 (GPU)", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RadarScanDialog(
    isScanning: Boolean,
    discoveredHosts: List<DiscoveredHost>,
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
    onQuickAdd: (DiscoveredHost, DiscoveredPort) -> Unit,
    onGetConnectCode: (DiscoveredHost, DiscoveredPort) -> String
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Dev Radar Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRescan) {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                        }
                    }
                }

                Text(
                    text = if (isScanning) "Scanning local Wi-Fi & Tailscale for SSH, Ollama, ADB, and dev servers..."
                           else "Scan complete. Found ${discoveredHosts.size} active devices on your network:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (discoveredHosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "Probing ports..." else "No active services found on subnet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredHosts, key = { it.ip }) { host ->
                            DiscoveredHostCard(
                                host = host,
                                onQuickAdd = onQuickAdd,
                                onCopyCode = { port ->
                                    val code = onGetConnectCode(host, port)
                                    clipboardManager.setText(AnnotatedString(code))
                                    Toast.makeText(context, "Copied connection code for ${host.ip}:${port.port}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveredHostCard(
    host: DiscoveredHost,
    onQuickAdd: (DiscoveredHost, DiscoveredPort) -> Unit,
    onCopyCode: (DiscoveredPort) -> Unit
) {
    VmCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = host.hostname ?: "Host ${host.ip}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = host.ip,
                    style = VmTheme.code.mono,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            host.openPorts.forEach { port ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${port.port} • ${port.serviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Code",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onCopyCode(port) }
                                .padding(4.dp)
                        )

                        if (port.category == ServiceCategory.SSH) {
                            Text(
                                text = "Pair",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF22C55E),
                                modifier = Modifier
                                    .clickable { onQuickAdd(host, port) }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    server: Server,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = VmTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    VmCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (server.isProduction) {
                        "${server.displayTarget} • Production"
                    } else {
                        server.displayTarget
                    },
                    style = VmTheme.code.mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Actions for ${server.name}",
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickConnectPasswordDialog(
    target: QuickConnectTarget,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenManual: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${target.username}@${target.host}:${target.port}",
                            style = VmTheme.code.mono,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Text(
                        text = "Enter Windows or SSH password to connect securely over WireGuard Tailscale mesh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp),
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Windows user password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotBlank()) onConnect(password)
                        },
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenManual) {
                        Text("Manual Setup", fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onConnect(password) },
                            enabled = password.isNotBlank(),
                        ) {
                            Text("Connect")
                        }
                    }
                }
            }
        }
    }
}
