// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import kotlin.math.abs

internal class KaraokeMotionLayout(
    layout: TextLayoutResult,
    offsets: IntArray,
    rows: List<KaraokeSweepRow>,
    private val motion: KaraokeMotion,
    private val fontPx: Float,
    private val dropPx: Float,
    private val liftPx: Float,
) {
    private val lines: List<MotionLine>
    private var drawnFrame = Int.MIN_VALUE
    private var uniformDrop: Float? = 0f

    init {
        val length = layout.layoutInput.text.length
        val boxes = FloatArray(length * 4)
        layout.multiParagraph.fillBoundingBoxes(TextRange(0, length), boxes, 0)
        lines = rows.mapIndexed { index, row ->
            val glyphs = buildList {
                for (grapheme in row.startGrapheme until row.endGrapheme) {
                    val owner = motion.cellForGrapheme[grapheme]
                    if (owner < 0) continue
                    val box = offsets[grapheme] * 4
                    val left = boxes[box]
                    val right = boxes[box + 2]
                    if (left.isFinite() && right > left && right.isFinite()) add(Glyph(owner, left, right))
                }
            }.sortedBy { it.center }
            val slices = buildList {
                var start = 0
                var left = row.bounds.left
                while (start < glyphs.size) {
                    val glyph = glyphs[start]
                    var end = start + 1
                    var inkLeft = glyph.left
                    var inkRight = glyph.right
                    // Physically adjoining glyphs owned by the same cell can be transformed as one.
                    while (end < glyphs.size && glyphs[end].cell == glyph.cell) {
                        inkLeft = minOf(inkLeft, glyphs[end].left)
                        inkRight = maxOf(inkRight, glyphs[end].right)
                        end++
                    }
                    val next = glyphs.getOrNull(end)
                    val right = if (next == null) row.bounds.right else {
                        val previous = glyphs[end - 1]
                        ((previous.right + next.left) * 0.5f)
                            .coerceIn(previous.center, next.center)
                            .coerceIn(left, row.bounds.right)
                    }
                    add(Slice(glyph.cell, left, right, inkLeft, inkRight))
                    start = end
                    left = right
                }
            }
            val words = slices.groupBy { motion.cells[it.cell].word }.values.map(::MotionWord)
            MotionLine(row, layout.getLineBaseline(index), slices, words)
        }
    }

    /** Returns one translation when the line can be drawn intact. */
    fun prepare(): Float? {
        val frame = motion.drawFrame.intValue
        if (frame == drawnFrame) return uniformDrop
        drawnFrame = frame
        var firstY: Float? = null
        var uniform = true
        val waitingDropPx = dropPx * motion.entryAmplitude
        for (line in lines) {
            for (word in line.words) {
                var total = 0f
                for (cell in word.cells) total += motion.swellAt(cell)
                val meanSwell = total / word.cells.size
                word.glowAlpha = 0.16f * meanSwell
                for (slice in word.slices) {
                    val swell = motion.swellAt(slice.cell).takeIf { it >= 0.002f } ?: 0f
                    val rise = motion.riseAt(slice.cell)
                    val lift = waitingDropPx * (1f - rise) - liftPx * swell
                    val spread = (slice.pivot - word.center) * SWELL_SCALE * meanSwell
                    slice.zoom = 1f + SWELL_SCALE * swell
                    slice.x = spread + (1f - slice.zoom) * slice.pivot
                    slice.y = lift + (1f - slice.zoom) * line.baseline
                    if (firstY == null) firstY = slice.y
                    if (slice.zoom != 1f || abs(slice.x) > MERGE_PX || abs(slice.y - firstY) > MERGE_PX) uniform = false
                }
            }
        }
        uniformDrop = if (uniform) firstY ?: 0f else null
        return uniformDrop
    }

    fun DrawScope.drawMotion(layer: GraphicsLayer, head: Float) {
        for (line in lines) {
            for (word in line.words) {
                if (word.glowAlpha < 0.01f) continue
                val trailingX = line.row.headXAt(head) - fontPx * 0.45f * line.row.flow
                val center = Offset(trailingX.coerceIn(word.left, word.right), line.baseline - fontPx * 0.35f)
                val radius = ((word.right - word.left) * 0.65f).coerceIn(fontPx * 0.8f, fontPx * 2f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color.White.copy(alpha = word.glowAlpha), Color.Transparent), center, radius),
                    radius = radius,
                    center = center,
                )
            }
        }

        for (line in lines) {
            var start = 0
            while (start < line.slices.size) {
                val slice = line.slices[start]
                var end = start + 1
                while (end < line.slices.size && slice.sameTransform(line.slices[end])) end++
                val right = line.slices[end - 1].right
                withTransform({
                    translate(slice.x, slice.y)
                    scale(slice.zoom, slice.zoom, pivot = Offset.Zero)
                    // Clip in source coordinates so a cell cannot pick up ink from an adjacent row.
                    clipRect(slice.left, line.row.bounds.top, right, line.row.bounds.bottom)
                }) {
                    drawLayer(layer)
                }
                start = end
            }
        }
    }
}

private class Glyph(val cell: Int, val left: Float, val right: Float) {
    val center = (left + right) * 0.5f
}

private class Slice(val cell: Int, val left: Float, val right: Float, val inkLeft: Float, val inkRight: Float) {
    val pivot = (inkLeft + inkRight) * 0.5f
    var x = 0f
    var y = 0f
    var zoom = 1f

    fun sameTransform(other: Slice): Boolean = zoom == other.zoom && abs(x - other.x) <= MERGE_PX && abs(y - other.y) <= MERGE_PX
}

private class MotionWord(val slices: List<Slice>) {
    val cells = slices.map { it.cell }.distinct().toIntArray()
    val left = slices.minOf { it.inkLeft }
    val right = slices.maxOf { it.inkRight }
    val center = (left + right) * 0.5f
    var glowAlpha = 0f
}

private class MotionLine(val row: KaraokeSweepRow, val baseline: Float, val slices: List<Slice>, val words: List<MotionWord>)

private const val SWELL_SCALE = 0.075f
private const val MERGE_PX = 0.05f
