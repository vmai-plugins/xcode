package digital.vmstudio.code.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.ui.theme.VmTheme

/**
 * Renders an assistant message as markdown.
 *
 * Everything is inside a [SelectionContainer] because the agent's output is routinely
 * a command or a patch the user needs verbatim; before this it rendered as flat,
 * unselectable text and had to be retyped by hand.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    selectable: Boolean = true,
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val spacing = VmTheme.spacing
    val quoteBar = MaterialTheme.colorScheme.outlineVariant

    @Composable
    fun Content() {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            blocks.forEach { block ->
                when (block) {
                    // Code sits outside the selection container: it has its own copy
                    // button, and a horizontal scroller inside a text selector fights
                    // the drag gesture.
                    is MarkdownBlock.CodeBlock -> DisableSelection {
                        CodeBlockView(block)
                    }

                    is MarkdownBlock.Heading -> {
                        Text(
                            text = block.spans.toAnnotated(),
                            style = when (block.level) {
                                1 -> MaterialTheme.typography.titleMedium
                                2 -> MaterialTheme.typography.titleSmall
                                else -> MaterialTheme.typography.labelLarge
                            },
                            color = color,
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier.padding(start = (block.depth * NEST_INDENT_DP).dp),
                        ) {
                            Text(
                                text = block.ordinal ?: "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = block.spans.toAnnotated(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = color,
                            )
                        }
                    }

                    is MarkdownBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = spacing.xs))

                    is MarkdownBlock.Quote -> {
                        Text(
                            text = block.spans.toAnnotated(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .drawBehind {
                                    drawRect(color = quoteBar, size = size.copy(width = QUOTE_BAR_DP.dp.toPx()))
                                }
                                .padding(start = spacing.sm),
                        )
                    }

                    is MarkdownBlock.Table -> TableView(block, color)

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = block.spans.toAnnotated(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                        )
                    }
                }
            }
        }
    }

    if (selectable) {
        SelectionContainer {
            Content()
        }
    } else {
        Content()
    }
}

/**
 * A table as one card per row: the first cell is the card's title and every other
 * cell is labelled with its column name. A phone is too narrow for a grid of long
 * cells; this is how the same content reads in Claude's own mobile app.
 */
@Composable
private fun TableView(table: MarkdownBlock.Table, color: Color) {
    val spacing = VmTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        table.rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                row.forEachIndexed { column, cell ->
                    if (cell.isEmpty()) return@forEachIndexed
                    val label = table.header.getOrNull(column).orEmpty()
                    if (column == 0) {
                        Text(
                            text = cell.toAnnotated(),
                            style = MaterialTheme.typography.titleSmall,
                            color = color,
                        )
                    } else {
                        Text(
                            text = buildAnnotatedString {
                                if (label.isNotEmpty()) {
                                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                        append(label.joinToString("") { it.text })
                                        append(": ")
                                    }
                                }
                                append(cell.toAnnotated())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val spacing = VmTheme.spacing
    val highlighted = remember(block.code) { highlightCodeSyntax(block.code) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(VmTheme.colors.codeSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                .padding(start = spacing.sm, end = spacing.xxs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language?.uppercase()?.ifBlank { "CODE" } ?: "CODE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(block.code)) },
                modifier = Modifier.size(30.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = highlighted,
            style = VmTheme.code.mono,
            color = MaterialTheme.colorScheme.onSurface,
            // Code must not reflow: a wrapped command line changes what it says.
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
        )
    }
}

private val CODE_KEYWORDS = Regex(
    "\\b(fun|val|var|class|object|interface|return|if|else|when|for|while|in|is|as|null|true|false|" +
        "suspend|private|public|internal|protected|override|data|sealed|enum|companion|import|package|" +
        "def|async|await|from|try|catch|finally|throw|raise|const|let|export|default|function|type)\\b",
)
private val CODE_STRINGS = Regex("\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'")
private val CODE_COMMENTS = Regex("(//.*$|#.*$)", RegexOption.MULTILINE)
private val CODE_NUMBERS = Regex("\\b\\d+(\\.\\d+)?\\b")

private fun highlightCodeSyntax(code: String): AnnotatedString = buildAnnotatedString {
    append(code)
    CODE_KEYWORDS.findAll(code).forEach { match ->
        addStyle(
            SpanStyle(color = Color(0xFFC792EA), fontWeight = FontWeight.SemiBold),
            match.range.first,
            match.range.last + 1,
        )
    }
    CODE_NUMBERS.findAll(code).forEach { match ->
        addStyle(SpanStyle(color = Color(0xFFF78C6C)), match.range.first, match.range.last + 1)
    }
    CODE_STRINGS.findAll(code).forEach { match ->
        addStyle(SpanStyle(color = Color(0xFFC3E88D)), match.range.first, match.range.last + 1)
    }
    CODE_COMMENTS.findAll(code).forEach { match ->
        addStyle(
            SpanStyle(color = Color(0xFF697098), fontStyle = FontStyle.Italic),
            match.range.first,
            match.range.last + 1,
        )
    }
}

@Composable
private fun List<MarkdownSpan>.toAnnotated(): AnnotatedString {
    val codeBackground = VmTheme.colors.codeSurface
    val codeColor = MaterialTheme.colorScheme.secondary
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                fontSize = if (span.code) INLINE_CODE_SIZE_SP.sp else TextUnit.Unspecified,
                color = if (span.code) codeColor else Color.Unspecified,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
            val url = span.url
            if (url == null) {
                withStyle(style) { append(span.text) }
            } else {
                val isSafe = url.startsWith("http://", ignoreCase = true) ||
                    url.startsWith("https://", ignoreCase = true) ||
                    url.startsWith("mailto:", ignoreCase = true)
                if (isSafe) {
                    val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(linkStyle))) { append(span.text) }
                } else {
                    withStyle(style.copy(color = linkColor)) { append(span.text) }
                }
            }
        }
    }
}

/** Monospace runs visually larger at the same size, so inline code is nudged down. */
private const val INLINE_CODE_SIZE_SP = 13
private const val NEST_INDENT_DP = 16
private const val QUOTE_BAR_DP = 3
