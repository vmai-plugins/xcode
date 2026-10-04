package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ui.theme.VmTheme

private val MODEL_CHOICES = listOf(
    Triple("sonnet", "Sonnet", "Everyday coding"),
    Triple("opus", "Opus", "Hardest problems"),
    Triple("haiku", "Haiku", "Fast and light"),
)

private fun AgentPermissionMode.chatLabel(): String = when (this) {
    AgentPermissionMode.PLAN -> "Plan only"
    AgentPermissionMode.ACCEPT_EDITS -> "Auto-edit"
    AgentPermissionMode.MANUAL -> "Ask first"
    AgentPermissionMode.BYPASS -> "Full auto"
}

/** Flat, rounded input like Claude's: text on top, small pickers and send below. */
@Composable
internal fun ModernComposer(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    isRunning: Boolean,
    isResuming: Boolean,
    isStartingBackgroundRun: Boolean,
    permissionMode: AgentPermissionMode,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    selectedModel: String,
    onModelChange: (String) -> Unit,
    workingDirectory: String,
    onEditDirectory: () -> Unit,
    onSend: (String) -> Unit,
    onSendInBackground: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    var showModeMenu by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }
    val canSend = enabled && text.isNotBlank()
    val modelLabel = MODEL_CHOICES.firstOrNull { it.first == selectedModel }?.second ?: "Sonnet"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = !isRunning,
                placeholder = {
                    Text(
                        text = if (isResuming) "Reply…" else "Message Claude Code",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Folder chip: the working directory, tap to change.
                ComposerChip(
                    label = workingDirectory.trimEnd('/').substringAfterLast('/')
                        .ifBlank { "Set folder" },
                    icon = Icons.Default.Folder,
                    enabled = !isRunning,
                    onClick = onEditDirectory,
                )

                Box {
                    ComposerChip(
                        label = permissionMode.chatLabel(),
                        enabled = !isRunning,
                        onClick = { showModeMenu = true },
                    )
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        AgentPermissionMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.chatLabel()) },
                                leadingIcon = {
                                    if (mode == permissionMode) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    onPermissionModeChange(mode)
                                    showModeMenu = false
                                },
                            )
                        }
                    }
                }

                Box {
                    ComposerChip(
                        label = modelLabel,
                        enabled = !isRunning,
                        onClick = { showModelMenu = true },
                    )
                    DropdownMenu(expanded = showModelMenu, onDismissRequest = { showModelMenu = false }) {
                        MODEL_CHOICES.forEach { (id, name, desc) ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(name)
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                leadingIcon = {
                                    if (id == selectedModel) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    onModelChange(id)
                                    showModelMenu = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Run detached on the server; survives closing the app.
                IconButton(
                    onClick = {
                        val toSend = text
                        onTextChange("")
                        onSendInBackground(toSend)
                    },
                    enabled = canSend && !isStartingBackgroundRun,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (isStartingBackgroundRun) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Run in background",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (isRunning) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onStop),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) {
                                val toSend = text
                                onTextChange("")
                                onSend(toSend)
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (canSend) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
    }
}
