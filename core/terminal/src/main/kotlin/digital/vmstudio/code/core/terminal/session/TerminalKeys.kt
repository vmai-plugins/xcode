package digital.vmstudio.code.core.terminal.session

/**
 * Keys a soft keyboard cannot produce but a shell needs.
 *
 * Encoded here rather than at the UI layer so the key bar, a hardware keyboard and
 * the AI agent all send byte-identical sequences.
 */
enum class TerminalKey {
    ENTER,
    TAB,
    BACKSPACE,
    ESCAPE,
    ARROW_UP,
    ARROW_DOWN,
    ARROW_LEFT,
    ARROW_RIGHT,
    HOME,
    END,
    PAGE_UP,
    PAGE_DOWN,
    INSERT,
    DELETE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
}

/**
 * Translates keys and modifiers into the bytes a remote shell expects.
 *
 * Control characters are built from their code points rather than written as
 * literals, so the source stays plain ASCII and cannot be corrupted by an editor or
 * a transfer that mangles control bytes.
 *
 * Two decisions worth stating:
 *
 *  - **Backspace sends DEL (0x7F), not BS (0x08).** This is what every modern
 *    terminal does and what `stty erase` defaults to on Linux and macOS; sending
 *    0x08 makes backspace do nothing in bash on many hosts.
 *  - **Application cursor mode is not tracked.** Normal-mode sequences (`ESC [ A`)
 *    are always sent. Full-screen programs that request application mode still
 *    accept these, so the cost is limited to a few programs' keypad behaviour,
 *    weighed against threading the mode back out of the emulator.
 */
object TerminalKeyEncoder {

    private val ESC: String = Char(0x1B).toString()
    private val CSI: String = ESC + "["
    private val SS3: String = ESC + "O"
    private val DEL: String = Char(0x7F).toString()

    fun encode(key: TerminalKey): String = when (key) {
        TerminalKey.ENTER -> "\r"
        TerminalKey.TAB -> "\t"
        TerminalKey.BACKSPACE -> DEL
        TerminalKey.ESCAPE -> ESC
        TerminalKey.ARROW_UP -> CSI + "A"
        TerminalKey.ARROW_DOWN -> CSI + "B"
        TerminalKey.ARROW_RIGHT -> CSI + "C"
        TerminalKey.ARROW_LEFT -> CSI + "D"
        TerminalKey.HOME -> CSI + "H"
        TerminalKey.END -> CSI + "F"
        TerminalKey.PAGE_UP -> CSI + "5~"
        TerminalKey.PAGE_DOWN -> CSI + "6~"
        TerminalKey.INSERT -> CSI + "2~"
        TerminalKey.DELETE -> CSI + "3~"
        TerminalKey.F1 -> SS3 + "P"
        TerminalKey.F2 -> SS3 + "Q"
        TerminalKey.F3 -> SS3 + "R"
        TerminalKey.F4 -> SS3 + "S"
        TerminalKey.F5 -> CSI + "15~"
        TerminalKey.F6 -> CSI + "17~"
        TerminalKey.F7 -> CSI + "18~"
        TerminalKey.F8 -> CSI + "19~"
        TerminalKey.F9 -> CSI + "20~"
        TerminalKey.F10 -> CSI + "21~"
        TerminalKey.F11 -> CSI + "23~"
        TerminalKey.F12 -> CSI + "24~"
    }

    /**
     * Control-key combination, e.g. Ctrl+C. Letters map to 0x01..0x1A; the handful
     * of non-letter control codes shells rely on are mapped explicitly. Returns
     * null when the combination has no control representation.
     */
    fun encodeControl(char: Char): String? {
        val upper = char.uppercaseChar()
        return when {
            upper in 'A'..'Z' -> Char(upper - 'A' + 1).toString()
            upper == '@' || upper == ' ' -> Char(0x00).toString()
            upper == '[' -> Char(0x1B).toString()
            upper == '\\' -> Char(0x1C).toString()
            upper == ']' -> Char(0x1D).toString()
            upper == '^' -> Char(0x1E).toString()
            upper == '_' || upper == '-' -> Char(0x1F).toString()
            upper == '?' -> DEL
            else -> null
        }
    }

    /** Alt/Meta is sent as an ESC prefix, which is what xterm does. */
    fun encodeAlt(text: String): String = ESC + text

    /** Ctrl+C. Offered as a first-class action in the key bar. */
    val INTERRUPT: String = Char(0x03).toString()

    /** Ctrl+D, end of transmission. */
    val END_OF_TRANSMISSION: String = Char(0x04).toString()

    /** Ctrl+Z, suspend. */
    val SUSPEND: String = Char(0x1A).toString()

    /** Ctrl+L, which most shells bind to clearing the screen. */
    val CLEAR_SCREEN: String = Char(0x0C).toString()
}
