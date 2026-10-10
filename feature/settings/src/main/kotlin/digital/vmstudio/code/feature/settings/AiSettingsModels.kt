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

@Composable
internal fun AiModelSection(state: AiSettingsUiState, viewModel: AiSettingsViewModel) {
    val spacing = VmTheme.spacing
    VmCard {
        Text("Model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            state.availableModels.take(MAX_MODEL_CHIPS).forEach { model ->
                FilterChip(
                    selected = state.preferences.aiSelectedModelId == model,
                    onClick = { viewModel.selectModel(model) },
                    label = { Text(model) },
                )
            }
        }
        if (state.availableModels.isEmpty()) {
            Text(
                text = "Run the connection test to sync the model list from the gateway.",
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
