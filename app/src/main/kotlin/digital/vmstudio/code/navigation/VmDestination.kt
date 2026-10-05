package digital.vmstudio.code.navigation

/**
 * Every screen the app can navigate to.
 *
 * Routes are declared centrally in the app module rather than inside features, so
 * feature modules stay independent of one another: a feature exposes composables,
 * and only this graph knows how they connect.
 */
sealed class VmDestination(val route: String) {

    /** Start screen: resolves to a fresh chat on the most recently used server. */
    data object NewChat : VmDestination("new")

    data object Servers : VmDestination("servers")

    data object Projects : VmDestination("projects")

    data object Settings : VmDestination("settings")

    /** Every stored conversation, with delete; the drawer only shows the latest few. */
    data object Chats : VmDestination("chats")

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

    data class ProjectDetail(val projectId: String) : VmDestination(routeFor(projectId)) {
        companion object {
            const val ARG_PROJECT_ID = "projectId"
            const val ROUTE_PATTERN = "projects/{$ARG_PROJECT_ID}"
            fun routeFor(projectId: String) = "projects/$projectId"
        }
    }

    data object Connectors : VmDestination("connectors")

    data object Tasks : VmDestination("tasks")
}
