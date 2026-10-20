package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.preferences.AgentAutonomyLevel
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmProgress
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.core.update.UpdateState

@Composable
fun AgentSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item {
            VmCard {
                ToggleRow(
                    label = "Always confirm destructive commands",
                    description = "Prompts before any command that can delete data or " +
                        "restart a service, in the terminal and anywhere else commands run.",
                    checked = state.preferences.confirmDestructiveCommands,
                    onCheckedChange = viewModel::setConfirmDestructiveCommands,
                )
            }
        }
        item {
            VmCard {
                Text(
                    "Autonomy Level",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "How much the agent may do autonomously before asking. " +
                        "Detached background runs on your VPS daemon will execute tools " +
                        "according to this policy.",
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
    }
}

@Composable
fun AboutSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = spacing.screenHorizontal,
            vertical = spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item { AboutSettingsContent(state = state, viewModel = viewModel) }
        item { DiagnosticsSettingsContent(viewModel = viewModel) }
    }
}

@Composable
internal fun AboutSettingsContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    UpdateCard(state = state, viewModel = viewModel)
}

@Composable
private fun UpdateCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    VmCard {
        LabelValueRow(label = "Application Version", value = state.installedVersionName)

        when (val update = state.updateState) {
            is UpdateState.Checking -> VmButton(
                text = "Checking for updates...",
                style = VmButtonStyle.Secondary,
                onClick = {},
                enabled = false,
            )

            is UpdateState.UpToDate -> VmButton(
                text = "Up to date · Check again",
                style = VmButtonStyle.Secondary,
                onClick = viewModel::checkForUpdates,
            )

            is UpdateState.Available -> VmButton(
                text = "Download version ${update.info.versionName}",
                onClick = viewModel::downloadUpdate,
            )

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
