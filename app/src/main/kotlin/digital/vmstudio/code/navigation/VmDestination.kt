package digital.vmstudio.code.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every screen the app can navigate to.
 *
 * Routes are declared centrally in the app module rather than inside features, so
 * feature modules stay independent of one another: a feature exposes composables,
 * and only this graph knows how they connect.
 */
sealed class VmDestination(val route: String) {

    /** Destinations reachable from the primary navigation surface. */
    sealed class Primary(
        route: String,
        val label: String,
        val icon: ImageVector,
    ) : VmDestination(route)

    data object Home : Primary("home", "Home", Icons.Default.Home)
    data object Projects : Primary("projects", "Projects", Icons.Default.Workspaces)
    data object Servers : Primary("servers", "Servers", Icons.Default.Dns)
    data object Agent : Primary("agent", "AI Agent", Icons.Default.AutoAwesome)
    data object Settings : Primary("settings", "Settings", Icons.Default.Settings)

    // Secondary destinations, reached from within a section.
    data object ServerAdd : VmDestination("servers/new")

    data object ProjectAdd : VmDestination("projects/new")

    /** Reopens a stored conversation, transcript and resume id intact. */
    data class AgentConversation(val conversationId: String) :
        VmDestination(routeFor(conversationId)) {
        companion object {
            const val ARG_CONVERSATION_ID = "conversationId"
            const val ROUTE_PATTERN = "agent/conversations/{$ARG_CONVERSATION_ID}"
            fun routeFor(conversationId: String) = "agent/conversations/$conversationId"
        }
    }

    data class ServerEdit(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}/edit"
            fun routeFor(serverId: String) = "servers/$serverId/edit"
        }
    }

    data class ServerDetail(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}"
            fun routeFor(serverId: String) = "servers/$serverId"
        }
    }

    data class ServerTerminal(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}/terminal"
            fun routeFor(serverId: String) = "servers/$serverId/terminal"
        }
    }

    data class ServerFiles(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}/files"
            fun routeFor(serverId: String) = "servers/$serverId/files"
        }
    }

    data class ServerApps(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}/apps"
            fun routeFor(serverId: String) = "servers/$serverId/apps"
        }
    }

    data class ServerRecipes(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ROUTE_PATTERN = "servers/{$ARG_SERVER_ID}/recipes"
            fun routeFor(serverId: String) = "servers/$serverId/recipes"
        }
    }

    data class ServerAgent(val serverId: String) : VmDestination(routeFor(serverId)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ARG_PATH = "path"
            const val ROUTE_PATTERN =
                "servers/{$ARG_SERVER_ID}/agent?$ARG_PATH={$ARG_PATH}"

            fun routeFor(serverId: String, path: String? = null): String {
                val encoded = path?.let { java.net.URLEncoder.encode(it, "UTF-8") }
                return "servers/$serverId/agent" + (encoded?.let { "?path=$it" } ?: "")
            }
        }
    }

    data class ServerEditor(val serverId: String, val filePath: String) : VmDestination(routeFor(serverId, filePath)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ARG_FILE_PATH = "filePath"
            const val ROUTE_PATTERN =
                "servers/{$ARG_SERVER_ID}/editor?{$ARG_FILE_PATH}={$ARG_FILE_PATH}"

            fun routeFor(serverId: String, filePath: String): String {
                val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
                return "servers/$serverId/editor?filePath=$encoded"
            }
        }
    }

    data class ServerDiff(val serverId: String, val filePath: String) : VmDestination(routeFor(serverId, filePath)) {
        companion object {
            const val ARG_SERVER_ID = "serverId"
            const val ARG_FILE_PATH = "filePath"
            const val ROUTE_PATTERN =
                "servers/{$ARG_SERVER_ID}/diff?{$ARG_FILE_PATH}={$ARG_FILE_PATH}"

            fun routeFor(serverId: String, filePath: String): String {
                val encoded = java.net.URLEncoder.encode(filePath, "UTF-8")
                return "servers/$serverId/diff?filePath=$encoded"
            }
        }
    }

    data object Onboarding : VmDestination("onboarding")

    data object Tasks : VmDestination("tasks")

    data object Diagnostics : VmDestination("settings/diagnostics")

    data object Appearance : VmDestination("settings/appearance")

    data object SecuritySettings : VmDestination("settings/security")

    companion object {
        /**
         * Order matters: this is the order shown in the navigation bar and rail.
         * Kept to five so the bar stays usable on a small phone; the remaining
         * sections are reached from Home and from within their parent section.
         */
        val primaryDestinations: List<Primary> = listOf(
            Home,
            Projects,
            Servers,
            Agent,
            Settings,
        )
    }
}

/** Sections that exist but are not yet reachable as their own tab. */
enum class VmSection(val title: String, val icon: ImageVector) {
    WORKSPACE("Workspace", Icons.Default.AccountTree),
    TERMINAL("Terminal", Icons.Default.Terminal),
    FILES("Files", Icons.Default.Folder),
    GIT("Git", Icons.Default.AccountTree),
    CONNECTORS("Connectors", Icons.Default.Cloud),
    TASKS("Tasks", Icons.AutoMirrored.Filled.List),
    ACTIVITY("Activity", Icons.Default.History),
}
