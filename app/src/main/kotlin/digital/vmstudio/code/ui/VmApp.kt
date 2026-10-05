package digital.vmstudio.code.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import digital.vmstudio.code.feature.ai.AgentConversationsViewModel
import digital.vmstudio.code.navigation.VmDestination
import digital.vmstudio.code.navigation.VmNavHost
import digital.vmstudio.code.navigation.navigateToNewChat
import kotlinx.coroutines.launch

/** How many recent chats the drawer lists; the rest live behind "All chats". */
private const val DRAWER_RECENT_CHATS = 15

/**
 * Root layout.
 *
 * One surface, no tab bar: the app opens on a chat and everything else is reached
 * from a slide-out drawer, the way Claude's own apps are laid out.
 */
@Composable
fun VmApp(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    conversationsViewModel: AgentConversationsViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentConversationId = backStackEntry?.arguments
        ?.getString(VmDestination.AgentConversation.ARG_CONVERSATION_ID)

    val conversations by conversationsViewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Forms and detail screens own the full screen; swiping the drawer open
    // mid-edit would invite an accidental context switch.
    val drawerAvailable = currentRoute in DRAWER_ROUTES

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    fun openTopLevel(destination: VmDestination) {
        closeDrawerThen {
            navController.navigate(destination.route) {
                popUpTo(navController.graph.id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerAvailable,
            drawerContent = {
                ModalDrawerSheet {
                    Text(
                        text = "VMStudio Code",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("New chat") },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen { navigateToNewChat(navController) } },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )

                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(
                            conversations.conversations.take(DRAWER_RECENT_CHATS),
                            key = { it.id },
                        ) { chat ->
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = chat.title.ifBlank { "Untitled chat" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                selected = chat.id == currentConversationId,
                                onClick = {
                                    closeDrawerThen {
                                        navController.navigate(
                                            VmDestination.AgentConversation.routeFor(chat.id),
                                        ) { launchSingleTop = true }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        if (conversations.conversations.size > DRAWER_RECENT_CHATS) {
                            item(key = "all-chats") {
                                NavigationDrawerItem(
                                    label = { Text("All chats") },
                                    selected = false,
                                    onClick = { openTopLevel(VmDestination.Chats) },
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Servers") },
                        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                        selected = currentRoute == VmDestination.Servers.route,
                        onClick = { openTopLevel(VmDestination.Servers) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Projects") },
                        icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                        selected = currentRoute == VmDestination.Projects.route,
                        onClick = { openTopLevel(VmDestination.Projects) },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Connectors") },
                        icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                        selected = currentRoute == VmDestination.Connectors.route,
                        // Pushed rather than made top-level: the screen has a back arrow,
                        // and back should return here instead of leaving the app.
                        onClick = {
                            closeDrawerThen { navController.navigate(VmDestination.Connectors.route) }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        selected = currentRoute == VmDestination.Settings.route,
                        onClick = { openTopLevel(VmDestination.Settings) },
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            },
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                Box(modifier = Modifier.padding(padding)) {
                    VmNavHost(
                        navController = navController,
                        isOnline = isOnline,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                    )

                    // Above the graph so an approval raised by background work
                    // reaches the user wherever they currently are.
                    CommandApprovalHost()
                    FileEditApprovalHost()
                }
            }
        }
    }
}

private val DRAWER_ROUTES = setOf(
    VmDestination.NewChat.route,
    VmDestination.Servers.route,
    VmDestination.Projects.route,
    VmDestination.Settings.route,
    VmDestination.Chats.route,
    VmDestination.ServerAgent.ROUTE_PATTERN,
    VmDestination.AgentConversation.ROUTE_PATTERN,
)
