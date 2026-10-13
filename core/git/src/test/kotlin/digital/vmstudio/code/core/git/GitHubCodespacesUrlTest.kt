package digital.vmstudio.code.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GitHubCodespacesUrlTest {

    @Test
    fun `repository opens in its GitHub Codespaces create page`() {
        val repository = GitHubRepository(
            id = 1L,
            fullName = "owner/repo-name",
            description = null,
            cloneUrl = "https://github.com/owner/repo-name.git",
            defaultBranch = "main",
            isPrivate = false,
        )

        assertEquals(
            "https://github.com/codespaces/new?repo=owner%2Frepo-name",
            repository.codespacesUrl(),
        )
    }

    @Test
    fun `invalid repository names cannot create unsafe Codespaces URLs`() {
        val repository = GitHubRepository(
            id = 1L,
            fullName = "owner/repo?redirect=example.com",
            description = null,
            cloneUrl = "https://github.com/owner/repo.git",
            defaultBranch = "main",
            isPrivate = false,
        )

        assertThrows(IllegalArgumentException::class.java) { repository.codespacesUrl() }
    }
}
