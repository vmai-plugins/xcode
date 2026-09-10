package digital.vmstudio.code.core.sftp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathTest {

    @Test
    fun `normalise collapses redundant separators and dots`() {
        assertEquals("/var/www/app", RemotePath.normalise("/var//www/./app/"))
        assertEquals("/var/app", RemotePath.normalise("/var/www/../app"))
        assertEquals("/", RemotePath.normalise("/"))
        assertEquals("/", RemotePath.normalise("//"))
    }

    @Test
    fun `dotdot above an absolute root stays at the root`() {
        assertEquals("/", RemotePath.normalise("/.."))
        assertEquals("/etc", RemotePath.normalise("/../../etc"))
    }

    @Test
    fun `relative dotdot is preserved so an escape attempt stays visible`() {
        assertEquals("../etc/passwd", RemotePath.normalise("../etc/passwd"))
        assertEquals("../../a", RemotePath.normalise("../../a"))
    }

    @Test
    fun `name and parent behave at the root`() {
        assertEquals("app", RemotePath.name("/var/www/app"))
        assertEquals("app", RemotePath.name("/var/www/app/"))
        assertEquals("/", RemotePath.name("/"))
        assertEquals("/var/www", RemotePath.parent("/var/www/app"))
        assertEquals("/", RemotePath.parent("/var"))
        assertNull(RemotePath.parent("/"))
    }

    @Test
    fun `join ignores stray separators`() {
        assertEquals("/var/www/app", RemotePath.join("/var", "www", "app"))
        assertEquals("/var/www/app", RemotePath.join("/var/", "/www/", "/app"))
        assertEquals("/var", RemotePath.join("/var"))
    }

    @Test
    fun `resolve treats an absolute child as absolute`() {
        assertEquals("/etc/hosts", RemotePath.resolve("/var/www", "/etc/hosts"))
        assertEquals("/var/www/src", RemotePath.resolve("/var/www", "src"))
    }

    @Test
    fun `extension is empty when there is none`() {
        assertEquals("kt", RemotePath.extension("/src/Main.kt"))
        assertEquals("", RemotePath.extension("/src/Makefile"))
        assertEquals("", RemotePath.extension("/src/noext"))
    }

    // --- the sandbox boundary --------------------------------------------------

    @Test
    fun `confine allows paths inside the root`() {
        val root = "/var/www/app"

        assertEquals("$root/src/Main.kt", RemotePath.confine(root, "src/Main.kt"))
        assertEquals("$root/src", RemotePath.confine(root, "$root/src"))
        assertEquals(root, RemotePath.confine(root, "."))
    }

    @Test
    fun `confine rejects traversal out of the root`() {
        val root = "/var/www/app"

        assertNull(RemotePath.confine(root, "../other"))
        assertNull(RemotePath.confine(root, "../../etc/passwd"))
        assertNull(RemotePath.confine(root, "src/../../../etc/passwd"))
        assertNull(RemotePath.confine(root, "/etc/passwd"))
        assertNull(RemotePath.confine(root, "/root/.ssh/id_rsa"))
    }

    @Test
    fun `confine rejects a sibling whose name merely starts with the root`() {
        val root = "/var/www/app"

        assertNull(
            "/var/www/app-backup must not be treated as inside /var/www/app",
            RemotePath.confine(root, "/var/www/app-backup/secret"),
        )
    }

    @Test
    fun `confine handles a root of slash`() {
        assertEquals("/etc/hosts", RemotePath.confine("/", "/etc/hosts"))
        assertEquals("/etc/hosts", RemotePath.confine("/", "etc/hosts"))
    }

    @Test
    fun `confine tolerates a trailing separator on the root`() {
        assertEquals("/var/www/app/src", RemotePath.confine("/var/www/app/", "src"))
    }

    @Test
    fun `isInside agrees with confine`() {
        val root = "/srv/project"

        assertTrue(RemotePath.isInside(root, "lib/util.py"))
        assertTrue(RemotePath.isInside(root, root))
        assertFalse(RemotePath.isInside(root, "../elsewhere"))
    }

    @Test
    fun `relativise produces display paths`() {
        val root = "/var/www/app"

        assertEquals("src/Main.kt", RemotePath.relativise(root, "$root/src/Main.kt"))
        assertEquals(".", RemotePath.relativise(root, root))
        assertEquals("/etc/hosts", RemotePath.relativise(root, "/etc/hosts"))
    }

    // --- sensitive file detection ----------------------------------------------

    @Test
    fun `secret bearing files are recognised`() {
        listOf(
            "/app/.env",
            "/app/.env.production",
            "/home/deploy/.ssh/id_rsa",
            "/home/deploy/.ssh/id_ed25519",
            "/certs/server.pem",
            "/certs/server.key",
            "/app/credentials",
            "/home/deploy/.netrc",
            "/app/keystore.jks",
        ).forEach { path ->
            assertTrue("$path should be treated as sensitive", RemotePath.looksSensitive(path))
        }
    }

    @Test
    fun `ordinary source files are not sensitive`() {
        listOf(
            "/app/src/Main.kt",
            "/app/README.md",
            "/app/package.json",
            "/app/environment.ts",
        ).forEach { path ->
            assertFalse("$path should not be sensitive", RemotePath.looksSensitive(path))
        }
    }
}
