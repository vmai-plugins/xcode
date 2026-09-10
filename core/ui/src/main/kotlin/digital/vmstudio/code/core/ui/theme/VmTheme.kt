package digital.vmstudio.code.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/** User-selectable appearance. Mirrors the Settings > Appearance options. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Palette.Blue70,
    onPrimary = Palette.Neutral04,
    primaryContainer = Palette.Blue30,
    onPrimaryContainer = Palette.Blue90,
    inversePrimary = Palette.Blue40,

    secondary = Palette.Teal70,
    onSecondary = Palette.Neutral04,
    secondaryContainer = Palette.Teal20,
    onSecondaryContainer = Palette.Teal90,

    tertiary = Palette.Violet70,
    onTertiary = Palette.Neutral04,
    tertiaryContainer = Palette.Violet20,
    onTertiaryContainer = Palette.Violet90,

    background = Palette.Neutral04,
    onBackground = Palette.Neutral94,
    surface = Palette.Neutral06,
    onSurface = Palette.Neutral94,
    surfaceVariant = Palette.Neutral14,
    onSurfaceVariant = Palette.Neutral70,
    surfaceContainerLowest = Palette.Neutral04,
    surfaceContainerLow = Palette.Neutral06,
    surfaceContainer = Palette.Neutral10,
    surfaceContainerHigh = Palette.Neutral14,
    surfaceContainerHighest = Palette.Neutral20,

    outline = Palette.Neutral30,
    outlineVariant = Palette.Neutral20,

    error = Palette.Red70,
    onError = Palette.Neutral04,
    errorContainer = Palette.RedContainerDark,
    onErrorContainer = Palette.Red90,

    inverseSurface = Palette.Neutral94,
    inverseOnSurface = Palette.Neutral06,
    scrim = Palette.Neutral04,
)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Palette.Blue40,
    onPrimary = Palette.Neutral100,
    primaryContainer = Palette.Blue90,
    onPrimaryContainer = Palette.Blue20,
    inversePrimary = Palette.Blue70,

    secondary = Palette.Teal40,
    onSecondary = Palette.Neutral100,
    secondaryContainer = Palette.Teal90,
    onSecondaryContainer = Palette.Teal20,

    tertiary = Palette.Violet40,
    onTertiary = Palette.Neutral100,
    tertiaryContainer = Palette.Violet90,
    onTertiaryContainer = Palette.Violet20,

    background = Palette.Neutral100,
    onBackground = Palette.Neutral10,
    surface = Palette.Neutral100,
    onSurface = Palette.Neutral10,
    surfaceVariant = Palette.Neutral97,
    onSurfaceVariant = Palette.Neutral50,
    surfaceContainerLowest = Palette.Neutral100,
    surfaceContainerLow = Palette.Neutral97,
    surfaceContainer = Palette.Neutral97,
    surfaceContainerHigh = Palette.Neutral94,
    surfaceContainerHighest = Palette.Neutral94,

    outline = Palette.Neutral50,
    outlineVariant = Palette.Neutral85,

    error = Palette.Red40,
    onError = Palette.Neutral100,
    errorContainer = Palette.Red90,
    onErrorContainer = Palette.RedContainerLight,

    inverseSurface = Palette.Neutral10,
    inverseOnSurface = Palette.Neutral97,
    scrim = Palette.Neutral04,
)

/**
 * Root theme.
 *
 * Dynamic colour is deliberately not offered: server environment badges, Git status
 * and diff colouring all carry meaning, and letting the wallpaper repaint them
 * would make the UI less legible, not more personal.
 */
@Composable
fun VmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    codeFontSizeSp: Float = 13f,
    terminalFontSizeSp: Float = 12.5f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (dark) DarkColorScheme else LightColorScheme
    val semanticColors = if (dark) DarkSemanticColors else LightSemanticColors
    val codeTypography = remember(codeFontSizeSp, terminalFontSizeSp) {
        VmCodeTypography.default(codeFontSizeSp, terminalFontSizeSp)
    }

    CompositionLocalProvider(
        LocalVmSemanticColors provides semanticColors,
        LocalVmCodeTypography provides codeTypography,
        LocalVmSpacing provides VmSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VmTypography,
            shapes = VmShapes,
            content = content,
        )
    }
}

/** Convenience accessors so screens read `VmTheme.colors.connected`. */
object VmTheme {

    val colors: VmSemanticColors
        @Composable @ReadOnlyComposable get() = LocalVmSemanticColors.current

    val code: VmCodeTypography
        @Composable @ReadOnlyComposable get() = LocalVmCodeTypography.current

    val spacing: VmSpacing
        @Composable @ReadOnlyComposable get() = LocalVmSpacing.current
}
