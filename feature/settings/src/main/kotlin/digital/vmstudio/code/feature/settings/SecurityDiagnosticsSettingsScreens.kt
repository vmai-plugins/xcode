package digital.vmstudio.code.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var confirmErase by remember { mutableStateOf(false) }
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
                LabelValueRow(
                    label = "Stored credentials",
                    value = state.storedCredentialCount.toString(),
                )
                LabelValueRow(
                    label = "Encryption key",
                    value = if (state.isHardwareBackedKeystore) {
                        "Hardware-backed (StrongBox)"
                    } else {
                        "Keystore (Software AES-256)"
                    },
                )
                Text(
                    text = "Passwords, keys and API tokens are encrypted with AES-256-GCM " +
                        "using a key held in the Android Keystore. " +
                        "The key cannot be extracted from this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    if (confirmErase) {
        VmDialog(
            title = "Erase all credentials?",
            onDismiss = { confirmErase = false },
            confirmLabel = "Erase",
            destructive = true,
            onConfirm = {
                confirmErase = false
                viewModel.eraseAllCredentials { }
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
fun DiagnosticsSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = VmTheme.spacing.screenHorizontal,
                vertical = VmTheme.spacing.md,
            ),
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
    ) {
        DiagnosticsSettingsContent(viewModel = viewModel)
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
