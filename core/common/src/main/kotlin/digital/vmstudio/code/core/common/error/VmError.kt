package digital.vmstudio.code.core.common.error

/**
 * Every failure surfaced to the user must be able to answer three questions:
 * what happened, why it probably happened, and what the user can do next.
 *
 * Subsystems map their native exceptions into one of these types at the boundary
 * so no `Exception` from sshj, JGit or OkHttp ever reaches the UI layer, and so
 * "Unknown error" is only ever produced when genuinely nothing else is known.
 */
sealed class VmError {

    /** One short sentence: what happened. Shown as the error title. */
    abstract val summary: String

    /** The most likely cause, phrased for a developer. */
    abstract val reason: String?

    /** A concrete next step the user can take. */
    abstract val suggestedAction: String?

    /** Whether retrying the identical operation could plausibly succeed. */
    abstract val retryable: Boolean

    /** Underlying throwable, kept for diagnostics only. Never shown verbatim. */
    abstract val cause: Throwable?

    /** Extra key/value detail rendered under the message (host, path, exit code). */
    open val details: Map<String, String> get() = emptyMap()

    data class Network(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = true,
        override val cause: Throwable? = null,
        val statusCode: Int? = null,
        val endpoint: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap {
                endpoint?.let { put("Endpoint", it) }
                statusCode?.let { put("Status", it.toString()) }
            }
    }

    data class Offline(
        override val summary: String = "No network connection",
        override val reason: String? = "The device is offline or the network is unreachable.",
        override val suggestedAction: String? = "Reconnect to a network and try again.",
        override val cause: Throwable? = null,
    ) : VmError() {
        override val retryable: Boolean = true
    }

    data class Ssh(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = true,
        override val cause: Throwable? = null,
        val host: String? = null,
        val port: Int? = null,
        val username: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap {
                host?.let { put("Host", if (port != null) "$it:$port" else it) }
                username?.let { put("User", it) }
            }
    }

    /**
     * The server presented a host key that does not match the pinned one. This is
     * never auto-retryable: it may indicate a man-in-the-middle attack and always
     * requires an explicit human decision.
     */
    data class HostKeyMismatch(
        val host: String,
        val port: Int,
        val expectedFingerprint: String,
        val actualFingerprint: String,
        val keyType: String,
    ) : VmError() {
        override val summary: String = "Host key verification failed"
        override val reason: String =
            "The key presented by $host does not match the key VMStudio Code trusted previously. " +
                "This happens after a legitimate server rebuild, but it can also indicate interception."
        override val suggestedAction: String =
            "Verify the new fingerprint out-of-band before trusting it."
        override val retryable: Boolean = false
        override val cause: Throwable? = null
        override val details: Map<String, String> = mapOf(
            "Host" to "$host:$port",
            "Key type" to keyType,
            "Expected" to expectedFingerprint,
            "Received" to actualFingerprint,
        )
    }

    data class UnknownHostKey(
        val host: String,
        val port: Int,
        val fingerprint: String,
        val keyType: String,
    ) : VmError() {
        override val summary: String = "Unrecognised host"
        override val reason: String = "VMStudio Code has not connected to $host before."
        override val suggestedAction: String =
            "Confirm the fingerprint matches your server before trusting it."
        override val retryable: Boolean = false
        override val cause: Throwable? = null
        override val details: Map<String, String> = mapOf(
            "Host" to "$host:$port",
            "Key type" to keyType,
            "Fingerprint" to fingerprint,
        )
    }

    data class Authentication(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val cause: Throwable? = null,
        val realm: String? = null,
    ) : VmError() {
        override val retryable: Boolean = false
        override val details: Map<String, String>
            get() = buildMap { realm?.let { put("Account", it) } }
    }

    data class FileSystem(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        val path: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap { path?.let { put("Path", it) } }
    }

    data class Git(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        val repository: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap { repository?.let { put("Repository", it) } }
    }

    data class Command(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
        val command: String? = null,
        val exitCode: Int? = null,
        val stderrExcerpt: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap {
                command?.let { put("Command", it) }
                exitCode?.let { put("Exit code", it.toString()) }
                stderrExcerpt?.let { put("stderr", it) }
            }
    }

    data class Ai(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = true,
        override val cause: Throwable? = null,
        val provider: String? = null,
        val model: String? = null,
    ) : VmError() {
        override val details: Map<String, String>
            get() = buildMap {
                provider?.let { put("Provider", it) }
                model?.let { put("Model", it) }
            }
    }

    /** An operation the current permission level or safety policy forbids. */
    data class PermissionDenied(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        val subject: String? = null,
    ) : VmError() {
        override val retryable: Boolean = false
        override val cause: Throwable? = null
        override val details: Map<String, String>
            get() = buildMap { subject?.let { put("Blocked", it) } }
    }

    data class Storage(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null,
    ) : VmError()

    data class Security(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
        override val cause: Throwable? = null,
    ) : VmError() {
        override val retryable: Boolean = false
    }

    data class Validation(
        override val summary: String,
        // Named `fieldName`, not `field`: inside a property accessor the identifier
        // `field` is the backing-field keyword and would resolve to that instead.
        val fieldName: String? = null,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
    ) : VmError() {
        override val retryable: Boolean = false
        override val cause: Throwable? = null
        override val details: Map<String, String>
            get() = buildMap { fieldName?.let { put("Field", it) } }
    }

    data class NotFound(
        override val summary: String,
        override val reason: String? = null,
        override val suggestedAction: String? = null,
    ) : VmError() {
        override val retryable: Boolean = false
        override val cause: Throwable? = null
    }

    /** Cooperative cancellation. Never rendered as a failure banner. */
    data object Cancelled : VmError() {
        override val summary: String = "Cancelled"
        override val reason: String? = null
        override val suggestedAction: String? = null
        override val retryable: Boolean = false
        override val cause: Throwable? = null
    }

    data class Unexpected(
        override val summary: String = "Something went wrong",
        override val reason: String? = null,
        override val suggestedAction: String? = "Retry, and export diagnostics if it keeps happening.",
        override val cause: Throwable? = null,
    ) : VmError() {
        override val retryable: Boolean = true
    }
}

/** Human-readable single-line rendering, used in logs and compact UI. */
fun VmError.displayText(): String = buildString {
    append(summary)
    reason?.let { append(" - ").append(it) }
}

/** The title the network layer gives an error about an unavailable, unknown or invalid model. */
const val MODEL_PROBLEM_SUMMARY = "This model can't be used right now"
