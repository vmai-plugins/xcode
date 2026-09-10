package digital.vmstudio.code.core.terminal.emulator

/**
 * Cell attributes packed into a single `Long`.
 *
 * A terminal with 5 000 lines of scrollback at 120 columns holds 600 000 cells.
 * One style *object* per cell would be tens of megabytes of heap and a
 * garbage-collection problem on a phone, so attributes are packed into a primitive
 * that lives in a `LongArray` alongside the characters.
 *
 * Layout (58 bits used of 64):
 * ```
 *   bits  0..24   foreground colour
 *   bits 25..49   background colour
 *   bits 50..57   attribute flags
 * ```
 * A colour is 25 bits: one mode bit plus a 24-bit value. In indexed mode the value
 * is a palette index 0..255, or [COLOR_DEFAULT] meaning "whatever the theme says".
 * In truecolour mode it is packed RGB.
 */
object TerminalStyle {

    const val FG_SHIFT = 0
    const val BG_SHIFT = 25
    const val FLAG_SHIFT = 50

    private const val COLOR_MASK = 0x1FFFFFFL // 25 bits
    private const val RGB_FLAG = 1L shl 24

    /** Sentinel for "use the theme's default", distinct from palette index 0. */
    const val COLOR_DEFAULT = 0xFFFFFF

    const val FLAG_BOLD = 1L shl 0
    const val FLAG_DIM = 1L shl 1
    const val FLAG_ITALIC = 1L shl 2
    const val FLAG_UNDERLINE = 1L shl 3
    const val FLAG_BLINK = 1L shl 4
    const val FLAG_INVERSE = 1L shl 5
    const val FLAG_INVISIBLE = 1L shl 6
    const val FLAG_STRIKETHROUGH = 1L shl 7

    val DEFAULT: Long = pack(
        foreground = indexed(COLOR_DEFAULT),
        background = indexed(COLOR_DEFAULT),
        flags = 0,
    )

    fun indexed(index: Int): Long = index.toLong() and COLOR_MASK

    fun rgb(red: Int, green: Int, blue: Int): Long =
        RGB_FLAG or ((red.toLong() and 0xFF) shl 16) or
            ((green.toLong() and 0xFF) shl 8) or (blue.toLong() and 0xFF)

    fun pack(foreground: Long, background: Long, flags: Long): Long =
        ((foreground and COLOR_MASK) shl FG_SHIFT) or
            ((background and COLOR_MASK) shl BG_SHIFT) or
            ((flags and 0xFF) shl FLAG_SHIFT)

    fun foreground(style: Long): Long = (style ushr FG_SHIFT) and COLOR_MASK

    fun background(style: Long): Long = (style ushr BG_SHIFT) and COLOR_MASK

    fun flags(style: Long): Long = (style ushr FLAG_SHIFT) and 0xFF

    fun withForeground(style: Long, color: Long): Long =
        pack(color, background(style), flags(style))

    fun withBackground(style: Long, color: Long): Long =
        pack(foreground(style), color, flags(style))

    fun withFlags(style: Long, flags: Long): Long =
        pack(foreground(style), background(style), flags)

    fun addFlag(style: Long, flag: Long): Long = withFlags(style, flags(style) or flag)

    fun removeFlag(style: Long, flag: Long): Long = withFlags(style, flags(style) and flag.inv())

    fun hasFlag(style: Long, flag: Long): Boolean = flags(style) and flag != 0L

    fun isRgb(color: Long): Boolean = color and RGB_FLAG != 0L

    fun isDefault(color: Long): Boolean = !isRgb(color) && color.toInt() == COLOR_DEFAULT

    /** Palette index, valid only when the colour is not RGB or default. */
    fun paletteIndex(color: Long): Int = (color and 0xFFFFFF).toInt()

    fun red(color: Long): Int = ((color ushr 16) and 0xFF).toInt()

    fun green(color: Long): Int = ((color ushr 8) and 0xFF).toInt()

    fun blue(color: Long): Int = (color and 0xFF).toInt()
}
