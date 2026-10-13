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

    // Primary accent ramp: vibrant electric indigo-blue for primary actions & interactive elements.
    val Clay20 = Color(0xFF13204A)
    val Clay30 = Color(0xFF1E326E)
    val Clay40 = Color(0xFF3B63E6)
    val Clay70 = Color(0xFF688BFF)
    val Clay90 = Color(0xFFDCE5FF)

    val Blue20 = Color(0xFF072A47)
    val Blue30 = Color(0xFF0C416D)
    val Blue40 = Color(0xFF0E6EB8)
    val Blue60 = Color(0xFF2196F3)
    val Blue70 = Color(0xFF47B2FF)
    val Blue80 = Color(0xFF88CEFF)
    val Blue90 = Color(0xFFD0ECFF)

    // Secondary: vibrant cyan-teal for supporting badges & tool highlights.
    val Teal20 = Color(0xFF053330)
    val Teal40 = Color(0xFF0D746B)
    val Teal70 = Color(0xFF2EE5D0)
    val Teal80 = Color(0xFF7CF2E3)
    val Teal90 = Color(0xFFCBFBF4)

    // Tertiary: luminous violet for AI/agent surfaces.
    val Violet20 = Color(0xFF26184E)
    val Violet40 = Color(0xFF5D3FD3)
    val Violet70 = Color(0xFFA88BFF)
    val Violet80 = Color(0xFFC9B6FF)
    val Violet90 = Color(0xFFECE5FF)

    // Neutrals (crisp midnight blue-slate IDE surfaces).
    val Neutral04 = Color(0xFF0B0E14)
    val Neutral06 = Color(0xFF10151F)
    val Neutral10 = Color(0xFF161C28)
    val Neutral14 = Color(0xFF1E2636)
    val Neutral20 = Color(0xFF293347)
    val Neutral30 = Color(0xFF3D4B66)
    val Neutral50 = Color(0xFF6B7A99)
    val Neutral70 = Color(0xFFA0AEC7)
    val Neutral85 = Color(0xFFD5DFEE)
    val Neutral94 = Color(0xFFF0F4FA)
    val Neutral97 = Color(0xFFF7F9FC)
    val Neutral100 = Color(0xFFFFFFFF)

    // Status ramp. Chosen for >= 4.5:1 against their paired containers.
    val Green40 = Color(0xFF167945)
    val Green70 = Color(0xFF34D399)
    val Green90 = Color(0xFFD1FAE5)
    val GreenContainerDark = Color(0xFF093121)

    val Amber40 = Color(0xFF946200)
    val Amber70 = Color(0xFFFBBF24)
    val Amber90 = Color(0xFFFEF3C7)
    val AmberContainerDark = Color(0xFF3D2804)

    val Red40 = Color(0xFFB91C1C)
    val Red70 = Color(0xFFF87171)
    val Red90 = Color(0xFFFEE2E2)
    val RedContainerDark = Color(0xFF421014)
    val RedContainerLight = Color(0xFF3B080C)

    // Diff colours, tuned so added/removed lines stay readable behind text.
    val DiffAddedDark = Color(0xFF0B3020)
    val DiffRemovedDark = Color(0xFF3A1219)
    val DiffAddedLight = Color(0xFFDCFCE7)
    val DiffRemovedLight = Color(0xFFFEE2E2)
}
