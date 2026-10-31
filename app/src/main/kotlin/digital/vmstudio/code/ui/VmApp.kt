package digital.vmstudio.code.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import digital.vmstudio.code.crash.CrashReportDialog
import digital.vmstudio.code.feature.ai.AgentConversationsUiState
import digital.vmstudio.code.feature.ai.AgentConversationsViewModel
import digital.vmstudio.code.feature.ai.ConversationAction
import digital.vmstudio.code.feature.ai.ConversationActionDialogs
import digital.vmstudio.code.feature.ai.ConversationMenu
import digital.vmstudio.code.feature.ai.ConversationSummary
import digital.vmstudio.code.navigation.VmDestination
import digital.vmstudio.code.navigation.VmNavHost
import digital.vmstudio.code.navigation.navigateToNewChat
import kotlinx.coroutines.launch

private const val DRAWER_RECENT_CHATS = 50

@Composable
fun VmApp(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    hasSignedIn: Boolean = true,
    hasCompletedOnboarding: Boolean = true,
    navController: NavHostController = rememberNavController(),
    conversationsViewModel: AgentConversationsViewModel = hiltViewModel(),
    appViewModel: VmAppViewModel = hiltViewModel(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentConversationId = backStackEntry?.arguments
        ?.getString(VmDestination.AgentConversation.ARG_CONVERSATION_ID)

    val conversations by conversationsViewModel.uiState.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pendingChatAction by remember {
        mutableStateOf<Pair<ConversationSummary, ConversationAction>?>(null)
    }
    var chatMenuFor by remember { mutableStateOf<String?>(null) }

    ConversationActionDialogs(
        pending = pendingChatAction,
        onDismiss = { pendingChatAction = null },
        onRename = conversationsViewModel::rename,
        onDelete = { id ->
            conversationsViewModel.delete(id)
            if (id == currentConversationId) navigateToNewChat(navController)
        },
    )

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
                popUpTo(destination.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTabletOrDex = maxWidth >= 840.dp

            if (isTabletOrDex) {
                // Adaptive Dual-Pane layout for Tablets, Foldables and Samsung DeX Mode
                PermanentNavigationDrawer(
                    drawerContent = {
                        PermanentDrawerSheet(modifier = Modifier.width(320.dp)) {
                            DrawerWorkspaceHeader()
                            DrawerNavigationItems(
                                currentRoute = currentRoute,
                                currentConversationId = currentConversationId,
                                conversations = conversations,
                                chatMenuFor = chatMenuFor,
                                onChatMenuClick = { chatMenuFor = it },
                                onChatMenuDismiss = { chatMenuFor = null },
                                onChatAction = { pendingChatAction = it },
                                onNewChat = { navigateToNewChat(navController) },
                                onSelectChat = { id ->
                                    if (id != currentConversationId) {
                                        navController.navigate(VmDestination.AgentConversation.routeFor(id)) {
                                            popUpTo(VmDestination.AgentConversation.ROUTE_PATTERN) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                },
                                onNavigate = { openTopLevel(it) },
                            )
                        }
                    },
                ) {
                    AppMainContent(
                        navController = navController,
                        isOnline = isOnline,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        hasSignedIn = hasSignedIn,
                        appViewModel = appViewModel,
                    )
                }
            } else {
                // Phone single-column layout with Slide-Out Navigation Drawer
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = drawerAvailable,
                    drawerContent = {
                        ModalDrawerSheet {
                            DrawerWorkspaceHeader()
                            DrawerNavigationItems(
                                currentRoute = currentRoute,
                                currentConversationId = currentConversationId,
                                conversations = conversations,
                                chatMenuFor = chatMenuFor,
                                onChatMenuClick = { chatMenuFor = it },
                                onChatMenuDismiss = { chatMenuFor = null },
                                onChatAction = { pendingChatAction = it },
                                onNewChat = { closeDrawerThen { navigateToNewChat(navController) } },
                                onSelectChat = { id ->
                                    closeDrawerThen {
                                        if (id != currentConversationId) {
                                            navController.navigate(VmDestination.AgentConversation.routeFor(id)) {
                                                popUpTo(VmDestination.AgentConversation.ROUTE_PATTERN) {
                                                    inclusive = true
                                                }
                                            }
                                        }
                                    }
                                },
                                onNavigate = { openTopLevel(it) },
                            )
                        }
                    },
                ) {
                    AppMainContent(
                        navController = navController,
                        isOnline = isOnline,
                        onOpenMenu = { scope.launch { drawerState.open() } },
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        hasSignedIn = hasSignedIn,
                        appViewModel = appViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppMainContent(
    navController: NavHostController,
    isOnline: Boolean,
    onOpenMenu: () -> Unit,
    hasCompletedOnboarding: Boolean,
    hasSignedIn: Boolean,
    appViewModel: VmAppViewModel,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            VmNavHost(
                navController = navController,
                isOnline = isOnline,
                onOpenMenu = onOpenMenu,
                hasCompletedOnboarding = hasCompletedOnboarding,
                hasSignedIn = hasSignedIn,
                onFinishOnboarding = appViewModel::completeOnboarding,
            )

            CommandApprovalHost()
            FileEditApprovalHost()
            CrashReportDialog()
        }
    }
}

@Composable
private fun DrawerWorkspaceHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "X-codes Workspace",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Cloud IDE & Autonomous Agent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.DrawerNavigationItems(
    currentRoute: String?,
    currentConversationId: String?,
    conversations: AgentConversationsUiState,
    chatMenuFor: String?,
    onChatMenuClick: (String) -> Unit,
    onChatMenuDismiss: () -> Unit,
    onChatAction: (Pair<ConversationSummary, ConversationAction>) -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onNavigate: (VmDestination) -> Unit,
) {
    NavigationDrawerItem(
        label = { Text("New chat", fontWeight = FontWeight.SemiBold) },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        selected = false,
        onClick = onNewChat,
        modifier = Modifier.padding(horizontal = 12.dp),
    )

    Text(
        text = "Recents & History",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
    )
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(
            conversations.conversations.take(DRAWER_RECENT_CHATS),
            key = { it.id },
        ) { chat ->
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(
                            text = chat.title.ifBlank { "Untitled chat" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        chat.workingDirectory?.let { dir ->
                            Text(
                                text = dir.substringAfterLast('/'),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                },
                selected = chat.id == currentConversationId,
                badge = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (chat.isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Box {
                            IconButton(
                                onClick = { onChatMenuClick(chat.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Rename or delete chat",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            ConversationMenu(
                                expanded = chatMenuFor == chat.id,
                                onDismiss = onChatMenuDismiss,
                                onAction = { action -> onChatAction(chat to action) },
                            )
                        }
                    }
                },
                onClick = { onSelectChat(chat.id) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (conversations.conversations.isNotEmpty()) {
            item(key = "all-chats") {
                NavigationDrawerItem(
                    label = { Text("All chats") },
                    selected = false,
                    onClick = { onNavigate(VmDestination.Chats) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    NavigationDrawerItem(
        label = { Text("Servers (SSH & PC)") },
        icon = { Icon(Icons.Default.Dns, contentDescription = null) },
        selected = currentRoute == VmDestination.Servers.route,
        onClick = { onNavigate(VmDestination.Servers) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
    NavigationDrawerItem(
        label = { Text("Settings") },
        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
        selected = currentRoute == VmDestination.Settings.route,
        onClick = { onNavigate(VmDestination.Settings) },
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
    )
}

private val DRAWER_ROUTES = setOf(
    VmDestination.NewChat.route,
    VmDestination.GeneralChat.route,
    VmDestination.Projects.route,
    VmDestination.Files.route,
    VmDestination.Settings.route,
    VmDestination.Chats.route,
    VmDestination.ServerAgent.ROUTE_PATTERN,
    VmDestination.AgentConversation.ROUTE_PATTERN,
)
