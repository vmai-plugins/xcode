package digital.vmstudio.code.core.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitParsersTest {

    @Test
    fun parseStatus_detectsMergeConflicts_includingBothAddedAndBothDeleted() {
        val porcelain = """
            ## main...origin/main [ahead 1, behind 2]
            UU src/App.kt
            AA src/BothAdded.kt
            DD src/BothDeleted.kt
            AU src/AddedByUs.kt
            UD src/DeletedByThem.kt
            M  staged/Modified.kt
             M unstaged/Modified.kt
            ?? untracked/New.kt
        """.trimIndent()

        val status = GitParsers.parseStatus(porcelain, "main")

        assertEquals("main", status.branch)
        assertEquals("origin/main", status.upstream)
        assertEquals(1, status.aheadCount)
        assertEquals(2, status.behindCount)

        val conflictPaths = status.unstaged.filter { it.status == GitFileStatus.CONFLICTED }.map { it.path }
        assertTrue("UU should be conflict", conflictPaths.contains("src/App.kt"))
        assertTrue("AA should be conflict", conflictPaths.contains("src/BothAdded.kt"))
        assertTrue("DD should be conflict", conflictPaths.contains("src/BothDeleted.kt"))
        assertTrue("AU should be conflict", conflictPaths.contains("src/AddedByUs.kt"))
        assertTrue("UD should be conflict", conflictPaths.contains("src/DeletedByThem.kt"))

        assertEquals(1, status.staged.size)
        assertEquals("staged/Modified.kt", status.staged[0].path)
        assertEquals(GitFileStatus.MODIFIED, status.staged[0].status)
    }

    @Test
    fun parseStatus_extractsBranchFromHeader_whenCallerPassesHeadOrBlank() {
        val porcelain = """
            ## feature/login...origin/feature/login
             M MainActivity.kt
        """.trimIndent()

        val status = GitParsers.parseStatus(porcelain, "HEAD")
        assertEquals("feature/login", status.branch)
    }
}
