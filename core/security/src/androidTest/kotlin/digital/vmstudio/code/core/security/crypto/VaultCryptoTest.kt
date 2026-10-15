package digital.vmstudio.code.core.security.crypto

import digital.vmstudio.code.core.common.result.VmResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultCryptoTest {

    private val vaultCrypto = VaultCrypto()
    private val sampleJson = """{"servers":[{"name":"Prod VPS","host":"1.2.3.4"}]}"""
    private val passphrase = "super-secret-master-passphrase".toCharArray()

    @Test
    fun roundtripEncryptionAndDecryptionSucceeds() {
        val encryptedResult = vaultCrypto.exportEncryptedVault(sampleJson, passphrase)
        assertTrue(encryptedResult is VmResult.Success)

        val encryptedBase64 = (encryptedResult as VmResult.Success).value
        assertTrue(encryptedBase64.isNotBlank())

        val decryptedResult = vaultCrypto.importEncryptedVault(encryptedBase64, passphrase)
        assertTrue(decryptedResult is VmResult.Success)
        assertEquals(sampleJson, (decryptedResult as VmResult.Success).value)
    }

    @Test
    fun wrongPassphraseFailsDecryptionSafely() {
        val encryptedResult = vaultCrypto.exportEncryptedVault(sampleJson, passphrase)
        assertTrue(encryptedResult is VmResult.Success)
        val encryptedBase64 = (encryptedResult as VmResult.Success).value

        val wrongPassphrase = "wrong-passphrase".toCharArray()
        val decryptedResult = vaultCrypto.importEncryptedVault(encryptedBase64, wrongPassphrase)
        assertTrue(decryptedResult is VmResult.Failure)
    }
}
