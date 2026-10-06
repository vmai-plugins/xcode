package digital.vmstudio.code.feature.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Git prints repo-relative paths; the agent reports absolute ones. */
class DiffPathTest {

    @Test
    fun `an absolute path matches git's repo-relative name`() {
        assertTrue("/srv/app/src/Main.kt".isSamePath("src/Main.kt"))
    }

    @Test
    fun `a different file with the same tail name does not match`() {
        assertFalse("/srv/app/src/OtherMain.kt".isSamePath("Main.kt"))
        assertFalse("/srv/app/src/Main.kt".isSamePath(null))
    }
}
