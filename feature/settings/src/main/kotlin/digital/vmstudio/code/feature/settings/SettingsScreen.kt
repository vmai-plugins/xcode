package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Lean settings root: one row per area, each opening a focused sub-screen.
 *
 * The previous version mixed navigation shortcuts to servers/projects/files with
 * every preference on a single scroll. Places now live in the drawer; this list
 * only opens real settings pages.
 */
@Composable
fun SettingsScreen(
    onOpenAi: () -> Unit = {},
    onOpenAgent: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenEditor: () -> Unit = {},
    onOpenSecurity: () -> Unit = {},
    onOpenServers: () -> Unit = {},
    onOpenGitHub: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = uiState.preferences

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // --- 1. Account & Profile Section ---
        item { VmSectionHeader(title = "Account & Cloud Profile") }
        item {
            VmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = preferences.userName ?: preferences.userEmail ?: "Guest User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (preferences.hasSignedIn) {
                                preferences.userEmail ?: "Signed in via Google"
                            } else {
                                "Guest mode (Local only)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (preferences.hasSignedIn) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.signOut()
                                onSignOut()
                            }
                        ) {
                            Text("Sign Out", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // --- 2. AI Intelligence & Autonomy ---
        item { VmSectionHeader(title = "AI & Agent Autonomy") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SettingsRow(
                    title = "AI Provider & Gateway",
                    description = "Gateway URL, API keys, models (OmniRoute, Gemini, Claude)",
                    icon = Icons.Default.SmartToy,
                    onClick = onOpenAi,
                )
                SettingsRow(
                    title = "Agent & Autonomy Policy",
                    description = "Command permissions, confirmations, safe auto mode",
                    icon = Icons.Default.Code,
                    onClick = onOpenAgent,
                )
            }
        }

        // --- 3. Workspace & Code Environment ---
        item { VmSectionHeader(title = "Workspace & Repositories") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SettingsRow(
                    title = "GitHub Integration",
                    description = "Personal access token, clone and sync repositories",
                    icon = Icons.Default.Cloud,
                    onClick = onOpenGitHub,
                )
                SettingsRow(
                    title = "Editor & Terminal",
                    description = "Font sizes, line wrapping, scrollback buffer",
                    icon = Icons.Default.Terminal,
                    onClick = onOpenEditor,
                )
                SettingsRow(
                    title = "Appearance",
                    description = "System, Dark, Light, AMOLED black themes",
                    icon = Icons.Default.Palette,
                    onClick = onOpenAppearance,
                )
            }
        }

        // --- 4. Security & System Diagnostics ---
        item { VmSectionHeader(title = "Security & System") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SettingsRow(
                    title = "Security & Keystore",
                    description = "Hardware-backed keystore, clear stored secrets",
                    icon = Icons.Default.Security,
                    onClick = onOpenSecurity,
                )
                SettingsRow(
                    title = "Export Diagnostics",
                    description = "Share sanitized app logs and system status via Android Share",
                    icon = Icons.Default.BugReport,
                    onClick = {
                        val report = viewModel.exportDiagnostics()
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_TEXT, report)
                            type = "text/plain"
                        }
                        context.startActivity(android.content.Intent.createChooser(sendIntent, "Export X-codes Diagnostics"))
                    },
                )
                SettingsRow(
                    title = "About & Updates",
                    description = "Version, updates, release channel",
                    icon = Icons.Default.Info,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val spacing = VmTheme.spacing
    VmCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

