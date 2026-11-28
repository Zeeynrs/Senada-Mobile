package senada.lyrics

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
internal fun KaraokeText(
    timing: KaraokeTiming,
    positionMs: LongState,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var layout by remember(timing.text) { mutableStateOf<TextLayoutResult?>(null) }
    val litLayer = rememberGraphicsLayer()
    val sweep = remember(timing, positionMs, litLayer) {
        Modifier.drawWithCache {
            val textLayout = layout
            if (textLayout == null) {
                onDrawWithContent { drawContent() }
            } else {
                val bleed = 4.dp.roundToPx()
                val rows = textLayout.sweepRows(timing.offsets, bleed.toFloat())
                val words = timing.words.map { word ->
                    Path().apply {
                        // Keep the emphasis within the glyph boxes at soft wraps.
                        for (index in 0 until word.graphemeCount) {
                            val bounds = textLayout.getBoundingBox(word.graphemeOffsets[index])
                            if (!bounds.isEmpty) addRect(bounds)
                        }
                    }
                }
                val fill = Path()
                val wordColor = Color.White.copy(alpha = if (timing.isEstimated) .25f else .40f)
                val fillColor = Color.White.copy(alpha = if (timing.isEstimated) .88f else 1f)
                val fontSize = textLayout.layoutInput.style.fontSize
                val featherMax = (if (fontSize.isSp) fontSize else 14.sp).toPx() * FEATHER_EM
                // An offscreen buffer clips to its own size, so reserve room for outer glyph overhang.
                val layerSize = IntSize(textLayout.size.width + 2 * bleed, textLayout.size.height + 2 * bleed)
                litLayer.compositingStrategy = CompositingStrategy.Offscreen
                litLayer.topLeft = IntOffset(-bleed, -bleed)
                onDrawWithContent {
                    drawContent()
                    // This is the only per-frame read by the text. Shape and layout are cached.
                    val position = positionMs.longValue
                    val active = timing.activeWordIndex(position)
                    if (active >= 0) clipPath(words[active]) { drawText(textLayout, wordColor) }

                    val head = timing.headAt(position)
                    fill.rewind()
                    var edgeRow: KaraokeSweepRow? = null
                    for (row in rows) {
                        fill.addRect(row.fillBounds(head, EDGE_LEAD_PX) ?: break)
                        if (head < row.endGrapheme) {
                            edgeRow = row
                            break
                        }
                    }
                    if (fill.isEmpty) return@onDrawWithContent

                    litLayer.record(this, layoutDirection, layerSize) {
                        translate(bleed.toFloat(), bleed.toFloat()) {
                            clipPath(fill) { drawText(textLayout, fillColor) }
                            edgeRow?.let { row ->
                                val headX = row.headXAt(head)
                                val width = minOf(abs(headX - row.headXAt(head - 1f)), featherMax)
                                val fromX = headX - row.flow * width
                                val toX = headX + row.flow * EDGE_LEAD_PX
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Black,
                                        1f to Color.Transparent,
                                        startX = fromX,
                                        endX = toX,
                                    ),
                                    topLeft = Offset(minOf(fromX, toX), row.bounds.top),
                                    size = Size(abs(toX - fromX), row.bounds.height),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                    }
                    drawLayer(litLayer)
                }
            }
        }
    }
    Text(timing.text, modifier.then(sweep), color = color, style = style, onTextLayout = { layout = it })
}

private fun TextLayoutResult.sweepRows(offsets: IntArray, bleed: Float): List<KaraokeSweepRow> =
    List(lineCount) { line ->
        val startOffset = getLineStart(line)
        val start = offsets.boundaryAtOrAfter(startOffset)
        val end = offsets.boundaryAtOrAfter(getLineEnd(line, visibleEnd = true))
        val rtl = getParagraphDirection(startOffset) == ResolvedTextDirection.Rtl
        val left = getLineLeft(line)
        val right = getLineRight(line)
        val startX = if (rtl) right else left
        val endX = if (rtl) left else right
        val carets = FloatArray(end - start + 1)
        carets[0] = startX
        for (index in 1 until carets.lastIndex) {
            val x = getHorizontalPosition(offsets[start + index], usePrimaryDirection = true)
            val previousX = carets[index - 1]
            // A bidirectional run can send the caret backward. Keep the fill moving in the row's direction.
            carets[index] = if (rtl) x.coerceIn(endX, previousX) else x.coerceIn(previousX, endX)
        }
        // At a soft wrap, the caret after the last glyph can belong to the following line.
        carets[carets.lastIndex] = endX
        KaraokeSweepRow(
            start,
            carets,
            Rect(left, getLineTop(line), right, getLineBottom(line)),
            rtl,
            bleed,
            isFirst = line == 0,
            isLast = line == lineCount - 1,
        )
    }

internal class KaraokeSweepRow(
    private val startGrapheme: Int,
    private val carets: FloatArray,
    lineBounds: Rect,
    private val rtl: Boolean,
    bleed: Float,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val endGrapheme = startGrapheme + carets.lastIndex
    val flow = if (rtl) -1f else 1f
    val bounds = Rect(
        lineBounds.left - bleed,
        lineBounds.top - if (isFirst) bleed else 0f,
        lineBounds.right + bleed,
        lineBounds.bottom + if (isLast) bleed else 0f,
    )

    fun headXAt(head: Float): Float {
        val intoRow = (head - startGrapheme).coerceIn(0f, carets.lastIndex.toFloat())
        val index = intoRow.toInt()
        val x = carets[index]
        val nextX = carets[minOf(index + 1, carets.lastIndex)]
        return x + (nextX - x) * (intoRow - index)
    }

    fun fillBounds(head: Float, lead: Float): Rect? {
        if (head <= startGrapheme) return null
        if (head >= endGrapheme) return bounds
        val edge = headXAt(head) + flow * lead
        return if (rtl) bounds.copy(left = maxOf(bounds.left, edge)) else bounds.copy(right = minOf(bounds.right, edge))
    }
}

private const val EDGE_LEAD_PX = 6f
private const val FEATHER_EM = 0.55f
