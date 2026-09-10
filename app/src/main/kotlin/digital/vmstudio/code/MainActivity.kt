package digital.vmstudio.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.ui.VmApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Preferences drive the theme, so rendering before they load would show
            // the wrong theme for a frame and then flip.
            val ready = state as? MainUiState.Ready

            VmTheme(
                themeMode = ready?.themeMode ?: digital.vmstudio.code.core.ui.theme.ThemeMode.SYSTEM,
                codeFontSizeSp = ready?.preferences?.editorFontSizeSp ?: 13f,
                terminalFontSizeSp = ready?.preferences?.terminalFontSizeSp ?: 12.5f,
            ) {
                if (ready != null) {
                    VmApp(
                        widthSizeClass = calculateWindowSizeClass(this).widthSizeClass,
                        isOnline = ready.isOnline,
                    )
                }
            }
        }
    }
}
