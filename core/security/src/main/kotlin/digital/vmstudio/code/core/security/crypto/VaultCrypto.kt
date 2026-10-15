package digital.vmstudio.code.core.security.crypto

import android.util.Base64
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.vmCatching
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Zero-Trust AES-256-GCM encryption with PBKDF2 passphrase derivation
 * for exporting and importing complete encrypted backups of servers, settings, and keys.
 *
 * Wire layout of the encrypted container (Base64 encoded):
 * [16 bytes Salt] + [12 bytes IV] + [AES-GCM-256 Ciphertext + Tag]
 */
@Singleton
class VaultCrypto @Inject constructor() {

    private val secureRandom = SecureRandom()

    fun exportEncryptedVault(jsonPayload: String, passphrase: CharArray): VmResult<String> =
        vmCatching(::mapVaultError) {
            val salt = ByteArray(SALT_BYTES).also { secureRandom.nextBytes(it) }
            val iv = ByteArray(IV_BYTES).also { secureRandom.nextBytes(it) }

            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val ciphertext = cipher.doFinal(jsonPayload.toByteArray(Charsets.UTF_8))

            val combined = ByteArray(salt.size + iv.size + ciphertext.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        }

    fun importEncryptedVault(vaultBase64: String, passphrase: CharArray): VmResult<String> =
        vmCatching(::mapVaultError) {
            val combined = Base64.decode(vaultBase64.trim(), Base64.DEFAULT)
            require(combined.size >= SALT_BYTES + IV_BYTES + 16) {
                "Encrypted vault package is corrupt or too short."
            }

            val salt = combined.copyOfRange(0, SALT_BYTES)
            val iv = combined.copyOfRange(SALT_BYTES, SALT_BYTES + IV_BYTES)
            val ciphertext = combined.copyOfRange(SALT_BYTES + IV_BYTES, combined.size)

            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun mapVaultError(throwable: Throwable): VmError = when (throwable) {
        is javax.crypto.AEADBadTagException -> VmError.Security(
            summary = "Incorrect vault passphrase",
            reason = "The passphrase provided could not decrypt this vault or the data is corrupted.",
            suggestedAction = "Check your master passphrase and try again.",
            cause = throwable,
        )
        else -> VmError.Security(
            summary = "Vault decryption failed",
            reason = throwable.message,
            suggestedAction = "Ensure the backup file is intact and not modified.",
            cause = throwable,
        )
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_SIZE_BITS = 256
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
    }
}
