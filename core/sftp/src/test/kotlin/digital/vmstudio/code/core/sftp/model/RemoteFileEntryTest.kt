package digital.vmstudio.code.core.sftp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteFileEntryTest {

    private fun entry(
        name: String,
        isDirectory: Boolean = false,
        size: Long = 100,
        modified: Long = 1000,
    ) = RemoteFileEntry(
        name = name,
        path = "/var/www/$name",
        type = if (isDirectory) RemoteFileType.DIRECTORY else RemoteFileType.FILE,
        sizeBytes = size,
        modifiedAtSeconds = modified,
        permissions = RemotePermissions(0b111101101),
        uid = 1000,
        gid = 1000,
    )

    @Test
    fun `sortedFor filters out dot and dotdot even when showHidden is true`() {
        val entries = listOf(
            entry("."),
            entry(".."),
            entry(".env"),
            entry("src", isDirectory = true),
            entry("build.gradle"),
        )

        val hiddenShown = entries.sortedFor(RemoteListingOptions(showHidden = true))
        val namesHiddenShown = hiddenShown.map { it.name }
        assertFalse(namesHiddenShown.contains("."))
        assertFalse(namesHiddenShown.contains(".."))
        assertTrue(namesHiddenShown.contains(".env"))
        assertTrue(namesHiddenShown.contains("src"))
        assertTrue(namesHiddenShown.contains("build.gradle"))

        val hiddenHidden = entries.sortedFor(RemoteListingOptions(showHidden = false))
        val namesHiddenHidden = hiddenHidden.map { it.name }
        assertFalse(namesHiddenHidden.contains("."))
        assertFalse(namesHiddenHidden.contains(".."))
        assertFalse(namesHiddenHidden.contains(".env"))
        assertTrue(namesHiddenHidden.contains("src"))
        assertTrue(namesHiddenHidden.contains("build.gradle"))
    }

    @Test
    fun `sortedFor puts directories first when requested`() {
        val entries = listOf(
            entry("z_file.txt", isDirectory = false),
            entry("a_dir", isDirectory = true),
        )

        val result = entries.sortedFor(RemoteListingOptions(directoriesFirst = true))
        assertEquals("a_dir", result[0].name)
        assertEquals("z_file.txt", result[1].name)
    }
}
