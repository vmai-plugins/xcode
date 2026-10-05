package digital.vmstudio.code.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import digital.vmstudio.code.core.database.entity.KnownHostKeyEntity
import digital.vmstudio.code.core.database.entity.ServerEntity
import digital.vmstudio.code.core.database.entity.ServerGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM server ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM server WHERE id = :id")
    fun observeById(id: String): Flow<ServerEntity?>

    @Query("SELECT * FROM server WHERE id = :id")
    suspend fun getById(id: String): ServerEntity?

    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Update
    suspend fun update(server: ServerEntity)

    @Delete
    suspend fun delete(server: ServerEntity)

    @Query("DELETE FROM server WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE server SET lastConnectedAtMillis = :timestamp WHERE id = :id")
    suspend fun markConnected(id: String, timestamp: Long)

    /** Reports the credential references a server owns so they can be cleaned up. */
    @Query(
        """
        SELECT passwordCredentialId FROM server WHERE id = :id AND passwordCredentialId IS NOT NULL
        UNION ALL
        SELECT privateKeyCredentialId FROM server WHERE id = :id AND privateKeyCredentialId IS NOT NULL
        UNION ALL
        SELECT passphraseCredentialId FROM server WHERE id = :id AND passphraseCredentialId IS NOT NULL
        """,
    )
    suspend fun credentialIdsFor(id: String): List<String>

    @Query("SELECT * FROM server_group ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeGroups(): Flow<List<ServerGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(group: ServerGroupEntity)

    @Query("DELETE FROM server_group WHERE id = :id")
    suspend fun deleteGroup(id: String)
}

@Dao
interface KnownHostKeyDao {

    @Query("SELECT * FROM known_host_key WHERE host = :host AND port = :port")
    suspend fun findForHost(host: String, port: Int): List<KnownHostKeyEntity>

    @Query(
        "SELECT * FROM known_host_key WHERE host = :host AND port = :port AND keyType = :keyType LIMIT 1",
    )
    suspend fun find(host: String, port: Int, keyType: String): KnownHostKeyEntity?

    @Query("SELECT * FROM known_host_key ORDER BY host ASC")
    fun observeAll(): Flow<List<KnownHostKeyEntity>>

    @Upsert
    suspend fun upsert(entity: KnownHostKeyEntity)

    @Query("UPDATE known_host_key SET lastSeenAtMillis = :timestamp WHERE id = :id")
    suspend fun touch(id: String, timestamp: Long)

    @Query("DELETE FROM known_host_key WHERE host = :host AND port = :port")
    suspend fun forget(host: String, port: Int)

    @Query(
        "DELETE FROM known_host_key WHERE host = :host AND port = :port AND keyType = :keyType",
    )
    suspend fun forgetType(host: String, port: Int, keyType: String)

    @Query("DELETE FROM known_host_key")
    suspend fun forgetAll()

    /**
     * Records the user's acceptance of a key for one algorithm, replacing any prior
     * key of the *same* algorithm for that host. Keys of other algorithms are left
     * in place: a host legitimately serves several (RSA and ed25519, say), and
     * wiping the lot on every acceptance would make a multi-key host prompt on every
     * other connection as negotiation alternated between them.
     *
     * One transaction so a crash cannot leave the host with no trusted key of this
     * type while the old one is already gone.
     */
    @Transaction
    suspend fun replaceTrusted(entity: KnownHostKeyEntity) {
        forgetType(entity.host, entity.port, entity.keyType)
        upsert(entity)
    }
}
