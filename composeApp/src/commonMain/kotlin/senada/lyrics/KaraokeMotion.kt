// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics

import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.exp
import kotlinx.coroutines.flow.first

internal data class KaraokeVisualWord(val start: Int, val end: Int, val splitsLetters: Boolean)

internal data class KaraokeMotionCell(val word: Int, val start: Int, val end: Int)

internal fun karaokeVisualWords(text: String, offsets: IntArray = graphemeOffsets(text)): List<KaraokeVisualWord> =
    buildList {
        var start = 0
        while (start < offsets.lastIndex) {
            val offset = offsets[start]
            if (text[offset].isWhitespace()) {
                start++
                continue
            }
            if (text.graphemeCodePoint(offset).isEastAsianGlyph()) {
                add(KaraokeVisualWord(start, start + 1, splitsLetters = false))
                start++
                continue
            }

            var end = start
            var splitsLetters = true
            while (end < offsets.lastIndex) {
                val nextOffset = offsets[end]
                val next = text[nextOffset]
                val codePoint = text.graphemeCodePoint(nextOffset)
                if (next.isWhitespace() || codePoint.isEastAsianGlyph()) break
                // Keep words in shaping-sensitive and unrecognized scripts together.
                if ((next.isLetter() || codePoint > 0xFFFF) && !codePoint.isUnjoinedLetter()) splitsLetters = false
                end++
            }
            add(KaraokeVisualWord(start, end, splitsLetters))
            start = end
        }
    }

internal fun karaokeMotionCells(
    words: List<KaraokeVisualWord>,
    wordOnly: (KaraokeVisualWord) -> Boolean = { false },
): List<KaraokeMotionCell> = buildList {
    for ((index, word) in words.withIndex()) {
        if (word.splitsLetters && !wordOnly(word)) {
            for (grapheme in word.start until word.end) add(KaraokeMotionCell(index, grapheme, grapheme + 1))
        } else {
            add(KaraokeMotionCell(index, word.start, word.end))
        }
    }
}

internal fun karaokeRiseTarget(head: Float, wordStart: Int, wordEnd: Int, cellStart: Int): Float {
    if (head >= wordEnd) return 1f
    val wordRise = smoothstep((head - wordStart) / (0.85f * (wordEnd - wordStart)))
    val letterRise = smoothstep((head - cellStart + 0.5f) / 3f)
    return 0.45f * wordRise + 0.55f * letterRise
}

internal fun karaokeHold(durationMs: Long, text: String, offsets: IntArray): Float {
    var weight = 0
    for (index in 0 until offsets.lastIndex) {
        val offset = offsets[index]
        if (!text[offset].isWhitespace()) weight += if (text.graphemeCodePoint(offset).isEastAsianGlyph()) 3 else 1
    }
    if (weight == 0) return 0f
    val msPerLetter = durationMs.toFloat() / weight
    return smoothstep((msPerLetter - 100f) / 240f)
}

internal fun karaokeSwellEnvelope(hold: Float, wordProgress: Float): Float =
    hold * smoothstep(minOf(wordProgress / 0.20f, (1f - wordProgress) / 0.12f))

internal fun karaokeCellSwell(head: Float, cellStart: Int, envelope: Float): Float =
    envelope * smoothstep((head - cellStart + 0.35f) / 1.4f)

internal fun karaokeEntryAmplitude(elapsedMs: Float, animationScale: Float): Float =
    if (animationScale <= 0f) 1f else smoothstep(elapsedMs / (180f * animationScale))

internal fun KaraokeTiming.snapKaraokeEntry(positionMs: Long): Boolean =
    // The composition can arrive a few frames into a natural cue. Allow the same margin as a clock jump.
    words.firstOrNull()?.let { positionMs - it.startMs > SEEK_JUMP_MS } == true

