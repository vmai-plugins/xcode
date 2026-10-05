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
        assertNotNull(PathGuard.problem(wd, "certs/server.pem"))
        assertNull(PathGuard.problem(wd, ".env.example"))
    }

    @Test
    fun `normalize collapses dots`() {
        assertEquals("/a/c", PathGuard.normalize("/a/b/../c/./"))
        assertEquals("/", PathGuard.normalize("/.."))
    }
}
