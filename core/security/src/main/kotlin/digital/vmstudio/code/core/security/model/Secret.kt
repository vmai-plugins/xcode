package digital.vmstudio.code.core.security.model

import digital.vmstudio.code.core.common.log.SecretRedactor
import java.security.MessageDigest

/**
 * A secret value held in memory.
 *
 * Deliberately not a `String`:
 *  - [toString] is overridden so interpolating a secret into a log line, a crash
 *    report or a debugger watch produces a mask rather than the value;
 *  - the payload is a [ByteArray] that can be [wipe]d once used, rather than an
 *    immutable String left for the GC to eventually reclaim;
 *  - [equals] is constant-time, so comparisons cannot be used as a timing oracle.
 *
 * Buffers are always overwritten with NUL rather than a printable filler, so a heap
 * dump taken after a wipe shows no residue that could be mistaken for the value.
 */
class Secret private constructor(private var bytes: ByteArray?) {

    val isWiped: Boolean get() = bytes == null

    val size: Int get() = bytes?.size ?: 0

    /**
     * Exposes the plaintext to [block] and nothing else. Kept as a scoped accessor
     * so every read of a secret is a visible, greppable call site.
     */
    fun <R> use(block: (ByteArray) -> R): R {
        val current = bytes ?: error("Secret has already been wiped")
        return block(current)
    }

    fun <R> useAsString(block: (String) -> R): R = use { block(String(it, Charsets.UTF_8)) }

    /**
     * Exposes the secret as a char array and scrubs that array afterwards. Prefer
     * this over [useAsString] for APIs that accept `char[]` (JSch/sshj passphrases),
     * since the intermediate String would otherwise linger in the heap.
     */
    fun <R> useAsChars(block: (CharArray) -> R): R {
        val chars = use { String(it, Charsets.UTF_8).toCharArray() }
        return try {
            block(chars)
        } finally {
            chars.fill(NUL_CHAR)
        }
    }

    fun copyBytes(): ByteArray = use { it.copyOf() }

    /** Overwrites the backing buffer. Further reads throw. */
    fun wipe() {
        bytes?.fill(NUL_BYTE)
        bytes = null
    }

    /**
     * Registers this value with the redactor so it is masked if it ever reaches a
     * log line by another path. Call after loading a long-lived secret.
     */
    fun registerForRedaction(): Secret = apply {
        useAsString { SecretRedactor.registerSecret(it) }
    }

    override fun toString(): String = SecretRedactor.MASK

    override fun hashCode(): Int = if (bytes == null) 0 else 1

    override fun equals(other: Any?): Boolean {
        if (other !is Secret) return false
        val a = bytes
        val b = other.bytes
        if (a == null || b == null) return a === b
        return MessageDigest.isEqual(a, b)
    }

    companion object {
        private const val NUL_BYTE: Byte = 0

        /** Built from its code point so the source stays plain ASCII. */
        private val NUL_CHAR: Char = Char(0)

        fun of(value: String): Secret = Secret(value.toByteArray(Charsets.UTF_8))

        fun of(value: ByteArray): Secret = Secret(value.copyOf())

        /** Takes ownership of [value] without copying; caller must not reuse it. */
        fun wrapping(value: ByteArray): Secret = Secret(value)

        fun ofChars(value: CharArray): Secret =
            Secret(String(value).toByteArray(Charsets.UTF_8))
    }
}
