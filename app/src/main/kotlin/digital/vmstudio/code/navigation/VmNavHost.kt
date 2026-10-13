package digital.vmstudio.code.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import digital.vmstudio.code.feature.projects.ProjectsScreen
import digital.vmstudio.code.feature.projects.ProjectDetailScreen
import digital.vmstudio.code.feature.servers.ServersScreen
import digital.vmstudio.code.feature.settings.AboutSettingsScreen
import digital.vmstudio.code.feature.settings.AgentSettingsScreen
import digital.vmstudio.code.feature.settings.AiSettingsScreen
import digital.vmstudio.code.feature.settings.AppearanceSettingsScreen
import digital.vmstudio.code.feature.settings.DiagnosticsSettingsScreen
import digital.vmstudio.code.feature.settings.EditorSettingsScreen
import digital.vmstudio.code.feature.settings.SecuritySettingsScreen
import digital.vmstudio.code.feature.settings.SettingsScreen
import digital.vmstudio.code.feature.terminal.TerminalRoute
import digital.vmstudio.code.ui.NewChatScreen

import digital.vmstudio.code.ui.onboarding.OnboardingScreen
import digital.vmstudio.code.ui.activity.ActivityTimelineScreen

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
    hasCompletedOnboarding: Boolean = true,
    hasSignedIn: Boolean = false,
    onFinishSignIn: () -> Unit = {},
    onFinishOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Resolved once here rather than per screen: it owns a permission launcher,
    // which must be created during composition, not inside a navigation callback.
    val startWatchingRuns = rememberAgentRunWatchStarter()

    val startDestination = androidx.compose.runtime.remember {
        when {
            !hasSignedIn -> VmDestination.Auth.route
            hasCompletedOnboarding -> VmDestination.NewChat.route
            else -> VmDestination.Onboarding.route
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(VmDestination.Auth.route) {
            digital.vmstudio.code.ui.auth.AuthScreen(
                onSignedIn = {
                    onFinishSignIn()
                    val next = if (hasCompletedOnboarding) VmDestination.NewChat.route else VmDestination.Onboarding.route
                    navController.navigate(next) { popUpTo(VmDestination.Auth.route) { inclusive = true } }
                }
            )
        }
        composable(VmDestination.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    onFinishOnboarding()
                    navController.navigate(VmDestination.NewChat.route) {
                        popUpTo(VmDestination.Onboarding.route) { inclusive = true }
                    }
                },
            )
        }

        composable(VmDestination.ActivityTimeline.route) {
            ActivityTimelineScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(VmDestination.NewChat.route) {
            NewChatScreen(
                onOpenChat = { serverId ->
                    // Replace the start screen so Back leaves the app instead of
                    // bouncing through a redirect. No server: a general chat.
                    val route = serverId?.let(VmDestination.ServerAgent::routeFor)
                        ?: VmDestination.GeneralChat.route
                    navController.navigate(route) {
                        popUpTo(VmDestination.NewChat.route) { inclusive = true }
                    }
                },
                onOpenMenu = onOpenMenu,
            )
        }

        composable(VmDestination.Servers.route) {
            val canPop = navController.previousBackStackEntry != null
            MenuScaffold(
                title = "Servers",
                onOpenMenu = onOpenMenu,
                onNavigateBack = if (canPop) { { navController.popBackStack() } } else null,
            ) {
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
                navArgument(VmDestination.ServerTerminal.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            TerminalRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = VmDestination.ServerFiles.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ServerFiles.ARG_SERVER_ID) { type = NavType.StringType },
                navArgument(VmDestination.ServerFiles.ARG_PATH) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            FilesScreen(
                onNavigateBack = { navController.popBackStack() },
                // The screen's own server id: its switcher can move off the route's.
                onOpenEditor = { serverId, filePath ->
                    navController.navigate(VmDestination.ServerEditor.routeFor(serverId, filePath))
                },
                onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
            )
        }

        composable(VmDestination.Files.route) {
            FilesScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenEditor = { serverId, filePath ->
                    navController.navigate(VmDestination.ServerEditor.routeFor(serverId, filePath))
                },
                onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
                onOpenMenu = onOpenMenu,
            )
        }

        composable(VmDestination.Projects.route) {
            val canPop = navController.previousBackStackEntry != null
            ProjectsScreen(
                onOpenProject = { project ->
                    navController.navigate(VmDestination.ProjectDetail.routeFor(project.id))
                },
                onCreateProject = { navController.navigate(VmDestination.ProjectAdd.route) },
                onAddServer = { navController.navigate(VmDestination.ServerAdd.route) },
                onOpenMenu = onOpenMenu,
                onNavigateBack = if (canPop) { { navController.popBackStack() } } else null,
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
                onOpenTerminal = { serverId, path ->
                    navController.navigate(VmDestination.ServerTerminal.routeFor(serverId, path))
                },
                onOpenFiles = { serverId, path ->
                    navController.navigate(VmDestination.ServerFiles.routeFor(serverId, path))
                },
                onOpenAgent = { serverId, path ->
                    navController.navigate(VmDestination.ServerAgent.routeFor(serverId, path))
                },
            )
        }

        composable(
            route = VmDestination.ProjectAdd.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(VmDestination.ProjectAdd.ARG_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ProjectCreateScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreated = { projectId ->
                    navController.popBackStack()
                    navController.navigate(VmDestination.ProjectDetail.routeFor(projectId))
                },
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
        ) {
            AgentChat(navController, onOpenMenu, startWatchingRuns)
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
            // A reopened chat gets the same Files and diff buttons as a new one.
            AgentChat(navController, onOpenMenu, startWatchingRuns)
        }

        composable(VmDestination.GeneralChat.route) {
            AgentChat(navController, onOpenMenu, startWatchingRuns)
        }

        composable(VmDestination.Tasks.route) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            TasksScreen(
                onNavigateBack = { navController.popBackStack() },
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
                onSolveWithAi = { title, detail ->
                    val taskPrompt = if (detail.isNotBlank()) "Task: $title\n\nDetails:\n$detail" else "Task: $title"
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(taskPrompt))
                    android.widget.Toast.makeText(context, "Task prompt copied to clipboard! Opening AI Agent...", android.widget.Toast.LENGTH_SHORT).show()
                    navController.navigate(VmDestination.GeneralChat.route)
                },
            )
        }

        composable(VmDestination.Settings.route) {
            MenuScaffold(title = "Settings", onOpenMenu = onOpenMenu) {
                SettingsScreen(
                    onOpenAi = { navController.navigate(VmDestination.SettingsAi.route) },
                    onOpenAgent = { navController.navigate(VmDestination.SettingsAgent.route) },
                    onOpenAppearance = { navController.navigate(VmDestination.SettingsAppearance.route) },
                    onOpenEditor = { navController.navigate(VmDestination.SettingsEditor.route) },
                    onOpenSecurity = { navController.navigate(VmDestination.SettingsSecurity.route) },
                    onOpenServers = { navController.navigate(VmDestination.Servers.route) },
                    onOpenGitHub = { navController.navigate("settings/github") },
                    onOpenAbout = { navController.navigate(VmDestination.SettingsAbout.route) },
                    onSignOut = {
                        navController.navigate(VmDestination.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(VmDestination.SettingsAi.route) {
            SettingsDetailScaffold(
                title = "AI provider",
                onNavigateBack = { navController.popBackStack() },
            ) { AiSettingsScreen() }
        }

        composable(VmDestination.SettingsAgent.route) {
            SettingsDetailScaffold(
                title = "Agent & autonomy",
                onNavigateBack = { navController.popBackStack() },
            ) { AgentSettingsScreen() }
        }

        composable(VmDestination.SettingsAppearance.route) {
            SettingsDetailScaffold(
                title = "Appearance",
                onNavigateBack = { navController.popBackStack() },
            ) { AppearanceSettingsScreen() }
        }

        composable(VmDestination.SettingsEditor.route) {
            SettingsDetailScaffold(
                title = "Editor & terminal",
                onNavigateBack = { navController.popBackStack() },
            ) { EditorSettingsScreen() }
        }

        composable(VmDestination.SettingsSecurity.route) {
            SettingsDetailScaffold(
                title = "Security",
                onNavigateBack = { navController.popBackStack() },
            ) { SecuritySettingsScreen() }
        }

        composable("settings/github") {
            SettingsDetailScaffold(
                title = "GitHub Codespaces",
                onNavigateBack = { navController.popBackStack() },
            ) { digital.vmstudio.code.feature.settings.GitHubSettingsScreen() }
        }

        composable(VmDestination.Connectors.route) {
            digital.vmstudio.code.feature.connectors.ConnectorsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenServers = { navController.navigate(VmDestination.Servers.route) },
                onOpenGitHubSettings = { navController.navigate("settings/github") },
            )
        }

        composable(VmDestination.SettingsAbout.route) {
            SettingsDetailScaffold(
                title = "About & updates",
                onNavigateBack = { navController.popBackStack() },
            ) {
                AboutSettingsScreen()
            }
        }
    }
}

@Composable
private fun AgentChat(
    navController: NavHostController,
    onOpenMenu: () -> Unit,
    startWatchingRuns: (String) -> Unit,
) {
    AgentChatScreen(
        onNavigateBack = { navController.popBackStack() },
        onOpenMenu = onOpenMenu,
        onOpenSettings = { navController.navigate(VmDestination.Settings.route) },
        onWatchBackgroundRuns = startWatchingRuns,
        onOpenDiff = { serverId, filePath ->
            navController.navigate(VmDestination.ServerDiff.routeFor(serverId, filePath))
        },
        onOpenFiles = { serverId, path ->
            navController.navigate(VmDestination.ServerFiles.routeFor(serverId, path))
        },
    )
}

/** Clears the stack and reopens the start screen, which forwards into a fresh chat. */
fun navigateToNewChat(navController: NavHostController) {
    navController.navigate(VmDestination.NewChat.route) {
        popUpTo(navController.graph.id) { inclusive = true }
    }
}

@Composable
private fun SettingsDetailScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    MenuScaffold(title = title, onOpenMenu = {}, onNavigateBack = onNavigateBack) {
        content()
    }
}

/** Top-level screens that have no bar of their own get one carrying the drawer button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuScaffold(
    title: String,
    onOpenMenu: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        IconButton(onClick = onOpenMenu) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

