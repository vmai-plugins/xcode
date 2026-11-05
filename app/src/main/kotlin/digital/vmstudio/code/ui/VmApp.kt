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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextAlign
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
                            DrawerWorkspaceHeader(onNewChat = { navigateToNewChat(navController) })
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
                            DrawerWorkspaceHeader(onNewChat = { closeDrawerThen { navigateToNewChat(navController) } })
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
private fun DrawerWorkspaceHeader(
    onNewChat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 26.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "X-Codes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Autonomous Coding Agent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        FilledTonalButton(
            onClick = onNewChat,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New Chat", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
    var searchQuery by remember { mutableStateOf("") }

    // Search bar for conversations
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search conversations…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                },
            )
            if (searchQuery.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { searchQuery = "" },
                )
            }
        }
    }

    val filteredChats = remember(conversations.conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations.conversations
        else conversations.conversations.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.workingDirectory?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    val groupedChats = remember(filteredChats) {
        val now = java.time.LocalDate.now()
        val zone = java.time.ZoneId.systemDefault()
        filteredChats.groupBy { summary ->
            val chatDate = java.time.Instant.ofEpochMilli(summary.updatedAtMillis).atZone(zone).toLocalDate()
            val days = java.time.temporal.ChronoUnit.DAYS.between(chatDate, now)
            when {
                days == 0L -> "Today"
                days == 1L -> "Yesterday"
                days in 2..7 -> "Previous 7 Days"
                days in 8..30 -> "Previous 30 Days"
                else -> "Older"
            }
        }
    }

    val timeOrder = listOf("Today", "Yesterday", "Previous 7 Days", "Previous 30 Days", "Older")

    if (filteredChats.isEmpty()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (searchQuery.isNotBlank()) "No chats found matching \"$searchQuery\""
                else "No conversations yet.\nTap 'New Chat' above to begin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            timeOrder.forEach { groupName ->
                val chatsInGroup = groupedChats[groupName].orEmpty()
                if (chatsInGroup.isNotEmpty()) {
                    item(key = "header-$groupName") {
                        Text(
                            text = groupName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }

                    items(chatsInGroup, key = { it.id }) { chat ->
                        val isSelected = chat.id == currentConversationId
                        NavigationDrawerItem(
                            label = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = chat.title.ifBlank { "Untitled chat" },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        ),
                                    )
                                    chat.workingDirectory?.let { dir ->
                                        Text(
                                            text = "📁 " + dir.substringAfterLast('/'),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            },
                            icon = {
                                if (chat.isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            },
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
                            badge = {
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
                            },
                            onClick = { onSelectChat(chat.id) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )

    // Bottom Settings & Hub Tile (1-tap access to Settings where Servers, Connectors, Projects, Appearance reside)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onNavigate(VmDestination.Settings) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE07A5F)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "XC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
                Column {
                    Text(
                        text = "Settings & Hub",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Servers, Connectors, AI Models",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
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
