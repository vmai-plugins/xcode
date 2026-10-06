package digital.vmstudio.code.core.sftp.fs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Saving text decoded with replacement characters would rewrite the file's bytes. */
class StrictTextDecodingTest {

    @Test
    fun `utf8 text decodes`() {
        assertEquals("héllo", decodeTextStrictly("héllo".toByteArray(Charsets.UTF_8)))
    }

    @Test(expected = IllegalStateException::class)
    fun `latin1 text is refused`() {
        decodeTextStrictly("héllo".toByteArray(Charsets.ISO_8859_1))
    }

    @Test(expected = IllegalStateException::class)
    fun `binary is refused`() {
        decodeTextStrictly(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0x00, 0x01))
    }
}
