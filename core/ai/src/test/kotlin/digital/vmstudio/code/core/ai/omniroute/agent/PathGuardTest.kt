package digital.vmstudio.code.core.ai.omniroute.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PathGuardTest {

    private val wd = "/srv/app"

    @Test
    fun `paths inside the project are allowed`() {
        assertNull(PathGuard.problem(wd, "src/Main.kt"))
        assertNull(PathGuard.problem(wd, "/srv/app/README.md"))
        assertNull(PathGuard.problem(wd, "."))
        assertNull(PathGuard.problem(wd, "a/../b.txt"))
    }

    @Test
    fun `escaping the project is refused`() {
        assertNotNull(PathGuard.problem(wd, "../other/file"))
        assertNotNull(PathGuard.problem(wd, "/etc/passwd"))
        assertNotNull(PathGuard.problem(wd, "/srv/app-secrets/x"))
        assertNotNull(PathGuard.problem(wd, "~/.bashrc"))
    }

    @Test
    fun `credentials inside the project are refused`() {
        assertNotNull(PathGuard.problem(wd, ".env"))
        assertNotNull(PathGuard.problem(wd, "config/.env.production"))
        assertNotNull(PathGuard.problem(wd, ".ssh/id_rsa"))
        assertNotNull(PathGuard.problem(wd, ".SSH/config"))
        assertNotNull(PathGuard.problem(wd, "certs/server.pem"))
        assertNull(PathGuard.problem(wd, ".env.example"))
    }

    @Test
    fun `normalize collapses dots`() {
        assertEquals("/a/c", PathGuard.normalize("/a/b/../c/./"))
        assertEquals("/", PathGuard.normalize("/.."))
    }

    @Test
    fun `SAF folders resolve child paths and reject escaping or other trees`() {
        val root = "content://com.android.externalstorage.documents/tree/primary%3AWorkspace!/"
        assertNull(PathGuard.problem(root, "src/Main.kt"))
        assertEquals(root + "src/Main.kt", PathGuard.resolve(root, "src/Main.kt"))
        assertNotNull(PathGuard.problem(root, "../outside.txt"))
        assertNotNull(PathGuard.problem(root, "/etc/passwd"))
        assertNotNull(
            PathGuard.problem(
                root,
                "content://com.android.externalstorage.documents/tree/primary%3AOther!/secret.txt",
            ),
        )
    }

    @Test
    fun `SAF paths keep secret-file restrictions`() {
        val root = "content://com.android.externalstorage.documents/tree/primary%3AWorkspace!/"
        assertNotNull(PathGuard.problem(root, ".env"))
        assertNotNull(PathGuard.problem(root, "keys/id_ed25519"))
        assertNull(PathGuard.problem(root, ".env.example"))
    }

    @Test
    fun `SAF absolute paths stay inside a nested project folder`() {
        val tree = "content://com.android.externalstorage.documents/tree/primary%3AWorkspace!/"
        val root = tree + "projects/app/"
        assertNull(PathGuard.problem(root, tree + "projects/app/src/Main.kt"))
        assertNotNull(PathGuard.problem(root, tree + "projects/other/README.md"))
        assertNotNull(PathGuard.problem(root, tree + "projects/app/../../outside.txt"))
    }

    @Test
    fun `content URIs are rejected in POSIX workspaces`() {
        val posixRoot = "/home/ubuntu/app"
        val contentUri = "content://com.android.externalstorage.documents/tree/primary%3AWorkspace!/file.txt"
        assertNotNull(PathGuard.problem(posixRoot, contentUri))
        assertNull(PathGuard.resolve(posixRoot, contentUri))
    }

    @Test
    fun `windows paths are accepted and confined in Windows workspaces`() {
        val winRoot = "D:\\Dev\\x-codes"
        assertNull(PathGuard.problem(winRoot, "src/Main.kt"))
        assertNull(PathGuard.problem(winRoot, "D:\\Dev\\x-codes\\README.md"))
        assertNull(PathGuard.problem(winRoot, "D:/Dev/x-codes/README.md"))
        assertEquals("/D:/Dev/x-codes/src/Main.kt", PathGuard.resolve(winRoot, "src/Main.kt"))
        assertEquals("/D:/Dev/x-codes/README.md", PathGuard.resolve(winRoot, "D:\\Dev\\x-codes\\README.md"))

        assertNotNull(PathGuard.problem(winRoot, "../outside.txt"))
        assertNotNull(PathGuard.problem(winRoot, "C:\\Users\\tripc\\secret.txt"))
        assertNotNull(PathGuard.problem(winRoot, ".env"))
        assertNull(PathGuard.resolve(winRoot, "C:\\Users\\tripc\\secret.txt"))
    }
}
