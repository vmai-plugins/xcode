package digital.vmstudio.code.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.editor.ColorScheme
import digital.vmstudio.code.core.editor.SyntaxHighlighter
import digital.vmstudio.code.core.editor.SyntaxHighlighters
import digital.vmstudio.code.core.ui.component.VmErrorAction
import digital.vmstudio.code.core.ui.component.VmErrorPanel
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.delay

/**
 * Remote file editor with syntax highlighting, line numbers, undo/redo, and
 * save. Files are loaded from and saved to the server via SFTP.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing

    // Unsaved edits live only in this screen; leaving must not drop them silently.
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (state.snapshot?.isDirty == true) confirmDiscard = true else onNavigateBack() }
    BackHandler(enabled = state.snapshot?.isDirty == true) { confirmDiscard = true }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes to the server before leaving?") },
            confirmButton = {
                TextButton(
                    enabled = state.canSave,
                    onClick = {
                        confirmDiscard = false
                        viewModel.save(onSuccess = onNavigateBack)
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") }
                    TextButton(onClick = {
                        confirmDiscard = false
                        onNavigateBack()
                    }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }

    // Auto-commit changes after typing pauses (for undo checkpoints)
    LaunchedEffect(state.snapshot?.textFieldValue?.text) {
        delay(500)
        viewModel.commitChange()
    }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var isReplaceVisible by remember { mutableStateOf(false) }
    var showGoToLineDialog by remember { mutableStateOf(false) }
    var targetLineInput by remember { mutableStateOf("") }

    if (showGoToLineDialog) {
        AlertDialog(
            onDismissRequest = { showGoToLineDialog = false },
            title = { Text("Go to Line") },
            text = {
                OutlinedTextField(
                    value = targetLineInput,
                    onValueChange = { targetLineInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Line number") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = targetLineInput.toIntOrNull()
                        if (num != null && num > 0) {
                            viewModel.goToLine(num)
                        }
                        showGoToLineDialog = false
                    },
                ) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLineDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = state.filePath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (state.snapshot?.isDirty == true) {
                                    Text(
                                        text = " ●",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                            Text(
                                text = "${state.serverName} · ${state.filePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = leave) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchOpen = !isSearchOpen }) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Find",
                                tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { showGoToLineDialog = true }) {
                            Icon(
                                Icons.Default.FormatListNumbered,
                                contentDescription = "Go to line",
                            )
                        }
                        state.snapshot?.let { snapshot ->
                            IconButton(
                                onClick = viewModel::undo,
                                enabled = snapshot.canUndo,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (snapshot.canUndo) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                            IconButton(
                                onClick = viewModel::redo,
                                enabled = snapshot.canRedo,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (snapshot.canRedo) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                            IconButton(
                                onClick = viewModel::save,
                                enabled = state.canSave,
                            ) {
                                Icon(
                                    Icons.Default.Save,
                                    contentDescription = "Save",
                                    tint = if (state.canSave) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                )
                            }
                        }
                    },
                )

                if (isSearchOpen) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.xs)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = {
                                        searchQuery = it
                                        if (it.isNotEmpty()) viewModel.findNext(it, forward = true)
                                    },
                                    placeholder = { Text("Find...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { viewModel.findNext(searchQuery, forward = false) }) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous")
                                }
                                IconButton(onClick = { viewModel.findNext(searchQuery, forward = true) }) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next")
                                }
                                TextButton(onClick = { isReplaceVisible = !isReplaceVisible }) {
                                    Text(if (isReplaceVisible) "Hide" else "Replace")
                                }
                                IconButton(onClick = { isSearchOpen = false }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close search")
                                }
                            }

                            if (isReplaceVisible) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                ) {
                                    OutlinedTextField(
                                        value = replaceQuery,
                                        onValueChange = { replaceQuery = it },
                                        placeholder = { Text("Replace with...") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { viewModel.replace(searchQuery, replaceQuery, all = false) }) {
                                        Text("Replace")
                                    }
                                    TextButton(onClick = { viewModel.replace(searchQuery, replaceQuery, all = true) }) {
                                        Text("All")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge windows no longer resize for the keyboard.
                .imePadding(),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.loadError != null -> {
                    val errorArgs = state.loadError ?: return@Box
                    VmErrorPanel(
                        error = errorArgs,
                        onRetry = { viewModel.loadFile() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.snapshot != null -> {
                    val snapshotArgs = state.snapshot ?: return@Box
                    EditorContent(
                        snapshot = snapshotArgs,
                        isSaving = state.isSaving,
                        saveError = state.saveError,
                        hasConflict = state.hasConflict,
                        onOverwrite = { viewModel.save(overwrite = true) },
                        onValueChange = viewModel::onTextFieldValueChange,
                        onClearSaveError = viewModel::clearSaveError,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorContent(
    snapshot: digital.vmstudio.code.core.editor.EditorSnapshot,
    isSaving: Boolean,
    saveError: VmError?,
    hasConflict: Boolean,
    onOverwrite: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onClearSaveError: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val spacing = VmTheme.spacing
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val text = snapshot.textFieldValue.text
    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }
    // The size chosen in Settings; mono at a fixed 12sp ignored it.
    val baseStyle = VmTheme.code.editor
    val fontSp = baseStyle.fontSize.value.coerceAtLeast(10f)
    val editorStyle = baseStyle.copy(
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    )
    val gutterWidth = (lineCount.toString().length * (fontSp * 0.65f) + 20).dp

    Column(modifier = Modifier.fillMaxSize()) {
        saveError?.let { err ->
            VmErrorPanel(
                error = err,
                actions = listOfNotNull(
                    VmErrorAction(label = "Overwrite", onClick = onOverwrite).takeIf { hasConflict },
                    VmErrorAction(label = "Dismiss", onClick = onClearSaveError),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // Line number gutter
            Column(
                modifier = Modifier
                    .widthIn(min = gutterWidth)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .verticalScroll(verticalScrollState)
                    .padding(end = spacing.sm),
                horizontalAlignment = Alignment.End,
            ) {
                // One text block in the editor's own style, so each number sits on
                // its line at any font size instead of drifting further down.
                Text(
                    text = lineNumbers,
                    softWrap = false,
                    style = editorStyle.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.End,
                    ),
                    modifier = Modifier.padding(vertical = spacing.xs),
                )
            }

            // Editor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                val colorScheme = editorColorScheme()
                val highlighter = SyntaxHighlighters.forLanguage(snapshot.language)
                val highlight = remember(highlighter, colorScheme) {
                    SyntaxHighlightTransformation(highlighter, colorScheme)
                }

                BasicTextField(
                    value = snapshot.textFieldValue,
                    onValueChange = onValueChange,
                    textStyle = editorStyle.copy(color = colorScheme.plain),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = highlight,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState)
                        .padding(horizontal = spacing.sm, vertical = spacing.xs),
                )

                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(spacing.sm),
                    )
                }
            }
        }

        DeveloperSymbolBar(
            onInsert = { symbol, cursorOffset ->
                val current = snapshot.textFieldValue
                val text = current.text
                val selection = current.selection
                val newText = text.replaceRange(selection.min, selection.max, symbol)
                val newCursor = (selection.min + cursorOffset).coerceIn(0, newText.length)
                onValueChange(
                    current.copy(
                        text = newText,
                        selection = androidx.compose.ui.text.TextRange(newCursor),
                    ),
                )
            },
            onUndo = onUndo,
            onRedo = onRedo,
            canUndo = snapshot.canUndo,
            canRedo = snapshot.canRedo,
        )
    }
}

/**
 * Applies [SyntaxHighlighter] tokens as span styles over the editor text.
 *
 * Tokenisation runs on every keystroke; the highlighters are single-pass regex
 * scanners and the editor caps file size on load, so this stays cheap for the
 * files a phone editor is actually used on. Offsets are identity-mapped — the
 * transformation only colours, it never changes the text.
 */
