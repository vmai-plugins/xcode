package digital.vmstudio.code.core.ssh.host

import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * A host key presented by a server during the handshake.
 *
 * Fingerprints are not secret, so these values are safe to display, log and store
 * in Room. What matters is that the *decision* to trust one is always the user's.
 */
data class HostKeyCandidate(
    val host: String,
    val port: Int,
    val keyType: String,
    /** OpenSSH-style `SHA256:<base64>` fingerprint, matching `ssh-keygen -lf`. */
    val fingerprintSha256: String,
    val publicKeyBase64: String,
)

/** A key the user has previously chosen to trust. */
data class TrustedHostKey(
    val id: String,
    val host: String,
    val port: Int,
    val keyType: String,
    val fingerprintSha256: String,
    val publicKeyBase64: String,
    val firstTrustedAtMillis: Long,
    val lastSeenAtMillis: Long,
)

/** Outcome of comparing a presented key against what is stored. */
sealed interface HostKeyVerdict {

    /** The key matches a stored, trusted key. */
    data class Trusted(val key: TrustedHostKey) : HostKeyVerdict

    /** No key has ever been stored for this host: trust on first use. */
    data class Unknown(val candidate: HostKeyCandidate) : HostKeyVerdict

    /**
     * The host is already known, but the presented key is not trusted: either a
     * stored key of the same algorithm has a different fingerprint, or a new
     * algorithm has appeared for a host that already has trusted keys. Never
     * resolved automatically — this is what a man-in-the-middle looks like, and also
     * what a legitimate server rebuild or key addition looks like, so only a human
     * can tell them apart. [trusted] is one of the keys already trusted for the host.
     */
    data class Mismatch(
        val candidate: HostKeyCandidate,
        val trusted: TrustedHostKey,
    ) : HostKeyVerdict
}

/**
 * Computes the fingerprint the way OpenSSH does, so the value the user sees here
 * can be compared character-for-character with `ssh-keygen -lf /etc/ssh/...`.
 *
 * That comparability is the entire point: a fingerprint the user cannot verify
 * against their server out-of-band is security theatre.
 */
internal fun sha256Fingerprint(wireEncodedKey: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(wireEncodedKey)
    // OpenSSH prints base64 without padding.
    val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
    return "SHA256:$encoded"
}

internal fun PublicKey.toSshWireFormat(): ByteArray =
    net.schmizz.sshj.common.Buffer.PlainBuffer().putPublicKey(this).compactData

internal fun PublicKey.sshKeyTypeName(): String =
    net.schmizz.sshj.common.KeyType.fromKey(this).toString()
