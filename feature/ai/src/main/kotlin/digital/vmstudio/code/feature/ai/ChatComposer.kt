package digital.vmstudio.code.feature.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
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
    onOpenConnectors: (() -> Unit)? = null,
    onOpenAntigravity: (() -> Unit)? = null,
    activeAntigravitySessionId: String? = null,
    onClearAntigravitySession: (() -> Unit)? = null,
    onDetach: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    lastUserPrompt: String? = null,
    onRecallLastPrompt: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val spacing = VmTheme.spacing
    val canSend = enabled && (text.isNotBlank() || attachments.isNotEmpty())
    val haptic = LocalHapticFeedback.current
    var showMentionSheet by remember { mutableStateOf(false) }
    var showSwarmGuide by remember { mutableStateOf(false) }
    var showPromptLibrary by remember { mutableStateOf(false) }
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

    val launchSpeech: () -> Unit = {
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
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchSpeech()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice prompts", Toast.LENGTH_SHORT).show()
        }
    }

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

                // Paperclip Autonomous Agent Fleet
                listOf(
                    Pair("@kernel", Icons.Default.Bolt),
                    Pair("@toolchain", Icons.Default.Build),
                    Pair("@auditor", Icons.Default.Source),
                    Pair("@paperclip", Icons.Default.AutoAwesome),
                ).filter { (tag, _) -> query.isBlank() || tag.removePrefix("@").contains(query, ignoreCase = true) }
                    .forEach { (tag, icon) ->
                        MentionChip(
                            label = tag,
                            icon = icon,
                            onClick = {
                                val beforeAt = text.substring(0, text.lastIndexOf('@'))
                                onTextChange("$beforeAt$tag ")
                            },
                        )
                    }

                listOf(
                    Pair("@architect", Icons.Default.Code),
                    Pair("@research", Icons.Default.Search),
                    Pair("@coder", Icons.Default.Code),
                    Pair("@security", Icons.Default.Source),
                    Pair("@tester", Icons.Default.AutoAwesome),
                    Pair("@frontend", Icons.Default.Code),
                    Pair("@backend", Icons.Default.Terminal),
                ).filter { (tag, _) -> query.isNotBlank() && tag.removePrefix("@").contains(query, ignoreCase = true) }
                    .forEach { (tag, icon) ->
                        MentionChip(
                            label = tag,
                            icon = icon,
                            onClick = {
                                val beforeAt = text.substring(0, text.lastIndexOf('@'))
                                onTextChange("$beforeAt$tag ")
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

        var showDevToolsPills by remember { mutableStateOf(false) }

        // Minimal Gemini / Claude / VS Code Copilot top selector chips (Model & Mode)
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

            // Quick Tools Toggle Pill (keeps canvas minimal like VS Code / Antigravity)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (showDevToolsPills) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, if (showDevToolsPills) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDevToolsPills = !showDevToolsPills
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Toggle Quick Actions",
                        tint = if (showDevToolsPills) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = if (showDevToolsPills) "Actions ▴" else "Actions ▾",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (showDevToolsPills) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Developer Agentic Quick Action Chips (Collapsible for minimal cockpit)
        if (showDevToolsPills) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            QuickActionPill(
                label = "💡 Recipes",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFFF59E0B),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showPromptLibrary = true
                },
            )

            QuickActionPill(
                label = "Build APK",
                icon = Icons.Default.Bolt,
                color = Color(0xFF10B981),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (onTriggerCloudBuild != null) {
                        onTriggerCloudBuild()
                    } else {
                        onTextChange("/build x-codes")
                    }
                },
            )

            QuickActionPill(
                label = "Cluster Nodes",
                icon = Icons.Default.Laptop,
                color = Color(0xFF38BDF8),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTextChange("/status cluster")
                },
            )

            QuickActionPill(
                label = "ComfyUI Studio",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFFA855F7),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    try {
                        uriHandler.openUri("http://100.123.215.64:8188")
                    } catch (_: Exception) {
                        onTextChange("http://100.123.215.64:8188")
                    }
                },
            )

            QuickActionPill(
                label = "Paperclip Squad",
                icon = Icons.Default.Code,
                color = Color(0xFF6366F1),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val updated = if (text.isBlank()) "@paperclip " else "$text @paperclip "
                    onTextChange(updated)
                },
            )

            if (onOpenAntigravity != null) {
                QuickActionPill(
                    label = if (activeAntigravitySessionId != null) "Antigravity (${activeAntigravitySessionId.take(6)})" else "Antigravity Brain",
                    icon = Icons.Default.Bolt,
                    color = Color(0xFF818CF8),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenAntigravity()
                    },
                )
            }

            QuickActionPill(
                label = "Git Diff",
                icon = Icons.Default.Source,
                color = Color(0xFFF59E0B),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val updated = if (text.isBlank()) "@git-diff " else "$text @git-diff "
                    onTextChange(updated)
                },
            )

            QuickActionPill(
                label = "Run Tests",
                icon = Icons.Default.Check,
                color = Color(0xFF06B6D4),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val updated = if (text.isBlank()) "/test " else "$text /test "
                    onTextChange(updated)
                },
            )
        }
    }

        // Active Antigravity Session Chip
        if (!activeAntigravitySessionId.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF6366F1).copy(alpha = 0.2f),
                border = BorderStroke(1.dp, Color(0xFF818CF8).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF818CF8), modifier = Modifier.size(14.dp))
                        Text(
                            text = "Connected Antigravity Session: ${activeAntigravitySessionId.take(8)}…",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (onClearAntigravitySession != null) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Detach session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { onClearAntigravitySession() },
                        )
                    }
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
                    onOpenConnectors = onOpenConnectors,
                    onOpenSwarmGuide = { showSwarmGuide = true },
                    onTriggerWebSearch = {
                        val trimmed = text.trim()
                        val updated = if (trimmed.startsWith("@web")) trimmed else "@web $trimmed".trim()
                        onTextChange(updated)
                    },
                    onTriggerPlan = {
                        val trimmed = text.trim()
                        val updated = if (trimmed.startsWith("/plan")) trimmed else "/plan $trimmed".trim()
                        onTextChange(updated)
                    },
                    onTriggerCompact = {
                        onTextChange("/compact")
                    },
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
                            text = if (isRunning) "Queue follow-up task…" else "Ask anything, type / for commands, @ for files…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
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
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    launchSpeech()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    if (showSwarmGuide) {
        SwarmGuideSheet(
            onSelectPrompt = { selectedPrompt ->
                onTextChange(selectedPrompt)
                showSwarmGuide = false
            },
            onDismiss = { showSwarmGuide = false },
        )
    }

    if (showPromptLibrary) {
        PromptLibrarySheet(
            onSelectRecipe = { selectedPrompt ->
                onTextChange(selectedPrompt)
                showPromptLibrary = false
            },
            onDismiss = { showPromptLibrary = false },
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
    onOpenConnectors: (() -> Unit)? = null,
    onOpenSwarmGuide: (() -> Unit)? = null,
    onTriggerWebSearch: (() -> Unit)? = null,
    onTriggerPlan: (() -> Unit)? = null,
    onTriggerCompact: (() -> Unit)? = null,
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
            if (onOpenConnectors != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Connectors")
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "MCP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = "Drive, Notion, WordPress, Slack, Figma",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onOpenConnectors()
                    },
                )
            }
            if (onOpenSwarmGuide != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("Agent Swarm Guide")
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFFB74D).copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        "PRO",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = "Orchestrate parallel specialist agents",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFB74D)) },
                    onClick = {
                        expanded = false
                        onOpenSwarmGuide()
                    },
                )
            }
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
            if (onTriggerWebSearch != null) {
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Web search")
                                Text(
                                    text = "Ground agent in live Google web search",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onTriggerWebSearch()
                    },
                )
            }
            if (onTriggerPlan != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Architectural Plan (/plan)")
                            Text(
                                text = "Draft step-by-step design before code edits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF6366F1)) },
                    onClick = {
                        expanded = false
                        onTriggerPlan()
                    },
                )
            }
            if (onTriggerCompact != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("Compact context (/compact)")
                            Text(
                                text = "Archive old turns, freeing 80% context window",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF10B981)) },
                    onClick = {
                        expanded = false
                        onTriggerCompact()
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            MenuSectionLabel("Permission Mode")
            AgentPermissionMode.entries.forEach { mode ->
                val subtitle = when (mode) {
                    AgentPermissionMode.MANUAL -> "Safe for beginners · Asks before edits & commands"
                    AgentPermissionMode.ACCEPT_EDITS -> "Edits files automatically · Asks before commands"
                    AgentPermissionMode.PLAN -> "Plan-only mode · Proposes plan without touching code"
                    AgentPermissionMode.BYPASS -> "Hands-free autonomy · No approval prompts"
                }
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(mode.chatLabel(), fontWeight = FontWeight.SemiBold)
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = {
                        if (mode == permissionMode) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

private fun attachmentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')

@Composable
private fun QuickActionPill(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                color = color,
            )
        }
    }
}
