package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.provider.AiProviderKind
import digital.vmstudio.code.core.ai.provider.capabilitySummary
import digital.vmstudio.code.core.ai.provider.displayName
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun AiSettingsSection(
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var urlDraft by remember(state.preferences.aiBaseUrl) { mutableStateOf(state.preferences.aiBaseUrl) }
    var keyDraft by remember { mutableStateOf("") }
    val isGateway = state.selectedKind == AiProviderKind.OMNIROUTE

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        VmCard {
            Text("AI Engine Provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            AiProviderKind.entries.forEach { kind ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(kind.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = kind.capabilitySummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RadioButton(
                        selected = state.selectedKind == kind,
                        onClick = { viewModel.selectProvider(kind) },
                    )
                }
            }
        }
        AiGatewaySection(
            state = state,
            viewModel = viewModel,
            urlDraft = urlDraft,
            onUrlDraft = { urlDraft = it },
            keyDraft = keyDraft,
            onKeyDraft = { keyDraft = it },
            onKeyConsumed = { keyDraft = "" },
            isGateway = isGateway,
        )
    }
}
