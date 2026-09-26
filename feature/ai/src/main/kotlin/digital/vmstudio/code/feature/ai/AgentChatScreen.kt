package digital.vmstudio.code.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ai.background.BackgroundRun
import digital.vmstudio.code.core.ai.model.AgentPermissionMode
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.component.VmStatusBadge
import digital.vmstudio.code.core.ui.markdown.MarkdownText
import digital.vmstudio.code.core.ui.theme.VmTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Invoked when a detached run starts, so the host can begin watching it. The
     * watcher is a service in the app module, which a feature must not reach into.
     */
    onWatchBackgroundRuns: (serverId: String) -> Unit = {},
    onOpenDiff: (filePath: String) -> Unit = {},
    viewModel: AgentChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    val listState = rememberLazyListState()

    state.watchServerId?.let { server ->
        LaunchedEffect(server) {
            onWatchBackgroundRuns(server)
            viewModel.onWatchStarted()
        }
    }

    // Follow the tail as events arrive; a transcript that stays at the top while the
    // agent works reads as nothing happening.
    LaunchedEffect(state.transcript.size) {
        if (state.transcript.isNotEmpty()) {
            listState.animateScrollToItem(state.transcript.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI Agent") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.transcript.isNotEmpty()) {
                        IconButton(onClick = viewModel::startNewConversation) {
                            Icon(Icons.Default.Add, contentDescription = "New conversation")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            HealthBanner(state = state, onRetry = viewModel::checkHealth)

            RunContextBar(
                state = state,
                onDirectoryChange = viewModel::setWorkingDirectory,
                onPermissionModeChange = viewModel::setPermissionMode,
            )

            state.error?.let { error ->
                VmErrorPanel(
                    error = error,
                    modifier = Modifier.padding(spacing.md),
                    onRetry = viewModel::checkHealth,
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(state.transcript, key = { it.id }) { item ->
                    TranscriptRow(item, onOpenDiff = onOpenDiff)
                }
            }

            BackgroundRunsSection(
                runs = state.backgroundRuns,
                onOpenLogs = viewModel::backgroundLogs,
                onStop = viewModel::stopBackgroundRun,
                onRefresh = viewModel::refreshBackgroundRuns,
            )

            Composer(
                enabled = state.canRun,
                isRunning = state.isRunning,
                isResuming = state.isResuming,
                isStartingBackgroundRun = state.isStartingBackgroundRun,
                onSend = viewModel::send,
                onSendInBackground = viewModel::sendInBackground,
                onStop = viewModel::stop,
            )
        }
    }
}

@Composable
private fun HealthBanner(state: AgentChatUiState, onRetry: () -> Unit) {
    val health = state.health
    val spacing = VmTheme.spacing

    // Health is reported before anything else because every failure mode below
    // presents identically at the prompt: an agent that appears to do nothing.
    if (state.isCheckingHealth) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Text(
                text = "Checking Claude Code on the server",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (health == null) return

    val ready = health.isAvailable && health.isAuthenticated
    if (ready) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VmStatusBadge(status = VmStatus.CONNECTED, label = "Claude Code ready")
            health.version?.let { VmChip(text = it, monospace = true) }
            health.latencyMillis?.let { VmChip(text = "${it} ms", monospace = true) }
        }
        return
    }

    VmCard(modifier = Modifier.padding(spacing.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = VmTheme.colors.warning,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (!health.isAvailable) "Claude Code not found" else "Not signed in",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        health.diagnosis?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        VmButton(
            text = "Check again",
            onClick = onRetry,
            style = VmButtonStyle.Secondary,
            modifier = Modifier.padding(top = spacing.sm),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunContextBar(
    state: AgentChatUiState,
    onDirectoryChange: (String) -> Unit,
    onPermissionModeChange: (AgentPermissionMode) -> Unit,
) {
    val spacing = VmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        OutlinedTextField(
            value = state.workingDirectory,
            onValueChange = onDirectoryChange,
            label = { Text("Project directory on the server") },
            singleLine = true,
            enabled = !state.isRunning,
            textStyle = VmTheme.code.mono,
            supportingText = {
                Text(
                    // Scope is the one setting with real consequences here, so it is
                    // stated rather than left implied.
                    text = "The agent reads and writes inside this directory.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            AgentPermissionMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.permissionMode == mode,
                    onClick = { onPermissionModeChange(mode) },
                    enabled = !state.isRunning,
                    label = { Text(mode.label()) },
                )
            }
        }

        if (state.isResuming) {
            Text(
                text = "Continuing the same conversation on the server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TranscriptRow(item: TranscriptItem, onOpenDiff: (String) -> Unit) {
    val spacing = VmTheme.spacing

    when (item) {
        is TranscriptItem.UserPrompt -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(spacing.md),
            )
        }

        is TranscriptItem.AssistantText -> Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = VmTheme.colors.agent,
                modifier = Modifier.size(16.dp).padding(top = 3.dp),
            )
            // Rendered as markdown: the agent writes code blocks and lists, and as
            // flat text a shell command it produced had to be retyped by hand.
            MarkdownText(markdown = item.text)
        }

        is TranscriptItem.StreamingText -> Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = VmTheme.colors.agent,
                modifier = Modifier.size(16.dp).padding(top = 3.dp),
            )
            // Plain text while streaming: re-parsing markdown on every token would
            // flicker as a half-typed code fence opens and closes.
            Text(
                text = item.text + "▌",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is TranscriptItem.Reasoning -> CollapsibleReasoning(item)

        is TranscriptItem.ToolCall -> ToolRow(item, onOpenDiff = onOpenDiff)

        is TranscriptItem.Diagnostic -> Text(
            text = item.text,
            style = VmTheme.code.mono,
            color = if (item.isStderr) VmTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(VmTheme.colors.codeSurface)
                .padding(spacing.sm),
        )

        is TranscriptItem.RunSummary -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (item.isError) MaterialTheme.colorScheme.error else VmTheme.colors.success,
                modifier = Modifier.size(14.dp),
            )
            VmChip(text = "${item.durationMillis / 1000}s", monospace = true)
            VmChip(text = "${item.inputTokens + item.outputTokens} tokens", monospace = true)
            if (item.costUsd > 0) {
                VmChip(text = "$%.4f".format(item.costUsd), monospace = true)
            }
        }

        is TranscriptItem.Failure -> VmErrorPanel(error = item.error)
    }
}

@Composable
private fun CollapsibleReasoning(item: TranscriptItem.Reasoning) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = VmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(VmTheme.colors.agentContainer)
            .clickable { expanded = !expanded }
            .padding(spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = null,
                tint = VmTheme.colors.agent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (expanded) "Reasoning" else "Reasoning (tap to expand)",
                style = MaterialTheme.typography.labelMedium,
                color = VmTheme.colors.onAgentContainer,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = VmTheme.colors.onAgentContainer,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

@Composable
private fun ToolRow(
    item: TranscriptItem.ToolCall,
    onOpenDiff: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = VmTheme.spacing
    val hasOutput = !item.output.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (hasOutput) Modifier.clickable { expanded = !expanded } else Modifier)
            .padding(spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                item.isRunning -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                item.isError -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Failed",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
                else -> Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = item.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val affectedPath = item.affectedPath
            if (affectedPath != null) {
                IconButton(
                    onClick = { onOpenDiff(affectedPath) },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Difference,
                        contentDescription = "Review changes",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded && hasOutput) {
            Text(
                text = item.output.orEmpty(),
                style = VmTheme.code.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
    }
}

@Composable
private fun Composer(
    enabled: Boolean,
    isRunning: Boolean,
    isResuming: Boolean,
    isStartingBackgroundRun: Boolean,
    onSend: (String) -> Unit,
    onSendInBackground: (String) -> Unit,
    onStop: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val spacing = VmTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            enabled = !isRunning,
            placeholder = {
                Text(if (isResuming) "Continue the conversation" else "What should the agent do?")
            },
            maxLines = 5,
        )

        if (isRunning) {
            IconButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop the agent",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            // Detached send sits beside the normal one rather than behind a menu:
            // on a phone it is the option that survives the app being closed, so it
            // is at least as important as watching the run live.
            IconButton(
                onClick = {
                    onSendInBackground(text)
                    text = ""
                },
                enabled = enabled && text.isNotBlank() && !isStartingBackgroundRun,
            ) {
                if (isStartingBackgroundRun) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Run in the background",
                    )
                }
            }

            IconButton(
                onClick = {
                    onSend(text)
                    text = ""
                },
                enabled = enabled && text.isNotBlank(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

/**
 * Detached runs for this project.
 *
 * Shown above the composer because their whole purpose is to be visible after
 * returning to the app — a run the user started yesterday should be the first thing
 * they see, not something buried behind a refresh.
 */
@Composable
private fun BackgroundRunsSection(
    runs: List<BackgroundRun>,
    onOpenLogs: (String) -> Unit,
    onStop: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    if (runs.isEmpty()) return
    val spacing = VmTheme.spacing

    VmCard(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Running in the background", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh background runs",
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        runs.forEach { run ->
            val id = run.id ?: return@forEach
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
            ) {
                Text(
                    text = run.name ?: id,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Scrolls rather than squeezing: the id and status chips have no
                    // fixed budget, and a long status ("Needs attention") must never
                    // fight the Output/Stop controls for space on a narrow screen.
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        VmChip(text = id, monospace = true)
                        // A blocked run looks identical to a slow one unless it is
                        // named; an expired login on the server produces exactly this.
                        if (run.isBlocked) {
                            VmChip(text = "Needs attention")
                        } else {
                            run.status?.let { VmChip(text = it) }
                        }
                    }

                    VmButton(
                        text = "Output",
                        onClick = { onOpenLogs(id) },
                        style = VmButtonStyle.Tertiary,
                    )
                    IconButton(onClick = { onStop(id) }) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop this run",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun AgentPermissionMode.label(): String = when (this) {
    AgentPermissionMode.PLAN -> "Plan only"
    AgentPermissionMode.ACCEPT_EDITS -> "Auto-edit"
    AgentPermissionMode.MANUAL -> "Ask first"
    AgentPermissionMode.BYPASS -> "No prompts"
}
