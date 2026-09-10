package digital.vmstudio.code.core.ai.repository

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.database.dao.AgentTaskDao
import digital.vmstudio.code.core.database.dao.ProjectDao
import digital.vmstudio.code.core.database.entity.AgentTaskEntity
import digital.vmstudio.code.core.database.entity.AgentTaskStatus
import digital.vmstudio.code.core.database.entity.ProjectEntity
import digital.vmstudio.code.core.database.entity.ProjectLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Uses hand-rolled in-memory DAO fakes so the read-modify-write bookkeeping rules
 * (dedupe, caps, status transitions, missing-row failures) are tested against the
 * same shapes Room would return, without an instrumented database.
 */
class AgentTaskRepositoryTest {

    private val taskStore = LinkedHashMap<String, AgentTaskEntity>()
    private val taskFlow = MutableStateFlow<List<AgentTaskEntity>>(emptyList())
    private val projects = MutableStateFlow<List<ProjectEntity>>(emptyList())

    private val dao = FakeAgentTaskDao()
    private val projectDao = FakeProjectDao()
    private val repository = DefaultAgentTaskRepository(dao, projectDao, Dispatchers.Unconfined)

    private fun publish() {
        taskFlow.value = taskStore.values.toList()
    }

    private fun seedProject(id: String, serverId: String, remotePath: String) {
        projects.value = projects.value + ProjectEntity(
            id = id,
            name = "Project $id",
            location = ProjectLocation.REMOTE_SSH,
            serverId = serverId,
            remotePath = remotePath,
            projectTypeId = "web",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
    }

    private suspend fun seedTask(id: String) {
        dao.upsert(
            AgentTaskEntity(
                id = id,
                projectId = "p1",
                conversationId = "c1",
                title = "Fix login",
                status = AgentTaskStatus.RUNNING,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            ),
        )
    }

    private inner class FakeAgentTaskDao : AgentTaskDao {
        override fun observeAll(): Flow<List<AgentTaskEntity>> = taskFlow

        override fun observeForProject(projectId: String): Flow<List<AgentTaskEntity>> =
            taskFlow.map { list -> list.filter { it.projectId == projectId } }

        override fun observeByStatus(statuses: List<AgentTaskStatus>): Flow<List<AgentTaskEntity>> =
            taskFlow.map { list -> list.filter { it.status in statuses } }

        override fun observeById(id: String): Flow<AgentTaskEntity?> =
            taskFlow.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun getById(id: String): AgentTaskEntity? = taskStore[id]

        override suspend fun upsert(task: AgentTaskEntity) {
            taskStore[task.id] = task
            publish()
        }

        override suspend fun updateStatus(id: String, status: AgentTaskStatus, timestamp: Long) {
            taskStore[id]?.let {
                taskStore[id] = it.copy(status = status, updatedAtMillis = timestamp)
            }
            publish()
        }

        override suspend fun deleteById(id: String) {
            taskStore.remove(id)
            publish()
        }

        /** Mirrors the real DAO: in-flight rows become FAILED with an explicit reason. */
        override suspend fun markInterrupted(statuses: List<AgentTaskStatus>, timestamp: Long) {
            taskStore.values
                .filter { it.status in statuses }
                .forEach {
                    taskStore[it.id] = it.copy(
                        status = AgentTaskStatus.FAILED,
                        errorText = "Interrupted: the app stopped before the task finished",
                        updatedAtMillis = timestamp,
                    )
                }
            publish()
        }
    }

    private inner class FakeProjectDao : ProjectDao {
        override fun observeAll(): Flow<List<ProjectEntity>> = projects

        override fun observeById(id: String): Flow<ProjectEntity?> =
            projects.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun getById(id: String): ProjectEntity? =
            projects.value.firstOrNull { it.id == id }

        override fun observeByServer(serverId: String): Flow<List<ProjectEntity>> =
            projects.map { list -> list.filter { it.serverId == serverId } }

        override fun observeCountForServer(serverId: String): Flow<Int> =
            projects.map { list -> list.count { it.serverId == serverId } }

        override fun observeRecent(limit: Int): Flow<List<ProjectEntity>> = projects

        override suspend fun upsert(project: ProjectEntity) {
            projects.value = projects.value.filterNot { it.id == project.id } + project
        }

        override suspend fun deleteById(id: String) {
            projects.value = projects.value.filterNot { it.id == id }
        }

        override suspend fun markOpened(id: String, timestamp: Long) = Unit

        override suspend fun updateBranch(id: String, branch: String?, timestamp: Long) = Unit

        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
    }

    @Test
    fun `start creates running row and returns its id`() = runTest {
        seedProject("p1", "s1", "/srv/app")
        val projectId = repository.findProjectId("s1", "/srv/app")

        val taskId = repository.start("c1", "Fix the login bug", projectId)

        assertEquals("p1", projectId)
        assertTrue(!taskId.isNullOrBlank())
        assertEquals(AgentTaskStatus.RUNNING, taskStore[taskId]?.status)
        assertEquals("Fix the login bug", taskStore[taskId]?.title)
    }

    @Test
    fun `start without project returns null and records nothing`() = runTest {
        assertNull(repository.start("c1", "Fix", null))
        assertTrue(taskStore.isEmpty())
    }

    @Test
    fun `start truncates title to first line`() = runTest {
        val taskId = repository.start("c1", "First line\nsecond line", "p1")
        assertEquals("First line", taskStore[taskId]?.title)
    }

    @Test
    fun `recordChangedFile deduplicates`() = runTest {
        seedTask("t1")
        repository.recordChangedFile("t1", "/a.php")
        repository.recordChangedFile("t1", "/a.php")

        val files = taskStore["t1"]?.let { ChangedFilesCodec.decode(it.changedFilesJson) }
        assertEquals(listOf("/a.php"), files)
    }

    @Test
    fun `complete sets status and token counts`() = runTest {
        seedTask("t1")
        repository.complete("t1", promptTokens = 10L, completionTokens = 20L)

        val task = taskStore["t1"]!!
        assertEquals(AgentTaskStatus.COMPLETED, task.status)
        assertEquals(10L, task.promptTokens)
        assertEquals(20L, task.completionTokens)
        assertNull(task.errorText)
    }

    @Test
    fun `update on missing row reports storage failure`() = runTest {
        val result = repository.complete("missing", 0L, 0L)

        assertTrue(result is VmResult.Failure)
    }

    @Test
    fun `cancel marks stopped with reason`() = runTest {
        seedTask("t1")
        repository.cancel("t1")

        val task = taskStore["t1"]!!
        assertEquals(AgentTaskStatus.CANCELLED, task.status)
        assertTrue(task.errorText!!.isNotBlank())
    }

    @Test
    fun `changedFilesCodec roundtrips and caps size`() = runTest {
        val many = (1..60).map { "/file$it" }
        assertEquals(50, ChangedFilesCodec.decode(ChangedFilesCodec.encode(many)).size)
    }

    @Test
    fun `changedFilesCodec tolerates corrupt input`() = runTest {
        assertEquals(emptyList<String>(), ChangedFilesCodec.decode("not json"))
    }

    @Test
    fun `delete removes the row`() = runTest {
        seedTask("t1")
        repository.delete("t1")

        assertTrue(taskStore.isEmpty())
    }

    @Test
    fun `findProjectId matches on server and path`() = runTest {
        seedProject("p1", "s1", "/srv/app")

        assertEquals("p1", repository.findProjectId("s1", "/srv/app"))
        assertNull(repository.findProjectId("s1", "/other"))
        assertNull(repository.findProjectId("missing", "/srv/app"))
    }

    @Test
    fun `serverIdFor resolves through the project`() = runTest {
        seedProject("p1", "s1", "/srv/app")

        assertEquals("s1", repository.serverIdFor("p1"))
        assertNull(repository.serverIdFor("missing"))
        assertNull(repository.serverIdFor(null))
    }

    @Test
    fun `recordToolCall increments counter`() = runTest {
        seedTask("t1")
        repository.recordToolCall("t1")
        repository.recordToolCall("t1")

        assertEquals(2, taskStore["t1"]?.commandCount)
    }
}
