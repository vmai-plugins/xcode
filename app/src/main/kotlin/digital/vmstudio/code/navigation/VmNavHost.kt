package digital.vmstudio.code.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import digital.vmstudio.code.background.rememberAgentRunWatchStarter
import digital.vmstudio.code.feature.ai.AgentChatScreen
import digital.vmstudio.code.feature.ai.AgentConversationsScreen
import digital.vmstudio.code.feature.tasks.TasksScreen
import digital.vmstudio.code.feature.files.FilesScreen
import digital.vmstudio.code.feature.servers.ServerDetailScreen
import digital.vmstudio.code.feature.servers.ServerEditScreen
import digital.vmstudio.code.feature.projects.ProjectCreateScreen
import digital.vmstudio.code.feature.ai.DiffReviewScreen
import digital.vmstudio.code.feature.editor.EditorScreen
import digital.vmstudio.code.feature.servers.RecipeScreen
import digital.vmstudio.code.feature.servers.ServerAppsScreen
import digital.vmstudio.code.feature.projects.ProjectsScreen
import digital.vmstudio.code.feature.projects.ProjectDetailScreen
import digital.vmstudio.code.feature.connectors.ConnectorsScreen
import digital.vmstudio.code.feature.servers.ServersScreen
import digital.vmstudio.code.feature.settings.SettingsScreen
import digital.vmstudio.code.feature.terminal.TerminalRoute
import digital.vmstudio.code.ui.NewChatScreen

/**
 * The app's single navigation graph.
 *
 * Features contribute composables; only this file knows how they are wired
 * together, which is what keeps `:feature:*` modules free of dependencies on one
 * another.
 */
