package digital.vmstudio.code.feature.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModels
import digital.vmstudio.code.core.ui.theme.VmTheme

private fun AgentPermissionMode.changesThings(): Boolean =
    this == AgentPermissionMode.ACCEPT_EDITS || this == AgentPermissionMode.BYPASS

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
    onSendInBackground: (String, () -> Unit) -> Unit,
    onStop: () -> Unit,
    onOpenPreview: (() -> Unit)? = null,
    onOpenFiles: (() -> Unit)? = null,
    onDetach: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val canSend = enabled && text.isNotBlank()
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF1E1F22),
        border = BorderStroke(1.dp, Color(0xFF2E3036)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            AttachmentChips(attachments = attachments, onRemove = onRemoveAttachment)

            // Quick Slash Actions Bar (/preview, /files, /diff, /terminal, /clear)
            val showQuickActions = text.isBlank() || text.endsWith("/")
            if (showQuickActions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (onOpenPreview != null) {
                        MentionChip(
                            label = "/preview",
                            icon = Icons.Default.Code,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenPreview()
                            },
                        )
                    }
                    if (onOpenFiles != null) {
                        MentionChip(
                            label = "/files",
                            icon = Icons.Default.Folder,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenFiles()
                            },
                        )
                    }
                    MentionChip(
                        label = "/diff",
                        icon = Icons.Default.Source,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val updated = if (text.endsWith("/")) text.dropLast(1) + "@git-diff " else "$text @git-diff ".trimStart()
                            onTextChange(updated)
                        },
                    )
                    MentionChip(
                        label = "/terminal",
                        icon = Icons.Default.Terminal,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val updated = if (text.endsWith("/")) text.dropLast(1) + "@terminal " else "$text @terminal ".trimStart()
                            onTextChange(updated)
                        },
                    )
                    MentionChip(
                        label = "/clear",
                        icon = Icons.Default.Close,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTextChange("")
                        },
                    )
                    if (isRunning && onDetach != null) {
                        MentionChip(
                            label = "/detach",
                            icon = Icons.Default.Schedule,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDetach()
                            },
                        )
                    }
                }
            }

            // Antigravity-style @ Mention Context Helper Pill
            val showMentions = text.endsWith("@")
            if (showMentions) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MentionChip(
                        label = "@file",
                        icon = Icons.Default.Folder,
                        onClick = { onTextChange(text + "file: ") },
                    )
                    MentionChip(
                        label = "@git-diff",
                        icon = Icons.Default.Source,
                        onClick = { onTextChange(text + "git-diff ") },
                    )
                    MentionChip(
                        label = "@terminal",
                        icon = Icons.Default.Terminal,
                        onClick = { onTextChange(text + "terminal ") },
                    )
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                enabled = enabled,
                placeholder = {
                    Text(
                        text = if (isRunning) "Queue follow-up task…" else "Ask anything, @ to mention, / for actions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8E919A),
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
                // Claude-style: one quiet row. Everything used now and then (attach,
                // folder, mode, background) lives behind +; only the model and Send
                // stay visible.
                ComposerPlusMenu(
                    enabled = !isRunning,
                    canSend = canSend && !isStartingBackgroundRun,
                    isStartingBackgroundRun = isStartingBackgroundRun,
                    folderName = workingDirectory.trimEnd('/').substringAfterLast('/')
                        .ifBlank { "Set folder" },
                    permissionMode = permissionMode,
                    onAttach = onAttach,
                    onEditDirectory = onEditDirectory,
                    onPermissionModeChange = onPermissionModeChange,
                    // The box is cleared only once the run really started, so a refusal
                    // ("Python 3 is needed", a production server) never eats the message.
                    onSendInBackground = { onSendInBackground(text) { onTextChange("") } },
                )
                // Only the modes that change things are worth a permanent reminder.
                if (permissionMode.changesThings()) {
                    Text(
                        text = permissionMode.chatLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = VmTheme.colors.warning,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
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

                // Voice-to-Code Microphone Button
                val speechLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == android.app.Activity.RESULT_OK) {
                        val spokenText = result.data
                            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?.firstOrNull()
                        if (!spokenText.isNullOrBlank()) {
                            val updated = if (text.isBlank()) spokenText else "$text $spokenText"
                            onTextChange(updated)
                        }
                    }
                }

                val context = LocalContext.current
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                )
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak instructions for AI…")
                            }
                            speechLauncher.launch(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Speech recognition is not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice prompt",
                        tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else Color(0xFF8E919A),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.size(4.dp))

                if (isRunning && text.isBlank()) {
                    if (onDetach != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2B2D31),
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDetach()
                                }),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Detach to background",
                                    tint = Color(0xFF6C9EFF),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStop()
                            }),
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
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (text.trim().equals("/detach", ignoreCase = true) && onDetach != null) {
                                    onTextChange("")
                                    onDetach()
                                } else {
                                    val toSend = text
                                    onTextChange("")
                                    onSend(toSend)
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = if (isRunning) "Queue task" else "Send",
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
internal fun ComposerChip(
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

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** The + menu: attach, folder, mode and background run, out of the way until needed. */
@Suppress("LongParameterList")
@Composable
private fun ComposerPlusMenu(
    enabled: Boolean,
    canSend: Boolean,
    isStartingBackgroundRun: Boolean,
    folderName: String,
    permissionMode: AgentPermissionMode,
    onAttach: (Uri) -> Unit,
    onEditDirectory: () -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onSendInBackground: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach(onAttach)
    }

    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(36.dp)) {
            if (isStartingBackgroundRun) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Attach files") },
                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null) },
                onClick = {
                    expanded = false
                    picker.launch("*/*")
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Folder")
                        Text(
                            text = folderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEditDirectory()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Send in background")
                        Text(
                            text = "Keeps running with the phone locked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                enabled = canSend,
                onClick = {
                    expanded = false
                    onSendInBackground()
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuSectionLabel("Mode")
            AgentPermissionMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.chatLabel()) },
                    leadingIcon = {
                        if (mode == permissionMode) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onPermissionModeChange(mode)
                    },
                )
            }
        }
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

@Composable
private fun MentionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2B2D31),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFE2E2E5),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}


private fun attachmentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
