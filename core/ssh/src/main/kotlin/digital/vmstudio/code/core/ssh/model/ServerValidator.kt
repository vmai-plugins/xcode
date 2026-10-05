package digital.vmstudio.code.core.ssh.model

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.database.entity.SshAuthMethod

/**
 * Validates a [ServerDraft] before anything is written to the database or the
 * credential store.
 *
 * Pure and stateless so it is exhaustively unit-testable without Android, and so
 * the same rules apply whether a server is created by the user, imported, or
 * created by the AI agent through a tool call.
 */
object ServerValidator {

    private const val MAX_PORT = 65_535
    private const val MIN_TIMEOUT_SECONDS = 1
    private const val MAX_TIMEOUT_SECONDS = 300
    private const val MAX_KEEPALIVE_SECONDS = 3_600

    /**
     * Hostname per RFC 1123, or an IPv4/IPv6 literal. Deliberately strict: a
     * hostname with a space or a scheme prefix ("ssh://") is a common paste error
     * that otherwise fails much later with an opaque DNS message.
     */
    private val HOSTNAME = Regex(
        "^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$",
    )
    private val IPV4 = Regex(
        "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$",
    )
    private val IPV6 = Regex("^[0-9A-Fa-f:]+$")

    /** POSIX portable username; also rejects a pasted `user@host`. */
    private val USERNAME = Regex("^[a-z_][a-z0-9_-]{0,31}\\$?$", RegexOption.IGNORE_CASE)

    private val PEM_HEADER = Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----")

    fun validate(draft: ServerDraft): ServerValidation {
        val errors = buildMap {
            validateName(draft.name)?.let { put(ServerField.NAME, it) }
            validateHost(draft.host)?.let { put(ServerField.HOST, it) }
            validatePort(draft.port)?.let { put(ServerField.PORT, it) }
            validateUsername(draft.username)?.let { put(ServerField.USERNAME, it) }
            validateTimeout(draft.connectTimeoutSeconds)?.let { put(ServerField.CONNECT_TIMEOUT, it) }
            validateKeepAlive(draft.keepAliveSeconds)?.let { put(ServerField.KEEP_ALIVE, it) }
            validateCredentials(draft).forEach { (field, error) -> put(field, error) }
        }
        return ServerValidation(errors)
    }

    private fun validateName(name: String): VmError.Validation? = when {
        name.isBlank() -> VmError.Validation(
            summary = "Name is required",
            fieldName = "name",
            reason = "Servers are identified by name throughout the app.",
            suggestedAction = "Give this server a short name, e.g. \"Production VPS\".",
        )
        name.length > 64 -> VmError.Validation(
            summary = "Name is too long",
            fieldName = "name",
            reason = "Names are limited to 64 characters so they fit the context bar.",
        )
        else -> null
    }

    /**
     * The host as the user meant it. Phone keyboards and copy-paste slip in
     * characters that look identical but are not ASCII: zero-width spaces and
     * direction marks, full-width digits and dots, or a locale's own digits. An
     * address like 200.234.41.231 then failed validation (or DNS) for no visible
     * reason. Those are mapped to plain ASCII; real mistakes still fail.
     */
    fun normalizeHost(host: String): String = buildString {
        for (char in host.trim()) {
            when {
                Character.getType(char) == Character.FORMAT.toInt() -> Unit
                char in FULL_STOPS -> append('.')
                char in FULL_WIDTH_ASCII -> append(char - FULL_WIDTH_OFFSET)
                char.isDigit() && char !in '0'..'9' -> append(char.digitToInt().digitToChar())
                else -> append(char)
            }
        }
    }.trim()

    private val FULL_STOPS = setOf('\u3002', '\uFF0E', '\uFF61')
    private val FULL_WIDTH_ASCII = '\uFF01'..'\uFF5E'
    private const val FULL_WIDTH_OFFSET = 0xFEE0

    private fun validateHost(host: String): VmError.Validation? {
        val trimmed = normalizeHost(host)
        return when {
            trimmed.isBlank() -> VmError.Validation(
                summary = "Host is required",
                fieldName = "host",
                suggestedAction = "Enter a hostname or IP address.",
            )
            trimmed.contains("://") -> VmError.Validation(
                summary = "Remove the scheme from the host",
                fieldName = "host",
                reason = "Enter only the hostname, not a URL.",
                suggestedAction = "For example use \"example.com\", not \"ssh://example.com\".",
            )
            trimmed.contains('@') -> VmError.Validation(
                summary = "Enter the host without a username",
                fieldName = "host",
                reason = "The username belongs in its own field.",
                suggestedAction = "Use the part after the @ here.",
            )
            trimmed.contains(' ') -> VmError.Validation(
                summary = "Host cannot contain spaces",
                fieldName = "host",
            )
            IPV4.matches(trimmed) -> null
            // A dotted string whose labels are all numeric is an IP address the
            // user mistyped, not a hostname. `203.0.113.999` is syntactically a
            // legal DNS name, so the hostname rule alone would wave it through.
            isAllNumericLabels(trimmed) -> VmError.Validation(
                summary = "Not a valid IP address",
                fieldName = "host",
                reason = "Each part of an IPv4 address must be between 0 and 255.",
                suggestedAction = "Check the address, e.g. \"203.0.113.10\".",
            )
            HOSTNAME.matches(trimmed) -> null
            trimmed.contains(':') && IPV6.matches(trimmed) -> null
            else -> VmError.Validation(
                summary = "Host is not a valid hostname or IP address",
                fieldName = "host",
                suggestedAction = "Check for typos, e.g. \"vps.example.com\" or \"203.0.113.10\".",
            )
        }
    }

