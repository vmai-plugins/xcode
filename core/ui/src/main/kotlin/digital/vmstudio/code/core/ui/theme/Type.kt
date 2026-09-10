package digital.vmstudio.code.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Sizes are in `sp` throughout so the system font-size setting scales the whole UI
 * (accessibility requirement). Line heights are set explicitly rather than left to
 * defaults, because dense technical text with default leading is hard to scan.
 */
internal val VmTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Monospace styles for code, terminal output, paths and hashes.
 *
 * Kept out of `Typography` because Material's slots carry semantics ("body",
 * "title") that do not describe code, and because the editor and terminal need to
 * vary size independently of the rest of the UI.
 */
@Immutable
data class VmCodeTypography(
    val terminal: TextStyle,
    val editor: TextStyle,
    val inlineCode: TextStyle,
    val diff: TextStyle,
    /** Small monospace for paths, branch names, fingerprints. */
    val mono: TextStyle,
) {
    companion object {
        fun default(codeFontSizeSp: Float = 13f, terminalFontSizeSp: Float = 12.5f) =
            VmCodeTypography(
                terminal = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = terminalFontSizeSp.sp,
                    lineHeight = (terminalFontSizeSp * 1.35f).sp,
                    letterSpacing = 0.sp,
                ),
                editor = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = codeFontSizeSp.sp,
                    lineHeight = (codeFontSizeSp * 1.45f).sp,
                ),
                inlineCode = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                diff = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                ),
                mono = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
            )
    }
}

val LocalVmCodeTypography = staticCompositionLocalOf { VmCodeTypography.default() }
