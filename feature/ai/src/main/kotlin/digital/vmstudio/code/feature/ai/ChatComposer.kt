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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModels
import digital.vmstudio.code.core.git.GitHubRepository
import digital.vmstudio.code.core.project.Project
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
    squadPreset: digital.vmstudio.code.core.ai.model.AgentSquadPreset = digital.vmstudio.code.core.ai.model.AgentSquadPreset.FULL_STACK,
    onSquadPresetChange: (digital.vmstudio.code.core.ai.model.AgentSquadPreset) -> Unit = {},
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
    availableProjects: List<Project> = emptyList(),
    githubRepositories: List<GitHubRepository> = emptyList(),
    onSend: (String) -> Unit,
    onSendInBackground: (String, () -> Unit) -> Unit,
    onStop: () -> Unit,
    onOpenPreview: (() -> Unit)? = null,
    onOpenFiles: (() -> Unit)? = null,
    onTriggerCloudBuild: (() -> Unit)? = null,
    onDetach: (() -> Unit)? = null,
    totalTokens: Long = 0L,
    focusRequester: FocusRequester? = null,
    lastUserPrompt: String? = null,
    onRecallLastPrompt: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty())
    val haptic = LocalHapticFeedback.current
    var showMentionSheet by remember { mutableStateOf(false) }
    var lastClearedText by remember { mutableStateOf<String?>(null) }

    // Detect if user is actively typing a mention (e.g. "@", "@web", @"My Proj")
    val activeMentionQuery = remember(text) {
        val lastAt = text.lastIndexOf('@')
        if (lastAt >= 0) {
            val afterAt = text.substring(lastAt + 1)
            if (!afterAt.contains(' ') && !afterAt.contains('\n') && !afterAt.contains('"')) {
                afterAt
            } else null
        } else null
    }

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md, vertical = 2.dp),
    ) {
        // Attachments
        AttachmentChips(attachments = attachments, onRemove = onRemoveAttachment)

        // Quick Slash Actions Bar (/preview, /files, /diff, /terminal, /clear) - only when user types /
        val showQuickActions = text.startsWith("/") || text.endsWith("/")
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
                if (onTriggerCloudBuild != null) {
                    MentionChip(
                        label = "/build",
                        icon = Icons.Default.Build,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTriggerCloudBuild()
                        },
                    )
                }
                MentionChip(
                    label = "/test",
                    icon = Icons.Default.Check,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val prefix = if (text.endsWith("/")) text.dropLast(1) else text
                        val updated = if (prefix.isBlank()) "Run all unit tests and report any failures with suggested fixes." else "$prefix Run unit tests and report failures."
                        onTextChange(updated.trimStart())
                    },
                )
                MentionChip(
                    label = "/fix",
                    icon = Icons.Default.Build,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val prefix = if (text.endsWith("/")) text.dropLast(1) else text
                        val updated = if (prefix.isBlank()) "Analyze recent errors or warnings and apply the minimal correct fix." else "$prefix Fix errors and apply minimal fix."
                        onTextChange(updated.trimStart())
                    },
                )
                MentionChip(
                    label = "/review",
                    icon = Icons.Default.Source,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val prefix = if (text.endsWith("/")) text.dropLast(1) else text
                        val updated = if (prefix.isBlank()) "Review uncommitted git changes for bugs, security vulnerabilities, and code quality." else "$prefix Review git changes."
                        onTextChange(updated.trimStart())
                    },
                )
                MentionChip(
                    label = "/explain",
                    icon = Icons.Default.AutoAwesome,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val prefix = if (text.endsWith("/")) text.dropLast(1) else text
                        val updated = if (prefix.isBlank()) "Explain how this architecture and data flow works step-by-step." else "$prefix Explain this code."
                        onTextChange(updated.trimStart())
                    },
                )
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

        // Dynamic @ Mention Context Helper Bar
        val showMentions = activeMentionQuery != null
        if (showMentions) {
            val query = activeMentionQuery.orEmpty()
            val matchingProjects = remember(availableProjects, query) {
                if (query.isBlank()) availableProjects.take(6)
                else availableProjects.filter { it.name.contains(query, ignoreCase = true) }.take(6)
            }
            val matchingRepos = remember(githubRepositories, query) {
                if (query.isBlank()) githubRepositories.take(6)
                else githubRepositories.filter { it.fullName.contains(query, ignoreCase = true) }.take(6)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                matchingProjects.forEach { project ->
                    val insertText = if (project.name.contains(" ")) "@\"${project.name}\" " else "@${project.name} "
                    MentionChip(
                        label = "@${project.name}",
                        icon = Icons.Default.Folder,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt$insertText")
                        },
                    )
                }

                matchingRepos.forEach { repo ->
                    val repoShortName = repo.fullName.substringAfterLast('/')
                    MentionChip(
                        label = "@$repoShortName",
                        icon = Icons.Default.Code,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt@${repo.fullName} ")
                        },
                    )
                }

                if (query.isBlank() || "swarm".contains(query, ignoreCase = true)) {
                    MentionChip(
                        label = "@swarm",
                        icon = Icons.Default.AutoAwesome,
                        onClick = {
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt@swarm ")
                        },
                    )
                }

                if (query.isBlank() || "git-diff".contains(query, ignoreCase = true)) {
                    MentionChip(
                        label = "@git-diff",
                        icon = Icons.Default.Source,
                        onClick = {
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt@git-diff ")
                        },
                    )
                }
                if (query.isBlank() || "file".contains(query, ignoreCase = true)) {
                    MentionChip(
                        label = "@file",
                        icon = Icons.Default.Folder,
                        onClick = {
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt@file: ")
                        },
                    )
                }
                if (query.isBlank() || "terminal".contains(query, ignoreCase = true)) {
                    MentionChip(
                        label = "@terminal",
                        icon = Icons.Default.Terminal,
                        onClick = {
                            val beforeAt = text.substring(0, text.lastIndexOf('@'))
                            onTextChange("$beforeAt@terminal ")
                        },
                    )
                }

                MentionChip(
                    label = "⋯ All",
                    icon = Icons.Default.Add,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMentionSheet = true
                    },
                )
            }
        }

        // Undo clear / Restore prompt banner
        if (!lastClearedText.isNullOrBlank() && text.isBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cleared draft: \"${lastClearedText?.take(22)}…\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                TextButton(
                    onClick = {
                        lastClearedText?.let { onTextChange(it) }
                        lastClearedText = null
                    },
                    modifier = Modifier.height(28.dp),
                ) {
                    Text("Restore & Edit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Minimal Gemini / Claude-style top selector chips (Model & Mode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ModelPicker(
                selectedModel = selectedModel,
                omniModels = omniModels,
                isSyncing = isSyncingModels,
                enabled = !isRunning,
                onModelChange = onModelChange,
                onSync = onSyncModels,
            )
            if (permissionMode.changesThings()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VmTheme.colors.warning.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "⚡ ${permissionMode.chatLabel()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = VmTheme.colors.warning,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            if (squadPreset != digital.vmstudio.code.core.ai.model.AgentSquadPreset.FULL_STACK) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "👥 ${squadPreset.title}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        // Sleek Capsule Input Pill (Gemini / Claude style)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // + Menu Button
                ComposerPlusMenu(
                    enabled = !isRunning,
                    canSend = canSend && !isStartingBackgroundRun,
                    isStartingBackgroundRun = isStartingBackgroundRun,
                    folderName = workingDirectory.trimEnd('/').substringAfterLast('/')
                        .ifBlank { "Set folder" },
                    permissionMode = permissionMode,
                    squadPreset = squadPreset,
                    onAttach = onAttach,
                    onEditDirectory = onEditDirectory,
                    onPermissionModeChange = onPermissionModeChange,
                    onSquadPresetChange = onSquadPresetChange,
                    onSendInBackground = { onSendInBackground(text) { onTextChange("") } },
                )

                // Input Field
                TextField(
                    value = text,
                    onValueChange = {
                        if (it.isNotBlank()) lastClearedText = null
                        onTextChange(it)
                    },
                    enabled = enabled,
                    placeholder = {
                        Text(
                            text = if (isRunning) "Queue follow-up task…" else "Ask anything…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
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
                    modifier = Modifier
                        .weight(1f)
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )

                // Trailing Buttons: Clear, Recall, @, Voice Mic, Detach, Stop, Send
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            lastClearedText = text
                            onTextChange("")
                        },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.size(4.dp))
                }

                if (isRunning && text.isBlank()) {
                    if (onDetach != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .size(34.dp)
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(34.dp)
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
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                } else if (text.isBlank() && attachments.isEmpty()) {
                    // Recall last prompt edit button if available
                    if (lastUserPrompt != null && onRecallLastPrompt != null) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRecallLastPrompt()
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit last prompt",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Dedicated @ Mention Context Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMentionSheet = true
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "@",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(4.dp))

                    // Gemini-style Voice Microphone Button
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
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
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice prompt",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                } else {
                    // Accent circular Send button
                    Surface(
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(enabled = canSend) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (text.trim().equals("/detach", ignoreCase = true) && onDetach != null) {
                                    onTextChange("")
                                    onDetach()
                                } else {
                                    val toSend = text.ifBlank {
                                        if (attachments.isNotEmpty()) "Please inspect the attached file(s)." else ""
                                    }
                                    if (toSend.isNotBlank()) {
                                        onTextChange("")
                                        onSend(toSend)
                                    }
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = if (isRunning) "Queue task" else "Send",
                                tint = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMentionSheet) {
        ProjectMentionSheet(
            availableProjects = availableProjects,
            githubRepositories = githubRepositories,
            onSelectMention = { token ->
                val formatted = if (token.contains(" ")) "@\"$token\" " else "@$token "
                val updated = if (text.isBlank()) {
                    formatted
                } else if (text.endsWith("@")) {
                    text.dropLast(1) + formatted
                } else if (text.endsWith(" ")) {
                    text + formatted
                } else {
                    "$text $formatted"
                }
                onTextChange(updated)
                showMentionSheet = false
            },
            onDismiss = { showMentionSheet = false },
        )
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
    squadPreset: digital.vmstudio.code.core.ai.model.AgentSquadPreset = digital.vmstudio.code.core.ai.model.AgentSquadPreset.FULL_STACK,
    onAttach: (Uri) -> Unit,
    onEditDirectory: () -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
    onSquadPresetChange: (digital.vmstudio.code.core.ai.model.AgentSquadPreset) -> Unit = {},
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuSectionLabel("Squad Role")
            digital.vmstudio.code.core.ai.model.AgentSquadPreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.title) },
                    leadingIcon = {
                        if (preset == squadPreset) Icon(Icons.Default.Check, contentDescription = null)
                    },
                    onClick = {
                        expanded = false
                        onSquadPresetChange(preset)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}


@Composable
private fun DevAccessoryBar(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val symbols = listOf("Tab", "|", "~", "/", "$", "&&", ";", "-", "_", ":", "\"", "'", "{", "}", "[", "]")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbols.forEach { sym ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val insertValue = if (sym == "Tab") "    " else sym
                        onInsert(insertValue)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = sym,
                    style = VmTheme.code.mono.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun attachmentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
