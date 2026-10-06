package digital.vmstudio.code.feature.files

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.sftp.model.RemoteFileEntry
import digital.vmstudio.code.core.sftp.model.RemoteFileType
import digital.vmstudio.code.core.sftp.model.RemotePermissions
import digital.vmstudio.code.core.sftp.model.RemoteSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesLogicTest {

    private fun entry(name: String, size: Long = 10, type: RemoteFileType = RemoteFileType.FILE) =
        RemoteFileEntry(
            name = name,
            path = "/srv/$name",
            type = type,
            sizeBytes = size,
            modifiedAtSeconds = 0,
            permissions = RemotePermissions.DEFAULT_FILE,
            uid = 0,
            gid = 0,
        )

    /** A loaded folder holding one file, as the live tick finds it. */
    private fun listed() =
        FilesUiState(serverId = "s", path = "/srv", entries = listOf(entry("a")), isLoading = false)

    @Test
    fun `known text extensions preview as text up to the ceiling`() {
        assertEquals(PreviewKind.TEXT, previewKindFor("app.kt", 1_000))
        assertEquals(PreviewKind.TEXT, previewKindFor("CONFIG.YML", TEXT_PREVIEW_MAX_BYTES))
        assertEquals(PreviewKind.NONE, previewKindFor("huge.log", TEXT_PREVIEW_MAX_BYTES + 1))
    }

    @Test
    fun `extensionless well-known names are text`() {
        assertEquals(PreviewKind.TEXT, previewKindFor("Dockerfile", 2 * TEXT_SNIFF_MAX_BYTES))
        assertEquals(PreviewKind.TEXT, previewKindFor(".gitignore", 100))
    }

    @Test
    fun `images preview as images unless too large`() {
        assertEquals(PreviewKind.IMAGE, previewKindFor("shot.PNG", 50_000))
        assertEquals(PreviewKind.IMAGE, previewKindFor("a.webp", 1))
        assertEquals(PreviewKind.NONE, previewKindFor("a.jpg", IMAGE_PREVIEW_MAX_BYTES + 1))
    }

    @Test
    fun `unknown types are sniffed as text only when small`() {
        assertEquals(PreviewKind.TEXT, previewKindFor("notes", TEXT_SNIFF_MAX_BYTES))
        assertEquals(PreviewKind.NONE, previewKindFor("blob.dat", TEXT_SNIFF_MAX_BYTES + 1))
    }

    @Test
    fun `binaries never preview however small`() {
        assertEquals(PreviewKind.NONE, previewKindFor("app.jar", 10))
        assertEquals(PreviewKind.NONE, previewKindFor("backup.tar", 10))
    }

    @Test
    fun `directories never preview`() {
        assertEquals(PreviewKind.NONE, entry("src.txt", type = RemoteFileType.DIRECTORY).previewKind())
    }

    @Test
    fun `filter matches names case-insensitively and blank shows all`() {
        val entries = listOf(entry("Main.kt"), entry("build.gradle"), entry("README.md"))
        assertEquals(listOf("Main.kt"), filterEntries(entries, "main").map { it.name })
        assertEquals(listOf("README.md"), filterEntries(entries, "  read ").map { it.name })
        assertSame(entries, filterEntries(entries, "   "))
        assertTrue(filterEntries(entries, "zzz").isEmpty())
    }

    @Test
    fun `unchanged listing reports no change`() {
        val current = listOf(entry("a"), entry("b"))
        assertNull(changedListing(current, listOf(entry("a"), entry("b"))))
        val fresh = listOf(entry("a"), entry("b", size = 99))
        assertSame(fresh, changedListing(current, fresh))
    }

    @Test
    fun `selection keeps only paths still present`() {
        val pruned = pruneSelection(setOf("/srv/a", "/srv/gone"), listOf(entry("a"), entry("b")))
        assertEquals(setOf("/srv/a"), pruned)
    }

    @Test
    fun `default server is the most recently connected, else the first`() {
        val never = listOf(ServerOption("one", "One", "u@one"), ServerOption("two", "Two", "u@two"))
        assertEquals("one", pickDefaultServerId(never))
        val used = never + ServerOption("three", "Three", "u@three", lastConnectedAtMillis = 5)
        assertEquals("three", pickDefaultServerId(used))
        assertNull(pickDefaultServerId(emptyList()))
    }

    @Test
    fun `upload names get a numbered suffix before the extension`() {
        assertEquals(
            listOf("report.pdf", "report (1).pdf", "report (2).pdf"),
            candidateNames("report.pdf").take(3).toList(),
        )
        assertEquals(listOf(".env", ".env (1)"), candidateNames(".env").take(2).toList())
        assertEquals(MAX_NAME_CANDIDATES, candidateNames("a").count())
    }

    @Test
    fun `sample size brings the longest edge under the limit`() {
        assertEquals(1, sampleSizeFor(800, 600, maxEdge = 2048))
        assertEquals(2, sampleSizeFor(4000, 3000, maxEdge = 2048))
        assertEquals(8, sampleSizeFor(1000, 9000, maxEdge = 2048))
    }

    @Test
    fun `overlong preview lines are cut`() {
        val lines = previewLines("short\n" + "x".repeat(PREVIEW_LINE_MAX_CHARS + 10))
        assertEquals("short", lines[0])
        assertTrue(lines[1].length < PREVIEW_LINE_MAX_CHARS + 10)
    }

    @Test
    fun `sizes are formatted with binary units`() {
        assertEquals("512 B", formatSize(512))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("unknown", formatSize(-1))
    }

    @Test
    fun `quiet listing that failed keeps entries and marks the view stale`() {
        val state = listed()
        val failure = VmResult.Failure(VmError.FileSystem(summary = "gone"))
        val after = applyListing(state, state, failure, quiet = true)
        assertEquals(state.entries, after.entries)
        assertNull(after.error)
        assertTrue(after.liveStale)
    }

    @Test
    fun `quiet listing with no change returns an equal state`() {
        val state = listed()
        val after = applyListing(state, state, VmResult.Success(listOf(entry("a"))), quiet = true)
        assertEquals(state, after)
        assertSame(state.entries, after.entries)
    }

    @Test
    fun `listing for another folder or sort order is dropped`() {
        val requested = FilesUiState(serverId = "s", path = "/srv")
        val moved = requested.copy(path = "/etc")
        val result = VmResult.Success(listOf(entry("a")))
        assertSame(moved, applyListing(moved, requested, result, quiet = false))
        val resorted = requested.copy(options = requested.options.copy(sortOrder = RemoteSortOrder.SIZE))
        assertSame(resorted, applyListing(resorted, requested, result, quiet = true))
    }

    @Test
    fun `user listing failure clears entries and shows the error`() {
        val state = FilesUiState(serverId = "s", path = "/srv", entries = listOf(entry("a")))
        val error = VmError.FileSystem(summary = "denied")
        val after = applyListing(state, state, VmResult.Failure(error), quiet = false)
        assertTrue(after.entries.isEmpty())
        assertEquals(error, after.error)
        assertFalse(after.isLoading)
    }
}
