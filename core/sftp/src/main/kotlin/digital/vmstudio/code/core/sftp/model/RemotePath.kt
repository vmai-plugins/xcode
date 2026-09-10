package digital.vmstudio.code.core.sftp.model

/**
 * POSIX path handling for remote filesystems.
 *
 * `java.io.File` and `java.nio.Path` are not usable here: they follow the *local*
 * platform's rules, so on Windows-hosted tooling they would introduce backslashes,
 * and on Android they would resolve against the device's filesystem. Remote paths
 * are always POSIX regardless of what the client runs on.
 *
 * Normalisation is also a security primitive. [confine] is what stops a path
 * containing `../` from reaching outside a given root. It is enforced by callers
 * that operate against a bounded root (a project-scoped tool, an agent sandbox);
 * the user-driven file browser deliberately runs unconfined, since browsing
 * `/var/log` or `/etc` is legitimate there. Any caller that must not escape a root
 * is responsible for routing its paths through [confine] — this object does not
 * confine implicitly.
 */
object RemotePath {

    const val SEPARATOR = '/'
    const val ROOT = "/"

    fun isAbsolute(path: String): Boolean = path.startsWith(SEPARATOR)

    fun name(path: String): String =
        normalise(path).trimEnd(SEPARATOR).substringAfterLast(SEPARATOR).ifEmpty { ROOT }

    fun parent(path: String): String? {
        val normalised = normalise(path).trimEnd(SEPARATOR)
        if (normalised.isEmpty() || normalised == ROOT) return null
        val index = normalised.lastIndexOf(SEPARATOR)
        return when {
            index < 0 -> null
            index == 0 -> ROOT
            else -> normalised.substring(0, index)
        }
    }

    fun extension(path: String): String = name(path).substringAfterLast('.', "")

    fun join(base: String, vararg parts: String): String {
        val builder = StringBuilder(base.trimEnd(SEPARATOR))
        parts.forEach { part ->
            val trimmed = part.trim(SEPARATOR)
            if (trimmed.isNotEmpty()) {
                builder.append(SEPARATOR).append(trimmed)
            }
        }
        return normalise(builder.toString().ifEmpty { ROOT })
    }

    /**
     * Collapses `.`, `..`, duplicate separators and trailing separators.
     *
     * A `..` that would escape a relative path is preserved rather than dropped, so
     * callers can still detect the attempt; [confine] is what rejects it. Silently
     * swallowing it would turn a traversal attempt into a valid-looking path.
     */
    fun normalise(path: String): String {
        if (path.isEmpty()) return ""
        val absolute = isAbsolute(path)
        val stack = ArrayDeque<String>()
        var leadingParents = 0

        path.split(SEPARATOR).forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> {
                    if (stack.isNotEmpty()) {
                        stack.removeLast()
                    } else if (!absolute) {
                        // Cannot go above the root of an absolute path; POSIX
                        // defines `/..` as `/`.
                        leadingParents++
                    }
                }
                else -> stack.addLast(segment)
            }
        }

        val body = buildList {
            repeat(leadingParents) { add("..") }
            addAll(stack)
        }.joinToString(SEPARATOR.toString())

        return when {
            absolute -> ROOT + body
            body.isEmpty() -> "."
            else -> body
        }
    }

    /** Resolves [child] against [base], treating an absolute child as absolute. */
    fun resolve(base: String, child: String): String =
        if (isAbsolute(child)) normalise(child) else join(base, child)

    /**
     * Resolves [candidate] and verifies the result stays inside [root].
     *
     * Returns null when the path escapes, which callers must treat as a refusal
     * rather than clamping to the root: a tool asking to read `../../etc/passwd`
     * should fail loudly, not quietly read the project's own README.
     */
    fun confine(root: String, candidate: String): String? {
        val normalisedRoot = normalise(root).trimEnd(SEPARATOR).ifEmpty { ROOT }
        val resolved = resolve(normalisedRoot, candidate)

        if (resolved == normalisedRoot) return resolved
        val prefix = if (normalisedRoot == ROOT) ROOT else "$normalisedRoot$SEPARATOR"
        return if (resolved.startsWith(prefix)) resolved else null
    }

    /** True when [candidate] is inside [root] (or is [root] itself). */
    fun isInside(root: String, candidate: String): Boolean = confine(root, candidate) != null

    /** Path of [candidate] relative to [root], for display. */
    fun relativise(root: String, candidate: String): String {
        val normalisedRoot = normalise(root).trimEnd(SEPARATOR)
        val resolved = normalise(candidate)
        if (resolved == normalisedRoot) return "."
        val prefix = "$normalisedRoot$SEPARATOR"
        return if (resolved.startsWith(prefix)) resolved.removePrefix(prefix) else resolved
    }

    /**
     * True for files conventionally holding secrets. Used to withhold content from
     * AI context by default, not to prevent the user opening them.
     */
    fun looksSensitive(path: String): Boolean {
        val fileName = name(path).lowercase()
        return SENSITIVE_NAMES.any { fileName == it || fileName.startsWith("$it.") } ||
            SENSITIVE_SUFFIXES.any { fileName.endsWith(it) } ||
            fileName.startsWith(".env")
    }

    private val SENSITIVE_NAMES = setOf(
        "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "credentials", "secrets",
        ".netrc", ".pgpass", ".htpasswd", "authorized_keys", "known_hosts",
        "service-account", "terraform.tfvars",
    )

    private val SENSITIVE_SUFFIXES = setOf(
        ".pem", ".key", ".p12", ".pfx", ".jks", ".keystore", ".ppk",
    )
}