private fun smoothstep(fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal data class KaraokeSpringState(val value: Float, val velocity: Float = 0f) {
    // Solve the damped error curve over the whole frame. Integration stays stable when a frame is late.
    fun step(target: Float, dt: Float, followSeconds: Float): KaraokeSpringState {
        if (dt <= 0f || (value == target && velocity == 0f)) return this
        val error = target - value
        val rate = 1f / followSeconds
        val change = (rate * error - velocity) * dt
        val decay = exp(-rate * dt)
        val remaining = (error + change) * decay
        val speed = (velocity + rate * change) * decay
        if (error * remaining <= 0f || (abs(remaining) < 0.0005f && abs(speed) < 0.005f)) {
            return KaraokeSpringState(target)
        }
        return KaraokeSpringState(target - remaining, speed)
    }
}

internal class KaraokeMotion(
    private val timing: KaraokeTiming,
    private val words: List<KaraokeVisualWord>,
    val cells: List<KaraokeMotionCell>,
) {
    val drawFrame = mutableIntStateOf(0)
    var entryAmplitude = 0f
        private set
    private var entryStartNanos: Long? = null
    private val rise = Array(cells.size) { KaraokeSpringState(0f) }
    private val swell = Array(cells.size) { KaraokeSpringState(0f) }
    private val cueHolds = timing.words.map { karaokeHold(it.endMs - it.startMs, timing.text, it.graphemeOffsets) }
    private val cueEnvelopes = FloatArray(timing.words.size)
    private val cueForCell = IntArray(cells.size) { cell ->
        val startOffset = timing.offsets[cells[cell].start]
        val endOffset = timing.offsets[cells[cell].end]
        timing.words.indexOfFirst { it.graphemeOffsets.first() < endOffset && it.graphemeOffsets.last() > startOffset }
    }
    val cellForGrapheme = IntArray(timing.offsets.lastIndex) { -1 }.also { owners ->
        for ((index, cell) in cells.withIndex()) owners.fill(index, cell.start, cell.end)
    }

    fun riseAt(cell: Int): Float = rise[cell].value
    fun swellAt(cell: Int): Float = swell[cell].value

    suspend fun follow(positionMs: LongState, animationScale: Float) {
        var lastPosition = positionMs.longValue
        val initialEntry = if (entryStartNanos == null && timing.snapKaraokeEntry(lastPosition)) 1f else entryAmplitude
        var settling = advance(lastPosition, 0f, animationScale, snap = true, nextEntry = initialEntry)
        var previousFrame = 0L
        while (true) {
            if (!settling && lastPosition == positionMs.longValue) {
                snapshotFlow { positionMs.longValue }.first { it != lastPosition }
                previousFrame = 0L
            }
            val frame = withFrameNanos { it }
            val position = positionMs.longValue
            val dt = if (previousFrame == 0L) 0.016f else ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.064f)
            val seek = position < lastPosition || position - lastPosition > SEEK_JUMP_MS
            val nextEntry = if (entryAmplitude == 1f || (seek && timing.headAt(position) > 0f)) {
                1f
            } else {
                val start = entryStartNanos ?: frame.also { entryStartNanos = it }
                maxOf(entryAmplitude, karaokeEntryAmplitude((frame - start) / 1_000_000f, animationScale))
            }
            settling = advance(position, dt, animationScale, snap = seek, nextEntry = nextEntry)
            previousFrame = frame
            lastPosition = position
        }
    }

    private fun advance(position: Long, dt: Float, animationScale: Float, snap: Boolean, nextEntry: Float): Boolean {
        val head = timing.headAt(position)
        for ((index, cue) in timing.words.withIndex()) {
            cueEnvelopes[index] = if (cueHolds[index] > 0f && position >= cue.startMs && position < cue.endMs) {
                karaokeSwellEnvelope(cueHolds[index], (position - cue.startMs).toFloat() / (cue.endMs - cue.startMs))
            } else {
                0f
            }
        }

        var changed = entryAmplitude != nextEntry
        entryAmplitude = nextEntry
        var settling = nextEntry < 1f
        for ((index, cell) in cells.withIndex()) {
            val word = words[cell.word]
            val riseTarget = karaokeRiseTarget(head, word.start, word.end, cell.start)
            val cue = cueForCell[index]
            val swellTarget = if (cue < 0) 0f else karaokeCellSwell(head, cell.start, cueEnvelopes[cue])
            val oldRise = rise[index]
            val oldSwell = swell[index]
            val nextRise = if (snap || head >= word.end) {
                if (oldRise.value == riseTarget && oldRise.velocity == 0f) oldRise else KaraokeSpringState(riseTarget)
            } else {
                oldRise.step(riseTarget, dt, 0.08f * animationScale)
            }
            val nextSwell = if (snap) KaraokeSpringState(swellTarget) else oldSwell.step(swellTarget, dt, 0.20f * animationScale)
            rise[index] = nextRise
            swell[index] = nextSwell
            changed = changed || nextRise.value != oldRise.value || nextSwell.value != oldSwell.value
            settling = settling || nextRise.value != riseTarget || nextSwell.value != swellTarget ||
                nextRise.velocity != 0f || nextSwell.velocity != 0f
        }
        if (changed) drawFrame.intValue++
        return settling
    }
}

private const val SEEK_JUMP_MS = 150L

private fun String.graphemeCodePoint(offset: Int): Int {
    val first = this[offset].code
    val second = getOrNull(offset + 1)?.code ?: 0
    return if (first in 0xD800..0xDBFF && second in 0xDC00..0xDFFF) {
        0x10000 + ((first - 0xD800) shl 10) + (second - 0xDC00)
    } else {
        first
    }
}

private fun Int.isEastAsianGlyph(): Boolean = when (this) {
    in 0x1100..0x11FF, 0x3005, 0x3007, in 0x3040..0x30FF, in 0x31F0..0x31FF, in 0x3400..0x4DBF,
    in 0x4E00..0x9FFF, in 0xA960..0xA97F, in 0xAC00..0xD7FF, in 0xF900..0xFAFF, in 0xFF66..0xFF9F,
    in 0x1AFF0..0x1B16F, in 0x20000..0x2FA1F, in 0x30000..0x323AF -> true
    else -> false
}

private fun Int.isUnjoinedLetter(): Boolean = when (this) {
    // Latin, Greek, Cyrillic, Armenian and Georgian have independent letter shapes.
    in 0x0041..0x024F, in 0x0370..0x058F, in 0x10A0..0x10FF, in 0x1C80..0x1CBF, in 0x1D00..0x1FFF,
    in 0x2C60..0x2C7F, in 0x2D00..0x2D2F, in 0xA640..0xA7FF, in 0xAB30..0xAB6F, in 0xFF21..0xFF5A -> true
    else -> false
}
