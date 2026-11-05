package digital.vmstudio.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    appViewModel: VmAppViewModel,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            VmNavHost(
                navController = navController,
                isOnline = isOnline,
                onOpenMenu = onOpenMenu,
                hasCompletedOnboarding = hasCompletedOnboarding,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Text(
            text = "X-Codes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Cloud IDE & Autonomous Agent",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
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
    Text(
        text = "Workspace",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp),
    )

    DrawerNavItem(
        label = "Chat & Agent",
        icon = Icons.Default.ChatBubbleOutline,
        isSelected = currentRoute == VmDestination.NewChat.route || currentRoute == VmDestination.GeneralChat.route,
        onClick = onNewChat,
    )
    DrawerNavItem(
        label = "Projects",
        icon = Icons.Default.Folder,
        isSelected = currentRoute == VmDestination.Projects.route,
        onClick = { onNavigate(VmDestination.Projects) },
    )
    DrawerNavItem(
        label = "Files & Code",
        icon = Icons.Default.Code,
        isSelected = currentRoute == VmDestination.Files.route,
        onClick = { onNavigate(VmDestination.Files) },
    )
    DrawerNavItem(
        label = "Servers & Cluster",
        icon = Icons.Default.Dns,
        isSelected = currentRoute == VmDestination.Servers.route,
        onClick = { onNavigate(VmDestination.Servers) },
    )
    DrawerNavItem(
        label = "Connectors & MCP",
        icon = Icons.Default.Extension,
        isSelected = currentRoute == VmDestination.Connectors.route,
        onClick = { onNavigate(VmDestination.Connectors) },
    )
    DrawerNavItem(
        label = "Tasks & Runs",
        icon = Icons.Default.Schedule,
        isSelected = currentRoute == VmDestination.Tasks.route,
        onClick = { onNavigate(VmDestination.Tasks) },
    )
    DrawerNavItem(
        label = "Activity Timeline",
        icon = Icons.Default.History,
        isSelected = currentRoute == VmDestination.ActivityTimeline.route,
        onClick = { onNavigate(VmDestination.ActivityTimeline) },
    )

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )

    Text(
        text = "Recent Chats",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
    )
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(
            conversations.conversations.take(DRAWER_RECENT_CHATS),
            key = { it.id },
        ) { chat ->
            val isSelected = chat.id == currentConversationId
            NavigationDrawerItem(
                label = {
                    Column {
                        Text(
                            text = chat.title.ifBlank { "Untitled chat" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
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
                icon = {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                },
                selected = isSelected,
                shape = RoundedCornerShape(12.dp),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                ),
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
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
        if (conversations.conversations.isNotEmpty()) {
            item(key = "all-chats") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigate(VmDestination.Chats) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "All chats",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE07A5F))
                    .clickable { onNavigate(VmDestination.Settings) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "XC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                )
            }
            IconButton(
                onClick = { onNavigate(VmDestination.Settings) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                    tint = if (currentRoute == VmDestination.Settings.route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onNewChat,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
            Spacer(Modifier.width(6.dp))
            Text("New chat", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
        }
    }
}

@Composable
private fun DrawerNavItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(label, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        selected = isSelected,
        shape = RoundedCornerShape(12.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedContainerColor = Color.Transparent,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        ),
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

private val DRAWER_ROUTES = setOf(
    VmDestination.NewChat.route,
    VmDestination.GeneralChat.route,
    VmDestination.Projects.route,
    VmDestination.Files.route,
    VmDestination.Servers.route,
    VmDestination.Tasks.route,
    VmDestination.Connectors.route,
    VmDestination.ActivityTimeline.route,
    VmDestination.Settings.route,
    VmDestination.Chats.route,
    VmDestination.ServerAgent.ROUTE_PATTERN,
    VmDestination.AgentConversation.ROUTE_PATTERN,
)
