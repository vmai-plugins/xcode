package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ui.component.VmErrorPanel

@Composable
internal fun AiGatewaySection(
    state: AiSettingsUiState,
    viewModel: AiSettingsViewModel,
    keyDraft: String,
    onKeyDraft: (String) -> Unit,
    onKeyConsumed: () -> Unit,
) {
    // 1. Gateway Status Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF151619),
        border = BorderStroke(1.dp, Color(0xFF24262C)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            "OmniRoute Gateway",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E2E6),
                        )
                        Text(
                            text = digital.vmstudio.code.core.common.preferences.UserPreferences.DEFAULT_AI_BASE_URL,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF8E919B),
                        )
                    }
                }

                // Live status badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        )
                        Text(
                            text = "Online",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF34D399),
                        )
                    }
                }
            }

            Text(
                text = if (state.hasStoredKey) {
                    "Using custom API key override stored securely in Android Keystore."
                } else {
                    "Connected via built-in VM Studio Cloud key. Zero setup required for free & coding models."
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFA0A3AD),
                lineHeight = 18.sp,
            )

            ApiKeyField(
                key = keyDraft,
                onKeyChange = onKeyDraft,
                hasStoredKey = state.hasStoredKey,
                onSave = {
                    viewModel.runConnectionTest(
                        pendingKey = keyDraft,
                        onKeySaved = onKeyConsumed,
                    )
                },
                onClear = viewModel::clearApiKey,
            )
        }
    }

    // 2. Connectivity & Model Sync Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF151619),
        border = BorderStroke(1.dp, Color(0xFF24262C)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Gateway Connectivity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E2E6),
                )
                state.health?.let { health ->
                    if (health.isAvailable) {
                        Text(
                            text = "${health.availableModels.size} models synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF34D399),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Sync button
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E2026),
                border = BorderStroke(1.dp, Color(0xFF2C2F38)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !state.isTesting) {
                        viewModel.runConnectionTest(pendingKey = keyDraft, onKeySaved = onKeyConsumed)
                    },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF818CF8))
                        Spacer(Modifier.width(8.dp))
                        Text("Testing & syncing models...", style = MaterialTheme.typography.labelMedium, color = Color(0xFF818CF8))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test Ping & Sync Model Catalog", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFFE2E2E6))
                    }
                }
            }

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    onRetry = { viewModel.runConnectionTest(pendingKey = keyDraft, onKeySaved = onKeyConsumed) },
                )
            }
        }
    }

    AiModelSection(state = state, viewModel = viewModel)
    AiLimitsSection(state = state, viewModel = viewModel)
}

@Composable
private fun ApiKeyField(
    key: String,
    onKeyChange: (String) -> Unit,
    hasStoredKey: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = key,
        onValueChange = onKeyChange,
        placeholder = {
            Text(
                if (hasStoredKey) "Stored in hardware keystore" else "Enter custom API key (optional)",
                color = Color(0xFF6B7280),
            )
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color(0xFF8E919B),
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF6366F1),
            unfocusedBorderColor = Color(0xFF26282E),
            focusedContainerColor = Color(0xFF1B1C20),
            unfocusedContainerColor = Color(0xFF1B1C20),
            focusedTextColor = Color(0xFFE2E2E6),
            unfocusedTextColor = Color(0xFFE2E2E6),
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (key.isNotBlank()) Color(0xFF6366F1) else Color(0xFF22242B),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = key.isNotBlank(), onClick = onSave),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 9.dp)) {
                Text(
                    "Save Override Key",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (key.isNotBlank()) Color.White else Color(0xFF6B7280),
                )
            }
        }
        if (hasStoredKey) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E2026),
                border = BorderStroke(1.dp, Color(0xFF2C2F38)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClear),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text("Use Built-in Key", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE2E2E6))
                }
            }
        }
    }
}
