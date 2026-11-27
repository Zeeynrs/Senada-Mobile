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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.ResolvedTextDirection

@Composable
internal fun KaraokeText(
    timing: KaraokeTiming,
    positionMs: LongState,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var layout by remember(timing.text) { mutableStateOf<TextLayoutResult?>(null) }
    val sweep = remember(timing, positionMs) {
        Modifier.drawWithCache {
            val textLayout = layout
            if (textLayout == null) {
                onDrawWithContent { drawContent() }
            } else {
                val words = timing.words.map { WordShape(it, textLayout) }
                val fill = Path()
                val wordColor = Color.White.copy(alpha = if (timing.isEstimated) .25f else .40f)
                val fillColor = Color.White.copy(alpha = if (timing.isEstimated) .88f else 1f)
                onDrawWithContent {
                    drawContent()
                    // This is the only per-frame read by the text. Shape and layout are cached.
                    val position = positionMs.longValue
                    val active = timing.activeWordIndex(position)
                    if (active >= 0) clipPath(words[active].outline) { drawText(textLayout, wordColor) }

                    fill.rewind()
                    for (index in words.indices) {
                        words[index].appendFill(fill, position)
                    }
                    if (!fill.isEmpty) clipPath(fill) { drawText(textLayout, fillColor) }
                }
            }
        }
    }
    Text(timing.text, modifier.then(sweep), color = color, style = style, onTextLayout = { layout = it })
}

private class WordShape(private val word: KaraokeWord, layout: TextLayoutResult) {
    val outline = layout.getPathForRange(word.graphemeOffsets.first(), word.graphemeOffsets.last())
    private val glyphs = Array(word.graphemeCount) { index ->
        val start = word.graphemeOffsets[index]
        val end = word.graphemeOffsets[index + 1]
        GlyphShape(layout.getPathForRange(start, end).getBounds(), layout.getBidiRunDirection(start))
    }

    fun appendFill(path: Path, position: Long) {
        val progress = word.graphemesAt(position)
        if (progress <= 0.0) return
        if (progress >= glyphs.size) {
            path.addPath(outline)
            return
        }
        val complete = progress.toInt()
        for (index in 0 until complete) glyphs[index].appendTo(path, 1f)
        val fraction = (progress - complete).toFloat()
        if (fraction > 0f) glyphs[complete].appendTo(path, fraction)
    }
}

private class GlyphShape(private val bounds: Rect, private val direction: ResolvedTextDirection) {
    fun appendTo(path: Path, fraction: Float) {
        val width = bounds.width * fraction
        val left = if (direction == ResolvedTextDirection.Ltr) bounds.left else bounds.right - width
        val right = left + width
        path.moveTo(left, bounds.top)
        path.lineTo(right, bounds.top)
        path.lineTo(right, bounds.bottom)
        path.lineTo(left, bounds.bottom)
        path.close()
    }
}
