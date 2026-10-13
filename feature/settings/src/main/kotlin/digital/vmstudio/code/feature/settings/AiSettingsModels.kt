package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.theme.VmTheme

private enum class SettingsModelTab { SMART, FREE, ALL }

@Composable
internal fun AiModelSection(state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    val spacing = VmTheme.spacing
    var activeTab by remember { mutableStateOf(SettingsModelTab.SMART) }
    val smartModels = remember(state.availableModels) {
        state.availableModels.filter {
            it.startsWith("auto/") || it in setOf("opus", "opus-nvidia", "Kimi Coding")
        }
    }
    val freeModelsList = remember(state.availableModels, state.freeModels) {
        state.availableModels.filter { it in state.freeModels }
    }
    val displayedModels = when (activeTab) {
        SettingsModelTab.SMART -> smartModels.ifEmpty { state.availableModels }
        SettingsModelTab.FREE -> freeModelsList
        SettingsModelTab.ALL -> state.availableModels
    }

    VmCard {
        Text("Default Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            FilterChip(
                selected = activeTab == SettingsModelTab.SMART,
                onClick = { activeTab = SettingsModelTab.SMART },
                label = { Text("Smart Auto (${smartModels.size})") },
            )
            FilterChip(
                selected = activeTab == SettingsModelTab.FREE,
                onClick = { activeTab = SettingsModelTab.FREE },
                label = { Text("Free Models (${freeModelsList.size})") },
            )
            FilterChip(
                selected = activeTab == SettingsModelTab.ALL,
                onClick = { activeTab = SettingsModelTab.ALL },
                label = { Text("All (${state.availableModels.size})") },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            displayedModels.take(MAX_MODEL_CHIPS).forEach { model ->
                val isFree = model in state.freeModels
                FilterChip(
                    selected = state.preferences.aiSelectedModelId == model,
                    onClick = { viewModel.selectModel(model) },
                    label = { Text(if (isFree && !model.endsWith(":free")) "$model · FREE" else model) },
                )
            }
        }
        if (displayedModels.isEmpty()) {
            Text(
                text = "Tap 'Run test & sync models' above to live-sync models from OmniRoute.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CustomModelField(
            current = state.preferences.aiSelectedModelId.orEmpty(),
            onSelect = viewModel::selectModel,
        )
    }
    VmCard {
        ContextSizePicker(
            selected = state.preferences.aiContextSize,
            onSelect = viewModel::setContextSize,
        )
    }
}

@Composable
private fun CustomModelField(current: String, onSelect: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Model ID") },
            singleLine = true,
            textStyle = VmTheme.code.mono,
            modifier = Modifier.weight(1f),
        )
        if (draft.trim() != current) {
            VmButton(
                text = "Use",
                onClick = { onSelect(draft.trim()) },
                enabled = draft.isNotBlank(),
            )
        }
    }
}
