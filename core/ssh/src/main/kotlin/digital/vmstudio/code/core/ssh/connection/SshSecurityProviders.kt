package digital.vmstudio.code.core.ssh.connection

import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Installs the crypto providers sshj needs on Android.
 *
 * Android ships a deliberately cut-down provider registered under the name `BC`.
 * sshj looks up algorithms by that name and gets the stripped version, which is
 * missing the key formats and curves modern OpenSSH uses — the practical symptom is
 * ed25519 keys failing to parse and some KEX methods being unavailable. Replacing
 * the registration with the full BouncyCastle bundled here fixes both.
 *
 * Idempotent, and safe to call from any thread.
 */
internal object SshSecurityProviders {

    @Volatile
    private var initialised = false

    private val lock = Any()

    fun ensureInitialised() {
        if (initialised) return
        synchronized(lock) {
            if (initialised) return

            runCatching {
                val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                if (existing != null && existing.javaClass != BouncyCastleProvider::class.java) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                }
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)?.javaClass !=
                    BouncyCastleProvider::class.java
                ) {
                    // Position 1 so it is preferred over the platform's version.
                    Security.insertProviderAt(BouncyCastleProvider(), 1)
                }
            }.onFailure {
                VmLog.e(
                    LogCategory.SSH,
                    TAG,
                    "Could not install the BouncyCastle provider; key formats may be limited",
                    it,
                )
            }

            initialised = true
            VmLog.d(LogCategory.SSH, TAG, "Crypto providers initialised")
        }
    }

    private const val TAG = "SshSecurityProviders"
}
