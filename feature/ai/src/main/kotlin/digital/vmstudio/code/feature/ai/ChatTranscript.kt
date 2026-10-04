package digital.vmstudio.code.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.markdown.MarkdownText
import digital.vmstudio.code.core.ui.theme.VmTheme

internal sealed interface TranscriptRowItem {
    val id: String
    data class Single(val item: TranscriptItem) : TranscriptRowItem {
        override val id: String get() = item.id
    }
    data class ToolBatch(
        override val id: String,
        val tools: List<TranscriptItem.ToolCall>,
    ) : TranscriptRowItem
}

internal fun groupTranscript(items: List<TranscriptItem>): List<TranscriptRowItem> {
    val result = mutableListOf<TranscriptRowItem>()
    val currentTools = mutableListOf<TranscriptItem.ToolCall>()

    fun flushTools() {
        if (currentTools.isEmpty()) return
        if (currentTools.size == 1) {
            result.add(TranscriptRowItem.Single(currentTools.first()))
        } else {
            result.add(
                TranscriptRowItem.ToolBatch(
                    id = "batch-${currentTools.first().id}-${currentTools.size}",
                    tools = currentTools.toList(),
                ),
            )
        }
        currentTools.clear()
    }

    for (item in items) {
        if (item is TranscriptItem.ToolCall) {
            currentTools.add(item)
        } else {
            flushTools()
            result.add(TranscriptRowItem.Single(item))
        }
    }
    flushTools()
    return result
}

/**
 * Consecutive tool calls collapse into one quiet line ("Ran 5 tools ›") that expands
 * to the individual calls, so a long agent run does not bury the conversation.
 */
@Composable
internal fun ToolBatchRow(
    batch: TranscriptRowItem.ToolBatch,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val isRunning = batch.tools.any { it.isRunning }
    val failed = batch.tools.count { it.isError }
    val label = when {
        isRunning -> "Running ${batch.tools.size} tools…"
        failed > 0 -> "Ran ${batch.tools.size} tools, $failed failed"
        else -> "Ran ${batch.tools.size} tools"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (failed > 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                batch.tools.forEach { tool ->
                    ToolRow(item = tool, onOpenDiff = onOpenDiff, onRollback = onRollback)
                }
            }
        }
    }
}

/**
 * Message Row in the conversation transcript.
 */
@Composable
internal fun TranscriptRow(
    item: TranscriptItem,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    val spacing = VmTheme.spacing

    when (item) {
        is TranscriptItem.UserPrompt -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        // The reply is plain text on the page, not a bubble.
        is TranscriptItem.AssistantText -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        ) {
            MarkdownText(markdown = item.text)
        }

        is TranscriptItem.StreamingText -> Text(
            text = item.text + "▌",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )

        is TranscriptItem.Reasoning -> CollapsibleReasoning(item)

        is TranscriptItem.ToolCall -> ToolRow(item, onOpenDiff = onOpenDiff, onRollback = onRollback)

        is TranscriptItem.Diagnostic -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = VmTheme.colors.codeSurface,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.text,
                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                color = if (item.isStderr) VmTheme.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(spacing.sm),
            )
        }

        is TranscriptItem.RunSummary -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.md, vertical = 6.dp),
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
        }

        is TranscriptItem.Failure -> VmErrorPanel(error = item.error)
    }
}

/** "Thinking" is a quiet label that expands to the reasoning text. */
@Composable
private fun CollapsibleReasoning(item: TranscriptItem.Reasoning) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Thinking",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide" else "Show",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, top = 2.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * Terminal & tool execution card.
 */
@Composable
private fun ToolRow(
    item: TranscriptItem.ToolCall,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val spacing = VmTheme.spacing
    val hasOutput = !item.output.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (hasOutput) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
                        imageVector = if (item.name.contains("bash", ignoreCase = true)) {
                            Icons.Default.Terminal
                        } else {
                            Icons.Default.Build
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = item.summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val affectedPath = item.affectedPath
                if (affectedPath != null) {
                    if (item.isReverted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "Reverted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    } else if (onRollback != null) {
                        IconButton(
                            onClick = { onRollback(item.id, affectedPath) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Rollback changes",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    IconButton(
                        onClick = { onOpenDiff(affectedPath) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Difference,
                            contentDescription = "Review changes",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded && hasOutput) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = VmTheme.colors.codeSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    val rawOutput = item.output.orEmpty()
                    val outputLines = rawOutput.lines()
                    val isDiff = outputLines.any { it.startsWith("+") || it.startsWith("-") }
                    if (isDiff) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            outputLines.take(300).forEach { line ->
                                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                                val (bg, textColor) = when {
                                    line.startsWith("+++") || line.startsWith("---") -> Color.Transparent to dim
                                    line.startsWith("+") -> VmTheme.colors.diffAddedBackground to VmTheme.colors.diffAddedGutter
                                    line.startsWith("-") -> VmTheme.colors.diffRemovedBackground to VmTheme.colors.diffRemovedGutter
                                    line.startsWith("@@") -> Color.Transparent to dim
                                    else -> Color.Transparent to MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text = line,
                                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                                    color = textColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bg)
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = rawOutput,
                            style = VmTheme.code.mono.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
