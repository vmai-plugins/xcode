package digital.vmstudio.code.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.editor.ColorScheme
import digital.vmstudio.code.core.editor.SyntaxHighlighter
import digital.vmstudio.code.core.editor.SyntaxHighlighters
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

    // Auto-commit changes after typing pauses (for undo checkpoints)
    LaunchedEffect(state.snapshot?.textFieldValue?.text) {
        delay(500)
        viewModel.commitChange()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.filePath.substringAfterLast('/'),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${state.serverName} · ${state.filePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    state.snapshot?.let { snapshot ->
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = snapshot.canUndo,
                        ) {
                            Icon(
                                Icons.Default.Undo,
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
                                Icons.Default.Redo,
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
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.loadError != null -> {
                    VmErrorPanel(
                        error = state.loadError!!,
                        onRetry = { viewModel.loadFile() },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                state.snapshot != null -> {
                    EditorContent(
                        snapshot = state.snapshot!!,
                        isSaving = state.isSaving,
                        saveError = state.saveError,
                        onValueChange = viewModel::onTextFieldValueChange,
                        onClearSaveError = viewModel::clearSaveError,
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
    onValueChange: (TextFieldValue) -> Unit,
    onClearSaveError: () -> Unit,
) {
    val spacing = VmTheme.spacing
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val lineCount = snapshot.textFieldValue.text.lines().size
    val gutterWidth = (lineCount.toString().length * 8 + 16).dp

    Column(modifier = Modifier.fillMaxSize()) {
        saveError?.let { err ->
            VmErrorPanel(
                error = err,
                actions = listOf(
                    digital.vmstudio.code.core.ui.component.VmErrorAction(
                        label = "Dismiss",
                        onClick = onClearSaveError,
                    ),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
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
                for (lineNum in 1..lineCount) {
                    Text(
                        text = lineNum.toString(),
                        style = VmTheme.code.mono.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
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
                    textStyle = VmTheme.code.mono.copy(
                        fontSize = 12.sp,
                        color = colorScheme.plain,
                    ),
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
