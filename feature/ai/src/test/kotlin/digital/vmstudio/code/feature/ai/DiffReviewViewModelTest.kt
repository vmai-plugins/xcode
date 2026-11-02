package digital.vmstudio.code.feature.ai

import androidx.lifecycle.SavedStateHandle
import digital.vmstudio.code.core.git.GitError
import digital.vmstudio.code.core.git.GitResult
import digital.vmstudio.code.core.git.GitService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiffReviewViewModelTest {

    @Test
    fun `failed staging stops commit and reports the error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gitService = mockk<GitService>(relaxed = true)
            coEvery {
                gitService.add("server", "/repo", listOf("/repo/file.txt"))
            } returns GitResult.Failure(GitError.Unknown("index is locked"))
            val viewModel = DiffReviewViewModel(
                gitService = gitService,
                savedStateHandle = SavedStateHandle(
                    mapOf(
                        DiffReviewViewModel.ARG_SERVER_ID to "server",
                        DiffReviewViewModel.ARG_FILE_PATH to "/repo/file.txt",
                    ),
                ),
            )

            viewModel.commitAndPush("Update file")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSaving)
            assertNotNull(viewModel.uiState.value.error)
            coVerify(exactly = 0) { gitService.commit("server", "/repo", "Update file") }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
