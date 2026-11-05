package digital.vmstudio.code.feature.connectors

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.core.ai.mcp.CuratedConnector
import digital.vmstudio.code.core.ai.mcp.CuratedConnectorsCatalog
import digital.vmstudio.code.core.ai.mcp.McpServerConfig
import digital.vmstudio.code.core.ai.mcp.McpToolDefinition

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
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    // Expanded card states
    var githubExpanded by remember { mutableStateOf(false) }
    var githubTokenDraft by remember { mutableStateOf("") }
    var githubTokenVisible by remember { mutableStateOf(false) }

    var sheetsExpanded by remember { mutableStateOf(false) }
    var sheetsTokenDraft by remember { mutableStateOf("") }
    var sheetsTokenVisible by remember { mutableStateOf(false) }
    var sheetsIdDraft by remember { mutableStateOf("") }
    var sheetsRangeDraft by remember { mutableStateOf("Sheet1!A1:E5") }

    var gmailExpanded by remember { mutableStateOf(false) }
    var playstoreExpanded by remember { mutableStateOf(false) }

    var driveExpanded by remember { mutableStateOf(false) }
    var driveTokenDraft by remember { mutableStateOf("") }
    var driveTokenVisible by remember { mutableStateOf(false) }

    var hubExpanded by remember { mutableStateOf(false) }
    var hubKeyDraft by remember { mutableStateOf("") }
    var hubKeyVisible by remember { mutableStateOf(false) }
    var hubBaseUrlDraft by remember(state.hubBaseUrl) {
        mutableStateOf(if (state.hubBaseUrl.isNotBlank()) state.hubBaseUrl else "https://vmstudio.digital")
    }

    var aiExpanded by remember { mutableStateOf(false) }
    var aiKeyDraft by remember { mutableStateOf("") }
    var aiKeyVisible by remember { mutableStateOf(false) }

    var evolutionExpanded by remember { mutableStateOf(false) }
    var evolutionBaseUrlDraft by remember(state.evolutionApiBaseUrl) { mutableStateOf(state.evolutionApiBaseUrl) }
    var evolutionInstanceDraft by remember(state.evolutionApiInstance) { mutableStateOf(state.evolutionApiInstance) }
    var evolutionApiKeyDraft by remember(state.savedEvolutionApiKey) { mutableStateOf(state.savedEvolutionApiKey.orEmpty()) }
    var evolutionApiKeyVisible by remember { mutableStateOf(false) }
    var evolutionRecipientDraft by remember(state.evolutionApiRecipientNumber) { mutableStateOf(state.evolutionApiRecipientNumber) }
    var evolutionNotificationsDraft by remember(state.evolutionApiNotificationsEnabled) { mutableStateOf(state.evolutionApiNotificationsEnabled) }

    var mcpExpanded by remember { mutableStateOf(false) }
    var mcpNameDraft by remember { mutableStateOf("") }
    var mcpUrlDraft by remember { mutableStateOf("") }
    var mcpApiKeyDraft by remember { mutableStateOf("") }
    var mcpApiKeyVisible by remember { mutableStateOf(false) }
    var mcpTypeDraft by remember { mutableStateOf("HTTP") }
    var mcpProjectPathDraft by remember { mutableStateOf("") }
    var mcpIsAddingCustom by remember { mutableStateOf(false) }
    var mcpIsAddingWp by remember { mutableStateOf(false) }
    var wpUrlDraft by remember { mutableStateOf("") }
    var wpKeyDraft by remember { mutableStateOf("") }
    var wpKeyVisible by remember { mutableStateOf(false) }
    var wpProjectPathDraft by remember { mutableStateOf("") }
    var curatedDialogConnector by remember { mutableStateOf<CuratedConnector?>(null) }
    var curatedApiKeyDraft by remember { mutableStateOf("") }
    var curatedEndpointDraft by remember { mutableStateOf("") }
    var curatedApiKeyVisible by remember { mutableStateOf(false) }
    var editingMcpServer by remember { mutableStateOf<McpServerConfig?>(null) }

    LaunchedEffect(state.savedDriveToken) {
        state.savedDriveToken?.let { if (driveTokenDraft.isBlank()) driveTokenDraft = it }
    }
    LaunchedEffect(state.savedSheetsToken, state.savedSpreadsheetId) {
        state.savedSheetsToken?.let { if (sheetsTokenDraft.isBlank()) sheetsTokenDraft = it }
        state.savedSpreadsheetId?.let { if (sheetsIdDraft.isBlank()) sheetsIdDraft = it }
    }

    val openUrl = { url: String ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No web browser found to open link.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Connectors & Integrations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Cloud, Git VCS, Google Workspace & AI Providers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.testAiGateway()
                        viewModel.testGitHub()
                        viewModel.refreshProjectHubStatus()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh all connectors")
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
            // Category Filter Chips
            item(key = "category-filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConnectorFilterChip(
                        label = "All Integrations",
                        selected = state.selectedFilter == ConnectorFilter.ALL,
                        onClick = { viewModel.selectFilter(ConnectorFilter.ALL) },
                    )
                    ConnectorFilterChip(
                        label = "Git & VCS",
                        selected = state.selectedFilter == ConnectorFilter.GIT_VCS,
                        onClick = { viewModel.selectFilter(ConnectorFilter.GIT_VCS) },
                    )
                    ConnectorFilterChip(
                        label = "Cloud & Servers",
                        selected = state.selectedFilter == ConnectorFilter.CLOUD_SERVERS,
                        onClick = { viewModel.selectFilter(ConnectorFilter.CLOUD_SERVERS) },
                    )
                    ConnectorFilterChip(
                        label = "Data & Sheets",
                        selected = state.selectedFilter == ConnectorFilter.DATA_SHEETS,
                        onClick = { viewModel.selectFilter(ConnectorFilter.DATA_SHEETS) },
                    )
                    ConnectorFilterChip(
                        label = "AI Gateways",
                        selected = state.selectedFilter == ConnectorFilter.AI_GATEWAY,
                        onClick = { viewModel.selectFilter(ConnectorFilter.AI_GATEWAY) },
                    )
                    ConnectorFilterChip(
                        label = "WhatsApp (Evolution)",
                        selected = state.selectedFilter == ConnectorFilter.WHATSAPP,
                        onClick = { viewModel.selectFilter(ConnectorFilter.WHATSAPP) },
                    )
                    ConnectorFilterChip(
                        label = "Model Context Protocol (MCP)",
                        selected = state.selectedFilter == ConnectorFilter.MCP,
                        onClick = { viewModel.selectFilter(ConnectorFilter.MCP) },
                    )
                }
            }

            // --- HERO: APP HUB MASTER SINGLE-KEY PROVISIONING ---
            if (state.selectedFilter == ConnectorFilter.ALL) {
                item(key = "card-hub-master-hero") {
                    val hubAccent = if (state.hasHubKey) {
                        VmTheme.colors.success
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, hubAccent.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(hubAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        tint = hubAccent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "App Hub Master Connect",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "1 API Key automatically configures GitHub, AI Gateway, VPS & Drive",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = hubAccent.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, hubAccent.copy(alpha = 0.3f)),
                                ) {
                                    Text(
                                        text = if (state.hasHubKey) "PROVISIONED" else "READY TO PAIR",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = hubAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = hubKeyDraft,
                                onValueChange = { hubKeyDraft = it },
                                label = { Text("App Hub Master API Key (xc_...)") },
                                placeholder = { Text("Enter your Hub pairing key") },
                                singleLine = true,
                                visualTransformation = if (hubKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            clipboardManager.getText()?.text?.let { hubKeyDraft = it.trim() }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                        }
                                        IconButton(onClick = { hubKeyVisible = !hubKeyVisible }) {
                                            Icon(
                                                imageVector = if (hubKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VmButton(
                                    text = if (state.isSyncingHub) "Provisioning All Services…" else "Connect & Provision Everything",
                                    onClick = { viewModel.pairHubKey(hubKeyDraft) },
                                    loading = state.isSyncingHub,
                                    enabled = hubKeyDraft.isNotBlank() && !state.isSyncingHub,
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.hasHubKey) {
                                    OutlinedButton(
                                        onClick = { viewModel.unpairHub() },
                                        shape = RoundedCornerShape(8.dp),
                                    ) {
                                        Text("Unpair", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            state.hubStatusMessage?.let { msg ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. GitHub Integration (Git & VCS)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.GIT_VCS) {
                item(key = "card-github") {
                    ModernConnectorCard(
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF9C27B0),
                        title = "GitHub",
                        category = "Version Control & Codespaces",
                        status = if (state.hasGithubToken) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = when {
                            state.isTestingGithub -> "Validating…"
                            state.hasGithubToken -> state.githubRepoCount?.let { "$it repos" } ?: "Connected"
                            else -> "Unlinked"
                        },
                        description = "Sync repositories, browse file trees, clone workspaces, and launch GitHub Codespaces directly.",
                        tags = listOf("repo", "read:user", "OAuth/PAT", "REST v3"),
                        isExpanded = githubExpanded,
                        onToggleExpand = { githubExpanded = !githubExpanded },
                        statusMessage = state.githubStatusMessage,
                        primaryActionText = if (state.hasGithubToken) "Configure" else "Connect Account",
                        onPrimaryAction = { githubExpanded = !githubExpanded },
                        secondaryActionText = if (state.hasGithubToken) "Test Link" else null,
                        onSecondaryAction = { viewModel.testGitHub() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "1-Click Token Connection",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Authenticate with a GitHub Personal Access Token. Tap 'Get Token' to open GitHub with the exact permissions pre-selected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = githubTokenDraft,
                                onValueChange = { githubTokenDraft = it },
                                label = { Text("Personal Access Token (ghp_…)") },
                                singleLine = true,
                                visualTransformation = if (githubTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { githubTokenVisible = !githubTokenVisible }) {
                                            Icon(
                                                imageVector = if (githubTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (githubTokenVisible) "Hide token" else "Show token",
                                            )
                                        }
                                        IconButton(onClick = {
                                            clipboardManager.getText()?.text?.let { githubTokenDraft = it.trim() }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VmButton(
                                    text = if (state.isTestingGithub) "Saving…" else "Save & Connect",
                                    onClick = {
                                        viewModel.saveGitHubToken(githubTokenDraft)
                                        githubTokenDraft = ""
                                    },
                                    enabled = githubTokenDraft.isNotBlank() && !state.isTestingGithub,
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = {
                                        openUrl("https://github.com/settings/tokens/new?scopes=repo,read:user&description=X-codes%20Android")
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Get Token", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (state.hasGithubToken) {
                                OutlinedButton(
                                    onClick = onOpenGitHubSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Advanced GitHub Settings")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearGitHubToken() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Disconnect GitHub Account", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // 2. VPS SSH Remote Runner (Cloud & Servers)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.CLOUD_SERVERS) {
                item(key = "card-vps") {
                    ModernConnectorCard(
                        icon = Icons.Default.Terminal,
                        iconTint = Color(0xFF00897B),
                        title = "Linux VPS Servers",
                        category = "SSH Remote Compute & Execution",
                        status = if (state.servers.isNotEmpty()) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.servers.isNotEmpty()) "${state.servers.size} server${if (state.servers.size > 1) "s" else ""}" else "No servers",
                        description = "Direct terminal shells, background command runner, file browser, and agent subprocess execution on your servers.",
                        tags = listOf("SSH-2.0", "ed25519", "PTY Terminal", "Remote Docker/Gradle"),
                        isExpanded = false,
                        onToggleExpand = onOpenServers,
                        primaryActionText = if (state.servers.isNotEmpty()) "Manage Servers" else "Add Server",
                        onPrimaryAction = onOpenServers,
                    ) {
                        // Expandable body not needed since onToggleExpand directly opens servers management
                    }
                }
            }

            // 3. VM Project Hub (Universal Portfolio & Tasks)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.CLOUD_SERVERS) {
                item(key = "card-vm-hub") {
                    ModernConnectorCard(
                        icon = Icons.Default.Cloud,
                        iconTint = Color(0xFF0288D1),
                        title = "VM Project Hub",
                        category = "Universal Workspace & Kanban Sync",
                        status = if (state.hasHubKey) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.hasHubKey) {
                            val projPart = state.hubProjectCount?.let { "$it workspaces" } ?: "Paired"
                            val vpsPart = if (state.vpsConnectionState.isConnected) " · VPS Live" else ""
                            "$projPart$vpsPart"
                        } else {
                            "Not Paired"
                        },
                        description = "Synchronizes universal project repositories and Kanban sprint tasks across local storage, GitHub, and VPS environments with automated VPS connectivity.",
                        tags = listOf("Universal Portfolios", "VPS Auto-Connect", "Kanban Tasks", "Zero-Trust Pairing"),
                        isExpanded = hubExpanded,
                        onToggleExpand = { hubExpanded = !hubExpanded },
                        statusMessage = state.hubStatusMessage,
                        primaryActionText = if (state.hasHubKey) {
                            if (state.isSyncingHub) "Syncing Workspaces…" else "Sync Workspaces & Tasks"
                        } else {
                            "Pair Device"
                        },
                        onPrimaryAction = {
                            if (state.hasHubKey) {
                                viewModel.syncUniversalHubProjects()
                            } else {
                                hubExpanded = true
                            }
                        },
                        secondaryActionText = if (state.hasHubKey) "Sync Credentials & APIs" else null,
                        onSecondaryAction = if (state.hasHubKey) {
                            { viewModel.syncCredentialsFromHub() }
                        } else null,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                text = if (state.hasHubKey) "Hub Pairing Configuration" else "Pair with VM Project Hub",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (state.hasHubKey) {
                                    val domain = state.hubBaseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
                                    "Your device is paired with $domain. Single API key provisioning synchronizes GitHub, AI, VPS, and Drive."
                                } else {
                                    "Enter your App Hub API Key to automatically connect and provision all services from your Project Hub."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = hubBaseUrlDraft,
                                onValueChange = { hubBaseUrlDraft = it },
                                label = { Text("Hub Server Base URL") },
                                placeholder = { Text("https://vmstudio.digital or https://mathnotes.courses") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        clipboardManager.getText()?.text?.let { hubBaseUrlDraft = it.trim() }
                                    }) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Quick Domain Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ConnectorFilterChip(
                                    selected = hubBaseUrlDraft.contains("vmstudio.digital", ignoreCase = true),
                                    onClick = { hubBaseUrlDraft = "https://vmstudio.digital" },
                                    label = "vmstudio.digital",
                                )
                                ConnectorFilterChip(
                                    selected = hubBaseUrlDraft.contains("mathnotes.courses", ignoreCase = true),
                                    onClick = { hubBaseUrlDraft = "https://mathnotes.courses" },
                                    label = "mathnotes.courses",
                                )
                            }

                            OutlinedTextField(
                                value = hubKeyDraft,
                                onValueChange = { hubKeyDraft = it },
                                label = { Text("App Hub Master API Key (xc_...)") },
                                placeholder = { Text("xc_...") },
                                singleLine = true,
                                visualTransformation = if (hubKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row {
                                        IconButton(onClick = {
                                            clipboardManager.getText()?.text?.let { hubKeyDraft = it.trim() }
                                        }) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
                                        }
                                        IconButton(onClick = { hubKeyVisible = !hubKeyVisible }) {
                                            Icon(
                                                imageVector = if (hubKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                VmButton(
                                    text = if (state.isSyncingHub) "Verifying…" else if (state.hasHubKey) "Update & Sync" else "Pair Device",
                                    onClick = { viewModel.pairHubKey(hubKeyDraft, hubBaseUrlDraft) },
                                    enabled = hubKeyDraft.isNotBlank() && !state.isSyncingHub,
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.hasHubKey) {
                                    VmButton(
                                        text = "Unpair",
                                        onClick = {
                                            viewModel.unpairHub()
                                            hubKeyDraft = ""
                                        },
                                        style = VmButtonStyle.Destructive,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }

                            // VPS Cloud Connectivity status & 1-tap action
                            HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cloud VPS Connectivity",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = if (state.vpsConnectionState.isConnected) {
                                            "Connected to ${state.vpsHost ?: "VM Studio VPS"} · Terminal & Workspaces ready"
                                        } else if (state.isConnectingVps) {
                                            "Establishing SSH connection to VPS…"
                                        } else {
                                            "Auto-connects on launch so you don't have to connect each time"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (state.vpsConnectionState.isConnected) {
                                    VmButton(
                                        text = "Disconnect",
                                        onClick = viewModel::disconnectHubVps,
                                        style = VmButtonStyle.Secondary,
                                    )
                                } else {
                                    VmButton(
                                        text = if (state.isConnectingVps) "Connecting…" else "Connect VPS",
                                        onClick = viewModel::connectHubVps,
                                        loading = state.isConnectingVps,
                                        style = VmButtonStyle.Primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Google Sheets (Data & Sheets)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.DATA_SHEETS) {
                item(key = "card-sheets") {
                    ModernConnectorCard(
                        icon = Icons.Default.TableChart,
                        iconTint = Color(0xFF2E7D32),
                        title = "Google Sheets",
                        category = "Data & Living Knowledge Base",
                        status = if (state.sheetPreviewRows.isNotEmpty() || state.hasSheetsToken) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.sheetPreviewRows.isNotEmpty()) "${state.sheetPreviewRows.size} rows cached" else if (state.hasSheetsToken) "Google Workspace Live" else "Available",
                        description = "Allows AI agents to read context tables, inspect tracking sheets, and append build logs to Google Spreadsheets.",
                        tags = listOf("Sheets v4", "Cell Ranges", "Table Read/Append", "Live Sync"),
                        isExpanded = sheetsExpanded,
                        onToggleExpand = { sheetsExpanded = !sheetsExpanded },
                        statusMessage = state.sheetStatusMessage,
                        primaryActionText = "Test & Query",
                        onPrimaryAction = { sheetsExpanded = !sheetsExpanded },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "Query or Inspect Google Sheet",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Enter an OAuth access token or service key with spreadsheet permissions to test queries directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = sheetsTokenDraft,
                                onValueChange = { sheetsTokenDraft = it },
                                label = { Text(if (state.hasDriveToken) "Google OAuth Token (optional - inherits Google Login)" else "Google OAuth Token / Service Key") },
                                singleLine = true,
                                visualTransformation = if (sheetsTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { sheetsTokenVisible = !sheetsTokenVisible }) {
                                        Icon(
                                            imageVector = if (sheetsTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OutlinedTextField(
                                value = sheetsIdDraft,
                                onValueChange = { sheetsIdDraft = it },
                                label = { Text("Spreadsheet ID (from URL)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OutlinedTextField(
                                value = sheetsRangeDraft,
                                onValueChange = { sheetsRangeDraft = it },
                                label = { Text("Cell Range (e.g. Sheet1!A1:E10)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VmButton(
                                    text = if (state.hasSheetsToken) "Update Saved Token" else "Save Settings",
                                    onClick = {
                                        viewModel.saveSheetsSettings(sheetsTokenDraft, sheetsIdDraft)
                                    },
                                    enabled = sheetsTokenDraft.isNotBlank() && sheetsIdDraft.isNotBlank(),
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.hasSheetsToken) {
                                    VmButton(
                                        text = "Forget",
                                        onClick = {
                                            viewModel.clearSheetsSettings()
                                            sheetsTokenDraft = ""
                                            sheetsIdDraft = ""
                                        },
                                        style = VmButtonStyle.Secondary,
                                    )
                                }
                            }

                            VmButton(
                                text = if (state.isTestingSheet) "Querying Sheets API…" else "Run Sheets Query Test",
                                onClick = {
                                    viewModel.testGoogleSheets(sheetsTokenDraft, sheetsIdDraft, sheetsRangeDraft)
                                },
                                enabled = (sheetsTokenDraft.isNotBlank() || state.hasDriveToken) && sheetsIdDraft.isNotBlank() && !state.isTestingSheet,
                                style = VmButtonStyle.Secondary,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (state.sheetPreviewRows.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Preview (Top Rows):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        state.sheetPreviewRows.forEachIndexed { idx, row ->
                                            Text(
                                                text = "${idx + 1}: ${row.joinToString(" | ")}",
                                                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4b. Google Gmail
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.DATA_SHEETS) {
                item(key = "card-gmail") {
                    ModernConnectorCard(
                        icon = Icons.AutoMirrored.Filled.Send,
                        iconTint = Color(0xFFD32F2F),
                        title = "Google Gmail",
                        category = "Inbox, Threads & Outreach Automation",
                        status = if (state.gmailUnreadCount != null || state.hasSheetsToken) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.gmailUnreadCount != null) "${state.gmailUnreadCount} unread" else if (state.hasSheetsToken) "Ready" else "Available",
                        description = "Enables AI agents to search email threads, inspect client inquiries, draft proposals, and send notifications via Gmail API v1.",
                        tags = listOf("Gmail v1", "Inbox Search", "Drafts", "RFC 2822 Send"),
                        isExpanded = gmailExpanded,
                        onToggleExpand = { gmailExpanded = !gmailExpanded },
                        statusMessage = state.gmailStatusMessage,
                        primaryActionText = "Check Inbox",
                        onPrimaryAction = {
                            val tok = state.savedSheetsToken ?: state.savedDriveToken.orEmpty()
                            if (tok.isNotBlank()) {
                                viewModel.testGmailConnection(tok)
                            } else {
                                gmailExpanded = !gmailExpanded
                            }
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "Gmail Mailbox Automation",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Connect with Google OAuth to enable your AI agents to inspect unread client inquiries, draft replies, and send emails via Gmail API v1.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VmButton(
                                text = if (state.isTestingGmail) "Checking Inbox…" else "Check Unread Messages",
                                onClick = {
                                    val tok = state.savedSheetsToken ?: state.savedDriveToken.orEmpty()
                                    viewModel.testGmailConnection(tok)
                                },
                                enabled = (state.savedSheetsToken ?: state.savedDriveToken) != null && !state.isTestingGmail,
                                style = VmButtonStyle.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 4c. Google Play Console
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.CLOUD_SERVERS) {
                item(key = "card-playstore") {
                    ModernConnectorCard(
                        icon = Icons.Default.PlayArrow,
                        iconTint = Color(0xFF00C853),
                        title = "Google Play Console",
                        category = "Android Release & CI/CD Rollouts",
                        status = if (state.playstoreAppsCount != null) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.playstoreAppsCount != null) "${state.playstoreAppsCount} Apps Linked" else "Available",
                        description = "Deploy signed AAB bundles directly from GitHub builds to Internal, Alpha, Beta & Production tracks and manage reviews.",
                        tags = listOf("API v3", "AAB Bundles", "Tracks & Rollouts", "Reviews & Replies"),
                        isExpanded = playstoreExpanded,
                        onToggleExpand = { playstoreExpanded = !playstoreExpanded },
                        statusMessage = state.playstoreStatusMessage,
                        primaryActionText = "Inspect Tracks",
                        onPrimaryAction = {
                            viewModel.testPlaystoreConnection()
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "Play Console Automation Pipeline",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Seamlessly integrated with X-Brain and GitHub Actions: build APK for Apps Hub, generate release AAB bundles, and publish rollouts directly to Google Play Store tracks.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VmButton(
                                text = if (state.isTestingPlaystore) "Inspecting Tracks…" else "Test Play Console Connection",
                                onClick = { viewModel.testPlaystoreConnection() },
                                enabled = !state.isTestingPlaystore,
                                style = VmButtonStyle.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // 5. Google Drive & Docs (Cloud Backups & File Browser)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.DATA_SHEETS) {
                item(key = "card-drive") {
                    ModernConnectorCard(
                        icon = Icons.Default.Storage,
                        iconTint = Color(0xFFF57C00),
                        title = "Google Drive & Docs",
                        category = "Drive Storage, Docs & Sheet Intelligence",
                        status = if (state.driveFiles.isNotEmpty() || state.driveBackups.isNotEmpty() || state.hasDriveToken) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.driveFiles.isNotEmpty()) "${state.driveFiles.size} items" else if (state.driveBackups.isNotEmpty()) "${state.driveBackups.size} archives" else if (state.hasDriveToken) "Connected" else "Ready",
                        description = "Browse cloud folders, inspect Google Docs & Sheets, and automate VPS project archives directly to Google Drive.",
                        tags = listOf("Drive v3", "Docs & Sheets", "Folder Explorer", "Cloud Vault"),
                        isExpanded = driveExpanded,
                        onToggleExpand = { driveExpanded = !driveExpanded },
                        statusMessage = state.driveStatusMessage,
                        primaryActionText = if (state.isBrowsingDrive) "Hide Browser" else "Browse Drive & Docs",
                        onPrimaryAction = {
                            driveExpanded = true
                            viewModel.toggleDriveBrowser()
                        },
                        secondaryActionText = if (state.isBackingUpDrive) "Backing up…" else "Sync Backups",
                        onSecondaryAction = {
                            driveExpanded = true
                            viewModel.refreshDriveBackups(driveTokenDraft)
                        },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "Google Drive account access",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Connect with Google to browse and read all Drive files and create backups. This grants full Drive access; you can revoke it in your Google Account settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            VmButton(
                                text = if (state.hasDriveToken) "Reconnect Google Drive" else "Connect Google Drive",
                                onClick = { openUrl(viewModel.googleDriveAuthorizationUrl()) },
                                style = VmButtonStyle.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "Or use an existing short-lived access token:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = driveTokenDraft,
                                onValueChange = { driveTokenDraft = it },
                                label = { Text("Temporary Google Drive access token") },
                                singleLine = true,
                                visualTransformation = if (driveTokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { driveTokenVisible = !driveTokenVisible }) {
                                        Icon(
                                            imageVector = if (driveTokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VmButton(
                                    text = if (state.hasDriveToken) "Update Token" else "Save Token",
                                    onClick = { viewModel.saveDriveToken(driveTokenDraft) },
                                    enabled = driveTokenDraft.isNotBlank(),
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                VmButton(
                                    text = if (state.isBrowsingDrive) "Refresh Folder" else "Open Explorer",
                                    onClick = {
                                        if (!state.isBrowsingDrive) {
                                            viewModel.toggleDriveBrowser()
                                        } else {
                                            viewModel.browseDriveFolder(state.driveBrowserFolderId, state.driveBrowserFolderName)
                                        }
                                    },
                                    enabled = driveTokenDraft.isNotBlank() || state.hasDriveToken,
                                    style = VmButtonStyle.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (state.hasDriveToken) {
                                    VmButton(
                                        text = "Forget",
                                        onClick = {
                                            viewModel.clearDriveToken()
                                            driveTokenDraft = ""
                                        },
                                        style = VmButtonStyle.Secondary,
                                    )
                                }
                            }

                            // Drive Folder & File Browser UI
                            if (state.isBrowsingDrive) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Browser Header & Breadcrumbs
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF57C00),
                                                    modifier = Modifier.size(18.dp),
                                                )
                                                Text(
                                                    text = "Drive",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable { viewModel.browseDriveFolder("root", "My Drive") },
                                                )
                                                state.driveFolderBreadcrumbs.filterNot { it.first == "root" }.forEach { crumb ->
                                                    Text("/", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(
                                                        text = crumb.second,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.clickable { viewModel.browseDriveFolder(crumb.first, crumb.second) },
                                                    )
                                                }
                                            }
                                            if (state.driveFolderBreadcrumbs.size > 1) {
                                                IconButton(
                                                    onClick = { viewModel.navigateDriveUp() },
                                                    modifier = Modifier.size(28.dp),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                        contentDescription = "Navigate Up",
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                            }
                                        }

                                        if (state.isLoadingDriveFiles) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                                Text("Fetching Drive contents…", style = MaterialTheme.typography.bodySmall)
                                            }
                                        } else if (state.driveFiles.isEmpty()) {
                                            Text(
                                                "Folder is empty.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 8.dp),
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                state.driveFiles.forEach { file ->
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { viewModel.previewDriveFile(file) },
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            ) {
                                                                val icon = when {
                                                                    file.isFolder -> Icons.Default.Folder
                                                                    file.isSheet -> Icons.Default.TableChart
                                                                    file.isDoc -> Icons.Default.Description
                                                                    else -> Icons.Default.Storage
                                                                }
                                                                val iconTint = when {
                                                                    file.isFolder -> Color(0xFFFFA000)
                                                                    file.isSheet -> Color(0xFF0F9D58)
                                                                    file.isDoc -> Color(0xFF4285F4)
                                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                                }
                                                                Icon(
                                                                    imageVector = icon,
                                                                    contentDescription = null,
                                                                    tint = iconTint,
                                                                    modifier = Modifier.size(18.dp),
                                                                )
                                                                Column {
                                                                    Text(
                                                                        text = file.name,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        fontWeight = FontWeight.Medium,
                                                                        maxLines = 1,
                                                                    )
                                                                    val badgeText = when {
                                                                        file.isFolder -> "Folder"
                                                                        file.isSheet -> "Google Sheet (Auto-CSV)"
                                                                        file.isDoc -> "Google Doc (Plain text)"
                                                                        file.sizeBytes > 0 -> "${file.sizeBytes / 1024} KB"
                                                                        else -> file.mimeType.substringAfterLast("/")
                                                                    }
                                                                    Text(
                                                                        text = badgeText,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    )
                                                                }
                                                            }
                                                            if (file.isFolder) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                                                    contentDescription = "Open Folder",
                                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.size(16.dp),
                                                                )
                                                            } else {
                                                                Text(
                                                                    "View",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // VPS Backups Section
                            if (state.driveBackups.isNotEmpty()) {
                                Text(
                                    "VPS Cloud Backups",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    state.driveBackups.take(5).forEach { file ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                            ) {
                                                Text(file.name, style = VmTheme.code.mono.copy(fontSize = 11.sp), fontWeight = FontWeight.Medium)
                                                Text("${file.sizeBytes / 1024} KB", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. VM Studio OmniRoute AI Gateway (AI Gateways)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.AI_GATEWAY) {
                item(key = "card-omniroute") {
                    ModernConnectorCard(
                        icon = Icons.Default.SmartToy,
                        iconTint = Color(0xFF673AB7),
                        title = "OmniRoute AI Gateway",
                        category = "Multi-Model Intelligence & Code Agent",
                        status = VmStatus.CONNECTED,
                        statusText = "${state.aiModelCount} models",
                        description = "Connected to ai.vmstudio.digital with zero-setup built-in cloud intelligence, smart coding models, and free models.",
                        tags = listOf(
                            if (state.hasCustomAiKey) "Custom Key" else "Built-in Cloud",
                            "${state.aiFreeModelCount} Free Models",
                            state.aiLatencyMillis?.let { "${it}ms Ping" } ?: "Low Latency",
                            "Tool Calling",
                        ),
                        isExpanded = aiExpanded,
                        onToggleExpand = { aiExpanded = !aiExpanded },
                        statusMessage = state.aiStatusMessage,
                        primaryActionText = if (state.hasCustomAiKey) "Edit Key" else "Custom Key",
                        onPrimaryAction = { aiExpanded = !aiExpanded },
                        secondaryActionText = if (state.isTestingAi) "Testing…" else "Test Ping",
                        onSecondaryAction = { viewModel.testAiGateway() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "API Key & Endpoint Configuration",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (state.hasCustomAiKey) {
                                    "Custom API key active. It is encrypted in Android Keystore."
                                } else {
                                    "Using built-in VM Studio OmniRoute Cloud key. No key is required for free and core models, but you can enter your own key below."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = aiKeyDraft,
                                onValueChange = { aiKeyDraft = it },
                                label = { Text("Custom API Key (OpenRouter / Groq / OmniRoute)") },
                                singleLine = true,
                                visualTransformation = if (aiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { aiKeyVisible = !aiKeyVisible }) {
                                        Icon(
                                            imageVector = if (aiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                VmButton(
                                    text = "Save Key",
                                    onClick = {
                                        viewModel.saveCustomAiKey(aiKeyDraft)
                                        aiKeyDraft = ""
                                    },
                                    enabled = aiKeyDraft.isNotBlank(),
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedButton(
                                    onClick = { openUrl("https://openrouter.ai/keys") },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("OpenRouter", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (state.hasCustomAiKey) {
                                OutlinedButton(
                                    onClick = { viewModel.clearCustomAiKey() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text("Revert to Built-in Free Cloud Key", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // 7. WhatsApp & Evolution API (Messaging & Remote Control)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.WHATSAPP) {
                item(key = "card-whatsapp") {
                    ModernConnectorCard(
                        icon = Icons.Default.SmartToy,
                        iconTint = Color(0xFF25D366),
                        title = "WhatsApp (Evolution API)",
                        category = "Messaging, APK Dispatch & Remote AI",
                        status = when {
                            state.evolutionApiConnected == true -> VmStatus.CONNECTED
                            state.hasEvolutionApiKey -> VmStatus.CONNECTED
                            else -> VmStatus.IDLE
                        },
                        statusText = when {
                            state.isTestingEvolutionApi -> "Checking…"
                            state.evolutionApiConnected == true -> "Connected"
                            state.hasEvolutionApiKey -> "Configured"
                            else -> "Setup Needed"
                        },
                        description = "Dispatch release APKs, receive build completion alerts, and trigger agent coding workflows via WhatsApp.",
                        tags = listOf("WhatsApp", "Evolution API", "Webhook", "APK Dispatch"),
                        isExpanded = evolutionExpanded,
                        onToggleExpand = { evolutionExpanded = !evolutionExpanded },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                text = "Connect your WhatsApp via Evolution API (wa.vmstudio.digital) to dispatch APKs to your phone and receive instant build notifications.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = evolutionBaseUrlDraft,
                                onValueChange = { evolutionBaseUrlDraft = it },
                                label = { Text("Evolution API Host") },
                                placeholder = { Text("https://wa.vmstudio.digital") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                OutlinedTextField(
                                    value = evolutionInstanceDraft,
                                    onValueChange = { evolutionInstanceDraft = it },
                                    label = { Text("Instance Name") },
                                    placeholder = { Text("main") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = evolutionRecipientDraft,
                                    onValueChange = { evolutionRecipientDraft = it },
                                    label = { Text("WhatsApp Number") },
                                    placeholder = { Text("91xxxxxxxxxx") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            OutlinedTextField(
                                value = evolutionApiKeyDraft,
                                onValueChange = { evolutionApiKeyDraft = it },
                                label = { Text("Evolution API Key") },
                                placeholder = { Text("Paste apikey from wa.vmstudio.digital") },
                                singleLine = true,
                                visualTransformation = if (evolutionApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { evolutionApiKeyVisible = !evolutionApiKeyVisible }) {
                                        Icon(
                                            imageVector = if (evolutionApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (evolutionApiKeyVisible) "Hide Key" else "Show Key",
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Build & Agent Alerts",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = "Ping WhatsApp when APK builds or tasks complete",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                androidx.compose.material3.Switch(
                                    checked = evolutionNotificationsDraft,
                                    onCheckedChange = { evolutionNotificationsDraft = it },
                                )
                            }

                            if (!state.evolutionApiStatusMessage.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = state.evolutionApiStatusMessage.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (state.evolutionApiConnected == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(spacing.sm),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            ) {
                                VmButton(
                                    text = "Save Settings",
                                    onClick = {
                                        viewModel.saveEvolutionApiSettings(
                                            baseUrl = evolutionBaseUrlDraft,
                                            instance = evolutionInstanceDraft,
                                            apiKey = evolutionApiKeyDraft,
                                            recipientNumber = evolutionRecipientDraft,
                                            notificationsEnabled = evolutionNotificationsDraft,
                                        )
                                    },
                                    style = VmButtonStyle.Primary,
                                    modifier = Modifier.weight(1f),
                                )
                                VmButton(
                                    text = if (state.isTestingEvolutionApi) "Testing…" else "Test Connection",
                                    onClick = {
                                        viewModel.testEvolutionApiConnection(
                                            baseUrl = evolutionBaseUrlDraft,
                                            instance = evolutionInstanceDraft,
                                            apiKey = evolutionApiKeyDraft,
                                            recipientNumber = evolutionRecipientDraft,
                                        )
                                    },
                                    enabled = !state.isTestingEvolutionApi && evolutionApiKeyDraft.isNotBlank(),
                                    style = VmButtonStyle.Secondary,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // 8. Model Context Protocol (MCP) (Universal Apps, WordPress, Databases & Project Tools)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.MCP) {
                item(key = "card-mcp") {
                    val activeMcpCount = state.mcpServers.count { it.isEnabled }
                    ModernConnectorCard(
                        icon = Icons.Default.Extension,
                        iconTint = Color(0xFFFF5722),
                        title = "Model Context Protocol (MCP)",
                        category = "Universal App, WordPress & Project Tools",
                        status = if (activeMcpCount > 0) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = when {
                            state.isTestingMcp -> "Testing…"
                            state.mcpServers.isNotEmpty() -> "$activeMcpCount active server${if (activeMcpCount > 1) "s" else ""}"
                            else -> "Ready to Connect"
                        },
                        description = "Connect any app, WordPress site, database, or API via Model Context Protocol (MCP). The AI agent discovers advertised tools and executes actions or writes code in your project.",
                        tags = listOf("JSON-RPC 2.0", "WordPress MCP", "tools/list", "tools/call", "Project-Scoped"),
                        isExpanded = mcpExpanded,
                        onToggleExpand = { mcpExpanded = !mcpExpanded },
                        statusMessage = state.mcpStatusMessage,
                        primaryActionText = if (mcpExpanded) "Collapse" else "Configure MCP",
                        onPrimaryAction = { mcpExpanded = !mcpExpanded },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                        ) {
                            Text(
                                "Connected MCP Servers & Bridges",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Configure Model Context Protocol endpoints. Once connected, OmniRoute AI can list tools and execute remote actions or inspect resources directly in your workspace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Quick Add Presets Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        mcpIsAddingWp = !mcpIsAddingWp
                                        if (mcpIsAddingWp) mcpIsAddingCustom = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("WordPress MCP", style = MaterialTheme.typography.labelSmall)
                                }

                                OutlinedButton(
                                    onClick = {
                                        mcpIsAddingCustom = !mcpIsAddingCustom
                                        if (mcpIsAddingCustom) mcpIsAddingWp = false
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Custom MCP Server", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // WordPress MCP Preset Form
                            AnimatedVisibility(
                                visible = mcpIsAddingWp,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, Color(0xFF21759B).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "Connect WordPress Site via MCP",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF21759B),
                                        )
                                        Text(
                                            "Point to your WordPress URL. If you have an MCP plugin or VM companion installed, X-Codes will automatically bind its tools to your project.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        OutlinedTextField(
                                            value = wpUrlDraft,
                                            onValueChange = { wpUrlDraft = it },
                                            label = { Text("WordPress Site URL (https://my-blog.com)") },
                                            placeholder = { Text("https://mathnotes.courses or https://vmstudio.digital") },
                                            singleLine = true,
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    clipboardManager.getText()?.text?.let { wpUrlDraft = it.trim() }
                                                }) {
                                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            ConnectorFilterChip(
                                                selected = wpUrlDraft.contains("mathnotes.courses", ignoreCase = true),
                                                onClick = { wpUrlDraft = "https://mathnotes.courses" },
                                                label = "mathnotes.courses",
                                            )
                                            ConnectorFilterChip(
                                                selected = wpUrlDraft.contains("vmstudio.digital", ignoreCase = true),
                                                onClick = { wpUrlDraft = "https://vmstudio.digital" },
                                                label = "vmstudio.digital",
                                            )
                                        }

                                        OutlinedTextField(
                                            value = wpKeyDraft,
                                            onValueChange = { wpKeyDraft = it },
                                            label = { Text("API Key / Application Password (optional)") },
                                            singleLine = true,
                                            visualTransformation = if (wpKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { wpKeyVisible = !wpKeyVisible }) {
                                                    Icon(
                                                        imageVector = if (wpKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        OutlinedTextField(
                                            value = wpProjectPathDraft,
                                            onValueChange = { wpProjectPathDraft = it },
                                            label = { Text("Target Project Path (optional, e.g. wp-content/themes)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        VmButton(
                                            text = "Save & Connect WordPress MCP",
                                            onClick = {
                                                if (wpUrlDraft.isNotBlank()) {
                                                    viewModel.addWordPressMcpPreset(
                                                        siteUrl = wpUrlDraft,
                                                        apiKey = wpKeyDraft.ifBlank { null },
                                                        projectPath = wpProjectPathDraft.ifBlank { null },
                                                    )
                                                    wpUrlDraft = ""
                                                    wpKeyDraft = ""
                                                    wpProjectPathDraft = ""
                                                    mcpIsAddingWp = false
                                                }
                                            },
                                            enabled = wpUrlDraft.isNotBlank() && !state.isTestingMcp,
                                            style = VmButtonStyle.Primary,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            // Custom MCP Server Form
                            AnimatedVisibility(
                                visible = mcpIsAddingCustom,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = BorderStroke(1.dp, Color(0xFFFF5722).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "Add Custom Model Context Protocol Server",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFF5722),
                                        )

                                        OutlinedTextField(
                                            value = mcpNameDraft,
                                            onValueChange = { mcpNameDraft = it },
                                            label = { Text("Server Name (e.g. Production Database)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "Server Protocol / Type",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                listOf("HTTP", "WORDPRESS", "SSE").forEach { type ->
                                                    val selected = mcpTypeDraft.equals(type, ignoreCase = true)
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        border = BorderStroke(
                                                            1.dp,
                                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .clickable { mcpTypeDraft = type },
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.padding(vertical = 7.dp),
                                                        ) {
                                                            Text(
                                                                text = type,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = mcpUrlDraft,
                                            onValueChange = { mcpUrlDraft = it },
                                            label = { Text("MCP Endpoint URL (JSON-RPC or SSE endpoint)") },
                                            placeholder = { Text("https://mcp.example.com/api") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        OutlinedTextField(
                                            value = mcpApiKeyDraft,
                                            onValueChange = { mcpApiKeyDraft = it },
                                            label = { Text("Bearer Token / API Key (optional)") },
                                            singleLine = true,
                                            visualTransformation = if (mcpApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            trailingIcon = {
                                                IconButton(onClick = { mcpApiKeyVisible = !mcpApiKeyVisible }) {
                                                    Icon(
                                                        imageVector = if (mcpApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = null,
                                                    )
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        OutlinedTextField(
                                            value = mcpProjectPathDraft,
                                            onValueChange = { mcpProjectPathDraft = it },
                                            label = { Text("Scope to Project Path (optional)") },
                                            placeholder = { Text("Leave blank for all projects") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        VmButton(
                                            text = "Save MCP Server",
                                            onClick = {
                                                if (mcpNameDraft.isNotBlank() && mcpUrlDraft.isNotBlank()) {
                                                    viewModel.saveMcpServer(
                                                        name = mcpNameDraft,
                                                        endpointUrl = mcpUrlDraft,
                                                        apiKey = mcpApiKeyDraft.ifBlank { null },
                                                        serverType = mcpTypeDraft,
                                                        projectPath = mcpProjectPathDraft.ifBlank { null },
                                                    )
                                                    mcpNameDraft = ""
                                                    mcpUrlDraft = ""
                                                    mcpApiKeyDraft = ""
                                                    mcpProjectPathDraft = ""
                                                    mcpIsAddingCustom = false
                                                }
                                            },
                                            enabled = mcpNameDraft.isNotBlank() && mcpUrlDraft.isNotBlank() && !state.isTestingMcp,
                                            style = VmButtonStyle.Primary,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )

                            // Top Curated Connectors (Claude-Style Catalog)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "Top connectors",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ) {
                                        Text(
                                            text = "${CuratedConnectorsCatalog.TOP_10.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = "Model Context Protocol",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CuratedConnectorsCatalog.TOP_10.forEach { connector ->
                                    val isConnected = state.mcpServers.any { server ->
                                        server.name.contains(connector.name, ignoreCase = true) ||
                                            (connector.defaultEndpointUrl.isNotBlank() &&
                                                server.endpointUrl.contains(connector.defaultEndpointUrl.trimEnd('/'), ignoreCase = true))
                                    }
                                    CuratedConnectorItem(
                                        connector = connector,
                                        isConnected = isConnected,
                                        onConnect = {
                                            if (connector.requiresApiKey) {
                                                curatedDialogConnector = connector
                                                curatedApiKeyDraft = ""
                                                curatedEndpointDraft = connector.defaultEndpointUrl
                                            } else {
                                                viewModel.addCuratedConnector(connector)
                                            }
                                        },
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )

                            // Configured Servers List
                            if (state.mcpServers.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "No MCP servers configured yet. Add your WordPress site or a custom MCP server above to enable remote tools.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            } else {
                                state.mcpServers.forEach { server ->
                                    val discovered = state.mcpDiscoveredTools[server.id]
                                    ConfiguredMcpServerItem(
                                        server = server,
                                        discovered = discovered,
                                        isTesting = state.isTestingMcp,
                                        onToggle = { viewModel.toggleMcpServer(server.id, it) },
                                        onDelete = { viewModel.deleteMcpServer(server.id) },
                                        onTest = { viewModel.testMcpServer(server) },
                                        onEdit = { editingMcpServer = server },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Document / Sheet Content Preview Dialog
    val previewFile = state.drivePreviewFile
    if (previewFile != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDrivePreview() },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val icon = when {
                        previewFile.isSheet -> Icons.Default.TableChart
                        previewFile.isDoc -> Icons.Default.Description
                        else -> Icons.Default.Storage
                    }
                    val iconTint = when {
                        previewFile.isSheet -> Color(0xFF0F9D58)
                        previewFile.isDoc -> Color(0xFF4285F4)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
                    Text(
                        text = previewFile.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val typeLabel = when {
                        previewFile.isSheet -> "Google Sheet (Exported as CSV)"
                        previewFile.isDoc -> "Google Doc (Exported as Plain Text)"
                        else -> "File (${previewFile.mimeType})"
                    }
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )

                    if (state.isLoadingDrivePreview) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Loading content from Google Drive…", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp),
                        ) {
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = state.drivePreviewContent ?: "No content available.",
                                        style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.drivePreviewContent.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(state.drivePreviewContent.orEmpty()))
                                Toast.makeText(context, "Copied content to clipboard", Toast.LENGTH_SHORT).show()
                            },
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                    TextButton(onClick = { viewModel.dismissDrivePreview() }) {
                        Text("Close")
                    }
                }
            },
        )
    }

    // Curated Connector Setup Dialog
    curatedDialogConnector?.let { connector ->
        AlertDialog(
            onDismissRequest = { curatedDialogConnector = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = {
                Text(
                    text = "Connect ${connector.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = connector.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = curatedEndpointDraft,
                        onValueChange = { curatedEndpointDraft = it },
                        label = { Text("MCP Endpoint / Gateway URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = curatedApiKeyDraft,
                        onValueChange = { curatedApiKeyDraft = it },
                        label = { Text(connector.apiKeyPlaceholder) },
                        singleLine = true,
                        visualTransformation = if (curatedApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { curatedApiKeyVisible = !curatedApiKeyVisible }) {
                                Icon(
                                    imageVector = if (curatedApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCuratedConnector(
                            curated = connector,
                            apiKey = curatedApiKeyDraft.ifBlank { null },
                            customEndpointUrl = curatedEndpointDraft.ifBlank { connector.defaultEndpointUrl },
                        )
                        curatedDialogConnector = null
                    },
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { curatedDialogConnector = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit MCP Server Dialog
    editingMcpServer?.let { server ->
        EditMcpServerDialog(
            server = server,
            isTesting = state.isTestingMcp,
            onDismiss = { editingMcpServer = null },
            onSave = { name, url, apiKey, type, projectPath, enabled ->
                viewModel.saveMcpServer(
                    id = server.id,
                    name = name,
                    endpointUrl = url,
                    apiKey = apiKey,
                    serverType = type,
                    projectPath = projectPath,
                    isEnabled = enabled,
                )
            },
            onTest = { testConfig ->
                viewModel.testMcpServer(testConfig)
            },
            onDelete = {
                viewModel.deleteMcpServer(server.id)
            },
        )
    }
}

@Composable
private fun ConnectorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ModernConnectorCard(
    icon: ImageVector,
    iconTint: Color,
    badgeBackground: Color = iconTint.copy(alpha = 0.15f),
    title: String,
    category: String,
    status: VmStatus,
    statusText: String,
    description: String,
    tags: List<String>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    statusMessage: String? = null,
    primaryActionText: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    expandedContent: @Composable () -> Unit,
) {
    val spacing = VmTheme.spacing
    val isConnected = status == VmStatus.CONNECTED
    val statusColor = when (status) {
        VmStatus.CONNECTED -> VmTheme.colors.connected
        VmStatus.CONNECTING -> VmTheme.colors.connecting
        VmStatus.ERROR -> MaterialTheme.colorScheme.error
        else -> VmTheme.colors.disconnected
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = if (isConnected) iconTint.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            // Header Row: Icon + Title + Category + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Sleek glowing status indicator pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    statusColor,
                                ),
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = statusColor,
                        )
                    }
                }
            }

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )

            // Tags Pill Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // Live status banner if present
            statusMessage?.let { msg ->
                val isError = msg.contains("fail", ignoreCase = true) ||
                    msg.contains("error", ignoreCase = true) ||
                    msg.contains("401") ||
                    msg.contains("403") ||
                    msg.contains("404") ||
                    msg.contains("500") ||
                    msg.contains("unauthorized", ignoreCase = true) ||
                    msg.contains("forbidden", ignoreCase = true) ||
                    msg.contains("denied", ignoreCase = true) ||
                    msg.contains("invalid", ignoreCase = true) ||
                    msg.contains("exception", ignoreCase = true) ||
                    msg.contains("reject", ignoreCase = true) ||
                    msg.contains("refused", ignoreCase = true) ||
                    msg.contains("could not", ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) MaterialTheme.colorScheme.error else VmTheme.colors.success,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (primaryActionText != null && onPrimaryAction != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onPrimaryAction),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 9.dp),
                        ) {
                            Text(
                                text = primaryActionText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onSecondaryAction),
                    ) {
                        Text(
                            text = secondaryActionText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand configuration",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Animated Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    expandedContent()
                }
            }
        }
    }
}

@Composable
private fun CuratedConnectorItem(
    connector: CuratedConnector,
    isConnected: Boolean,
    onConnect: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            1.dp,
            if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = when (connector.id) {
                    "wordpress" -> Color(0xFF21759B).copy(alpha = 0.15f)
                    "brave_search" -> Color(0xFFFF5722).copy(alpha = 0.15f)
                    "github_mcp" -> Color(0xFF9C27B0).copy(alpha = 0.15f)
                    "postgres_mcp" -> Color(0xFF336791).copy(alpha = 0.15f)
                    "sqlite_mcp" -> Color(0xFF003B57).copy(alpha = 0.15f)
                    "puppeteer_mcp" -> Color(0xFF00D1B2).copy(alpha = 0.15f)
                    "memory_mcp" -> Color(0xFF673AB7).copy(alpha = 0.15f)
                    "slack" -> Color(0xFF4A154B).copy(alpha = 0.15f)
                    "canva" -> Color(0xFF00C4CC).copy(alpha = 0.15f)
                    "notion" -> Color(0xFF808080).copy(alpha = 0.15f)
                    "atlassian_mcp" -> Color(0xFF0052CC).copy(alpha = 0.15f)
                    "figma" -> Color(0xFFF24E1E).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (connector.id) {
                            "wordpress" -> Icons.Default.Language
                            "brave_search" -> Icons.Default.Language
                            "github_mcp" -> Icons.Default.Code
                            "postgres_mcp" -> Icons.Default.Storage
                            "sqlite_mcp" -> Icons.Default.Storage
                            "puppeteer_mcp" -> Icons.Default.Language
                            "memory_mcp" -> Icons.Default.SmartToy
                            "slack" -> Icons.AutoMirrored.Filled.Chat
                            "canva" -> Icons.Default.Extension
                            "notion" -> Icons.Default.Description
                            "atlassian_mcp" -> Icons.Default.Code
                            "figma" -> Icons.Default.Extension
                            else -> Icons.Default.Extension
                        },
                        contentDescription = connector.name,
                        tint = when (connector.id) {
                            "wordpress" -> Color(0xFF21759B)
                            "brave_search" -> Color(0xFFFF5722)
                            "github_mcp" -> Color(0xFF9C27B0)
                            "postgres_mcp" -> Color(0xFF336791)
                            "sqlite_mcp" -> Color(0xFF0098FF)
                            "puppeteer_mcp" -> Color(0xFF00D1B2)
                            "memory_mcp" -> Color(0xFF9C27B0)
                            "slack" -> Color(0xFFE01E5A)
                            "canva" -> Color(0xFF00C4CC)
                            "notion" -> MaterialTheme.colorScheme.onSurface
                            "atlassian_mcp" -> Color(0xFF0052CC)
                            "figma" -> Color(0xFFF24E1E)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = connector.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "MCP",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
                Text(
                    text = connector.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Text(
                    text = "by ${connector.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF4CAF50).copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Active",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onConnect,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Add", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ConfiguredMcpServerItem(
    server: McpServerConfig,
    discovered: List<McpToolDefinition>?,
    isTesting: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
) {
    val isWp = server.serverType.equals("WORDPRESS", ignoreCase = true)
    val isSse = server.serverType.equals("SSE", ignoreCase = true)
    val iconColor = when {
        !server.isEnabled -> MaterialTheme.colorScheme.outline
        isWp -> Color(0xFF21759B)
        server.name.contains("postgres", ignoreCase = true) -> Color(0xFF336791)
        server.name.contains("sqlite", ignoreCase = true) -> Color(0xFF0098FF)
        server.name.contains("github", ignoreCase = true) -> Color(0xFF9C27B0)
        server.name.contains("memory", ignoreCase = true) -> Color(0xFF673AB7)
        server.name.contains("brave", ignoreCase = true) -> Color(0xFFFF5722)
        isSse -> Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.primary
    }
    val iconVector = when {
        isWp -> Icons.Default.Language
        server.name.contains("database", ignoreCase = true) || server.name.contains("sql", ignoreCase = true) -> Icons.Default.Storage
        server.name.contains("code", ignoreCase = true) || server.name.contains("git", ignoreCase = true) -> Icons.Default.Code
        server.name.contains("memory", ignoreCase = true) -> Icons.Default.SmartToy
        server.name.contains("pc", ignoreCase = true) || server.name.contains("windows", ignoreCase = true) -> Icons.Default.Terminal
        else -> Icons.Default.Extension
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            if (server.isEnabled) iconColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Row: Icon + Title & Type + Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = server.name,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = iconColor.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, iconColor.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = server.serverType.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = iconColor,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                        }

                        if (!server.apiKey.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = "AUTH",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }

                        Text(
                            text = server.endpointUrl,
                            style = VmTheme.code.mono.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Switch(
                        checked = server.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.scale(0.78f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Server",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Server",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // Project Scope if configured
            server.projectPath?.takeIf { it.isNotBlank() }?.let { p ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "Scope: $p",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            // Advertised Tools Horizontal Scroll
            if (!discovered.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    discovered.forEach { tool ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                text = tool.name,
                                style = VmTheme.code.mono.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            // Bottom Status & Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!discovered.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50)),
                        )
                        Text(
                            text = "${discovered.size} tools discovered",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4CAF50),
                        )
                    }
                } else {
                    Text(
                        text = if (server.isEnabled) "Ready to test" else "Disabled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                    }

                    OutlinedButton(
                        onClick = onTest,
                        enabled = !isTesting,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isTesting) "Testing…" else "Test Tools",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditMcpServerDialog(
    server: McpServerConfig,
    isTesting: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, endpointUrl: String, apiKey: String?, serverType: String, projectPath: String?, isEnabled: Boolean) -> Unit,
    onTest: (McpServerConfig) -> Unit,
    onDelete: () -> Unit,
) {
    var nameDraft by remember(server) { mutableStateOf(server.name) }
    var endpointUrlDraft by remember(server) { mutableStateOf(server.endpointUrl) }
    var apiKeyDraft by remember(server) { mutableStateOf(server.apiKey.orEmpty()) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var serverTypeDraft by remember(server) { mutableStateOf(server.serverType) }
    var projectPathDraft by remember(server) { mutableStateOf(server.projectPath.orEmpty()) }
    var isEnabledDraft by remember(server) { mutableStateOf(server.isEnabled) }
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete MCP Server?") },
            text = { Text("Are you sure you want to remove \"${server.name}\"? Remote tools from this server will no longer be available.") },
            confirmButton = {
                VmButton(
                    text = "Delete",
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                        onDismiss()
                    },
                    style = VmButtonStyle.Destructive,
                )
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Edit MCP Server",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Enabled status toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Server Enabled",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isEnabledDraft) "Active and available for agent tools" else "Disabled temporarily",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isEnabledDraft,
                        onCheckedChange = { isEnabledDraft = it },
                        modifier = Modifier.scale(0.85f),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Server Name
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g. WordPress MCP Bridge or VM Desktop PC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Protocol / Type Selector Chips
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Protocol / Server Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("HTTP", "WORDPRESS", "SSE").forEach { type ->
                            val selected = serverTypeDraft.equals(type, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { serverTypeDraft = type },
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                ) {
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Endpoint URL
                OutlinedTextField(
                    value = endpointUrlDraft,
                    onValueChange = { endpointUrlDraft = it },
                    label = { Text("Endpoint URL") },
                    placeholder = { Text("https://example.com/mcp or http://192.168.1.10:8080/mcp") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Bearer Token / API Key
                OutlinedTextField(
                    value = apiKeyDraft,
                    onValueChange = { apiKeyDraft = it },
                    label = { Text("Bearer Token / API Key (optional)") },
                    singleLine = true,
                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let { apiKeyDraft = it }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                Icon(
                                    imageVector = if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Project Path
                OutlinedTextField(
                    value = projectPathDraft,
                    onValueChange = { projectPathDraft = it },
                    label = { Text("Project Scope (optional)") },
                    placeholder = { Text("Leave blank for all projects") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Test Connection Button inside Dialog
                OutlinedButton(
                    onClick = {
                        val temp = server.copy(
                            name = nameDraft.trim(),
                            endpointUrl = endpointUrlDraft.trim(),
                            apiKey = apiKeyDraft.trim().ifBlank { null },
                            serverType = serverTypeDraft,
                            projectPath = projectPathDraft.trim().ifBlank { null },
                            isEnabled = isEnabledDraft,
                        )
                        onTest(temp)
                    },
                    enabled = endpointUrlDraft.isNotBlank() && !isTesting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing Connection…", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Connection", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameDraft.isNotBlank() && endpointUrlDraft.isNotBlank()) {
                        onSave(
                            nameDraft.trim(),
                            endpointUrlDraft.trim(),
                            apiKeyDraft.trim().ifBlank { null },
                            serverTypeDraft,
                            projectPathDraft.trim().ifBlank { null },
                            isEnabledDraft,
                        )
                        onDismiss()
                    }
                },
                enabled = nameDraft.isNotBlank() && endpointUrlDraft.isNotBlank() && !isTesting,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = { showDeleteConfirmation = true },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
