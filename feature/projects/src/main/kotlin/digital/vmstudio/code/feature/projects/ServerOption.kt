package digital.vmstudio.code.feature.projects

import digital.vmstudio.code.core.ssh.model.Server

/** A server as the project screens need it: enough to choose and to label. */
data class ServerOption(val id: String, val name: String, val target: String)

/**
 * The single place `Server -> ServerOption` is mapped.
 *
 * Both the project list (for labelling a project's server) and the create screen
 * (for choosing one) needed this; it was copy-pasted into each ViewModel until now.
 */
fun List<Server>.toServerOptions(): List<ServerOption> =
    map { ServerOption(id = it.id, name = it.name, target = it.displayTarget) }
