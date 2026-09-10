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
                    value = state.preferences.aiBaseUrl,
                    onCommit = viewModel::setBaseUrl,
                )
                ApiKeyField(
                    hasStoredKey = state.hasStoredKey,
                    onSave = viewModel::saveApiKey,
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
                    onClick = { viewModel.runConnectionTest(null) },
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
                    health.endpoint?.let { ResultRow("Endpoint", it) }
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
private fun BaseUrlField(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Base URL") },
        singleLine = true,
        textStyle = VmTheme.code.mono,
        supportingText = {
            Text(
                text = "The gateway must implement the Anthropic Messages API or the " +
                    "OpenAI chat API. The connection test reports which it found.",
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (text != value) {
        VmButton(
            text = "Save URL",
            onClick = { onCommit(text) },
            style = VmButtonStyle.Tertiary,
        )
    }
}

@Composable
private fun ApiKeyField(
    hasStoredKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
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
            onClick = {
                onSave(key)
                key = ""
            },
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
