package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** What the user picked from a chat's menu. */
enum class ConversationAction { RENAME, DELETE, CHANGE_COLOR }

/** Rename, Color and Delete for one chat; shared by the drawer, the Chats list and the chat header. */
@Composable
fun ConversationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ConversationAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Color") },
            leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(ConversationAction.CHANGE_COLOR)
            },
        )
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
            onClick = {
                onDismiss()
                onAction(ConversationAction.RENAME)
            },
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            leadingIcon = {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismiss()
                onAction(ConversationAction.DELETE)
            },
        )
    }
}

private val CHAT_COLOR_OPTIONS = listOf(
    "#8957E5", // GitHub Violet
    "#10B981", // Emerald
    "#0284C7", // Azure
    "#F59E0B", // Amber
    "#6366F1", // Indigo
    "#06B6D4", // Cyan
    "#EC4899", // Pink
    "#EF4444", // Crimson
    "#14B8A6", // Teal
    "#F97316", // Orange
)

@Composable
fun ChatColorPickerDialog(
    currentColorHex: String?,
    onDismiss: () -> Unit,
    onSelectColor: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat Color Accent") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Choose an accent color for this chat conversation:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CHAT_COLOR_OPTIONS.take(5).forEach { hex ->
                        ColorCircle(
                            hex = hex,
                            isSelected = currentColorHex?.equals(hex, ignoreCase = true) == true,
                            onClick = { onSelectColor(hex) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CHAT_COLOR_OPTIONS.drop(5).forEach { hex ->
                        ColorCircle(
                            hex = hex,
                            isSelected = currentColorHex?.equals(hex, ignoreCase = true) == true,
                            onClick = { onSelectColor(hex) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelectColor(null) }) {
                Text("Default")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ColorCircle(
    hex: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = remember(hex) {
        val clean = hex.removePrefix("#")
        val longVal = clean.toLongOrNull(16) ?: 0xFF6366F1
        Color(if (clean.length == 6) 0xFF000000 or longVal else longVal)
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else Modifier
            ),
    )
}

/**
 * Shows the rename, delete or color dialog for [pending], if any.
 */
@Composable
fun ConversationActionDialogs(
    pending: Pair<ConversationSummary, ConversationAction>?,
    currentColorHex: String? = null,
    onDismiss: () -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onSetColor: ((id: String, colorHex: String?) -> Unit)? = null,
) {
    val (conversation, action) = pending ?: return
    when (action) {
        ConversationAction.CHANGE_COLOR -> ChatColorPickerDialog(
            currentColorHex = currentColorHex,
            onDismiss = onDismiss,
            onSelectColor = { hex ->
                onSetColor?.invoke(conversation.id, hex)
                onDismiss()
            },
        )
        ConversationAction.RENAME -> RenameDialog(
            initial = conversation.title,
            onDismiss = onDismiss,
            onConfirm = { title ->
                onRename(conversation.id, title)
                onDismiss()
            },
        )
        ConversationAction.DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete chat?") },
            text = {
                Text("\"${conversation.title.ifBlank { "Untitled chat" }}\" and its history will be removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(conversation.id)
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    val trimmed = remember(title) { title.trim() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename chat") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty(), onClick = { onConfirm(trimmed) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
