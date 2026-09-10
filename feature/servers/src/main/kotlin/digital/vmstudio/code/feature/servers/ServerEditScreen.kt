package digital.vmstudio.code.feature.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.ssh.model.ServerField
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmSectionHeader
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    LaunchedEffect(state.savedServerId) {
        state.savedServerId?.let { id ->
            viewModel.consumeSaved()
            onSaved(id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit server" else "Add server") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        bottom = spacing.xl,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            state.saveError?.let { error ->
                VmErrorPanel(error = error, onRetry = viewModel::save)
            }

            VmSectionHeader(title = "Connection")

            VmField(
                value = state.draft.name,
                onValueChange = viewModel::setName,
                label = "Name",
                supporting = "Shown throughout the app, e.g. \"Production VPS\"",
                error = state.errorFor(ServerField.NAME),
            )

            VmField(
                value = state.draft.host,
                onValueChange = viewModel::setHost,
                label = "Host",
                supporting = "Hostname or IP address",
                error = state.errorFor(ServerField.HOST),
                keyboardType = KeyboardType.Uri,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                VmField(
                    value = state.draft.port,
                    onValueChange = viewModel::setPort,
                    label = "Port",
                    error = state.errorFor(ServerField.PORT),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(0.4f),
                )
                VmField(
                    value = state.draft.username,
                    onValueChange = viewModel::setUsername,
                    label = "Username",
                    error = state.errorFor(ServerField.USERNAME),
                    modifier = Modifier.weight(0.6f),
                )
            }

            VmSectionHeader(title = "Authentication")

            AuthMethodSelector(
                selected = state.draft.authMethod,
                onSelect = viewModel::setAuthMethod,
            )

            if (state.draft.retainExistingSecret) {
                VmCard {
                    Text(
                        text = "The stored credential is kept. Enter a new one only if you " +
                            "want to replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (state.draft.authMethod) {
                SshAuthMethod.PASSWORD -> SecretField(
                    value = state.draft.password,
                    onValueChange = viewModel::setPassword,
                    label = "Password",
                    error = state.errorFor(ServerField.PASSWORD),
                )

                SshAuthMethod.PRIVATE_KEY,
                SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE,
                -> {
                    VmField(
                        value = state.draft.privateKeyPem,
                        onValueChange = viewModel::setPrivateKey,
                        label = "Private key",
                        supporting = "Paste the whole key file, including BEGIN and END lines",
                        error = state.errorFor(ServerField.PRIVATE_KEY),
                        singleLine = false,
                        minLines = 4,
                        monospace = true,
                    )
                    if (state.draft.authMethod == SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE) {
                        SecretField(
                            value = state.draft.passphrase,
                            onValueChange = viewModel::setPassphrase,
                            label = "Key passphrase",
                            error = state.errorFor(ServerField.PASSPHRASE),
                        )
                    }
                }

                SshAuthMethod.AGENT -> VmCard {
                    Text(
                        text = "Agent forwarding is not available yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            VmSectionHeader(title = "Environment")

            EnvironmentSelector(
                selected = state.draft.environment,
                onSelect = viewModel::setEnvironment,
            )

            if (state.draft.environment == ServerEnvironment.PRODUCTION) {
                VmCard {
                    Text(
                        text = "Marked as production. Destructive commands will always require " +
                            "confirmation on this server, including when the AI agent proposes them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = VmTheme.colors.onWarningContainer,
                    )
                }
            }

            VmSectionHeader(title = "Advanced")

            VmField(
                value = state.draft.defaultDirectory,
                onValueChange = viewModel::setDefaultDirectory,
                label = "Default directory",
                supporting = "Optional. Where terminals and the file browser open.",
                monospace = true,
            )

            VmField(
                value = state.draft.preferredShell,
                onValueChange = viewModel::setPreferredShell,
                label = "Preferred shell",
                supporting = "Optional. Leave empty to detect the login shell on connect.",
                monospace = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                VmField(
                    value = state.draft.connectTimeoutSeconds,
                    onValueChange = viewModel::setConnectTimeout,
                    label = "Timeout (s)",
                    error = state.errorFor(ServerField.CONNECT_TIMEOUT),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                VmField(
                    value = state.draft.keepAliveSeconds,
                    onValueChange = viewModel::setKeepAlive,
                    label = "Keepalive (s)",
                    supporting = "0 disables",
                    error = state.errorFor(ServerField.KEEP_ALIVE),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }

            VmField(
                value = state.draft.notes,
                onValueChange = viewModel::setNotes,
                label = "Notes",
                singleLine = false,
                minLines = 2,
            )

            VmButton(
                text = if (state.isEditing) "Save changes" else "Add server",
                onClick = viewModel::save,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VmField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    error: VmError.Validation? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = if (monospace) VmTheme.code.mono else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        supportingText = {
            val message = error?.let { it.reason ?: it.summary } ?: supporting
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
    )
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: VmError.Validation?,
    modifier: Modifier = Modifier,
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        isError = error != null,
        singleLine = true,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (revealed) "Hide $label" else "Show $label",
                )
            }
        },
        supportingText = {
            val message = error?.let { it.reason ?: it.summary }
                ?: "Stored encrypted with a key held in the Android Keystore."
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthMethodSelector(
    selected: SshAuthMethod,
    onSelect: (SshAuthMethod) -> Unit,
) {
    val options = listOf(
        SshAuthMethod.PASSWORD to "Password",
        SshAuthMethod.PRIVATE_KEY to "Key",
        SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE to "Key + passphrase",
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        options.forEach { (method, label) ->
            FilterChip(
                selected = selected == method,
                onClick = { onSelect(method) },
                label = { Text(label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnvironmentSelector(
    selected: ServerEnvironment,
    onSelect: (ServerEnvironment) -> Unit,
) {
    val options = listOf(
        ServerEnvironment.UNSPECIFIED to "Unspecified",
        ServerEnvironment.DEVELOPMENT to "Development",
        ServerEnvironment.STAGING to "Staging",
        ServerEnvironment.PRODUCTION to "Production",
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        options.forEach { (environment, label) ->
            FilterChip(
                selected = selected == environment,
                onClick = { onSelect(environment) },
                label = { Text(label) },
            )
        }
    }
}
