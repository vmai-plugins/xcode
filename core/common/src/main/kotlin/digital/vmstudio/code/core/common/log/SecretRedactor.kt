package digital.vmstudio.code.core.common.log

import java.util.concurrent.ConcurrentHashMap

/**
 * Last line of defence against secrets reaching logs, diagnostics exports or crash
 * reports.
 *
 * Two mechanisms, deliberately layered:
 *
 *  1. **Registered values.** Anything the app knows to be a secret (an API key, a
 *     decrypted SSH password) is registered here the moment it is loaded, and is
 *     then substituted out of every log line by exact match. This catches secrets
 *     that appear in contexts no pattern would anticipate.
 *  2. **Structural patterns.** Private key blocks, `Authorization` headers,
 *     `key=value` pairs with secret-looking names, and connection strings with
 *     inline credentials are redacted by shape, which catches secrets the app
 *     never held as a discrete value (for example ones echoed by a remote shell).
 *
 * Registered values are held only as a salted hash-free exact string in memory for
 * the process lifetime; they are never persisted.
 */
object SecretRedactor {

    const val MASK: String = "[REDACTED]"

    /** Values shorter than this are too collision-prone to substitute globally. */
    private const val MIN_REGISTERED_LENGTH = 8

    private val registeredSecrets = ConcurrentHashMap.newKeySet<String>()

    private val patterns: List<Pair<Regex, String>> = listOf(
        // PEM private key blocks, including the body.
        Regex(
            "-----BEGIN[ A-Z]*PRIVATE KEY-----.*?-----END[ A-Z]*PRIVATE KEY-----",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ) to "-----BEGIN PRIVATE KEY----- $MASK -----END PRIVATE KEY-----",
        // OpenSSH key body.
        Regex(
            "-----BEGIN OPENSSH PRIVATE KEY-----.*?-----END OPENSSH PRIVATE KEY-----",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ) to "-----BEGIN OPENSSH PRIVATE KEY----- $MASK -----END OPENSSH PRIVATE KEY-----",
        // Authorization / Proxy-Authorization headers. The value must be consumed
        // to end-of-line, not just to the first space: `Authorization: Bearer <jwt>`
        // would otherwise leave the token itself behind.
        Regex(
            "(?i)\\b(authorization|proxy-authorization)\\s*[:=]\\s*[^\\r\\n]+",
        ) to "$1: $MASK",
        // Bearer / token-style values.
        Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/-]{8,}=*") to "Bearer $MASK",
        // key=value and "key": "value" for secret-ish names.
        Regex(
            "(?i)\\b(api[_-]?key|apikey|secret|token|password|passwd|passphrase|" +
                "private[_-]?key|client[_-]?secret|refresh[_-]?token|access[_-]?token|" +
                "session[_-]?key|auth[_-]?token)\\b\\s*[\"']?\\s*[:=]\\s*[\"']?([^\\s\"',;&]+)",
        ) to "$1=$MASK",
        // Credentials embedded in a URL: scheme://user:pass@host
        Regex("(?<=://)([^/\\s:@]+):([^/\\s@]+)@") to "$1:$MASK@",
        // `ssh-rsa AAAA...` public blobs are not secret, but private key material
        // pasted into a terminal often appears as a long base64 run after a
        // secret-looking prompt; handled by the key=value rule above.
    )

    /**
     * Registers a known secret so it is masked wherever it appears. Safe to call
     * repeatedly. Values below [MIN_REGISTERED_LENGTH] are ignored to avoid
     * mangling unrelated text.
     */
    fun registerSecret(value: String?) {
        if (value == null) return
        val trimmed = value.trim()
        if (trimmed.length < MIN_REGISTERED_LENGTH) return
        registeredSecrets.add(trimmed)
    }

    /** Forgets a previously registered secret, for example on sign-out. */
    fun unregisterSecret(value: String?) {
        value?.trim()?.let(registeredSecrets::remove)
    }

    fun clearRegisteredSecrets() = registeredSecrets.clear()

    fun redact(input: String?): String {
        var output: String = input.orEmpty()
        if (output.isEmpty()) return output
        for (secret in registeredSecrets) {
            if (output.contains(secret)) output = output.replace(secret, MASK)
        }
        for ((pattern, replacement) in patterns) {
            output = pattern.replace(output, replacement)
        }
        return output
    }

    /**
     * Renders a throwable for logs with message and stack frames redacted. The
     * message is the usual leak vector: libraries habitually include the offending
     * value in it.
     */
    fun redactThrowable(throwable: Throwable?): String? {
        if (throwable == null) return null
        val text = buildString {
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth < 5) {
                if (depth > 0) append("\nCaused by: ")
                append(current::class.java.name)
                current.message?.let { append(": ").append(it) }
                current.stackTrace.take(12).forEach { frame ->
                    append("\n    at ").append(frame.toString())
                }
                current = current.cause
                depth++
            }
        }
        return redact(text)
    }
}
