package digital.vmstudio.code.core.ai.execution

import android.content.Context
import digital.vmstudio.code.core.ai.background.AgentWakeLockManager
import digital.vmstudio.code.core.ai.model.AgentEvent
import digital.vmstudio.code.core.ai.repository.AgentConversationRepository
import digital.vmstudio.code.core.ai.repository.AgentTaskRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AgentExecutionCoordinatorTest {

    @Test
    fun `a completed run is removed from active runs`() = runBlocking {
        val wakeLockManager = mockk<AgentWakeLockManager>(relaxed = true)
        val released = CompletableDeferred<Unit>()
        every { wakeLockManager.release() } answers { released.complete(Unit) }
        val coordinator = coordinator(wakeLockManager)

        coordinator.submitRun("conversation", null, "finish") { emit ->
            emit(AgentEvent.AssistantMessage("done"))
        }

        released.await()
        assertFalse(coordinator.isRunning("conversation"))
    }

    @Test
    fun `a replaced run cannot remove its replacement`() = runBlocking {
        val wakeLockManager = mockk<AgentWakeLockManager>(relaxed = true)
        val releaseCount = AtomicInteger()
        val firstReleased = CompletableDeferred<Unit>()
        val bothReleased = CompletableDeferred<Unit>()
        every { wakeLockManager.release() } answers {
            when (releaseCount.incrementAndGet()) {
                1 -> firstReleased.complete(Unit)
                2 -> bothReleased.complete(Unit)
            }
        }
        val coordinator = coordinator(wakeLockManager)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()

        coordinator.submitRun("conversation", null, "first") {
            firstStarted.complete(Unit)
            awaitCancellation()
        }
        firstStarted.await()

        coordinator.submitRun("conversation", null, "second") {
            secondStarted.complete(Unit)
            finishSecond.await()
        }
        secondStarted.await()
        firstReleased.await()

        assertTrue(coordinator.isRunning("conversation"))

        finishSecond.complete(Unit)
        bothReleased.await()
        withTimeout(5_000) {
            while (coordinator.isRunning("conversation")) delay(1)
        }
        assertFalse(coordinator.isRunning("conversation"))
    }

    private fun coordinator(
        wakeLockManager: AgentWakeLockManager,
    ) = AgentExecutionCoordinator(
        context = mockk<Context>(relaxed = true),
        conversations = mockk<AgentConversationRepository>(relaxed = true),
        tasks = mockk<AgentTaskRepository>(relaxed = true),
        wakeLockManager = wakeLockManager,
    )
}
