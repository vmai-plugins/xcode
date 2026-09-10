package digital.vmstudio.code.core.ssh.host

import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey
import java.util.Base64

/**
 * Bridges sshj's synchronous host-key callback to the app's trust store.
 *
 * **Why this never prompts inline.** `verify` is called on the SSH transport thread
 * during the handshake. Blocking it while a dialog waits for a human would stall
 * the transport for however long the user takes to read a fingerprint, and risks
 * tripping the negotiation timeout. So an unrecognised or changed key simply fails
 * the handshake, and the reason is recorded in [verdict] for the connection layer
 * to turn into a structured error. The UI then shows the fingerprint, and if the
 * user accepts, the key is stored and the connection is attempted again.
 *
 * The cost is one extra TCP connection on first contact with a host. The benefit is
 * that a security decision is never made by a thread under time pressure.
 *
 * A fresh instance is used per connection attempt; it is not thread-safe across
 * attempts and is not meant to be.
 */
internal class TofuHostKeyVerifier(
    private val hostKeyRepository: HostKeyRepository,
) : HostKeyVerifier {

    /** Set when [verify] rejects a key, so the caller can explain why. */
    @Volatile
    var verdict: HostKeyVerdict? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val candidate = HostKeyCandidate(
            host = hostname,
            port = port,
            keyType = key.sshKeyTypeName(),
            fingerprintSha256 = sha256Fingerprint(key.toSshWireFormat()),
            publicKeyBase64 = Base64.getEncoder().encodeToString(key.toSshWireFormat()),
        )

        // A local database read, measured in microseconds. Blocking the transport
        // thread for that is fine; blocking it for a user decision is not. The
        // blocking accessor keeps the read on Room's executor instead of hopping
        // through the app IO dispatcher, which could itself be saturated.
        val result = hostKeyRepository.evaluateBlocking(candidate)
        verdict = result
        return result is HostKeyVerdict.Trusted
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
        hostKeyRepository.trustedKeyTypesBlocking(hostname, port)
}
