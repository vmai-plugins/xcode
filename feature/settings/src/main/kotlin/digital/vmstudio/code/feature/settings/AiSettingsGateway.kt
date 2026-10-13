package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
internal fun AiGatewaySection(
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
    keyDraft: String,
    onKeyDraft: (String) -> Unit,
    onKeyConsumed: () -> Unit,
) {
    VmCard {
        Text("Gateway", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            text = digital.vmstudio.code.core.common.preferences.UserPreferences.DEFAULT_AI_BASE_URL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (state.hasStoredKey) {
                "Using custom API key override stored in Android Keystore."
            } else {
                "Connected via built-in VM Studio OmniRoute Cloud key (no manual key required)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        ApiKeyField(
            key = keyDraft,
            onKeyChange = onKeyDraft,
            hasStoredKey = state.hasStoredKey,
            onSave = {
                viewModel.runConnectionTest(
                    pendingKey = keyDraft,
                    onKeySaved = onKeyConsumed,
                )
            },
            onClear = viewModel::clearApiKey,
        )
    }
    VmCard {
        Text("Connection", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        VmButton(
            text = if (state.isTesting) "Testing..." else "Run test & sync models",
            onClick = { viewModel.runConnectionTest(pendingKey = keyDraft, onKeySaved = onKeyConsumed) },
            enabled = !state.isTesting,
            style = VmButtonStyle.Secondary,
        )
        state.health?.let { health ->
            Text(
                text = if (health.isAvailable) {
                    val freeCount = state.freeModels.size
                    "Connected (${health.availableModels.size} models synced · $freeCount free)"
                } else {
                    "Not reachable"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.error?.let { error ->
            VmErrorPanel(
                error = error,
                onRetry = { viewModel.runConnectionTest(pendingKey = keyDraft, onKeySaved = onKeyConsumed) },
            )
        }
    }
    AiModelSection(state = state, viewModel = viewModel)
    AiLimitsSection(state = state, viewModel = viewModel)
}

@Composable
private fun ApiKeyField(
    key: String,
    onKeyChange: (String) -> Unit,
    hasStoredKey: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = key,
        onValueChange = onKeyChange,
        label = {
            Text(
                if (hasStoredKey) "Replace custom API key"
                else "Custom API key override (optional)",
            )
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
        VmButton(
            text = "Save override key",
            onClick = onSave,
            enabled = key.isNotBlank(),
            style = VmButtonStyle.Secondary,
        )
        if (hasStoredKey) {
            VmButton(text = "Use built-in key", onClick = onClear, style = VmButtonStyle.Tertiary)
        }
    }
}
