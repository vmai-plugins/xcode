package digital.vmstudio.code.feature.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import digital.vmstudio.code.core.terminal.emulator.TerminalStyle
import digital.vmstudio.code.core.terminal.session.TerminalScreen
import kotlin.math.abs
import kotlin.math.floor

/**
 * Renders the terminal grid.
 *
 * Drawn on a Canvas rather than composed from Text nodes: a 40x100 screen is 4 000
 * cells, and a composable per cell would create thousands of layout nodes that
 * recompose on every frame of output. Canvas drawing costs one `drawText` per run
 * of identically-styled characters, which is what makes scrolling output smooth.
 */
@Composable
fun TerminalView(
    screen: TerminalScreen,
    palette: TerminalPalette,
    fontSizeSp: Float,
    backgroundColor: Color,
    foregroundColor: Color,
    cursorColor: Color,
    modifier: Modifier = Modifier,
    onSizeChanged: (columns: Int, rows: Int) -> Unit = { _, _ -> },
    onScroll: (rows: Int) -> Unit = {},
) {
    val density = LocalDensity.current

    // One Paint reused across frames; allocating per draw would churn the heap at
    // 30 fps.
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.MONOSPACE
        }
    }

    val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
    textPaint.textSize = fontSizePx

    val metrics = remember(fontSizePx) {
        val paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = fontSizePx
        }
        // Monospace guarantees a uniform advance, so measuring one glyph is enough.
        val advance = paint.measureText("M")
        val fontMetrics = paint.fontMetrics
        CellMetrics(
            width = advance,
            height = fontMetrics.descent - fontMetrics.ascent + LINE_SPACING_PX,
            baselineOffset = -fontMetrics.ascent,
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val columns = floor(widthPx / metrics.width).toInt().coerceAtLeast(1)
        val rows = floor(heightPx / metrics.height).toInt().coerceAtLeast(1)

        LaunchedEffect(columns, rows) {
            onSizeChanged(columns, rows)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    // TalkBack reads the visible text; the grid itself is not
                    // meaningfully navigable cell by cell.
                    contentDescription = screen.lines.joinToString("\n") { row ->
                        String(row.chars, 0, row.length).trimEnd()
                    }
                }
                .pointerInput(metrics.height) {
                    var accumulated = 0f
                    detectDragGestures(
                        onDragEnd = { accumulated = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount.y
                            // Dragging down reveals history, matching the direction
                            // of a scrollbar rather than of the content.
                            val rowsMoved = (accumulated / metrics.height).toInt()
                            if (abs(rowsMoved) >= 1) {
                                onScroll(rowsMoved)
                                accumulated -= rowsMoved * metrics.height
                            }
                        },
                    )
                },
        ) {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas

                screen.lines.forEachIndexed { rowIndex, row ->
                    val top = rowIndex * metrics.height
                    val baseline = top + metrics.baselineOffset

                    var column = 0
                    while (column < row.length) {
                        val style = row.styles[column]

                        // Extend the run while the style is identical, so a line of
                        // uncoloured output is a single drawText call.
                        var runEnd = column + 1
                        while (runEnd < row.length && row.styles[runEnd] == style) runEnd++

                        val runText = String(row.chars, column, runEnd - column)
                        val left = column * metrics.width

                        if (!palette.hasDefaultBackground(style)) {
                            drawRect(
                                color = palette.background(style),
                                topLeft = Offset(left, top),
                                size = Size((runEnd - column) * metrics.width, metrics.height),
                            )
                        }

                        if (runText.isNotBlank()) {
                            textPaint.color = palette.foreground(style).toArgb()
                            textPaint.isFakeBoldText =
                                TerminalStyle.hasFlag(style, TerminalStyle.FLAG_BOLD)
                            textPaint.isUnderlineText =
                                TerminalStyle.hasFlag(style, TerminalStyle.FLAG_UNDERLINE)
                            textPaint.isStrikeThruText =
                                TerminalStyle.hasFlag(style, TerminalStyle.FLAG_STRIKETHROUGH)
                            textPaint.textSkewX =
                                if (TerminalStyle.hasFlag(style, TerminalStyle.FLAG_ITALIC)) {
                                    ITALIC_SKEW
                                } else {
                                    0f
                                }
                            native.drawText(runText, left, baseline, textPaint)
                        }

                        column = runEnd
                    }
                }

                if (screen.cursorVisible && screen.cursorRow < screen.rows) {
                    drawRect(
                        color = cursorColor.copy(alpha = 0.75f),
                        topLeft = Offset(
                            screen.cursorColumn * metrics.width,
                            screen.cursorRow * metrics.height,
                        ),
                        size = Size(metrics.width, metrics.height),
                    )
                }
            }
        }
    }
}

private data class CellMetrics(
    val width: Float,
    val height: Float,
    val baselineOffset: Float,
)

/** A little leading; monospace metrics alone pack lines too tightly to scan. */
private const val LINE_SPACING_PX = 2f
private const val ITALIC_SKEW = -0.25f
