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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
 * Designed with a high-end dark obsidian aesthetic, quick workspace launch tiles,
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
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.preferences

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0F12)),
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
                color = Color(0xFF151619),
                border = BorderStroke(1.dp, Color(0xFF26282E)),
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
                                color = Color(0xFFE2E2E6),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (preferences.hasSignedIn) {
                                    preferences.userEmail ?: "Signed in via Google"
                                } else {
                                    "Pro Developer Workspace"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8E919B),
                            )
                        }

                        if (preferences.hasSignedIn) {
                            TextButton(
                                onClick = {
                                    viewModel.signOut()
                                    onSignOut()
                                },
                            ) {
                                Text("Sign Out", color = Color(0xFFEF4444))
                            }
                        }
                    }

                    // Live OmniRoute Gateway Status Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1C20),
                        border = BorderStroke(1.dp, Color(0xFF282A31)),
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
                                    .background(Color(0xFF10B981)),
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
                                        color = Color(0xFFE2E2E6),
                                    )
                                    Text(
                                        text = "• Active",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF10B981),
                                    )
                                }
                                Text(
                                    text = "${preferences.aiBaseUrl.removePrefix("https://").removePrefix("http://")} • ${preferences.aiSelectedModelId ?: "auto/best-coding"}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF8E919B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Configure AI",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // App Hub Master Connection Bar
                    val hasHubKey = !preferences.hubApiKey.isNullOrBlank()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B1C20),
                        border = BorderStroke(1.dp, if (hasHubKey) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFF282A31)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenConnectors),
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
                                    .background(if (hasHubKey) Color(0xFF10B981) else Color(0xFFF59E0B)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (hasHubKey) "App Hub Master Provisioning Active" else "App Hub 1-Key Master Connect",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE2E2E6),
                                )
                                Text(
                                    text = if (hasHubKey) "Single API key linked • GitHub, AI, VPS & Drive synchronized" else "Enter single API key to configure all connections",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF8E919B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Manage App Hub",
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        // --- 2. Workspaces & Storage Hub (2x2 Quick Tiles) ---
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

        // --- 3. AI Intelligence & Autonomy ---
        item { ModernSectionHeader(title = "AI & Autonomous Agent") }
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
                    title = "Agent Autonomy & Policy",
                    description = "Safe Auto execution, confirmations, tool permissions",
                    icon = Icons.Default.Code,
                    badgeText = preferences.agentAutonomyLevel.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    iconColor = Color(0xFF8B5CF6),
                    onClick = onOpenAgent,
                )
            }
        }

        // --- 4. IDE Customization & Terminal ---
        item { ModernSectionHeader(title = "IDE Environment & UI") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernSettingsRow(
                    title = "Appearance & Themes",
                    description = "AMOLED Pitch Black, Dark, Light theme options",
                    icon = Icons.Default.Palette,
                    badgeText = preferences.themePreference.name.lowercase().replaceFirstChar { it.uppercase() },
                    iconColor = Color(0xFFEC4899),
                    onClick = onOpenAppearance,
                )
                ModernSettingsRow(
                    title = "Editor & Terminal",
                    description = "Font scale (${preferences.terminalFontSizeSp}sp), scrollback (${preferences.terminalScrollbackLines} lines)",
                    icon = Icons.Default.Terminal,
                    badgeText = "${preferences.terminalFontSizeSp.toInt()}sp",
                    iconColor = Color(0xFF00B4D8),
                    onClick = onOpenEditor,
                )
            }
        }

        // --- 5. Remote Servers & Hardware Control ---
        item { ModernSectionHeader(title = "Remote Infrastructure & PC Control") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernSettingsRow(
                    title = "Remote Servers & SSH (PC Control)",
                    description = "Manage PC, VPS, SSH keys, passwords, and OpenSSH terminal connections",
                    icon = Icons.Default.Dns,
                    badgeText = "SSH",
                    iconColor = Color(0xFF10B981),
                    onClick = onOpenServers,
                )
            }
        }

        // --- 6. Security & System Diagnostics ---
        item { ModernSectionHeader(title = "Security & System") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModernSettingsRow(
                    title = "Hardware Keystore & Security",
                    description = "Biometric lock, encrypted credentials, erase secrets",
                    icon = Icons.Default.Security,
                    iconColor = Color(0xFFF59E0B),
                    onClick = onOpenSecurity,
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
                ModernSettingsRow(
                    title = "About X-Codes",
                    description = "Build updates, release channel, App Hub sync",
                    icon = Icons.Default.Info,
                    badgeText = uiState.installedVersionName.ifBlank { "v0.14.0" },
                    iconColor = Color(0xFF6366F1),
                    onClick = onOpenAbout,
                )
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
        color = Color(0xFF151619),
        border = BorderStroke(1.dp, Color(0xFF24262C)),
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
                        color = Color(0xFF1F2024),
                        border = BorderStroke(1.dp, Color(0xFF2A2C33)),
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFF9CA3AF),
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
                    color = Color(0xFFE2E2E6),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color(0xFF8E919B),
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
        color = Color(0xFF151619),
        border = BorderStroke(1.dp, Color(0xFF24262C)),
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
                    color = Color(0xFFE2E2E6),
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8E919B),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!badgeText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1F2024),
                    border = BorderStroke(1.dp, Color(0xFF2A2C33)),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 1,
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFF6B7280),
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
            color = Color(0xFF8E919B),
        )
    }
}
