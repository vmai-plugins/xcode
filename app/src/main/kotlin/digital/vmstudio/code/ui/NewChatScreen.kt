package digital.vmstudio.code.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The start screen.
 *
 * With a server saved it forwards straight into a fresh chat, so the app opens on
 * the conversation rather than on a dashboard. Without one it asks for the single
 * thing that is missing.
 */
@Composable
fun NewChatScreen(
    onOpenChat: (serverId: String) -> Unit,
    onAddServer: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsStateWithLifecycle()

    (target as? NewChatTarget.Server)?.let { server ->
        LaunchedEffect(server.serverId) { onOpenChat(server.serverId) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }

        when (target) {
            NewChatTarget.NoServer -> Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "Connect a server to start",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Add any machine you can reach over SSH. Claude Code runs on it, " +
                        "and you chat with it from here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onAddServer) { Text("Add server") }
            }

            else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
