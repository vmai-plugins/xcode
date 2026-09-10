package digital.vmstudio.code.core.ssh.repository

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.model.Server
import digital.vmstudio.code.core.ssh.model.ServerDraft
import digital.vmstudio.code.core.ssh.model.ServerGroup
import kotlinx.coroutines.flow.Flow

/**
 * Owns server configuration and the lifecycle of the secrets each server needs.
 *
 * Saving a draft is a two-store operation: the non-secret fields go to Room and the
 * secrets go to the Keystore-backed credential store. This interface is the only
 * place those two are coordinated, so a secret can never be orphaned by a failed
 * save or left behind by a delete.
 */
interface ServerRepository {

    val servers: Flow<List<Server>>

    val groups: Flow<List<ServerGroup>>

    fun observe(serverId: String): Flow<Server?>

    suspend fun get(serverId: String): VmResult<Server>

    /**
     * Validates, stores secrets, then persists the server. Returns the saved
     * [Server]; the caller should clear the draft's secret fields immediately after.
     */
    suspend fun save(draft: ServerDraft): VmResult<Server>

    /** Deletes the server and every credential it owns. */
    suspend fun delete(serverId: String): VmResult<Unit>

    suspend fun markConnected(serverId: String): VmResult<Unit>

    suspend fun saveGroup(group: ServerGroup): VmResult<Unit>

    suspend fun deleteGroup(groupId: String): VmResult<Unit>

    /** Populates a draft from a saved server, with secret fields left empty. */
    suspend fun draftFor(serverId: String): VmResult<ServerDraft>
}
