package digital.vmstudio.code.feature.terminal

import androidx.compose.ui.graphics.Color
import digital.vmstudio.code.core.terminal.emulator.TerminalStyle

/**
 * Resolves packed terminal colours to real ones.
 *
 * The 16 base colours are theme-owned so the terminal sits in the app's visual
 * language rather than looking like a pasted-in xterm; entries 16-255 follow the
 * standard xterm cube and greyscale ramp, which tools compute against directly and
 * which would look wrong if reinterpreted.
 */
class TerminalPalette(
    private val defaultForeground: Color,
    private val defaultBackground: Color,
) {

    fun foreground(style: Long): Color {
        val inverse = TerminalStyle.hasFlag(style, TerminalStyle.FLAG_INVERSE)
        val color = if (inverse) TerminalStyle.background(style) else TerminalStyle.foreground(style)
        val fallback = if (inverse) defaultBackground else defaultForeground
        val resolved = resolve(color, fallback)

        return when {
            TerminalStyle.hasFlag(style, TerminalStyle.FLAG_INVISIBLE) -> Color.Transparent
            // Dim is rendered as reduced alpha rather than a separate palette, which
            // keeps it correct for truecolour text too.
            TerminalStyle.hasFlag(style, TerminalStyle.FLAG_DIM) -> resolved.copy(alpha = 0.6f)
            else -> resolved
        }
    }

    fun background(style: Long): Color {
        val inverse = TerminalStyle.hasFlag(style, TerminalStyle.FLAG_INVERSE)
        val color = if (inverse) TerminalStyle.foreground(style) else TerminalStyle.background(style)
        val fallback = if (inverse) defaultForeground else defaultBackground
        return resolve(color, fallback)
    }

    /** True when the cell uses the default background and can be skipped when drawing. */
    fun hasDefaultBackground(style: Long): Boolean {
        if (TerminalStyle.hasFlag(style, TerminalStyle.FLAG_INVERSE)) return false
        return TerminalStyle.isDefault(TerminalStyle.background(style))
    }

    private fun resolve(color: Long, fallback: Color): Color = when {
        TerminalStyle.isRgb(color) -> Color(
            red = TerminalStyle.red(color),
            green = TerminalStyle.green(color),
            blue = TerminalStyle.blue(color),
        )
        TerminalStyle.isDefault(color) -> fallback
        else -> indexed(TerminalStyle.paletteIndex(color))
    }

    private fun indexed(index: Int): Color = when {
        index < 16 -> BASE_16[index]

        // 6x6x6 colour cube. The level table is xterm's, which is not linear:
        // the first step is 0 then 95, not 0 then 51.
        index < 232 -> {
            val offset = index - 16
            Color(
                red = CUBE_LEVELS[(offset / 36) % 6],
                green = CUBE_LEVELS[(offset / 6) % 6],
                blue = CUBE_LEVELS[offset % 6],
            )
        }

        index < 256 -> {
            val level = 8 + (index - 232) * 10
            Color(red = level, green = level, blue = level)
        }

        else -> fallback16(index)
    }

    private fun fallback16(index: Int): Color = BASE_16[index % 16]

    private companion object {
        val CUBE_LEVELS = intArrayOf(0, 95, 135, 175, 215, 255)

        /**
         * Base 16, tuned for a dark developer surface: the standard xterm values
         * for blue and red are close to unreadable on a dark background, so these
         * are lifted for contrast while keeping their hue identity.
         */
        val BASE_16 = arrayOf(
            Color(0xFF1D242F), // black
            Color(0xFFE05561), // red
            Color(0xFF52C77E), // green
            Color(0xFFD5A24A), // yellow
            Color(0xFF4FA6E0), // blue
            Color(0xFFB57BE8), // magenta
            Color(0xFF3FBFAC), // cyan
            Color(0xFFC6CDD6), // white
            Color(0xFF6B7686), // bright black
            Color(0xFFEF6B72), // bright red
            Color(0xFF6FDD97), // bright green
            Color(0xFFE0B75A), // bright yellow
            Color(0xFF6FC0F0), // bright blue
            Color(0xFFC99BF5), // bright magenta
            Color(0xFF5FD6C4), // bright cyan
            Color(0xFFF3F5F8), // bright white
        )
    }
}
