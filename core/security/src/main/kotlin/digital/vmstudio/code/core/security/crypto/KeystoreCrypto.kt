package digital.vmstudio.code.core.security.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.log.LogCategory
import digital.vmstudio.code.core.common.log.VmLog
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Envelope encryption for locally stored secrets.
 *
 * The master key lives in the Android Keystore and is non-exportable: the raw key
 * bytes never enter the app process, so a filesystem or backup compromise yields
 * only ciphertext. Hardware backing (StrongBox / TEE) is used when the device
 * offers it and silently degraded to a software-backed Keystore key when it does
 * not, because refusing to run on older hardware would be worse for the user than
 * a still-Keystore-protected key.
 */
interface KeystoreCrypto {

    /** Returns IV-prefixed ciphertext. */
    fun encrypt(plaintext: ByteArray): VmResult<ByteArray>

    /** Accepts IV-prefixed ciphertext as produced by [encrypt]. */
    fun decrypt(ciphertext: ByteArray): VmResult<ByteArray>

    /** True when the master key is bound to secure hardware. Shown in Settings. */
    fun isHardwareBacked(): Boolean

    /**
     * Destroys the master key, making every stored secret permanently unreadable.
     * Used by "erase all credentials" and by recovery when the key is invalidated.
     */
    fun destroyMasterKey(): VmResult<Unit>
}

@Singleton
class AndroidKeystoreCrypto @Inject constructor() : KeystoreCrypto {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(PROVIDER).apply { load(null) }
    }

    @Volatile
    private var hardwareBacked: Boolean = false

    override fun encrypt(plaintext: ByteArray): VmResult<ByteArray> = vmCatching(::mapCryptoError) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        require(iv.size == IV_LENGTH) { "Unexpected GCM IV length ${iv.size}" }
        val encrypted = cipher.doFinal(plaintext)
        // Layout: [12-byte IV][ciphertext || 16-byte GCM tag]
        ByteArray(iv.size + encrypted.size).also { out ->
            iv.copyInto(out, 0)
            encrypted.copyInto(out, iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): VmResult<ByteArray> = vmCatching(::mapCryptoError) {
        // >= rather than >: an empty plaintext encrypts to exactly IV + tag with no
        // body, and rejecting that length made a stored empty secret — an empty
        // passphrase, say — permanently undecryptable.
        require(ciphertext.size >= IV_LENGTH + GCM_TAG_BYTES) {
            "Ciphertext is too short to contain an IV and authentication tag"
        }
        val iv = ciphertext.copyOfRange(0, IV_LENGTH)
        val body = ciphertext.copyOfRange(IV_LENGTH, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            masterKey(),
            GCMParameterSpec(GCM_TAG_BYTES * 8, iv),
        )
        cipher.doFinal(body)
    }

    override fun isHardwareBacked(): Boolean {
        masterKeyOrNull()
        return hardwareBacked
    }

    override fun destroyMasterKey(): VmResult<Unit> = vmCatching(::mapCryptoError) {
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        VmLog.w(LogCategory.SECURITY, TAG, "Master key destroyed; stored secrets are now unreadable")
    }

    private fun masterKeyOrNull(): SecretKey? = runCatching { masterKey() }.getOrNull()

    @Synchronized
    private fun masterKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { entry ->
            hardwareBacked = detectHardwareBacking(entry.secretKey)
            return entry.secretKey
        }
        return generateMasterKey()
    }

    private fun generateMasterKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)

        fun specBuilder() = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // Forces a fresh IV per operation; reusing an IV under GCM is
            // catastrophic, so we let the Keystore own IV generation entirely.
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generator.init(specBuilder().setIsStrongBoxBacked(true).build())
                val key = generator.generateKey()
                hardwareBacked = true
                VmLog.i(LogCategory.SECURITY, TAG, "Master key created in StrongBox")
                return key
            } catch (_: StrongBoxUnavailableException) {
                // Expected on most devices; fall through to a TEE/software key.
            } catch (_: java.security.ProviderException) {
                // Some OEM StrongBox implementations fail late rather than throwing
                // StrongBoxUnavailableException. Treat identically.
            }
        }

        generator.init(specBuilder().build())
        val key = generator.generateKey()
        hardwareBacked = detectHardwareBacking(key)
        VmLog.i(
            LogCategory.SECURITY,
            TAG,
            "Master key created (hardwareBacked=$hardwareBacked)",
        )
        return key
    }

    private fun detectHardwareBacking(key: SecretKey): Boolean = runCatching {
        val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, PROVIDER)
        val info = factory.getKeySpec(
            key,
            android.security.keystore.KeyInfo::class.java,
        ) as android.security.keystore.KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel != android.security.keystore.KeyProperties.SECURITY_LEVEL_SOFTWARE
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    }.getOrDefault(false)

    private fun mapCryptoError(throwable: Throwable): VmError = when (throwable) {
        is android.security.keystore.KeyPermanentlyInvalidatedException -> VmError.Security(
            summary = "Stored credentials can no longer be decrypted",
            reason = "The device security configuration changed (screen lock removed or " +
                "biometrics reset), which permanently invalidated the encryption key.",
            suggestedAction = "Re-enter your server and AI credentials to store them again.",
            cause = throwable,
        )
        is javax.crypto.AEADBadTagException -> VmError.Security(
            summary = "Stored credential failed integrity check",
            reason = "The encrypted data was modified or corrupted.",
            suggestedAction = "Delete and re-enter the affected credential.",
            cause = throwable,
        )
        else -> VmError.Security(
            summary = "Secure storage is unavailable",
            reason = throwable.message,
            suggestedAction = "Restart the app; if this persists, re-enter your credentials.",
            cause = throwable,
        )
    }

    private companion object {
        const val TAG = "AndroidKeystoreCrypto"
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "vmstudio.master.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH = 12
        const val GCM_TAG_BYTES = 16
    }
}
