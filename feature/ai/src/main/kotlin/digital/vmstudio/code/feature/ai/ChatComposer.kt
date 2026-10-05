package digital.vmstudio.code.feature.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.model.ClaudeCodeModels
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModels
import digital.vmstudio.code.core.ui.theme.VmTheme

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
    omniModels: OmniRouteModels,
    isSyncingModels: Boolean,
    onSyncModels: () -> Unit,
    attachments: List<Uri>,
    onAttach: (Uri) -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    workingDirectory: String,
    onEditDirectory: () -> Unit,
    onSend: (String) -> Unit,
    onSendInBackground: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    var showModeMenu by remember { mutableStateOf(false) }
    val canSend = enabled && text.isNotBlank()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            AttachmentChips(attachments = attachments, onRemove = onRemoveAttachment)
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
                AttachButton(enabled = !isRunning, onAttach = onAttach)
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

                ModelPicker(
                    selectedModel = selectedModel,
                    omniModels = omniModels,
                    isSyncing = isSyncingModels,
                    enabled = !isRunning,
                    onModelChange = onModelChange,
                    onSync = onSyncModels,
                )

                Spacer(Modifier.weight(1f))

                // Run on the server; keeps going with the phone locked or the app closed.
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

/**
 * One picker for every model: Claude Code's aliases first, then whatever the
 * OmniRoute gateway serves, synced live. The chosen model decides the backend.
 */
@Composable
private fun ModelPicker(
    selectedModel: String,
    omniModels: OmniRouteModels,
    isSyncing: Boolean,
    enabled: Boolean,
    onModelChange: (String) -> Unit,
    onSync: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = ClaudeCodeModels.CHOICES.firstOrNull { it.id == selectedModel }?.label ?: selectedModel

    fun pick(id: String) {
        onModelChange(id)
        expanded = false
    }

    Box {
        ComposerChip(label = label, enabled = enabled, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 420.dp),
        ) {
            MenuSectionLabel("Claude Code")
            ClaudeCodeModels.CHOICES.forEach { choice ->
                ModelMenuItem(
                    title = choice.label,
                    subtitle = choice.description,
                    selected = choice.id == selectedModel,
                    onClick = { pick(choice.id) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuSectionLabel(omniSectionTitle(omniModels, isSyncing))
            omniModels.ids.forEach { id ->
                ModelMenuItem(
                    title = id,
                    subtitle = null,
                    selected = id == selectedModel,
                    onClick = { pick(id) },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (omniModels.isConfigured) SYNC_LABEL else SET_UP_LABEL,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                enabled = omniModels.isConfigured && !isSyncing,
                onClick = onSync,
            )
        }
    }
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun ModelMenuItem(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        },
        onClick = onClick,
    )
}

private fun omniSectionTitle(models: OmniRouteModels, isSyncing: Boolean): String = when {
    isSyncing -> "OmniRoute · syncing…"
    !models.isConfigured -> "OmniRoute · not set up"
    models.syncedAtMillis == 0L -> "OmniRoute · not synced yet"
    else -> "OmniRoute · synced ${relativeAge(models.syncedAtMillis)}"
}

private fun relativeAge(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / MILLIS_PER_MINUTE
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        else -> "${minutes / MINUTES_PER_HOUR} h ago"
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val SYNC_LABEL = "Sync models now"
private const val SET_UP_LABEL = "Set up OmniRoute in Settings"
private const val MINUTES_PER_HOUR = 60L

@Composable
private fun AttachButton(enabled: Boolean, onAttach: (Uri) -> Unit) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach(onAttach)
    }
    IconButton(onClick = { picker.launch("*/*") }, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = "Attach files",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Files picked for the next message, each removable before it is sent. */
@Composable
private fun AttachmentChips(attachments: List<Uri>, onRemove: (Uri) -> Unit) {
    if (attachments.isEmpty()) return
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { uri ->
            val name = remember(uri) { attachmentName(context, uri) }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                IconButton(onClick = { onRemove(uri) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove $name",
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun attachmentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
