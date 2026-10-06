package digital.vmstudio.code.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The start screen.
 *
 * Forwards straight into a fresh chat, so the app opens on the conversation rather
 * than on a dashboard: on the last server, or a general chat when there is none.
 */
@Composable
fun NewChatScreen(
    /** Null opens a general chat. */
    onOpenChat: (serverId: String?) -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val target by viewModel.target.collectAsStateWithLifecycle()

    when (val current = target) {
        is NewChatTarget.Server -> LaunchedEffect(current.serverId) { onOpenChat(current.serverId) }
        NewChatTarget.General -> LaunchedEffect(Unit) { onOpenChat(null) }
        NewChatTarget.Loading -> Unit
    }

    Box(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }

        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
    }
}
