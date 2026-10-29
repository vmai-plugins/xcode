package digital.vmstudio.code.feature.servers

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.database.entity.ServerEnvironment
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import digital.vmstudio.code.core.ssh.model.ServerField
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorPanel
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
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val keyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    if (text.isNotBlank()) {
                        viewModel.setPrivateKey(text)
                        Toast.makeText(context, "Private key loaded from file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

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
                title = {
                    Text(
                        text = if (state.isEditing) "Edit Server" else "Add New Server",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    VmButton(
                        text = "Test Connection",
                        onClick = viewModel::testConnection,
                        loading = state.isTesting,
                        enabled = !state.isSaving && !state.isTesting,
                        style = VmButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    VmButton(
                        text = if (state.isEditing) "Save Changes" else "Add Server",
                        onClick = viewModel::save,
                        loading = state.isSaving,
                        enabled = !state.isSaving && !state.isTesting,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = spacing.screenHorizontal,
                        end = spacing.screenHorizontal,
                        top = spacing.sm,
                        bottom = spacing.xl,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            state.saveError?.let { error ->
                VmErrorPanel(error = error, onRetry = viewModel::save)
            }

            if (state.testSuccessMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = state.testSuccessMessage!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // --- 1. Machine Type Preset Section ---
            CardSection(title = "Machine Type & Presets") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    FilterChip(
                        selected = state.selectedPreset == ServerPreset.CLOUD_VPS,
                        onClick = { viewModel.applyPreset(ServerPreset.CLOUD_VPS) },
                        leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Cloud Linux VPS", fontWeight = FontWeight.Medium) },
                        colors = chipColors(),
                    )
                    FilterChip(
                        selected = state.selectedPreset == ServerPreset.WINDOWS_PC,
                        onClick = { viewModel.applyPreset(ServerPreset.WINDOWS_PC) },
                        leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Windows PC / Laptop", fontWeight = FontWeight.Medium) },
                        colors = chipColors(),
                    )
                    FilterChip(
                        selected = state.selectedPreset == ServerPreset.MAC_LINUX_PC,
                        onClick = { viewModel.applyPreset(ServerPreset.MAC_LINUX_PC) },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text("Mac / Linux PC", fontWeight = FontWeight.Medium) },
                        colors = chipColors(),
                    )
                }

                if (state.selectedPreset == ServerPreset.WINDOWS_PC) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "💡 Windows PC OpenSSH Quick Guide",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "1. Enable OpenSSH on PC (Run in Admin PowerShell):\n   Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0; Start-Service sshd\n2. Host: Enter your PC's Tailscale IP (100.x.y.z) for 24/7 mobile access.\n3. Default Shell is automatically set to 'powershell.exe'.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                VmButton(
                                    text = "Copy Setup Command",
                                    icon = Icons.Default.ContentCopy,
                                    onClick = {
                                        clipboard.setText(AnnotatedString("Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0; Start-Service sshd"))
                                        Toast.makeText(context, "Command copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    style = VmButtonStyle.Tertiary,
                                )
                            }
                        }
                    }
                } else if (state.selectedPreset == ServerPreset.MAC_LINUX_PC) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "💡 Mac / Linux OpenSSH Guide",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "• macOS: Enable 'Remote Login' in System Settings > General > Sharing.\n• Linux: Run 'sudo systemctl enable --now ssh'.\nEnter your local Wi-Fi or Tailscale IP (100.x.y.z).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // --- 2. Connection Details Section ---
            CardSection(title = "Connection Details") {
                VmField(
                    value = state.draft.name,
                    onValueChange = viewModel::setName,
                    label = "Server Name",
                    supporting = "e.g. \"Work Laptop\", \"Production VPS\", \"TripCosmos Host\"",
                    error = state.errorFor(ServerField.NAME),
                )

                VmField(
                    value = state.draft.host,
                    onValueChange = viewModel::setHost,
                    label = "Host / IP Address",
                    supporting = "Tailscale IP (100.x.y.z), local IP (192.168.x.x), or domain",
                    error = state.errorFor(ServerField.HOST),
                    keyboardType = KeyboardType.Uri,
                    trailingIcon = {
                        IconButton(onClick = {
                            clipboard.getText()?.text?.let { viewModel.setHost(it.trim()) }
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste host from clipboard", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    VmField(
                        value = state.draft.port,
                        onValueChange = viewModel::setPort,
                        label = "Port",
                        error = state.errorFor(ServerField.PORT),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.width(100.dp),
                    )
                    VmField(
                        value = state.draft.username,
                        onValueChange = viewModel::setUsername,
                        label = "Username",
                        supporting = "e.g. root, tripc, ubuntu",
                        error = state.errorFor(ServerField.USERNAME),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // --- 3. Authentication Section ---
            CardSection(title = "Authentication") {
                AuthMethodSelector(
                    selected = state.draft.authMethod,
                    onSelect = viewModel::setAuthMethod,
                )

                if (state.draft.retainExistingSecret) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Stored credential is kept. Enter a new one only if you want to replace it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                when (state.draft.authMethod) {
                    SshAuthMethod.PASSWORD -> {
                        SecretField(
                            value = state.draft.password,
                            onValueChange = viewModel::setPassword,
                            label = "Password",
                            error = state.errorFor(ServerField.PASSWORD),
                        )
                    }

                    SshAuthMethod.PRIVATE_KEY,
                    SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE -> {
                        VmField(
                            value = state.draft.privateKeyPem,
                            onValueChange = viewModel::setPrivateKey,
                            label = "Private Key (PEM / OpenSSH)",
                            supporting = "Paste full key file including BEGIN and END lines",
                            error = state.errorFor(ServerField.PRIVATE_KEY),
                            singleLine = false,
                            minLines = 4,
                            monospace = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VmButton(
                                text = "Paste Key",
                                icon = Icons.Default.ContentPaste,
                                onClick = {
                                    clipboard.getText()?.text?.let { viewModel.setPrivateKey(it.trim()) }
                                },
                                style = VmButtonStyle.Tertiary,
                                modifier = Modifier.weight(1f),
                            )
                            VmButton(
                                text = "Import File",
                                icon = Icons.Default.FileOpen,
                                onClick = { keyPickerLauncher.launch(arrayOf("*/*")) },
                                style = VmButtonStyle.Tertiary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.draft.authMethod == SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE) {
                            SecretField(
                                value = state.draft.passphrase,
                                onValueChange = viewModel::setPassphrase,
                                label = "Key Passphrase",
                                error = state.errorFor(ServerField.PASSPHRASE),
                            )
                        }
                    }

                    SshAuthMethod.AGENT -> {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = "SSH Agent forwarding is not supported on Android.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // --- 4. Environment & Advanced Section ---
            CardSection(title = "Environment & Settings") {
                EnvironmentSelector(
                    selected = state.draft.environment,
                    onSelect = viewModel::setEnvironment,
                )

                if (state.draft.environment == ServerEnvironment.PRODUCTION) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "⚠️ Production Server: Destructive commands will always prompt for confirmation before execution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                VmField(
                    value = state.draft.defaultDirectory,
                    onValueChange = viewModel::setDefaultDirectory,
                    label = "Default Working Directory",
                    supporting = "e.g. D:\\Dev\\x-codes, /root, /var/www/html",
                    monospace = true,
                )

                VmField(
                    value = state.draft.preferredShell,
                    onValueChange = viewModel::setPreferredShell,
                    label = "Preferred Shell",
                    supporting = "e.g. powershell.exe, /bin/zsh, /bin/bash",
                    monospace = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
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
                        supporting = "0 to disable",
                        error = state.errorFor(ServerField.KEEP_ALIVE),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }

                VmField(
                    value = state.draft.notes,
                    onValueChange = viewModel::setNotes,
                    label = "Notes & Description",
                    singleLine = false,
                    minLines = 2,
                )
            }

            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    content: @Composable () -> Unit,
) {
    VmCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

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
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
            )
        },
        isError = error != null,
        singleLine = singleLine,
        minLines = minLines,
        textStyle = (if (monospace) VmTheme.code.mono else MaterialTheme.typography.bodyLarge).copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        trailingIcon = trailingIcon,
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
        label = {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
            )
        },
        isError = error != null,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            errorBorderColor = MaterialTheme.colorScheme.error,
            errorLabelColor = MaterialTheme.colorScheme.error,
        ),
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        supportingText = {
            val message = error?.let { it.reason ?: it.summary }
                ?: "Encrypted with hardware key in Android Keystore."
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
        SshAuthMethod.PRIVATE_KEY to "SSH Key",
        SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE to "Key + passphrase",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        options.forEach { (method, label) ->
            FilterChip(
                selected = selected == method,
                onClick = { onSelect(method) },
                label = { Text(label, fontWeight = FontWeight.Medium) },
                colors = chipColors(),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        options.forEach { (environment, label) ->
            FilterChip(
                selected = selected == environment,
                onClick = { onSelect(environment) },
                label = { Text(label, fontWeight = FontWeight.Medium) },
                colors = chipColors(),
            )
        }
    }
}
