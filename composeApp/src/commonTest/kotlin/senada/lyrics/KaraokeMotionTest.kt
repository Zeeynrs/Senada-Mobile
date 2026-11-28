package senada.lyrics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableLongStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class KaraokeMotionTest {
    @Test
    fun entryDropStartsAtTheBaselineAndEasesOverScaledTime() {
        assertEquals(0f, karaokeEntryAmplitude(0f, animationScale = 1f))
        assertEquals(0.15625f, karaokeEntryAmplitude(45f, animationScale = 1f))
        assertEquals(0.5f, karaokeEntryAmplitude(90f, animationScale = 1f))
        assertEquals(0.84375f, karaokeEntryAmplitude(135f, animationScale = 1f))
        assertEquals(1f, karaokeEntryAmplitude(180f, animationScale = 1f))
        assertEquals(1f, karaokeEntryAmplitude(300f, animationScale = 1f))
        assertEquals(0.15625f, karaokeEntryAmplitude(90f, animationScale = 2f))
        assertEquals(1f, karaokeEntryAmplitude(90f, animationScale = 0.5f))
    }

    @Test
    fun entryDropSnapsForAJoinWellIntoTheCue() {
        val timing = karaokeTiming(LyricLine(1_000L, "Hold this", endMs = 3_000L), null)

        assertFalse(timing.snapKaraokeEntry(1_016L))
        assertFalse(timing.snapKaraokeEntry(1_150L))
        assertTrue(timing.snapKaraokeEntry(1_151L))
        assertTrue(timing.snapKaraokeEntry(1_800L))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun entryDropFinishesWhilePlaybackIsPaused() = runTest {
        val timing = karaokeTiming(LyricLine(1_000L, "Hold this", endMs = 3_000L), null)
        val words = karaokeVisualWords(timing.text, timing.offsets)
        val motion = KaraokeMotion(timing, words, karaokeMotionCells(words))
        val position = mutableLongStateOf(1_000L)
        val frames = Channel<Long>(Channel.UNLIMITED)
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
        }
        backgroundScope.launch(clock) { motion.follow(position, animationScale = 1f) }
        runCurrent()

        assertEquals(0f, motion.entryAmplitude)
        frames.send(1_000_000_000L)
        runCurrent()
        assertEquals(0f, motion.entryAmplitude)
        val initialDrawFrame = motion.drawFrame.intValue

        frames.send(1_090_000_000L)
        runCurrent()
        assertEquals(0.5f, motion.entryAmplitude)
        assertTrue(motion.drawFrame.intValue > initialDrawFrame)

        frames.send(1_180_000_000L)
        runCurrent()
        assertEquals(1f, motion.entryAmplitude)
        assertEquals(1_000L, position.longValue)
    }

    @Test
    fun riseBlendsTheWordAndLetterTargets() {
        assertEquals(0f, karaokeRiseTarget(1.5f, wordStart = 2, wordEnd = 12, cellStart = 2))
        // At 6.25 the word is halfway through its rise and the fifth letter is a quarter in.
        assertEquals(0.3109375f, karaokeRiseTarget(6.25f, 2, 12, 6), absoluteTolerance = 0.00001f)
        assertEquals(1f, karaokeRiseTarget(10.5f, 2, 12, 8))
        assertTrue(karaokeRiseTarget(11.75f, 2, 12, 11) < 1f)
        assertEquals(1f, karaokeRiseTarget(12f, 2, 12, 11))
    }

    @Test
    fun holdUsesWeightedGraphemeCount() {
        fun hold(durationMs: Long, text: String) = karaokeHold(durationMs, text, graphemeOffsets(text))

        assertEquals(0f, hold(400L, "fast"))
        assertEquals(0.5f, hold(880L, "hold"), absoluteTolerance = 0.00001f)
        assertEquals(1f, hold(1_400L, "held"))
        assertEquals(1f, hold(680L, "ab"))
        assertEquals(0.5f, hold(1_320L, "空か"), absoluteTolerance = 0.00001f)
        assertEquals(0.5f, hold(660L, "한"), absoluteTolerance = 0.00001f)
        assertEquals(1f, hold(400L, "e\u0301"))
    }

    @Test
    fun swellAttacksAndReleasesAtTheSpecifiedCueFractions() {
        assertEquals(0f, karaokeSwellEnvelope(0.8f, 0f))
        assertEquals(0.4f, karaokeSwellEnvelope(0.8f, 0.1f), absoluteTolerance = 0.00001f)
        assertEquals(0.8f, karaokeSwellEnvelope(0.8f, 0.2f), absoluteTolerance = 0.00001f)
        assertEquals(0.8f, karaokeSwellEnvelope(0.8f, 0.88f), absoluteTolerance = 0.00001f)
        assertEquals(0.4f, karaokeSwellEnvelope(0.8f, 0.94f), absoluteTolerance = 0.00001f)
        assertEquals(0f, karaokeSwellEnvelope(0.8f, 1f))
        assertEquals(0f, karaokeSwellEnvelope(0f, 0.5f))
    }

    @Test
    fun swellReachesEachCellInHeadOrder() {
        assertEquals(0.75f, karaokeCellSwell(4.35f, cellStart = 3, envelope = 0.75f))
        assertEquals(0.375f, karaokeCellSwell(4.35f, cellStart = 4, envelope = 0.75f), absoluteTolerance = 0.00001f)
        assertEquals(0f, karaokeCellSwell(4.35f, cellStart = 5, envelope = 0.75f))
    }

    @Test
    fun springConvergesWithoutOvershootAcrossFrameTimes() {
        for (dt in listOf(0.001f, 0.016f, 0.033f, 0.064f)) {
            for (follow in listOf(0.08f, 0.2f)) {
                var state = KaraokeSpringState(0f)
                assertEquals(state, state.step(1f, 0f, follow))
                repeat((3f / dt).toInt()) {
                    val next = state.step(1f, dt, follow)
                    assertTrue(next.value in state.value..1f)
                    state = next
                }
                assertEquals(1f, state.value, absoluteTolerance = 0.001f)
                assertEquals(0f, state.velocity, absoluteTolerance = 0.005f)
            }
        }
    }

    @Test
    fun springDoesNotOvershootWhenTheTargetReverses() {
        val rising = KaraokeSpringState(0f).step(1f, 0.064f, 0.08f)
        val target = rising.value + 0.01f
        assertTrue(rising.step(target, 0.064f, 0.08f).value <= target)

        val falling = KaraokeSpringState(1f).step(0f, 0.064f, 0.08f)
        assertTrue(falling.step(rising.value, 0.064f, 0.08f).value >= rising.value)
    }

    @Test
    fun latinCellsKeepCombiningMarksWithTheirLetter() {
        val words = karaokeVisualWords("e\u0301lan go")
        assertEquals(listOf(0 to 4, 5 to 7), words.map { it.start to it.end })
        assertEquals(listOf(0, 1, 2, 3, 5, 6), karaokeMotionCells(words).map { it.start })
        assertEquals(listOf(1, 2, 3, 4, 6, 7), karaokeMotionCells(words).map { it.end })
        assertEquals(listOf(0 to 4, 5 to 7), karaokeMotionCells(words) { true }.map { it.start to it.end })
    }

    @Test
    fun cjkGraphemesBecomeIndependentVisualWords() {
        val words = karaokeVisualWords("空かな한𠮷")
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5), words.map { it.start to it.end })
        assertEquals(listOf(0, 1, 2, 3, 4), karaokeMotionCells(words).map { it.word })
    }

    @Test
    fun shapingSensitiveScriptsMoveAsWholeWords() {
        val words = karaokeVisualWords("سلام عالم नमस्ते ภาษาไทย")
        assertEquals(4, words.size)
        assertEquals(words.map { it.start to it.end }, karaokeMotionCells(words).map { it.start to it.end })
    }
}
