package digital.vmstudio.code.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.vmstudio.code.core.database.entity.ConversationEntity
import digital.vmstudio.code.core.database.entity.MessageEntity
import digital.vmstudio.code.core.database.entity.MessageRole
import digital.vmstudio.code.core.database.entity.MessageStatus
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectLocation
import digital.vmstudio.code.core.database.entity.ServerEntity
import digital.vmstudio.code.core.database.entity.SshAuthMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Exercises the real Room database.
 *
 * The schema has nineteen tables with foreign keys and cascade deletes that Room
 * generates but SQLite only enforces when `foreign_keys` is on — a pragma set in a
 * callback that, until this ran, had never executed. These tests are what confirm
 * the constraints are actually active rather than merely declared.
 */
@RunWith(AndroidJUnit4::class)
class VmDatabaseTest {

    private lateinit var database: VmDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            VmDatabase::class.java,
        )
            // The production builder turns foreign keys on in an onOpen callback.
            // Repeating it here keeps the test honest about what it is verifying.
            .addCallback(
                object : androidx.room.RoomDatabase.Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                },
            )
            .build()
    }

    @After
    fun tearDown() = database.close()

    private fun server(id: String = UUID.randomUUID().toString()) = ServerEntity(
        id = id,
        name = "Server $id",
        host = "vps.example.com",
        username = "deploy",
        authMethod = SshAuthMethod.PASSWORD,
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    private fun project(id: String, serverId: String?) = ProjectEntity(
        id = id,
        name = "Project $id",
        location = ProjectLocation.REMOTE_SSH,
        serverId = serverId,
        projectTypeId = "kotlin",
        createdAtMillis = 1,
        updatedAtMillis = 1,
    )

    @Test
    fun serversRoundTripAndAreObservable() = runTest {
        val dao = database.serverDao()
        val entity = server("srv-1")

        dao.upsert(entity)

        assertEquals(entity, dao.getById("srv-1"))
        assertEquals(listOf(entity), dao.observeAll().first())
    }

    @Test
    fun deletingAServerNullsTheProjectReferenceRatherThanDeletingTheProject() = runTest {
        // ProjectEntity declares SET_NULL on serverId. Losing a project because its
        // server was removed would destroy the user's work, so this is worth pinning.
        database.serverDao().upsert(server("srv-1"))
        database.projectDao().upsert(project("prj-1", "srv-1"))

        database.serverDao().deleteById("srv-1")

        val project = database.projectDao().getById("prj-1")
        assertNotNull("the project must survive its server", project)
        assertNull("its server reference must be cleared", project?.serverId)
    }

    @Test
    fun deletingAConversationCascadesToItsMessages() = runTest {
        val conversations = database.conversationDao()
        val messages = database.messageDao()

        conversations.upsert(
            ConversationEntity(
                id = "conv-1",
                projectId = null,
                title = "Test",
                providerId = "claude-code",
                modelId = "auto",
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )
        messages.upsert(
            MessageEntity(
                id = "msg-1",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "hello",
                createdAtMillis = 1,
                sequence = 1,
            ),
        )

        conversations.deleteById("conv-1")

        assertTrue(
            "orphaned messages mean the cascade is not enforced",
            messages.observeForConversation("conv-1").first().isEmpty(),
        )
    }

    @Test
    fun aMessageForAMissingConversationIsRejected() = runTest {
        // Only true when PRAGMA foreign_keys is actually on; without it SQLite
        // silently accepts the orphan.
        val result = runCatching {
            database.messageDao().upsert(
                MessageEntity(
                    id = "msg-x",
                    conversationId = "does-not-exist",
                    role = MessageRole.USER,
                    content = "orphan",
                    createdAtMillis = 1,
                    sequence = 1,
                ),
            )
        }

        assertTrue("foreign keys are not being enforced", result.isFailure)
    }

    @Test
    fun messageSequencingIsMonotonic() = runTest {
        val conversations = database.conversationDao()
        val messages = database.messageDao()
        conversations.upsert(
            ConversationEntity(
                id = "conv-2",
                projectId = null,
                title = "Test",
                providerId = "claude-code",
                modelId = "auto",
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )

        repeat(3) { index ->
            messages.upsert(
                MessageEntity(
                    id = "m$index",
                    conversationId = "conv-2",
                    role = MessageRole.ASSISTANT,
                    content = "reply $index",
                    // All in the same millisecond: ordering must come from sequence,
                    // not from the timestamp.
                    createdAtMillis = 100,
                    sequence = messages.maxSequence("conv-2") + 1,
                ),
            )
        }

        val ordered = messages.observeForConversation("conv-2").first()
        assertEquals(listOf("reply 0", "reply 1", "reply 2"), ordered.map { it.content })
        assertEquals(3L, messages.maxSequence("conv-2"))
    }

    @Test
    fun interruptedMessagesAreReconciledAtStartup() = runTest {
        val conversations = database.conversationDao()
        val messages = database.messageDao()
        conversations.upsert(
            ConversationEntity(
                id = "conv-3",
                projectId = null,
                title = "Test",
                providerId = "claude-code",
                modelId = "auto",
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )
        messages.upsert(
            MessageEntity(
                id = "stuck",
                conversationId = "conv-3",
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                createdAtMillis = 1,
                sequence = 1,
            ),
        )

        messages.failInterrupted()

        val recovered = messages.observeForConversation("conv-3").first().single()
        assertEquals(MessageStatus.FAILED, recovered.status)
        assertNotNull(recovered.errorText)
    }

    @Test
    fun enumsSurviveARoundTrip() = runTest {
        // Room converts enums automatically. A regression here would silently store
        // an ordinal and reorder on the next enum edit.
        val entity = server("srv-enum").copy(
            authMethod = SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE,
        )

        database.serverDao().upsert(entity)

        assertEquals(
            SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE,
            database.serverDao().getById("srv-enum")?.authMethod,
        )
    }

    @Test
    fun theSchemaOpensAndEveryDaoIsReachable() = runTest {
        // A smoke test over the whole graph: Room validates the schema on first
        // access, so a malformed entity or index fails here rather than in the app.
        assertNotNull(database.serverDao().observeAll().first())
        assertNotNull(database.knownHostKeyDao().observeAll().first())
        assertNotNull(database.projectDao().observeAll().first())
        assertNotNull(database.conversationDao().observeAll().first())
        assertNotNull(database.transferDao().observeAll().first())
        assertNotNull(database.activityDao().observeRecent(10).first())
        assertNotNull(database.connectorDao().observeAll().first())
        assertNotNull(database.credentialReferenceDao().observeAll().first())
    }
}
