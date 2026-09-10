package digital.vmstudio.code.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours a developer tool needs that Material's `ColorScheme` has no slot for:
 * connection state, Git file status, diff backgrounds, terminal chrome.
 *
 * Exposed through a `CompositionLocal` so screens never hardcode a hex value and
 * light/dark switching stays a single decision made in [VmTheme].
 */
@Immutable
data class VmSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    /** Agent/AI surfaces, kept visually distinct from ordinary content. */
    val agent: Color,
    val agentContainer: Color,
    val onAgentContainer: Color,

    val connected: Color,
    val connecting: Color,
    val disconnected: Color,
    val degraded: Color,

    val gitAdded: Color,
    val gitModified: Color,
    val gitDeleted: Color,
    val gitUntracked: Color,
    val gitConflicted: Color,
    val gitRenamed: Color,

    val diffAddedBackground: Color,
    val diffRemovedBackground: Color,
    val diffAddedGutter: Color,
    val diffRemovedGutter: Color,

    val terminalBackground: Color,
    val terminalForeground: Color,
    val terminalSelection: Color,
    val terminalCursor: Color,

    val editorGutter: Color,
    val editorCurrentLine: Color,

    /** Hairline separators; deliberately lower contrast than `outline`. */
    val divider: Color,
    /** Background for inline code and path chips. */
    val codeSurface: Color,
)

internal val DarkSemanticColors = VmSemanticColors(
    success = Palette.Green70,
    onSuccess = Palette.Neutral04,
    successContainer = Palette.GreenContainerDark,
    onSuccessContainer = Palette.Green90,

    warning = Palette.Amber70,
    onWarning = Palette.Neutral04,
    warningContainer = Palette.AmberContainerDark,
    onWarningContainer = Palette.Amber90,

    info = Palette.Blue70,
    infoContainer = Palette.Blue20,
    onInfoContainer = Palette.Blue90,

    agent = Palette.Violet70,
    agentContainer = Palette.Violet20,
    onAgentContainer = Palette.Violet90,

    connected = Palette.Green70,
    connecting = Palette.Amber70,
    disconnected = Palette.Neutral50,
    degraded = Palette.Amber70,

    gitAdded = Palette.Green70,
    gitModified = Palette.Amber70,
    gitDeleted = Palette.Red70,
    gitUntracked = Palette.Neutral70,
    gitConflicted = Palette.Red70,
    gitRenamed = Palette.Blue70,

    diffAddedBackground = Palette.DiffAddedDark,
    diffRemovedBackground = Palette.DiffRemovedDark,
    diffAddedGutter = Palette.Green70,
    diffRemovedGutter = Palette.Red70,

    terminalBackground = Palette.Neutral04,
    terminalForeground = Palette.Neutral85,
    terminalSelection = Palette.Blue30,
    terminalCursor = Palette.Blue70,

    editorGutter = Palette.Neutral50,
    editorCurrentLine = Palette.Neutral14,

    divider = Palette.Neutral20,
    codeSurface = Palette.Neutral10,
)

internal val LightSemanticColors = VmSemanticColors(
    success = Palette.Green40,
    onSuccess = Palette.Neutral100,
    successContainer = Palette.Green90,
    onSuccessContainer = Color(0xFF07240F),

    warning = Palette.Amber40,
    onWarning = Palette.Neutral100,
    warningContainer = Palette.Amber90,
    onWarningContainer = Color(0xFF2C1D00),

    info = Palette.Blue40,
    infoContainer = Palette.Blue90,
    onInfoContainer = Color(0xFF032133),

    agent = Palette.Violet40,
    agentContainer = Palette.Violet90,
    onAgentContainer = Color(0xFF1B1235),

    connected = Palette.Green40,
    connecting = Palette.Amber40,
    disconnected = Palette.Neutral50,
    degraded = Palette.Amber40,

    gitAdded = Palette.Green40,
    gitModified = Palette.Amber40,
    gitDeleted = Palette.Red40,
    gitUntracked = Palette.Neutral50,
    gitConflicted = Palette.Red40,
    gitRenamed = Palette.Blue40,

    diffAddedBackground = Palette.DiffAddedLight,
    diffRemovedBackground = Palette.DiffRemovedLight,
    diffAddedGutter = Palette.Green40,
    diffRemovedGutter = Palette.Red40,

    // The terminal stays dark in light mode: a light terminal misreads ANSI
    // colour output, which is authored for dark backgrounds.
    terminalBackground = Palette.Neutral06,
    terminalForeground = Palette.Neutral85,
    terminalSelection = Palette.Blue30,
    terminalCursor = Palette.Blue60,

    editorGutter = Palette.Neutral50,
    editorCurrentLine = Palette.Neutral97,

    divider = Palette.Neutral94,
    codeSurface = Palette.Neutral97,
)

val LocalVmSemanticColors = staticCompositionLocalOf { DarkSemanticColors }