private class SyntaxHighlightTransformation(
    private val highlighter: SyntaxHighlighter,
    private val scheme: ColorScheme,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        // Re-tokenising a very large file on every keystroke freezes the screen.
        if (source.length > MAX_HIGHLIGHT_CHARS) return TransformedText(text, OffsetMapping.Identity)
        val tokens = runCatching { highlighter.tokenize(source, scheme) }
            .getOrDefault(emptyList())
        val annotated = buildAnnotatedString {
            append(source)
            tokens.forEach { token ->
                val start = token.start.coerceIn(0, source.length)
                val end = token.end.coerceIn(start, source.length)
                if (end > start) addStyle(token.style, start, end)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}

private const val MAX_HIGHLIGHT_CHARS = 60_000

@Composable
private fun editorColorScheme(): ColorScheme {
    val colors = MaterialTheme.colorScheme
    val vmColors = VmTheme.colors
    return ColorScheme(
        keyword = vmColors.info,
        string = vmColors.success,
        number = vmColors.warning,
        comment = colors.onSurfaceVariant.copy(alpha = 0.6f),
        function = vmColors.info,
        type = vmColors.warning,
        plain = colors.onSurface,
    )
}

@Composable
private fun DeveloperSymbolBar(
    onInsert: (String, Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier,
) {
    val symbols = remember {
        listOf(
            "Tab" to ("    " to 4),
            "{ }" to ("{}" to 1),
            "( )" to ("()" to 1),
            "[ ]" to ("[]" to 1),
            "\" \"" to ("\"\"" to 1),
            "' '" to ("''" to 1),
            "//" to ("// " to 3),
            "/* */" to ("/*  */" to 3),
            "< >" to ("<>" to 1),
            ";" to (";" to 1),
            ":" to (":" to 1),
            "=" to ("=" to 1),
            "." to ("." to 1),
            "," to ("," to 1),
            "_" to ("_" to 1),
            "->" to ("->" to 2),
            "=>" to ("=>" to 2),
            "$" to ("$" to 1),
            "&" to ("&" to 1),
            "|" to ("|" to 1),
            "!" to ("!" to 1),
            "?" to ("?" to 1),
            "/" to ("/" to 1),
            "\\" to ("\\" to 1),
            "+" to ("+" to 1),
            "-" to ("-" to 1),
            "*" to ("*" to 1),
            "%" to ("%" to 1),
            "@" to ("@" to 1),
            "#" to ("#" to 1),
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Quick Undo & Redo docked right above keyboard for mobile thumb reach
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier.height(34.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.padding(2.dp),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier.height(34.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.padding(2.dp),
                    tint = if (canRedo) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .widthIn(min = 1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
            symbols.forEach { (label, insertion) ->
                val (symbol, offset) = insertion
                Surface(
                    onClick = { onInsert(symbol, offset) },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.height(34.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    ) {
                        Text(
                            text = label,
                            style = VmTheme.code.mono.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        }
    }
}


