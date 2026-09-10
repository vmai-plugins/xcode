package digital.vmstudio.code.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import digital.vmstudio.code.core.common.result.getOrNull
import digital.vmstudio.code.core.common.result.getOrThrow
import digital.vmstudio.code.core.security.crypto.AndroidKeystoreCrypto
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android Keystore.
 *
 * These cannot be unit tests: `AndroidKeystoreCrypto` calls into the platform
 * keystore, which does not exist on the JVM. Everything about the app's credential
 * security rests on this class, and until these ran it had never executed once.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCryptoTest {

    private lateinit var crypto: AndroidKeystoreCrypto

    @Before
    fun setUp() {
        crypto = AndroidKeystoreCrypto()
    }

    @After
    fun tearDown() {
        // Each test starts from a known state; leaving a key behind would let one
        // test's master key mask a failure to create one in the next.
        crypto.destroyMasterKey()
    }

    @Test
    fun encryptedBytesRoundTrip() {
        val plaintext = "correct horse battery staple".toByteArray()

        val ciphertext = crypto.encrypt(plaintext).getOrThrow()
        val decrypted = crypto.decrypt(ciphertext).getOrThrow()

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun ciphertextDoesNotContainThePlaintext() {
        val plaintext = "a-very-distinctive-secret-value".toByteArray()

        val ciphertext = crypto.encrypt(plaintext).getOrThrow()

        assertFalse(
            "the plaintext must not survive in the output",
            String(ciphertext, Charsets.ISO_8859_1).contains("a-very-distinctive-secret-value"),
        )
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        val plaintext = "same input".toByteArray()

        val first = crypto.encrypt(plaintext).getOrThrow()
        val second = crypto.encrypt(plaintext).getOrThrow()

        // Identical ciphertext for identical input would mean a reused IV, which
        // under GCM leaks the XOR of the plaintexts and can expose the auth key.
        assertNotEquals(
            "identical input must not produce identical ciphertext",
            first.toList(),
            second.toList(),
        )
        assertArrayEquals(plaintext, crypto.decrypt(first).getOrThrow())
        assertArrayEquals(plaintext, crypto.decrypt(second).getOrThrow())
    }

    @Test
    fun ivIsPrefixedAtTheExpectedLength() {
        val ciphertext = crypto.encrypt("x".toByteArray()).getOrThrow()

        // 12-byte IV + 1 byte of content + 16-byte GCM tag.
        assertEquals(29, ciphertext.size)
    }

    @Test
    fun tamperedCiphertextFailsTheIntegrityCheck() {
        val ciphertext = crypto.encrypt("sensitive".toByteArray()).getOrThrow()

        // Flip a bit in the body, past the IV.
        val tampered = ciphertext.copyOf()
        tampered[20] = (tampered[20].toInt() xor 0x01).toByte()

        val result = crypto.decrypt(tampered)

        assertTrue("GCM must reject modified ciphertext", result.isFailure)
        assertNull(result.getOrNull())
    }

    @Test
    fun truncatedCiphertextIsRejectedRatherThanCrashing() {
        val ciphertext = crypto.encrypt("sensitive".toByteArray()).getOrThrow()

        assertTrue(crypto.decrypt(ciphertext.copyOf(8)).isFailure)
        assertTrue(crypto.decrypt(ByteArray(0)).isFailure)
    }

    @Test
    fun emptyPlaintextRoundTrips() {
        val ciphertext = crypto.encrypt(ByteArray(0)).getOrThrow()

        assertArrayEquals(ByteArray(0), crypto.decrypt(ciphertext).getOrThrow())
    }

    @Test
    fun largePayloadRoundTrips() {
        // A private key is the largest thing this ever encrypts; 64 KiB is well past
        // any realistic one and checks nothing assumes a single cipher block.
        val plaintext = ByteArray(64 * 1024) { (it % 251).toByte() }

        val decrypted = crypto.decrypt(crypto.encrypt(plaintext).getOrThrow()).getOrThrow()

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun theSameKeyIsReusedAcrossInstances() {
        val ciphertext = crypto.encrypt("persisted".toByteArray()).getOrThrow()

        // A second instance must find the existing key rather than generating a new
        // one, or every app restart would orphan the user's stored credentials.
        val other = AndroidKeystoreCrypto()

        assertArrayEquals("persisted".toByteArray(), other.decrypt(ciphertext).getOrThrow())
    }

    @Test
    fun destroyingTheKeyMakesExistingCiphertextUnreadable() {
        val ciphertext = crypto.encrypt("gone after erase".toByteArray()).getOrThrow()

        crypto.destroyMasterKey()

        // A fresh key is generated on next use, and it cannot open the old data.
        assertTrue(crypto.decrypt(ciphertext).isFailure)
    }

    @Test
    fun hardwareBackingIsReportedWithoutThrowing() {
        crypto.encrypt("force key creation".toByteArray()).getOrThrow()

        // An emulator reports software backing; a real device usually reports
        // hardware. Either is acceptable — the query must simply not blow up, since
        // the Settings screen calls it on every open.
        val backed = crypto.isHardwareBacked()

        assertTrue(backed || !backed)
    }
}
