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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import digital.vmstudio.code.core.ui.component.VmChip
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.markdown.MarkdownText
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.delay

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
    if (items.isEmpty()) return emptyList()
    val result = mutableListOf<TranscriptRowItem>()
    val toolBuffer = mutableListOf<TranscriptItem.ToolCall>()

    fun flushToolBuffer() {
        if (toolBuffer.isEmpty()) return
        if (toolBuffer.size == 1) {
            result += TranscriptRowItem.Single(toolBuffer.first())
        } else {
            result += TranscriptRowItem.ToolBatch(
                id = "batch-${toolBuffer.first().id}",
                tools = toolBuffer.toList(),
            )
        }
        toolBuffer.clear()
    }

    for (item in items) {
        if (item is TranscriptItem.ToolCall) {
            toolBuffer += item
        } else {
            flushToolBuffer()
            result += TranscriptRowItem.Single(item)
        }
    }
    flushToolBuffer()
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
    onPreview: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val isRunning = batch.tools.any { it.isRunning }
    val failed = batch.tools.count { it.isError }
    val readCount = batch.tools.count { it.name.contains("read", ignoreCase = true) || it.name.contains("list", ignoreCase = true) }
    val editCount = batch.tools.count { it.name.contains("edit", ignoreCase = true) || it.name.contains("write", ignoreCase = true) }
    val cmdCount = batch.tools.count { it.name.contains("command", ignoreCase = true) || it.name.contains("bash", ignoreCase = true) }

    val summary = buildString {
        if (isRunning) {
            append("Executing ${batch.tools.size} steps…")
        } else {
            val parts = mutableListOf<String>()
            if (readCount > 0) parts.add("read $readCount file${if (readCount > 1) "s" else ""}")
            if (editCount > 0) parts.add("edited $editCount file${if (editCount > 1) "s" else ""}")
            if (cmdCount > 0) parts.add("ran $cmdCount command${if (cmdCount > 1) "s" else ""}")
            if (parts.isNotEmpty()) {
                append("Explored: " + parts.joinToString(", "))
            } else {
                append("Ran ${batch.tools.size} tools")
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1F22),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    expanded = !expanded
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2B2D31))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${batch.tools.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFC4C6D0),
                        )
                    }
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (failed > 0) MaterialTheme.colorScheme.error else Color(0xFFC4C6D0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide" else "Show",
                    tint = Color(0xFF8E919A),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                batch.tools.forEach { tool ->
                    ToolRow(
                        item = tool,
                        onOpenDiff = onOpenDiff,
                        onRollback = onRollback,
                        onPreview = onPreview,
                    )
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
    onPreview: ((String) -> Unit)? = null,
    chatBubbleColorHex: String? = null,
) {
    val spacing = VmTheme.spacing

    when (item) {
        is TranscriptItem.UserPrompt -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val bubbleColor = remember(chatBubbleColorHex) {
                if (!chatBubbleColorHex.isNullOrBlank()) {
                    try {
                        val clean = chatBubbleColorHex.trim().removePrefix("#")
                        val full = if (clean.length == 6) "FF$clean" else clean
                        Color(full.toLong(16))
                    } catch (_: Exception) {
                        Color(0xFF1E1F23)
                    }
                } else {
                    Color(0xFF1E1F23)
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = bubbleColor,
                border = BorderStroke(1.dp, bubbleColor.copy(alpha = 0.6f)),
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = Color(0xFFF3F4F6),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // The reply is plain text on the page, not a bubble.
        is TranscriptItem.AssistantText -> AssistantReply(item.text)

        // Formatted while it streams, not only once finished: raw `**`, `##` and
        // table pipes showed for the whole length of a long reply.
        is TranscriptItem.StreamingText -> MarkdownText(
            markdown = item.text + "▌",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            selectable = false,
        )

        is TranscriptItem.Reasoning -> CollapsibleReasoning(item)

        is TranscriptItem.ToolCall ->
            ToolRow(item, onOpenDiff = onOpenDiff, onRollback = onRollback, onPreview = onPreview)

        is TranscriptItem.Diagnostic -> Surface(
            shape = RoundedCornerShape(8.dp),
            color = VmTheme.colors.codeSurface,
            border = BorderStroke(1.dp, VmTheme.colors.divider),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.text,
                style = VmTheme.code.mono.copy(fontSize = 11.sp),
                color = if (item.isStderr) {
                    VmTheme.colors.warning
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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

/** Antigravity-style compact reasoning line. */
@Composable
private fun CollapsibleReasoning(item: TranscriptItem.Reasoning) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 3.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Thought",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFF9CA3AF),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Hide" else "Show",
                tint = Color(0xFF6B7280),
                modifier = Modifier.size(14.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF141416),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                    color = Color(0xFFD1D5DB),
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

/**
 * Antigravity-style tight tool execution row (e.g. "Analyzed ◧ file.kt #L1-60", "Edited ◧ file.kt +1 -0").
 */
@Composable
private fun ToolRow(
    item: TranscriptItem.ToolCall,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)? = null,
    onPreview: ((String) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasOutput = !item.output.isNullOrBlank()
    val isEdit = isFileChange(item.name)
    val isRead = item.name.contains("read", ignoreCase = true) || item.name.contains("view", ignoreCase = true)
    val isSearch = item.name.contains("search", ignoreCase = true) || item.name.contains("grep", ignoreCase = true)
    val isCmd = item.name.contains("command", ignoreCase = true) || item.name.contains("bash", ignoreCase = true)

    val verb = when {
        isEdit -> "Edited"
        isRead -> "Analyzed"
        isSearch -> "Search"
        isCmd -> "Ran"
        else -> item.name.replace("_", " ").replaceFirstChar { it.uppercase() }
    }

    val targetName = when {
        item.affectedPath != null -> item.affectedPath.substringAfterLast('/')
        item.summary.isNotBlank() -> {
            val s = item.summary
            s.removePrefix("Read ")
                .removePrefix("View ")
                .removePrefix("Write ")
                .removePrefix("Edit ")
                .removePrefix("Analyzed ")
                .removePrefix("Search ")
                .removePrefix("Ran ")
        }
        else -> item.name
    }

    val rawOutput = item.output.orEmpty()
    val diffPlus = if (isEdit && hasOutput) {
        rawOutput.lines().count { it.startsWith("+") && !it.startsWith("+++") }.takeIf { it > 0 }
    } else null
    val diffMinus = if (isEdit && hasOutput) {
        rawOutput.lines().count { it.startsWith("-") && !it.startsWith("---") }.takeIf { it > 0 }
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = hasOutput) { expanded = !expanded }
                .padding(vertical = 3.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = verb,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFF9CA3AF),
            )

            // Antigravity Purple glyph badge
            if (item.isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = Color(0xFFA855F7),
                )
            } else if (item.isError) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(13.dp),
                )
            } else {
                Icon(
                    imageVector = when {
                        isEdit -> Icons.Default.Edit
                        isSearch -> Icons.Default.Search
                        isCmd -> Icons.Default.Terminal
                        else -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = Color(0xFFA855F7),
                    modifier = Modifier.size(13.dp),
                )
            }

            Text(
                text = targetName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                color = Color(0xFFE5E7EB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (diffPlus != null) {
                Text(
                    text = "+$diffPlus",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF22C55E),
                )
            }
            if (diffMinus != null) {
                Text(
                    text = "-$diffMinus",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFFEF4444),
                )
            }

            item.affectedPath?.let { path ->
                FileActions(item, path, onOpenDiff, onRollback, onPreview)
            }

            if (hasOutput) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        AnimatedVisibility(visible = expanded && hasOutput) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF141416),
                border = BorderStroke(1.dp, Color(0xFF27272A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
            ) {
                val outputLines = rawOutput.lines()
                val isDiff = outputLines.any { it.startsWith("+") || it.startsWith("-") }
                if (isDiff) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        outputLines.take(300).forEach { line ->
                            val dim = MaterialTheme.colorScheme.onSurfaceVariant
                            val colors = VmTheme.colors
                            val (bg, textColor) = when {
                                line.startsWith("+++") || line.startsWith("---") ->
                                    Color.Transparent to dim
                                line.startsWith("+") ->
                                    colors.diffAddedBackground to colors.diffAddedGutter
                                line.startsWith("-") ->
                                    colors.diffRemovedBackground to colors.diffRemovedGutter
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
                        color = Color(0xFFD1D5DB),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

/** Rollback, diff and (for web pages) preview buttons on a row that changed a file. */
@Composable
private fun FileActions(
    item: TranscriptItem.ToolCall,
    path: String,
    onOpenDiff: (String) -> Unit,
    onRollback: ((toolCallId: String, filePath: String) -> Unit)?,
    onPreview: ((String) -> Unit)?,
) {
    var confirmRollback by remember { mutableStateOf(false) }
    if (confirmRollback && onRollback != null) {
        AlertDialog(
            onDismissRequest = { confirmRollback = false },
            title = { Text("Undo changes to ${path.substringAfterLast('/')}?") },
            text = {
                Text(
                    "The file goes back to its last committed version in git. Every " +
                        "uncommitted change in it is discarded, including your own and " +
                        "earlier edits by the agent.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRollback = false
                    onRollback(item.id, path)
                }) { Text("Undo changes", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRollback = false }) { Text("Cancel") } },
        )
    }
    // Only an edit can be undone or reviewed: a read or a folder listing has nothing to
    // revert, and "undoing" a listing of "." used to discard every change in the repo.
    val changesFile = isFileChange(item.name)
    if (changesFile && item.isReverted) {
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
    } else if (changesFile && onRollback != null) {
        IconButton(
            onClick = { confirmRollback = true },
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
    if (changesFile) {
        IconButton(
            onClick = { onOpenDiff(path) },
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
    if (onPreview != null && isWebPage(path)) {
        IconButton(onClick = { onPreview(path) }, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = "Preview page",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** Tools that write a file, for both backends (Claude Code's and the gateway agent's). */
internal fun isFileChange(toolName: String): Boolean =
    toolName.lowercase() in FILE_CHANGING_TOOLS

private val FILE_CHANGING_TOOLS = setOf(
    "edit", "multiedit", "write", "notebookedit", "write_file", "edit_file",
)

internal fun isWebPage(path: String): Boolean =
    path.endsWith(".html", ignoreCase = true) || path.endsWith(".htm", ignoreCase = true)

/** A reply as plain text on the page, with copy and voice TTS read-out buttons. */
@Composable
private fun AssistantReply(text: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isSpeaking by remember { mutableStateOf(false) }
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        ttsInstance = tts
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    var cleanTtsText by remember(text) { mutableStateOf<String?>(null) }

    LaunchedEffect(text) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            cleanTtsText = text
                .replace(Regex("[#*`_~>\\[\\]]"), "")
                .replace(Regex("\\(https?://[^)]+\\)"), "")
                .take(1500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        MarkdownText(markdown = text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Copied reply", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val tts = ttsInstance
                    if (tts != null) {
                        if (isSpeaking) {
                            tts.stop()
                            isSpeaking = false
                        } else {
                            val speechText = cleanTtsText ?: text.take(500)
                            tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "tts_reply")
                            isSpeaking = true
                        }
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop voice" else "Read out loud",
                    tint = if (isSpeaking) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * A live "Thinking… 12s" line while the agent works with nothing else moving.
 *
 * Without it a run looked dead: a reasoning model can think for a minute before
 * its first word, and the transcript showed only the user's message. Hidden while
 * reply text streams in or a tool row already shows its own spinner.
 */
internal fun LazyListScope.runStatus(isRunning: Boolean, transcript: List<TranscriptItem>) {
    if (!isRunning) return
    val label = when (val last = transcript.lastOrNull()) {
        is TranscriptItem.StreamingText -> return
        is TranscriptItem.ToolCall -> if (last.isRunning) return else "Reading the results"
        else -> "Thinking"
    }
    item(key = RUN_STATUS_KEY) { RunStatusRow(label) }
}

@Composable
private fun RunStatusRow(label: String) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ONE_SECOND_MILLIS)
            seconds++
        }
    }
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
            text = "$label… ${seconds}s",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val RUN_STATUS_KEY = "run-status"
private const val ONE_SECOND_MILLIS = 1_000L

/**
 * Keeps the newest output in view while the user is reading at the bottom, also
 * as a long reply grows inside one row, and leaves them alone once they scroll up
 * to read something earlier.
 */
@Composable
internal fun FollowTail(listState: LazyListState, transcript: List<TranscriptItem>) {
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }
            .collect { (scrolling, canScrollForward) ->
                if (scrolling) following = !canScrollForward
            }
    }
    val tailLength = (transcript.lastOrNull() as? TranscriptItem.StreamingText)?.text?.length ?: 0
    val tailChunk = tailLength / 80
    LaunchedEffect(transcript.size, tailChunk) {
        if (!following) return@LaunchedEffect
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) {
            val visibleLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (visibleLast < last) {
                listState.animateScrollToItem(last)
            }
        }
    }
}

