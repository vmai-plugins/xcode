package digital.vmstudio.code.core.common.result

import digital.vmstudio.code.core.common.error.VmError
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VmResultTest {

    @Test
    fun `map transforms success and leaves failure untouched`() {
        assertEquals(4, VmResult.Success(2).map { it * 2 }.getOrNull())

        val failure: VmResult<Int> = VmResult.Failure(VmError.NotFound("gone"))
        assertNull(failure.map { it * 2 }.getOrNull())
    }

    @Test
    fun `flatMap chains only while successful`() {
        val result = VmResult.Success(2)
            .flatMap { VmResult.Success(it + 1) }
            .flatMap { VmResult.Failure(VmError.NotFound("stop")) }
            .flatMap { fail("must not run past a failure"); VmResult.Success(Unit) }

        assertTrue(result.isFailure)
    }

    @Test
    fun `vmCatching converts a throwable into a structured error`() {
        val result = vmCatching { error("boom") }

        val error = result.errorOrNull()
        assertTrue(error is VmError.Unexpected)
        assertEquals("boom", error?.reason)
    }

    @Test
    fun `vmCatching rethrows cancellation so structured concurrency still works`() {
        try {
            vmCatching { throw CancellationException("scope closed") }
            fail("CancellationException must not be captured as a failure")
        } catch (expected: CancellationException) {
            assertEquals("scope closed", expected.message)
        }
    }

    @Test
    fun `fold selects the matching branch`() {
        val ok: VmResult<String> = VmResult.Success("value")
        assertEquals("value", ok.fold(onSuccess = { it }, onFailure = { "err" }))

        val bad: VmResult<String> = VmResult.Failure(VmError.NotFound("missing"))
        assertEquals("missing", bad.fold(onSuccess = { it }, onFailure = { it.summary }))
    }

    @Test
    fun `host key mismatch is never retryable`() {
        val error = VmError.HostKeyMismatch(
            host = "example.com",
            port = 22,
            expectedFingerprint = "SHA256:aaa",
            actualFingerprint = "SHA256:bbb",
            keyType = "ssh-ed25519",
        )

        assertEquals(false, error.retryable)
        assertTrue(error.details.containsKey("Received"))
    }
}
