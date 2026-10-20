package digital.vmstudio.code.feature.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmDialog
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun SecuritySettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var confirmErase by remember { mutableStateOf(false) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportPassphrase by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var importPayload by remember { mutableStateOf("") }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = spacing.sm)) {
                        Text(
                            text = "Biometric & Screen Lock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Require fingerprint, face unlock, or device PIN every time X-Codes is launched.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = state.preferences.biometricLockEnabled,
                        onCheckedChange = { viewModel.setBiometricLockEnabled(it) },
                    )
                }
            }
        }

        item {
            VmCard {
                Text(
                    text = "Zero-Trust Encrypted Vault",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Backup and sync your VPS servers and preferences across devices. " +
                        "Data is encrypted on-device with AES-256-GCM using your master passphrase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.vaultStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    VmButton(
                        text = "Export Vault",
                        onClick = { showExportDialog = true },
                        style = VmButtonStyle.Primary,
                        loading = state.isVaultBusy,
                        modifier = Modifier.weight(1f),
                    )
                    VmButton(
                        text = "Import Vault",
                        onClick = { showImportDialog = true },
                        style = VmButtonStyle.Secondary,
                        loading = state.isVaultBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            VmCard {
                LabelValueRow(
                    label = "Stored credentials",
                    value = state.storedCredentialCount.toString(),
                )
                LabelValueRow(
                    label = "Encryption key",
                    value = when (state.isHardwareBackedKeystore) {
                        true -> "Hardware-backed (TEE or StrongBox)"
                        false -> "Software-backed Keystore (AES-256)"
                        null -> "Not initialized (no credentials stored)"
                    },
                )
                Text(
                    text = "Passwords, keys and API tokens are encrypted with AES-256-GCM " +
                        "using a key held in the Android Keystore. " +
                        "The key cannot be extracted from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.credentialEraseError?.let { error ->
                    Text(
                        text = "Could not erase credentials: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                VmButton(
                    text = "Erase all credentials",
                    onClick = { confirmErase = true },
                    style = VmButtonStyle.Destructive,
                    enabled = state.storedCredentialCount > 0,
                    loading = state.isErasing,
                )
            }
        }
    }

    if (showExportDialog) {
        VmDialog(
            title = "Export Encrypted Vault",
            onDismiss = { showExportDialog = false },
            confirmLabel = "Export & Share",
            onConfirm = {
                showExportDialog = false
                viewModel.exportVault(exportPassphrase) { result ->
                    when (result) {
                        is VmResult.Success -> {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "x-codes-vault-backup.txt")
                                putExtra(Intent.EXTRA_TEXT, result.value)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Encrypted Vault"))
                        }
                        is VmResult.Failure -> {
                            Toast.makeText(context, result.error.summary, Toast.LENGTH_LONG).show()
                        }
                    }
                    exportPassphrase = ""
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a strong passphrase. You will need this passphrase to restore your vault.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = exportPassphrase,
                    onValueChange = { exportPassphrase = it },
                    label = { Text("Master Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showImportDialog) {
        VmDialog(
            title = "Import Encrypted Vault",
            onDismiss = { showImportDialog = false },
            confirmLabel = "Decrypt & Restore",
            onConfirm = {
                showImportDialog = false
                viewModel.importVault(importPayload, importPassphrase) { result ->
                    when (result) {
                        is VmResult.Success -> {
                            Toast.makeText(context, "Restored ${result.value} servers!", Toast.LENGTH_SHORT).show()
                        }
                        is VmResult.Failure -> {
                            Toast.makeText(context, result.error.summary, Toast.LENGTH_LONG).show()
                        }
                    }
                    importPayload = ""
                    importPassphrase = ""
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paste the encrypted vault payload and enter the master passphrase.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = importPayload,
                    onValueChange = { importPayload = it },
                    label = { Text("Vault Base64 Content") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = importPassphrase,
                    onValueChange = { importPassphrase = it },
                    label = { Text("Master Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                viewModel.eraseAllCredentials()
            },
        ) {
            Text(
                text = "Every stored password, private key and API token is deleted from " +
                    "this device, along with the encryption key. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun DiagnosticsSettingsContent(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    VmCard {
        Text(
            text = "Diagnostics contain sanitized telemetry and logs. " +
                "Secrets are stripped automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm)) {
            VmButton(
                text = "Export Diagnostics",
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

