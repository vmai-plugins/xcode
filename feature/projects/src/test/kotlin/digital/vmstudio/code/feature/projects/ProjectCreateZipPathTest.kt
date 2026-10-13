package digital.vmstudio.code.feature.projects

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectCreateZipPathTest {

    @Test
    fun `zip paths stay inside the staging directory`() {
        val root = Files.createTempDirectory("zip-path-test").toFile()
        try {
            assertEquals(File(root, "src/Main.kt").canonicalFile, safeZipEntryFile(root, "src/Main.kt"))
            assertNull(safeZipEntryFile(root, "../outside.txt"))
            assertNull(safeZipEntryFile(root, "src/../../outside.txt"))
            assertNull(safeZipEntryFile(root, "/absolute.txt"))
            assertNull(safeZipEntryFile(root, "C:/outside.txt"))
            assertNull(safeZipEntryFile(root, "src\\outside.txt"))
        } finally {
            root.deleteRecursively()
        }
    }
}