    private fun isAllNumericLabels(host: String): Boolean {
        val labels = host.split('.')
        return labels.size > 1 && labels.all { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    private fun validatePort(port: String): VmError.Validation? {
        val value = port.trim().toIntOrNull()
        return when {
            port.isBlank() -> VmError.Validation(
                summary = "Port is required",
                fieldName = "port",
                suggestedAction = "SSH usually listens on 22.",
            )
            value == null -> VmError.Validation(
                summary = "Port must be a number",
                fieldName = "port",
            )
            value !in 1..MAX_PORT -> VmError.Validation(
                summary = "Port must be between 1 and $MAX_PORT",
                fieldName = "port",
            )
            else -> null
        }
    }

    private fun validateUsername(username: String): VmError.Validation? {
        val trimmed = username.trim()
        return when {
            trimmed.isBlank() -> VmError.Validation(
                summary = "Username is required",
                fieldName = "username",
                suggestedAction = "The account you log in as, e.g. \"deploy\" or \"ubuntu\".",
            )
            trimmed.contains('@') -> VmError.Validation(
                summary = "Enter the username only",
                fieldName = "username",
                reason = "The host belongs in its own field.",
            )
            !USERNAME.matches(trimmed) -> VmError.Validation(
                summary = "Username contains unsupported characters",
                fieldName = "username",
                suggestedAction = "Use letters, digits, underscore or hyphen.",
            )
            else -> null
        }
    }

    private fun validateTimeout(seconds: String): VmError.Validation? {
        val value = seconds.trim().toIntOrNull()
        return when {
            value == null -> VmError.Validation(
                summary = "Connection timeout must be a number",
                fieldName = "connectTimeout",
            )
            value !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS -> VmError.Validation(
                summary = "Timeout must be between $MIN_TIMEOUT_SECONDS and $MAX_TIMEOUT_SECONDS seconds",
                fieldName = "connectTimeout",
            )
            else -> null
        }
    }

    private fun validateKeepAlive(seconds: String): VmError.Validation? {
        val value = seconds.trim().toIntOrNull()
        return when {
            value == null -> VmError.Validation(
                summary = "Keepalive must be a number",
                fieldName = "keepAlive",
            )
            // Zero is meaningful: it disables keepalive entirely.
            value !in 0..MAX_KEEPALIVE_SECONDS -> VmError.Validation(
                summary = "Keepalive must be between 0 and $MAX_KEEPALIVE_SECONDS seconds",
                fieldName = "keepAlive",
                reason = "Use 0 to disable keepalive packets.",
            )
            else -> null
        }
    }

    private fun validateCredentials(
        draft: ServerDraft,
    ): Map<ServerField, VmError.Validation> {
        // When editing an existing server the user may leave secrets untouched.
        if (draft.retainExistingSecret) return emptyMap()

        return when (draft.authMethod) {
            SshAuthMethod.PASSWORD -> if (draft.password.isEmpty()) {
                mapOf(
                    ServerField.PASSWORD to VmError.Validation(
                        summary = "Password is required",
                        fieldName = "password",
                        reason = "Password authentication is selected.",
                        suggestedAction = "Enter the password, or switch to key authentication.",
                    ),
                )
            } else {
                emptyMap()
            }

            SshAuthMethod.PRIVATE_KEY,
            SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE,
            -> buildMap {
                if (draft.privateKeyPem.isBlank()) {
                    put(
                        ServerField.PRIVATE_KEY,
                        VmError.Validation(
                            summary = "Private key is required",
                            fieldName = "privateKey",
                            suggestedAction = "Paste or import the private key file.",
                        ),
                    )
                } else if (!PEM_HEADER.containsMatchIn(draft.privateKeyPem)) {
                    put(
                        ServerField.PRIVATE_KEY,
                        VmError.Validation(
                            summary = "This does not look like a private key",
                            fieldName = "privateKey",
                            reason = "No PEM header was found.",
                            suggestedAction =
                            "Paste the whole file including the BEGIN and END lines. " +
                                "Note this is the private key, not the .pub file.",
                        ),
                    )
                }
                if (draft.authMethod == SshAuthMethod.PRIVATE_KEY_WITH_PASSPHRASE &&
                    draft.passphrase.isEmpty()
                ) {
                    put(
                        ServerField.PASSPHRASE,
                        VmError.Validation(
                            summary = "Passphrase is required",
                            fieldName = "passphrase",
                            suggestedAction =
                            "Enter the key's passphrase, or choose key authentication without one.",
                        ),
                    )
                }
            }

            SshAuthMethod.AGENT -> emptyMap()
        }
    }
}
