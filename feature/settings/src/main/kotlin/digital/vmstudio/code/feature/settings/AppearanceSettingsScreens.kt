package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
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
                Text("Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                ThemePreference.entries.forEach { option ->
                    OptionRow(
                        label = option.label(),
                        selected = state.preferences.themePreference == option,
                        onSelect = { viewModel.setTheme(option) },
                    )
                }
            }
        }
    }
}

@Composable
fun EditorSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
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
                FontSliderRow(
                    label = "Editor font size",
                    value = state.preferences.editorFontSizeSp,
                    onValueChange = viewModel::setEditorFontSize,
                )
                FontSliderRow(
                    label = "Terminal font size",
                    value = state.preferences.terminalFontSizeSp,
                    onValueChange = viewModel::setTerminalFontSize,
                )
            }
        }
    }
}

@Composable
private fun FontSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = "${value.roundToInt()} sp", style = VmTheme.code.mono)
        Slider(value = value, onValueChange = onValueChange, valueRange = 9f..22f, steps = 12)
    }
}

private fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "Follow system"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}
