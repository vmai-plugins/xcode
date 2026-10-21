package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
internal fun AiLimitsSection(state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    val spacing = VmTheme.spacing
    VmCard {
        Text("Agent limits", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Max Model Turns: ${state.preferences.agentMaxIterations}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
            VmButton(
                text = "-5",
                onClick = { viewModel.setMaxIterations((state.preferences.agentMaxIterations - 5).coerceAtLeast(5)) },
                style = VmButtonStyle.Tertiary,
            )
            VmButton(
                text = "+5",
                onClick = { viewModel.setMaxIterations((state.preferences.agentMaxIterations + 5).coerceAtMost(200)) },
                style = VmButtonStyle.Tertiary,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))
        ToggleRow(
            label = "Tool use",
            description = "Let gateway models read/write files and run commands (still approval-gated).",
            checked = state.preferences.aiToolsEnabled,
            onCheckedChange = viewModel::setToolsEnabled,
        )
        ToggleRow(
            label = "Web access",
            description = "Let the agent fetch public documentation URLs.",
            checked = state.preferences.aiWebAccessEnabled,
            onCheckedChange = viewModel::setWebAccessEnabled,
        )
    }
}

@Composable
internal fun ContextSizePicker(selected: String, onSelect: (String) -> Unit) {
    val spacing = VmTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text("Context Window Size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            val options = listOf(
                UserPreferences.CONTEXT_SMALL to "Small (100k chars)",
                UserPreferences.CONTEXT_LARGE to "Large (400k chars)",
                UserPreferences.CONTEXT_HUGE to "Huge (1.2M chars)",
            )
            options.forEach { (size, label) ->
                FilterChip(
                    selected = selected == size,
                    onClick = { onSelect(size) },
                    label = { Text(label) },
                )
            }
        }
    }
}

internal const val MAX_MODEL_CHIPS = 8
