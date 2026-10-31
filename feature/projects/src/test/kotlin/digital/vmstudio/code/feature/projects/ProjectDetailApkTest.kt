package digital.vmstudio.code.feature.projects

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.sftp.fs.TransferEvent
import digital.vmstudio.code.core.sftp.fs.TransferProgress
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectDetailApkTest {

    @Test
    fun `only a completed transfer is accepted`() {
        val failure = VmError.FileSystem(summary = "transfer failed")

        assertNull(apkTransferFailure(TransferEvent.Completed(totalBytes = 20, durationMillis = 1)))
        assertEquals(failure, apkTransferFailure(TransferEvent.Failed(failure, transferredBytes = 10)))
        assertTrue(apkTransferFailure(TransferEvent.Started(totalBytes = 20, resumedFromBytes = 0)) != null)
        assertTrue(
            apkTransferFailure(
                TransferEvent.Progress(
                    TransferProgress(transferredBytes = 10, totalBytes = 20, bytesPerSecond = 10),
                ),
            ) != null,
        )
        assertTrue(apkTransferFailure(null) != null)
    }

    @Test
    fun `apk download names cannot escape downloads folder`() {
        assertEquals("debug.apk", safeApkDisplayName("debug.apk"))
        assertEquals("DEBUG.APK", safeApkDisplayName("DEBUG.APK"))
        assertThrows(IllegalArgumentException::class.java) { safeApkDisplayName("../debug.apk") }
        assertThrows(IllegalArgumentException::class.java) { safeApkDisplayName("folder\\debug.apk") }
        assertThrows(IllegalArgumentException::class.java) { safeApkDisplayName("notes.txt") }
    }

    @Test
    fun `private archive redirects are restricted to trusted https codeload`() {
        assertTrue(isTrustedGithubArchiveRedirect(URI("https://codeload.github.com/owner/repo/archive.zip")))
        assertTrue(isTrustedGithubArchiveRedirect(URI("https://codeload.github.com:443/owner/repo/archive.zip")))
        assertTrue(isTrustedGithubArchiveRedirect(URI("https://objects.githubusercontent.com/github-production-release-asset/archive.zip")))
        assertTrue(!isTrustedGithubArchiveRedirect(URI("http://codeload.github.com/owner/repo/archive.zip")))
        assertTrue(!isTrustedGithubArchiveRedirect(URI("https://example.com/owner/repo/archive.zip")))
        assertTrue(!isTrustedGithubArchiveRedirect(URI("https://user@codeload.github.com/owner/repo/archive.zip")))
        assertTrue(!isTrustedGithubArchiveRedirect(URI("https://codeload.github.com:444/owner/repo/archive.zip")))
    }
}
