package digital.vmstudio.code.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPathTest {
    @Test
    fun `normalises file paths without losing root`() {
        assertEquals("/workspace", ProjectPath.normalise("/workspace/"))
        assertEquals("/", ProjectPath.normalise("/"))
    }

    @Test
    fun `preserves SAF tree URI root and nested path`() {
        val root = "content://com.android.externalstorage.documents/tree/primary%3ASource!/"
        assertTrue(ProjectPath.isTreeUri(root))
        assertEquals(root, ProjectPath.normalise(root))
        assertEquals(root + "src/main.kt", ProjectPath.normalise("${root}src/main.kt/"))
        assertFalse(ProjectPath.isTreeUri("content://not-a-tree"))
    }
}
