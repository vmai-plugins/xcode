package digital.vmstudio.code.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * VMStudio Code palette.
 *
 * Dark is the primary target: this is a tool developers use in a terminal-adjacent
 * headspace, often at length. The neutrals are slightly blue-shifted slate rather
 * than pure grey so large surfaces read as "editor chrome" rather than washed-out
 * paper, and the accent is a saturated cyan-blue that stays legible against both
 * the dark and light neutrals without becoming a highlighter.
 *
 * Nothing in the app may reference these constants directly. They are wired into
 * `ColorScheme` and [VmSemanticColors]; components consume the theme.
 */
internal object Palette {

    // Accent ramp.
    val Blue20 = Color(0xFF03293F)
    val Blue30 = Color(0xFF06405F)
    val Blue40 = Color(0xFF0A5A83)
    val Blue60 = Color(0xFF1793C7)
    val Blue70 = Color(0xFF3FB0DE)
    val Blue80 = Color(0xFF7FCCEC)
    val Blue90 = Color(0xFFC5E7F7)

    // Secondary: a muted teal used for supporting emphasis.
    val Teal20 = Color(0xFF04302C)
    val Teal40 = Color(0xFF0B6157)
    val Teal70 = Color(0xFF3FBFAC)
    val Teal80 = Color(0xFF7FD8CB)
    val Teal90 = Color(0xFFC8EDE7)

    // Tertiary: violet, reserved for AI/agent surfaces so agent activity is
    // instantly distinguishable from ordinary tool output.
    val Violet20 = Color(0xFF231A45)
    val Violet40 = Color(0xFF473485)
    val Violet70 = Color(0xFF9B87E8)
    val Violet80 = Color(0xFFBFB1F1)
    val Violet90 = Color(0xFFE2DBF9)

    // Neutrals (blue-shifted slate).
    val Neutral04 = Color(0xFF0B0F14)
    val Neutral06 = Color(0xFF10151C)
    val Neutral10 = Color(0xFF161C25)
    val Neutral14 = Color(0xFF1D242F)
    val Neutral20 = Color(0xFF27303D)
    val Neutral30 = Color(0xFF3A4553)
    val Neutral50 = Color(0xFF6B7686)
    val Neutral70 = Color(0xFF9AA4B2)
    val Neutral85 = Color(0xFFC6CDD6)
    val Neutral94 = Color(0xFFE6EAEF)
    val Neutral97 = Color(0xFFF3F5F8)
    val Neutral100 = Color(0xFFFFFFFF)

    // Status ramp. Chosen for >= 4.5:1 against their paired containers.
    val Green40 = Color(0xFF1B6B3A)
    val Green70 = Color(0xFF48C97C)
    val Green90 = Color(0xFFC9EFD8)
    val GreenContainerDark = Color(0xFF0C2F1A)

    val Amber40 = Color(0xFF8A5A00)
    val Amber70 = Color(0xFFE0A429)
    val Amber90 = Color(0xFFF7E2B4)
    val AmberContainerDark = Color(0xFF3A2703)

    val Red40 = Color(0xFF9B1C24)
    val Red70 = Color(0xFFEF6B72)
    val Red90 = Color(0xFFF9D4D6)
    val RedContainerDark = Color(0xFF3E0E12)
    val RedContainerLight = Color(0xFF3B080C)

    // Diff colours, tuned so added/removed lines stay readable behind text.
    val DiffAddedDark = Color(0xFF0E2E1B)
    val DiffRemovedDark = Color(0xFF34131A)
    val DiffAddedLight = Color(0xFFDCF6E5)
    val DiffRemovedLight = Color(0xFFFBE0E3)
}
