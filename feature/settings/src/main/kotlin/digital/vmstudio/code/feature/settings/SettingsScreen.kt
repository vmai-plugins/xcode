package digital.vmstudio.code.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.core.update.UpdateState
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = VmTheme.spacing
    var confirmErase by remember { mutableStateOf(false) }
    var googleDriveSyncEnabled by remember { mutableStateOf(true) }
    var autoBackupProjects by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item { VmSectionHeader(title = "Appearance") }

        item {
            VmCard {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                ThemePreference.entries.forEach { option ->
                    OptionRow(
                        label = option.label(),
                        selected = state.preferences.themePreference == option,
                        onSelect = { viewModel.setTheme(option) },
                    )
                }
            }
        }

        item {
            VmCard {
                SliderRow(
                    label = "Editor font size",
                    value = state.preferences.editorFontSizeSp,
                    range = 9f..22f,
                    onValueChange = viewModel::setEditorFontSize,
                )
                SliderRow(
                    label = "Terminal font size",
                    value = state.preferences.terminalFontSizeSp,
                    range = 9f..22f,
                    onValueChange = viewModel::setTerminalFontSize,
                )
            }
        }

        item {
            VmCard {
                ToggleRow(
                    label = "Reduce motion",
                    description = "Removes non-essential animation across the app.",
                    checked = state.preferences.reduceMotion,
                    onCheckedChange = viewModel::setReduceMotion,
                )
            }
        }

        item { VmSectionHeader(title = "AI provider") }

        item { AiSettingsSection() }

        item { VmSectionHeader(title = "AI agent") }

        item {
            VmCard {
                ToggleRow(
                    label = "Always confirm destructive commands",
                    description = "Prompts before any command that can delete data or " +
                        "restart a service, in the terminal and anywhere else commands run. " +
                        "Commands classified as outright dangerous are refused regardless " +
                        "of this setting.",
                    checked = state.preferences.confirmDestructiveCommands,
                    onCheckedChange = viewModel::setConfirmDestructiveCommands,
                )
            }
        }

        item {
            VmCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
                ) {
                    Text("Autonomy", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    text = "How much the agent may do before asking. This is the default " +
                        "for new runs; each run can override it from the agent screen. " +
                        "Enforced by Claude Code on the server, which is what actually " +
                        "runs the tools — so it applies to the agent's own file edits and " +
                        "commands, not just the ones this app issues.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AgentAutonomyLevel.entries.forEach { level ->
                    OptionRow(
                        label = level.label(),
                        description = level.description(),
                        selected = state.preferences.agentAutonomyLevel == level,
                        onSelect = { viewModel.setAgentAutonomy(level) },
                    )
                }
            }
        }

        item { VmSectionHeader(title = "Servers & VPS Clusters") }

        item {
            VmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("VPS 1 · Primary Agent", style = MaterialTheme.typography.titleSmall)
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = androidx.compose.ui.graphics.Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "195.35.45.36:8443 · OmniRoute Gateway & Hub Agent",
                            style = VmTheme.code.mono.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("VPS 2 · Build Worker", style = MaterialTheme.typography.titleSmall)
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                                color = androidx.compose.ui.graphics.Color(0xFF3B82F6).copy(alpha = 0.2f),
                            ) {
                                Text(
                                    text = "READY",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = androidx.compose.ui.graphics.Color(0xFF60A5FA),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            text = "195.35.45.109:22 · Gradle Compiler & APK Publisher",
                            style = VmTheme.code.mono.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { VmSectionHeader(title = "Cloud Storage & Google Drive") }

        item {
            VmCard {
                ToggleRow(
                    label = "Google Drive Workspace Sync",
                    description = "Automatically snapshot remote code revisions and build artifacts to Google Drive cloud storage.",
                    checked = googleDriveSyncEnabled,
                    onCheckedChange = { googleDriveSyncEnabled = it },
                )
                ToggleRow(
                    label = "Auto-backup Project Trees",
                    description = "Creates daily timestamped backups of all project directories across VPS 1 and VPS 2.",
                    checked = autoBackupProjects,
                    onCheckedChange = { autoBackupProjects = it },
                )
            }
        }

        item { VmSectionHeader(title = "Security") }

        item {
            VmCard {
                LabelValueRow(
                    label = "Stored credentials",
                    value = state.storedCredentialCount.toString(),
                )
                LabelValueRow(
                    label = "Encryption key",
                    value = if (state.isHardwareBackedKeystore) {
                        "Hardware-backed"
                    } else {
                        "Keystore (software)"
                    },
                )
                Text(
                    text = "Passwords, keys and API tokens are encrypted with AES-256-GCM " +
                        "using a key held in the Android Keystore. The key cannot be " +
                        "extracted from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VmButton(
                    text = "Erase all credentials",
                    onClick = { confirmErase = true },
                    style = VmButtonStyle.Destructive,
                    enabled = state.storedCredentialCount > 0,
                    loading = state.isErasing,
                )
            }
        }

        item { VmSectionHeader(title = "About") }

        item { UpdateSection(state = state, viewModel = viewModel) }

        item { VmSectionHeader(title = "Diagnostics") }

        item {
            VmCard {
                Text(
                    text = "Diagnostics contain no passwords, keys or tokens: secrets are " +
                        "removed as each entry is captured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
                    VmButton(
                        text = "Export",
                        style = VmButtonStyle.Secondary,
                        onClick = {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "x-codes diagnostics")
                                putExtra(Intent.EXTRA_TEXT, viewModel.exportDiagnostics())
                            }
                            context.startActivity(
                                Intent.createChooser(share, "Share diagnostics"),
                            )
                        },
                    )
                    VmButton(
                        text = "Clear",
                        style = VmButtonStyle.Tertiary,
                        onClick = viewModel::clearDiagnostics,
                    )
                }
            }
        }
    }

    if (confirmErase) {
        VmDialog(
            title = "Erase all credentials?",
            onDismiss = { confirmErase = false },
            confirmLabel = "Erase",
            destructive = true,
            onConfirm = {
                confirmErase = false
                viewModel.eraseAllCredentials { }
            },
        ) {
            Text(
                text = "Every stored password, private key and API token is deleted from " +
                    "this device, along with the encryption key. Your servers stay " +
                    "configured but you will need to re-enter their credentials. " +
                    "This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UpdateSection(state: SettingsUiState, viewModel: SettingsViewModel) {
    VmCard {
        LabelValueRow(label = "Version", value = state.installedVersionName)

        when (val update = state.updateState) {
            is UpdateState.Checking -> VmButton(
                text = "Checking...",
                onClick = {},
                enabled = false,
                loading = true,
            )

            is UpdateState.UpToDate -> Text(
                text = "You're on the latest version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            is UpdateState.Available -> Column(
                verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
            ) {
                Text(
                    text = "Version ${update.info.versionName} is available.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                VmButton(text = "Download & install", onClick = viewModel::downloadUpdate)
            }

            is UpdateState.Downloading -> VmProgress(
                progress = update.progress,
                label = "Downloading update...",
                trailingLabel = "${(update.progress * 100).toInt()}%",
            )

            is UpdateState.ReadyToInstall -> VmButton(
                text = "Install version ${update.info.versionName}",
                onClick = viewModel::retryInstall,
            )

            is UpdateState.Failed -> Column(
                verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
            ) {
                Text(
                    text = update.error.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                VmButton(
                    text = "Check for updates",
                    style = VmButtonStyle.Secondary,
                    onClick = viewModel::checkForUpdates,
                )
            }

            is UpdateState.Idle -> VmButton(
                text = "Check for updates",
                style = VmButtonStyle.Secondary,
                onClick = viewModel::checkForUpdates,
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabelValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = VmTheme.code.mono)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = "${value.roundToInt()} sp", style = VmTheme.code.mono)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
        )
    }
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "Follow system"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

private fun AgentAutonomyLevel.label(): String = when (this) {
    AgentAutonomyLevel.ASK_EVERY_TIME -> "Ask every time"
    AgentAutonomyLevel.SAFE_AUTO -> "Safe auto"
    AgentAutonomyLevel.DEVELOPER -> "Developer"
    AgentAutonomyLevel.FULL_AGENT -> "Full agent"
}

private fun AgentAutonomyLevel.description(): String = when (this) {
    AgentAutonomyLevel.ASK_EVERY_TIME -> "Every operation is proposed for approval."
    AgentAutonomyLevel.SAFE_AUTO -> "Reads and searches run automatically; changes are asked."
    AgentAutonomyLevel.DEVELOPER -> "File edits and approved commands run automatically."
    AgentAutonomyLevel.FULL_AGENT -> "Edits, tests and builds run in a loop without prompting."
}
