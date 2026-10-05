package digital.vmstudio.code.core.common.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class FolderMemoryTest {

    @Test
    fun `folders survive a round trip`() {
        val folders = mapOf("srv-1" to "/home/vmstudio", "srv-2" to "/srv/my app")
        assertEquals(folders, parseFolders(formatFolders(folders)))
    }

    @Test
    fun `entries that cannot be stored are skipped, not corrupting others`() {
        val folders = mapOf("srv-1" to "/a", "bad\tid" to "/b", "srv-3" to "/c\nd")
        assertEquals(mapOf("srv-1" to "/a"), parseFolders(formatFolders(folders)))
    }

    @Test
    fun `nothing stored reads as empty`() {
        assertEquals(emptyMap<String, String>(), parseFolders(null))
        assertEquals(emptyMap<String, String>(), parseFolders("garbage"))
    }
}
