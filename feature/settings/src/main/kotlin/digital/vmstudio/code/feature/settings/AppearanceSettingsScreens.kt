package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.preferences.ThemePreference
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlin.math.roundToInt

private data class ColorPreset(val hex: String?, val label: String, val previewColor: Color)

private val ChatBubbleColors = listOf(
    ColorPreset(null, "Charcoal", Color(0xFF1E1F23)),
    ColorPreset("#1E2238", "Midnight Indigo", Color(0xFF1E2238)),
    ColorPreset("#0F291E", "Deep Emerald", Color(0xFF0F291E)),
    ColorPreset("#2A1B40", "Royal Violet", Color(0xFF2A1B40)),
    ColorPreset("#0D2847", "Ocean Navy", Color(0xFF0D2847)),
    ColorPreset("#3B1828", "Crimson Velvet", Color(0xFF3B1828)),
    ColorPreset("#133E3B", "Cyber Teal", Color(0xFF133E3B)),
    ColorPreset("#362208", "Dark Amber", Color(0xFF362208)),
    ColorPreset("#2B1A4A", "Electric Purple", Color(0xFF2B1A4A)),
)

private val ProjectAccentColors = listOf(
    ColorPreset(null, "Auto (By Source)", Color(0xFF6366F1)),
    ColorPreset("#8957E5", "GitHub Violet", Color(0xFF8957E5)),
    ColorPreset("#10B981", "Terminal Emerald", Color(0xFF10B981)),
    ColorPreset("#0284C7", "Electric Azure", Color(0xFF0284C7)),
    ColorPreset("#F59E0B", "Android Amber", Color(0xFFF59E0B)),
    ColorPreset("#06B6D4", "Cyber Cyan", Color(0xFF06B6D4)),
    ColorPreset("#EC4899", "Electric Pink", Color(0xFFEC4899)),
    ColorPreset("#EF4444", "Crimson Red", Color(0xFFEF4444)),
    ColorPreset("#F97316", "Sunset Orange", Color(0xFFF97316)),
)

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
        // App Theme (Light/Dark/System)
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

        // Chat Bubble Color Customization
        item {
            VmCard {
                Text(
                    text = "Chat Bubble Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Choose the tint for your prompt bubbles in agent conversations:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // Color Swatches Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChatBubbleColors.forEach { preset ->
                        val isSelected = (state.preferences.chatBubbleColorHex == preset.hex) ||
                            (state.preferences.chatBubbleColorHex.isNullOrBlank() && preset.hex == null)

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(preset.previewColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF3F424A),
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setChatBubbleColor(preset.hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Live Preview Bubble
                val activeBubbleColor = state.preferences.chatBubbleColorHex?.let { hex ->
                    try {
                        val cleanHex = hex.trim().removePrefix("#")
                        val fullHex = if (cleanHex.length == 6) "FF$cleanHex" else cleanHex
                        Color(fullHex.toLong(16))
                    } catch (_: Exception) {
                        Color(0xFF1E1F23)
                    }
                } ?: Color(0xFF1E1F23)

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = activeBubbleColor,
                        border = BorderStroke(1.dp, activeBubbleColor.copy(alpha = 0.6f)),
                        modifier = Modifier.widthIn(max = 260.dp),
                    ) {
                        Text(
                            text = "Sample user prompt bubble preview",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFFF3F4F6),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        // Project Accent Identity Color Customization
        item {
            VmCard {
                Text(
                    text = "Default Project Color",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Default accent used across project workspaces without a custom color:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                // Color Swatches Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ProjectAccentColors.forEach { preset ->
                        val isSelected = (state.preferences.defaultProjectColorHex == preset.hex) ||
                            (state.preferences.defaultProjectColorHex.isNullOrBlank() && preset.hex == null)

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(preset.previewColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color(0xFF3F424A),
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setDefaultProjectColor(preset.hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Live Preview Project Badge
                val activeProjectColor = state.preferences.defaultProjectColorHex?.let { hex ->
                    try {
                        val cleanHex = hex.trim().removePrefix("#")
                        val fullHex = if (cleanHex.length == 6) "FF$cleanHex" else cleanHex
                        Color(fullHex.toLong(16))
                    } catch (_: Exception) {
                        Color(0xFF6366F1)
                    }
                } ?: Color(0xFF6366F1)

                Surface(
                    color = activeProjectColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, activeProjectColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(activeProjectColor),
                        )
                        Text(
                            text = "Workspace Badge Accent Preview",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = activeProjectColor,
                        )
                    }
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

