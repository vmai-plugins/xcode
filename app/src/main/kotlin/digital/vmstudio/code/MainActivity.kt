package digital.vmstudio.code

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.ui.VmApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val authViewModel: digital.vmstudio.code.ui.auth.AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleDeepLink(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Preferences drive the theme, so rendering before they load would show
            // the wrong theme for a frame and then flip.
            val ready = state as? MainUiState.Ready
            var isBiometricUnlocked by rememberSaveable { mutableStateOf(false) }

            VmTheme(
                themeMode = ready?.themeMode ?: digital.vmstudio.code.core.ui.theme.ThemeMode.SYSTEM,
                codeFontSizeSp = ready?.preferences?.editorFontSizeSp ?: 13f,
                terminalFontSizeSp = ready?.preferences?.terminalFontSizeSp ?: 12.5f,
            ) {
                if (ready != null) {
                    if (ready.preferences.biometricLockEnabled && !isBiometricUnlocked) {
                        BiometricLockGate(onUnlocked = { isBiometricUnlocked = true })
                    } else {
                        VmApp(
                            isOnline = ready.isOnline,
                            hasSignedIn = ready.preferences.hasSignedIn,
                            hasCompletedOnboarding = ready.preferences.hasCompletedOnboarding,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "vmstudio" && uri.host == "auth") {
            when (uri.path) {
                "/github" -> {
                    val code = uri.getQueryParameter("code")
                    if (!code.isNullOrBlank()) {
                        authViewModel.handleGitHubAuthCode(code)
                    }
                }
                "/google" -> {
                    val code = uri.getQueryParameter("code")
                    if (!code.isNullOrBlank()) {
                        authViewModel.handleGoogleAuthCode(code)
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricLockGate(
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val keyguardManager = remember {
        context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }
    val isSecure = keyguardManager?.isDeviceSecure == true

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    val triggerUnlock = {
        if (isSecure) {
            val intent = keyguardManager?.createConfirmDeviceCredentialIntent(
                "Unlock X-Codes",
                "Verify your fingerprint, face, or PIN to access your workspaces",
            )
            if (intent != null) {
                launcher.launch(intent)
            } else {
                onUnlocked()
            }
        } else {
            onUnlocked()
        }
    }

    LaunchedEffect(Unit) {
        triggerUnlock()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "X-Codes Protected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSecure) {
                    "Biometric App Lock is active. Please authenticate to access your projects and credentials."
                } else {
                    "Device security is not configured. Set a screen lock in Android Settings to protect your credentials."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(32.dp))
            VmButton(
                text = if (isSecure) "Unlock with Biometrics / PIN" else "Open App",
                onClick = triggerUnlock,
                style = VmButtonStyle.Primary,
            )
        }
    }
}

