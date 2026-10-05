package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.ai.provider.capabilitySummary
import digital.vmstudio.code.core.ai.provider.displayName
import digital.vmstudio.code.core.common.preferences.UserPreferences
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * AI provider configuration and the connection test the specification calls for.
 *
 * The two backends are presented with their capabilities stated rather than as
 * interchangeable options, because they are not interchangeable: only one can touch
 * files, and discovering that by asking the other to edit something would be a poor
 * way to find out.
 */
@Composable
fun AiSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    // Drafts live here so "Run test" can save whatever was typed before testing.
    var urlDraft by remember(state.preferences.aiBaseUrl) { mutableStateOf(state.preferences.aiBaseUrl) }
    var keyDraft by remember { mutableStateOf("") }
    val isGateway = state.selectedKind == AiProviderKind.OMNIROUTE

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        VmCard {
            Text("Provider", style = MaterialTheme.typography.titleSmall)

            AiProviderKind.entries.forEach { kind ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    RadioButton(
                        selected = state.selectedKind == kind,
                        onClick = { viewModel.selectProvider(kind) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = kind.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = kind.capabilitySummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (state.selectedKind == AiProviderKind.OMNIROUTE) {
            VmCard {
                Text("Gateway", style = MaterialTheme.typography.titleSmall)
                BaseUrlField(
                    value = urlDraft,
                    saved = state.preferences.aiBaseUrl,
                    onValueChange = { urlDraft = it },
                    onCommit = viewModel::setBaseUrl,
                )
                ApiKeyField(
                    key = keyDraft,
                    onKeyChange = { keyDraft = it },
                    hasStoredKey = state.hasStoredKey,
                    onSave = {
                        viewModel.saveApiKey(keyDraft)
                        keyDraft = ""
                    },
                    onClear = viewModel::clearApiKey,
                )
            }
        } else {
            VmCard {
                Text(
                    text = "Claude Code authenticates on the server itself. Sign in there " +
                        "with \"claude auth login\", or set ANTHROPIC_BASE_URL and a token " +
                        "in its environment to use a gateway. This app never stores that " +
                        "credential.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.selectedKind == AiProviderKind.OMNIROUTE) {
            VmCard {
                Text("Model", style = MaterialTheme.typography.titleSmall)
                val models = state.availableModels
                if (models.isEmpty()) {
                    Text(
                        text = "No models synced yet. Run the connection test to fetch " +
                            "the gateway's model list, or enter a model id below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        models.take(MAX_MODEL_CHIPS).forEach { model ->
                            FilterChip(
                                selected = state.preferences.aiSelectedModelId == model,
                                onClick = { viewModel.selectModel(model) },
                                label = { Text(model) },
                            )
                        }
                    }
                }
                CustomModelField(
                    current = state.preferences.aiSelectedModelId,
                    onSelect = viewModel::selectModel,
                )
            }

            VmCard {
                ContextSizePicker(
                    selected = state.preferences.aiContextSize,
                    onSelect = viewModel::setContextSize,
                )
            }

            VmCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tool use", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Lets the model read, edit and search files, run " +
                                "commands and fetch web pages on your server. Edits and " +
                                "risky commands still ask first. How well it works " +
                                "depends on the model's tool calling.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.preferences.aiToolsEnabled,
                        onCheckedChange = viewModel::setToolsEnabled,
                    )
                }
            }
        }

        VmCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Connection test", style = MaterialTheme.typography.titleSmall)
                VmButton(
                    text = "Run test",
                    onClick = {
                        viewModel.runConnectionTest(
                            serverId = null,
                            pendingUrl = urlDraft.takeIf { isGateway },
                            pendingKey = keyDraft.takeIf { isGateway },
                        )
                        keyDraft = ""
                    },
                    style = VmButtonStyle.Secondary,
                    loading = state.isTesting,
                )
            }

            state.health?.let { health ->
                Column(
                    modifier = Modifier.padding(top = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (health.isAvailable) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.ErrorOutline
                            },
                            contentDescription = null,
                            tint = if (health.isAvailable) {
                                VmTheme.colors.success
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = if (health.isAvailable) "Reachable" else "Not reachable",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        health.latencyMillis?.let {
                            VmChip(text = "$it ms", monospace = true)
                        }
                    }

                    // The endpoint is shown; the credential never is, not even masked
                    // with a length hint.
                    health.endpoint?.let { ResultRow("Endpoint", it.ifBlank { "Not set" }) }
                    health.version?.let { ResultRow("API", it) }
                    ResultRow(
                        "Authentication",
                        if (health.isAuthenticated) "Accepted" else "Not established",
                    )
                    ResultRow("Models", "${health.availableModels.size} available")

                    health.diagnosis?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = VmTheme.colors.warning,
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            VmErrorPanel(
                error = error,
                onRetry = { viewModel.runConnectionTest(null) },
                actions = listOf(
                    digital.vmstudio.code.core.ui.component.VmErrorAction(
                        label = "Dismiss",
                        onClick = viewModel::dismissError,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun BaseUrlField(
    value: String,
    saved: String,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Gateway URL") },
        placeholder = { Text("https://your-gateway.example.com/v1") },
        singleLine = true,
        textStyle = VmTheme.code.mono,
        supportingText = {
            Text(
                text = "Your OmniRoute (or any OpenAI- or Anthropic-compatible) gateway. " +
                    "With or without /v1. Run test saves it.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (value.trim() != saved) {
        VmButton(
            text = "Save URL",
            onClick = { onCommit(value) },
            style = VmButtonStyle.Tertiary,
        )
    }
}

@Composable
private fun ApiKeyField(
    key: String,
    onKeyChange: (String) -> Unit,
    hasStoredKey: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = key,
        onValueChange = onKeyChange,
        label = { Text(if (hasStoredKey) "Replace API key" else "API key") },
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (revealed) "Hide key" else "Show key",
                )
            }
        },
        supportingText = {
            Text(
                text = if (hasStoredKey) {
                    "A key is stored, encrypted with a key held in the Android Keystore. " +
                        "It cannot be read back here."
                } else {
                    "Stored encrypted; only a reference is kept in settings."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
        VmButton(
            text = "Save key",
            onClick = onSave,
            enabled = key.isNotBlank(),
            style = VmButtonStyle.Secondary,
        )
        if (hasStoredKey) {
            VmButton(text = "Remove", onClick = onClear, style = VmButtonStyle.Tertiary)
        }
    }
}

/**
 * Free-text model entry for models the gateway knows but does not list, and the
 * only path to a selection before the first sync. The id is used verbatim as the
 * request's "model" field — no prefixing, no validation theatre.
 */
@Composable
private fun CustomModelField(current: String?, onSelect: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current.orEmpty()) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Model id") },
        singleLine = true,
        textStyle = VmTheme.code.mono,
        supportingText = {
            Text(
                text = if (current.isNullOrBlank()) {
                    "Sent verbatim as the \"model\" field in chat requests."
                } else {
                    "Current: $current. A new id is sent verbatim in chat requests."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = VmTheme.spacing.sm),
    )

    if (text.isNotBlank() && text.trim() != current) {
        VmButton(
            text = "Use this model",
            onClick = { onSelect(text.trim()) },
            style = VmButtonStyle.Tertiary,
        )
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = VmTheme.code.mono, maxLines = 1)
    }
}

/** Enough to choose from without turning the card into a scrolling list. */
private const val MAX_MODEL_CHIPS = 12

/**
 * How much conversation the agent keeps before trimming. Larger keeps more of a
 * long task in view but costs more per request and needs a model with a long
 * context window.
 */
@Composable
private fun ContextSizePicker(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        UserPreferences.CONTEXT_SMALL to "Small",
        UserPreferences.CONTEXT_LARGE to "Large",
        UserPreferences.CONTEXT_HUGE to "Huge",
    )
    Text("Context size", style = MaterialTheme.typography.titleSmall)
    Text(
        text = "How much of a long task the agent keeps in view. Small (about 25k tokens) " +
            "for free models with short windows, Large (about 100k) for most strong models, " +
            "Huge (about 300k) for long-context models. Larger costs more per step.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.padding(top = VmTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}
