package digital.vmstudio.code.feature.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.terminal.session.TerminalKey
import digital.vmstudio.code.core.terminal.session.TerminalSessionState
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun TerminalRoute(
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = VmTheme.colors

    val palette = remember(colors.terminalForeground, colors.terminalBackground) {
        TerminalPalette(
            defaultForeground = colors.terminalForeground,
            defaultBackground = colors.terminalBackground,
        )
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        if (state.hasSessions) {
            TerminalTabBar(
                tabs = state.sessions,
                activeId = state.activeSessionId,
                onSelect = viewModel::selectSession,
                onClose = viewModel::closeSession,
                onNewSession = { viewModel.openSession(DEFAULT_COLUMNS, DEFAULT_ROWS) },
                onNavigateBack = onNavigateBack,
            )
        } else if (onNavigateBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VmTheme.spacing.xs, vertical = VmTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        }

        state.error?.let { error ->
            VmErrorPanel(
                error = error,
                modifier = Modifier.padding(VmTheme.spacing.md),
                onRetry = { viewModel.dismissError() },
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                !state.hasSessions && !state.isOpening -> TerminalStarter(
                    onOpen = { columns, rows -> viewModel.openSession(columns, rows) },
                )

                else -> TerminalView(
                    screen = state.screen,
                    palette = palette,
                    fontSizeSp = state.fontSizeSp,
                    backgroundColor = colors.terminalBackground,
                    foregroundColor = colors.terminalForeground,
                    cursorColor = colors.terminalCursor,
                    onSizeChanged = { columns, rows ->
                        if (state.hasSessions) {
                            viewModel.resize(columns, rows)
                        } else {
                            viewModel.openSession(columns, rows)
                        }
                    },
                    onScroll = viewModel::scrollBy,
                )
            }

            (state.sessionState as? TerminalSessionState.Failed)?.let { failed ->
                VmErrorPanel(
                    error = failed.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(VmTheme.spacing.md),
                )
            }

            if (state.sessionState == TerminalSessionState.Closed && state.hasSessions) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(VmTheme.spacing.md),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Session disconnected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Reconnect",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f))
                                .clickable { viewModel.openSession(DEFAULT_COLUMNS, DEFAULT_ROWS) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        if (state.hasSessions) {
            TerminalInputBar(
                onSendText = viewModel::send,
                onSendKey = viewModel::sendKey,
                onSendBackspaces = viewModel::sendBackspaces,
                onSendControl = viewModel::sendControl,
                onInterrupt = viewModel::interrupt,
                onClear = viewModel::clear,
            )
        }
    }
}

/**
 * Shown before a session exists. It measures the view first, then opens the shell
 * at the right size, so the remote prompt is never drawn at the wrong width.
 */
@Composable
private fun TerminalStarter(onOpen: (Int, Int) -> Unit) {
    VmEmptyState(
        icon = Icons.Default.Terminal,
        title = "No terminal open",
        description = "Start a shell on this server. Sessions keep running while you " +
            "work elsewhere in the app.",
        actionLabel = "Open terminal",
        onAction = { onOpen(DEFAULT_COLUMNS, DEFAULT_ROWS) },
    )
}

@Composable
private fun TerminalTabBar(
    tabs: List<TerminalTab>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewSession: () -> Unit,
    onNavigateBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VmTheme.spacing.xs, vertical = VmTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onNavigateBack != null) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = tab.id == activeId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onSelect(tab.id) },
                        )
                        .padding(start = VmTheme.spacing.sm),
                ) {
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    IconButton(
                        onClick = { onClose(tab.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close ${tab.title}",
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            IconButton(
                onClick = onNewSession,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New terminal session",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Text entry plus the keys a soft keyboard cannot produce.
 *
 * A hidden text field rather than per-key handling: Android soft keyboards do not
 * reliably deliver key events for printable characters, so the field captures text
 * and forwards each change to the shell, while the key row covers Esc, Tab, Ctrl
 * and the arrows that a shell needs and a phone keyboard lacks.
 */
@Composable
private fun TerminalInputBar(
    onSendText: (String) -> Unit,
    onSendKey: (TerminalKey) -> Unit,
    onSendBackspaces: (Int) -> Unit,
    onSendControl: (Char) -> Unit,
    onInterrupt: () -> Unit,
    onClear: () -> Unit,
) {
    var value by remember { mutableStateOf(EMPTY_INPUT) }
    var controlArmed by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(VmTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.xs),
        ) {
            KeyChip("Paste") {
                clipboard.getText()?.text?.let { onSendText(it) }
            }
            KeyChip("Esc") { onSendKey(TerminalKey.ESCAPE) }
            KeyChip("Tab") { onSendKey(TerminalKey.TAB) }
            KeyChip("Ctrl", isActive = controlArmed) { controlArmed = !controlArmed }
            KeyChip("^C") { onInterrupt() }
            KeyChip("^D") { onSendControl('D') }
            KeyChip("^Z") { onSendControl('Z') }
            KeyChip("↑") { onSendKey(TerminalKey.ARROW_UP) }
            KeyChip("↓") { onSendKey(TerminalKey.ARROW_DOWN) }
            KeyChip("←") { onSendKey(TerminalKey.ARROW_LEFT) }
            KeyChip("→") { onSendKey(TerminalKey.ARROW_RIGHT) }
            KeyChip("|") { onSendText("|") }
            KeyChip("/") { onSendText("/") }
            KeyChip("-") { onSendText("-") }
            KeyChip("~") { onSendText("~") }
            KeyChip("&") { onSendText("&") }
            KeyChip("$") { onSendText("$") }
            KeyChip("Home") { onSendKey(TerminalKey.HOME) }
            KeyChip("End") { onSendKey(TerminalKey.END) }
            KeyChip("Bksp") { onSendKey(TerminalKey.BACKSPACE) }
            KeyChip("Clear") { onClear() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VmTheme.spacing.sm),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { newValue ->
                    // Defer resetting until IME active composition completes to prevent duplicated keystrokes
                    val hasActiveComposition = newValue.composition != null && !newValue.composition!!.collapsed
                    if (hasActiveComposition) {
                        value = newValue
                        return@BasicTextField
                    }
                    val added = newValue.text.removePrefix(value.text).replace("\u200B", "")
                    // The field always holds one invisible character, so the keyboard's
                    // Backspace has something to delete; in an empty field it changed
                    // nothing, onValueChange never ran and Backspace did nothing.
                    when {
                        // Text was appended: forward just the new characters.
                        newValue.text.length > value.text.length && added.isNotEmpty() -> {
                            if (controlArmed && added.length == 1) {
                                onSendControl(added.first())
                                controlArmed = false
                            } else {
                                onSendText(added)
                            }
                        }
                        // Text was deleted: the shell owns the line buffer, so send
                        // as many backspaces as characters actually disappeared -
                        // predictive-text corrections and select-all-then-delete can
                        // remove more than one character in a single callback, and a
                        // single BACKSPACE regardless of count would leave stale
                        // characters on the remote prompt.
                        newValue.text.length < value.text.length -> {
                            onSendBackspaces(value.text.length - newValue.text.length)
                        }
                    }
                    // The field is a keystroke conduit, not a buffer; clearing it
                    // keeps it from accumulating a shadow copy of the command line.
                    value = EMPTY_INPUT
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when {
                                event.isCtrlPressed && event.key == Key.C -> { onInterrupt(); true }
                                event.isCtrlPressed && event.key == Key.D -> { onSendControl('D'); true }
                                event.isCtrlPressed && event.key == Key.Z -> { onSendControl('Z'); true }
                                event.isCtrlPressed && event.key == Key.L -> { onClear(); true }
                                event.key == Key.Tab -> { onSendKey(TerminalKey.TAB); true }
                                event.key == Key.Escape -> { onSendKey(TerminalKey.ESCAPE); true }
                                event.key == Key.DirectionUp -> { onSendKey(TerminalKey.ARROW_UP); true }
                                event.key == Key.DirectionDown -> { onSendKey(TerminalKey.ARROW_DOWN); true }
                                event.key == Key.DirectionLeft -> { onSendKey(TerminalKey.ARROW_LEFT); true }
                                event.key == Key.DirectionRight -> { onSendKey(TerminalKey.ARROW_RIGHT); true }
                                event.key == Key.Enter -> { onSendKey(TerminalKey.ENTER); true }
                                event.key == Key.MoveHome -> { onSendKey(TerminalKey.HOME); true }
                                event.key == Key.MoveEnd -> { onSendKey(TerminalKey.END); true }
                                else -> false
                            }
                        } else false
                    },
                textStyle = VmTheme.code.mono.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(
                    onSend = { onSendKey(TerminalKey.ENTER) },
                ),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (value.text == EMPTY_INPUT.text) {
                            Text(
                                text = "Type a command",
                                style = VmTheme.code.mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )

            IconButton(onClick = { keyboard?.show(); focusRequester.requestFocus() }) {
                Icon(Icons.Default.Keyboard, contentDescription = "Show keyboard")
            }
            IconButton(onClick = { onSendKey(TerminalKey.ENTER) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Send return",
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun KeyChip(
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tonalElevation = 2.dp,
        modifier = Modifier.height(32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        ) {
            Text(
                text = label,
                style = VmTheme.code.mono.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
            )
        }
    }
}

private const val DEFAULT_COLUMNS = 80
private const val DEFAULT_ROWS = 24

/** One zero-width space with the cursor after it: what a Backspace deletes. */
private val EMPTY_INPUT = TextFieldValue("\u200B", selection = TextRange(1))
