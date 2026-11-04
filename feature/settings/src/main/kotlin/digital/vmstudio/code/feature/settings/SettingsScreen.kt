package digital.vmstudio.code.feature.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Ultra-Modern Settings & IDE Control Hub.
 *
 * Designed with a high-end adaptive aesthetic, quick workspace launch tiles,
 * live gateway status monitoring, and organized control sections.
 */
@Composable
fun SettingsScreen(
    onOpenProjects: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenConnectors: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onOpenAgent: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenEditor: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenServers: () -> Unit = {},
    onOpenGitHub: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenActivityTimeline: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.preferences
    var showAdvancedTools by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // --- 1. Hero Profile & Gateway Live Status Card ---
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Profile Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val initial = (preferences.userName?.firstOrNull()
                                ?: preferences.userEmail?.firstOrNull()
                                ?: 'X').uppercaseChar()
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preferences.userName ?: preferences.userEmail ?: "X-Codes Developer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (preferences.hasSignedIn && !preferences.userEmail.isNullOrBlank()) {
                                    preferences.userEmail.orEmpty()
                                } else {
                                    "Connected to OmniRoute AI"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (preferences.hasSignedIn && !preferences.userEmail.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    viewModel.signOut()
                                    onSignOut()
                                },
                            ) {
                                Text("Sign Out", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            androidx.compose.material3.Button(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Account", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Live OmniRoute Gateway Status Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenAi),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(VmTheme.colors.connected),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "OmniRoute Gateway",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "• Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = VmTheme.colors.connected,
                                    )
                                }
                                Text(
                                    text = "${preferences.aiBaseUrl.removePrefix("https://").removePrefix("http://")} • ${preferences.aiSelectedModelId ?: "auto/best-coding"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Configure AI",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Core Preferences ---
        item { ModernSectionHeader(title = "Core Preferences") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernSettingsRow(
                    title = "AI Models & Gateway",
                    description = "OmniRoute configuration, multi-model switcher, latency & keys",
                    icon = Icons.Default.SmartToy,
                    badgeText = (preferences.aiSelectedModelId ?: "auto/best-coding").substringAfterLast('/'),
                    iconColor = Color(0xFF6366F1),
                    onClick = onOpenAi,
                )
                ModernSettingsRow(
                    title = "Appearance & Themes",
                    description = "System, light, and dark theme options",
                    icon = Icons.Default.Palette,
                    badgeText = preferences.themePreference.name.lowercase().replaceFirstChar { it.uppercase() },
                    iconColor = Color(0xFFEC4899),
                    onClick = onOpenAppearance,
                )
                ModernSettingsRow(
                    title = "About X-Codes",
                    description = "Build updates, release channel, App Hub sync",
                    icon = Icons.Default.Info,
                    badgeText = uiState.installedVersionName.ifBlank { "v0.16.0" },
                    iconColor = Color(0xFF10B981),
                    onClick = onOpenAbout,
                )
            }
        }

        // --- 3. Advanced Developer & Infrastructure Tools (Collapsible) ---
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showAdvancedTools = !showAdvancedTools },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(22.dp),
                        )
                        Column {
                            Text(
                                text = "Developer & Cloud Tools",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (showAdvancedTools) "Tap to collapse" else "SSH, SFTP, Agent Policy, Diagnostics & Tools",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (showAdvancedTools) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Developer Tools",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (showAdvancedTools) {
            // Workspaces & Storage Hub (2x2 Quick Tiles)
            item { ModernSectionHeader(title = "Workspaces & Tools") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickNavTile(
                        title = "Projects",
                        subtitle = "Workspaces & Git",
                        icon = Icons.Default.Folder,
                        accentColor = Color(0xFF6366F1),
                        badgeText = "Local / Git",
                        onClick = onOpenProjects,
                        modifier = Modifier.weight(1f),
                    )
                    QuickNavTile(
                        title = "Files",
                        subtitle = "Workspace browser",
                        icon = Icons.Default.FolderOpen,
                        accentColor = Color(0xFF00B4D8),
                        badgeText = "Explorer",
                        onClick = onOpenFiles,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickNavTile(
                        title = "Connectors",
                        subtitle = "SSH & Cloud Hub",
                        icon = Icons.Default.Dns,
                        accentColor = Color(0xFFF59E0B),
                        badgeText = "Integrations",
                        onClick = onOpenConnectors,
                        modifier = Modifier.weight(1f),
                    )
                    QuickNavTile(
                        title = "Cloud Build",
                        subtitle = "GitHub APK builder",
                        icon = Icons.Default.Build,
                        accentColor = Color(0xFF10B981),
                        badgeText = "1-Tap",
                        onClick = onOpenGitHub,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Agent & IDE Customization
            item { ModernSectionHeader(title = "Agent & Terminal Policy") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModernSettingsRow(
                        title = "Agent Autonomy & Policy",
                        description = "Safe Auto execution, confirmations, tool permissions",
                        icon = Icons.Default.Code,
                        badgeText = preferences.agentAutonomyLevel.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                        iconColor = Color(0xFF8B5CF6),
                        onClick = onOpenAgent,
                    )
                    ModernSettingsRow(
                        title = "Editor & Terminal",
                        description = "Font scale (${preferences.terminalFontSizeSp}sp), scrollback (${preferences.terminalScrollbackLines} lines)",
                        icon = Icons.Default.Terminal,
                        badgeText = "${preferences.terminalFontSizeSp.toInt()}sp",
                        iconColor = Color(0xFF00B4D8),
                        onClick = onOpenEditor,
                    )
                    ModernSettingsRow(
                        title = "Remote Servers & SSH",
                        description = "Manage PC, VPS, SSH keys, passwords, and OpenSSH terminal connections",
                        icon = Icons.Default.Dns,
                        badgeText = "SSH",
                        iconColor = Color(0xFF10B981),
                        onClick = onOpenServers,
                    )
                    ModernSettingsRow(
                        title = "Hardware Keystore & Security",
                        description = "Biometric lock, encrypted credentials, erase secrets",
                        icon = Icons.Default.Security,
                        iconColor = Color(0xFFF59E0B),
                        onClick = onOpenSecurity,
                    )
                    ModernSettingsRow(
                        title = "Activity & Audit Timeline",
                        description = "Audit trail of agent runs, server actions, file transfers & tasks",
                        icon = Icons.Default.History,
                        iconColor = Color(0xFF38BDF8),
                        onClick = onOpenActivityTimeline,
                    )
                    ModernSettingsRow(
                        title = "Export Diagnostics",
                        description = "Export sanitized logs, system health & gateway connectivity telemetry",
                        icon = Icons.Default.BugReport,
                        iconColor = Color(0xFFEF4444),
                        onClick = {
                            val report = viewModel.exportDiagnostics()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export X-codes Diagnostics"))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickNavTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ModernSettingsRow(
    title: String,
    description: String,
    icon: ImageVector,
    badgeText: String? = null,
    iconColor: Color = Color(0xFF6366F1),
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun ModernSectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF6366F1)),
        )
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
