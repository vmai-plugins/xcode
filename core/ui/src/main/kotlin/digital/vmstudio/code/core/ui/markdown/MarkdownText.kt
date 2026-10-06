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
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val spacing = VmTheme.spacing
    val quoteBar = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        blocks.forEach { block ->
            when (block) {
                // Code sits outside the selection container: it has its own copy
                // button, and a horizontal scroller inside a text selector fights
                // the drag gesture.
                is MarkdownBlock.CodeBlock -> CodeBlockView(block)

                is MarkdownBlock.Heading -> SelectionContainer {
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

                is MarkdownBlock.BulletItem -> SelectionContainer {
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

                is MarkdownBlock.Quote -> SelectionContainer {
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

                is MarkdownBlock.Paragraph -> SelectionContainer {
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
            SelectionContainer {
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
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val clipboard = LocalClipboardManager.current
    val spacing = VmTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(VmTheme.colors.codeSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.sm, end = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy code",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SelectionContainer {
            Text(
                text = block.code,
                style = VmTheme.code.mono,
                color = MaterialTheme.colorScheme.onSurface,
                // Code must not reflow: a wrapped command line changes what it says.
                softWrap = false,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = spacing.sm)
                    .padding(bottom = spacing.sm),
            )
        }
    }
}

@Composable
private fun List<MarkdownSpan>.toAnnotated(): AnnotatedString {
    val codeBackground = VmTheme.colors.codeSurface
    val linkColor = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                fontSize = if (span.code) INLINE_CODE_SIZE_SP.sp else TextUnit.Unspecified,
                background = if (span.code) codeBackground else Color.Unspecified,
            )
            val url = span.url
            if (url == null) {
                withStyle(style) { append(span.text) }
            } else {
                val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                withLink(LinkAnnotation.Url(url, TextLinkStyles(linkStyle))) { append(span.text) }
            }
        }
    }
}

/** Monospace runs visually larger at the same size, so inline code is nudged down. */
private const val INLINE_CODE_SIZE_SP = 13
private const val NEST_INDENT_DP = 16
private const val QUOTE_BAR_DP = 3
