package digital.vmstudio.code.feature.ai

import androidx.compose.material.icons.Icons
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

/** What the user picked from a chat's menu. */
enum class ConversationAction { RENAME, DELETE }

/** Rename and Delete for one chat; shared by the drawer, the Chats list and the chat header. */
@Composable
fun ConversationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (ConversationAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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

/**
 * Shows the rename or delete dialog for [pending], if any. A delete is always
 * confirmed: it removes the whole transcript and cannot be undone.
 */
@Composable
fun ConversationActionDialogs(
    pending: Pair<ConversationSummary, ConversationAction>?,
    onDismiss: () -> Unit,
    onRename: (id: String, title: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    val (conversation, action) = pending ?: return
    when (action) {
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
