package digital.vmstudio.code.core.security.crypto

import org.junit.Assert.assertNotNull
import org.junit.Test

class VaultCryptoTest {

    @Test
    fun instanceCanBeCreated() {
        val vault = VaultCrypto()
        assertNotNull(vault)
    }
}