@Composable
fun VmNavHost(
    navController: NavHostController,
    isOnline: Boolean,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved once here rather than per screen: it owns a permission launcher,
    // which must be created during composition, not inside a navigation callback.
    val startWatchingRuns = rememberAgentRunWatchStarter()

    NavHost(
        navController = navController,
        startDestination = VmDestination.NewChat.route,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(VmDestination.NewChat.route) {
            NewChatScreen(
                onOpenChat = { serverId ->
                    // Replace the start screen so Back leaves the app instead of
                    // bouncing through a redirect.
                    navController.navigate(VmDestination.ServerAgent.routeFor(serverId)) {
                        popUpTo(VmDestination.NewChat.route) { inclusive = true }
                    }
                },
                onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
                onOpenMenu = onOpenMenu,
            )
        }

        composable(VmDestination.Servers.route) {
            MenuScaffold(title = "Servers", onOpenMenu = onOpenMenu) {
                ServersScreen(
                    onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
                    onOpenServer = { serverId ->
                        navController.navigate(VmDestination.ServerDetail.routeFor(serverId))
                    },
                    onEditServer = { serverId ->
                        navController.navigate(VmDestination.ServerEdit.routeFor(serverId))
                    },
                )
            }
        }

        composable(VmDestination.ServerAdd.route) {
            ServerEditScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = VmDestination.ServerEdit.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerEdit.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) {
            ServerEditScreen(
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }

        composable(
            route = VmDestination.ServerDetail.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerDetail.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) {
            ServerDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onEdit = { serverId ->
                    navController.navigate(VmDestination.ServerEdit.routeFor(serverId))
                },
                onOpenTerminal = { serverId ->
                    navController.navigate(VmDestination.ServerTerminal.routeFor(serverId))
                },
                onOpenFiles = { serverId ->
                    navController.navigate(VmDestination.ServerFiles.routeFor(serverId))
                },
                onOpenApps = { serverId ->
                    navController.navigate(VmDestination.ServerApps.routeFor(serverId))
                },
                onOpenRecipes = { serverId ->
                    navController.navigate(VmDestination.ServerRecipes.routeFor(serverId))
                },
                onOpenAgent = { serverId ->
                    navController.navigate(VmDestination.ServerAgent.routeFor(serverId))
                },
            )
        }

        composable(
            route = VmDestination.ServerTerminal.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerTerminal.ARG_SERVER_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            TerminalRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = VmDestination.ServerFiles.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerFiles.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getString(VmDestination.ServerFiles.ARG_SERVER_ID) ?: ""
            FilesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenEditor = { _, filePath ->
                    navController.navigate(VmDestination.ServerEditor.routeFor(serverId, filePath))
                },
            )
        }

        composable(
            route = VmDestination.ServerApps.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerApps.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) {
            ServerAppsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = VmDestination.ServerRecipes.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerRecipes.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) {
            RecipeScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(VmDestination.Projects.route) {
            ProjectsScreen(
                onOpenProject = { project ->
                    navController.navigate(VmDestination.ProjectDetail.routeFor(project.id))
                },
                onCreateProject = { navController.navigate(VmDestination.ProjectAdd.route) },
                onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
                onOpenMenu = onOpenMenu,
            )
        }

        composable(
            route = VmDestination.ProjectDetail.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ProjectDetail.ARG_PROJECT_ID) { type = NavType.StringType },
            ),
        ) {
            ProjectDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTerminal = { serverId, _ ->
                    navController.navigate(VmDestination.ServerTerminal.routeFor(serverId))
                },
                onOpenFiles = { serverId, _ ->
                    navController.navigate(VmDestination.ServerFiles.routeFor(serverId))
                },
                onOpenAgent = { serverId, path ->
                    navController.navigate(VmDestination.ServerAgent.routeFor(serverId, path))
                },
            )
        }

        composable(VmDestination.Connectors.route) {
            ConnectorsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTerminal = {
                    navController.navigate(VmDestination.Servers.route)
                },
            )
        }

        composable(VmDestination.ProjectAdd.route) {
            ProjectCreateScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable(
            route = VmDestination.ServerAgent.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerAgent.ARG_SERVER_ID) { type = NavType.StringType },
                navArgument(VmDestination.ServerAgent.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val agentServerId = backStackEntry.arguments?.getString(VmDestination.ServerAgent.ARG_SERVER_ID) ?: ""
            AgentChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenMenu = onOpenMenu,
                onOpenSettings = { navController.navigate(VmDestination.Settings.route) },
                onWatchBackgroundRuns = startWatchingRuns,
                onOpenDiff = { filePath ->
                    navController.navigate(VmDestination.ServerDiff.routeFor(agentServerId, filePath))
                },
                onOpenFiles = {
                    navController.navigate(VmDestination.ServerFiles.routeFor(agentServerId))
                },
            )
        }

        composable(
            route = VmDestination.ServerEditor.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerEditor.ARG_SERVER_ID) { type = NavType.StringType },
                navArgument(VmDestination.ServerEditor.ARG_FILE_PATH) { type = NavType.StringType },
            ),
        ) {
            EditorScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = VmDestination.ServerDiff.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerDiff.ARG_SERVER_ID) { type = NavType.StringType },
                navArgument(VmDestination.ServerDiff.ARG_FILE_PATH) { type = NavType.StringType },
            ),
        ) {
            DiffReviewScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(VmDestination.Chats.route) {
            AgentConversationsScreen(
                onOpenConversation = { conversationId ->
                    navController.navigate(
                        VmDestination.AgentConversation.routeFor(conversationId),
                    )
                },
                onNewConversation = { navigateToNewChat(navController) },
                onOpenTasks = { navController.navigate(VmDestination.Tasks.route) },
                onOpenMenu = onOpenMenu,
            )
        }

        composable(
            route = VmDestination.AgentConversation.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.AgentConversation.ARG_CONVERSATION_ID) {
                    type = NavType.StringType
                },
            ),
        ) {
            AgentChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenMenu = onOpenMenu,
                onOpenSettings = { navController.navigate(VmDestination.Settings.route) },
                onWatchBackgroundRuns = startWatchingRuns,
            )
        }

        composable(VmDestination.Tasks.route) {
            TasksScreen(
                onOpenConversation = { conversationId ->
                    navController.navigate(
                        VmDestination.AgentConversation.routeFor(conversationId),
                    )
                },
                onReviewChanges = { serverId, filePath ->
                    navController.navigate(
                        VmDestination.ServerDiff.routeFor(serverId, filePath),
                    )
                },
            )
        }

        composable(VmDestination.Settings.route) {
            MenuScaffold(title = "Settings", onOpenMenu = onOpenMenu) {
                SettingsScreen()
            }
        }
    }
}

/** Clears the stack and reopens the start screen, which forwards into a fresh chat. */
fun navigateToNewChat(navController: NavHostController) {
    navController.navigate(VmDestination.NewChat.route) {
        popUpTo(navController.graph.id) { inclusive = true }
    }
}

/** Top-level screens that have no bar of their own get one carrying the drawer button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuScaffold(
    title: String,
    onOpenMenu: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}
