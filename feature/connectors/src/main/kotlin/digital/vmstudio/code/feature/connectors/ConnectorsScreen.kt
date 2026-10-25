package digital.vmstudio.code.feature.connectors

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
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

    var driveExpanded by remember { mutableStateOf(false) }
    var driveTokenDraft by remember { mutableStateOf("") }
    var driveTokenVisible by remember { mutableStateOf(false) }

    var hubExpanded by remember { mutableStateOf(false) }
    var hubBaseUrlDraft by remember(state.hubBaseUrl) { mutableStateOf(state.hubBaseUrl) }
    var hubKeyDraft by remember { mutableStateOf("") }
    var hubKeyVisible by remember { mutableStateOf(false) }

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
                }
            }

            // 1. GitHub Integration (Git & VCS)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.GIT_VCS) {
                item(key = "card-github") {
                    ModernConnectorCard(
                        icon = Icons.Default.Code,
                        iconTint = Color(0xFF9C27B0),
                        badgeBackground = Color(0xFFF3E5F5),
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
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Get Token", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (state.hasGithubToken) {
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
                        badgeBackground = Color(0xFFE0F2F1),
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
                        badgeBackground = Color(0xFFE1F5FE),
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
                                    "Your device is paired with ${state.hubBaseUrl}. You can update your Hub URL, Key, or unpair at any time."
                                } else {
                                    "Enter your WordPress Hub URL (e.g. https://saketdhamashram.org) and your Hub API Key (found in WordPress Admin > VM Project Hub > Settings > API Token)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            OutlinedTextField(
                                value = hubBaseUrlDraft,
                                onValueChange = { hubBaseUrlDraft = it },
                                label = { Text("Hub WordPress Site URL") },
                                placeholder = { Text("https://saketdhamashram.org") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OutlinedTextField(
                                value = hubKeyDraft,
                                onValueChange = { hubKeyDraft = it },
                                label = { Text("Hub API Key (xc_...)") },
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
                                    onClick = { viewModel.pairHubKey(hubKeyDraft) },
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
                        badgeBackground = Color(0xFFE8F5E9),
                        title = "Google Sheets",
                        category = "Data & Living Knowledge Base",
                        status = if (state.sheetPreviewRows.isNotEmpty()) VmStatus.CONNECTED else VmStatus.IDLE,
                        statusText = if (state.sheetPreviewRows.isNotEmpty()) "${state.sheetPreviewRows.size} rows cached" else "Available",
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
                                label = { Text("Google OAuth Token / Service Key") },
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
                                enabled = sheetsTokenDraft.isNotBlank() && sheetsIdDraft.isNotBlank() && !state.isTestingSheet,
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

            // 5. Google Drive & Docs (Cloud Backups & File Browser)
            if (state.selectedFilter == ConnectorFilter.ALL || state.selectedFilter == ConnectorFilter.DATA_SHEETS) {
                item(key = "card-drive") {
                    ModernConnectorCard(
                        icon = Icons.Default.Storage,
                        iconTint = Color(0xFFF57C00),
                        badgeBackground = Color(0xFFFFF3E0),
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
                                "Google Drive Credentials & Vault",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedTextField(
                                value = driveTokenDraft,
                                onValueChange = { driveTokenDraft = it },
                                label = { Text("Google Drive OAuth Token") },
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
                        badgeBackground = Color(0xFFEDE7F6),
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
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        badgeBackground = Color(0xFFE8F5E9),
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
}

@Composable
private fun ConnectorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ModernConnectorCard(
    icon: ImageVector,
    iconTint: Color,
    badgeBackground: Color,
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

    VmCard(
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
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
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
                        )
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                VmStatusBadge(
                    status = status,
                    label = statusText,
                )
            }

            // Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    ) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Live status banner if present
            statusMessage?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                    VmButton(
                        text = primaryActionText,
                        onClick = onPrimaryAction,
                        style = VmButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (secondaryActionText != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(secondaryActionText, style = MaterialTheme.typography.labelMedium)
                    }
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand configuration",
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
                    expandedContent()
                }
            }
        }
    }
}
