package senada.lyrics

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KaraokeTimingTest {
    @Test
    fun sourceWordsKeepTheirExactStartEndAndSilentGap() {
        val lines = LrcParser.parse(
            """
                <tt><body><p begin="1s" end="4s"><span begin="1s" end="2s">Day</span> <span begin="2.5s" end="4s">break</span></p>
                <p begin="4s" end="6s">Next line</p></body></tt>
            """.trimIndent(),
        )
        val timing = karaokeTiming(lines.first(), lines[1].startMs)

        assertFalse(timing.isEstimated)
        assertEquals(listOf(1_000L to 2_000L, 2_500L to 4_000L), timing.words.map { it.startMs to it.endMs })
        assertEquals(-1, activeLineIndex(lines, 999L))
        assertEquals(0, activeLineIndex(lines, 1_000L))
        assertEquals(0, activeLineIndex(lines, 3_999L))
        assertEquals(1, activeLineIndex(lines, 4_000L))
        assertEquals(-1, timing.activeWordIndex(999L))
        assertEquals(0, timing.activeWordIndex(1_000L))
        assertEquals(0, timing.activeWordIndex(1_999L))
        assertEquals(-1, timing.activeWordIndex(2_000L))
        assertEquals(-1, timing.activeWordIndex(2_499L))
        assertEquals(1, timing.activeWordIndex(2_500L))
        assertEquals(-1, timing.activeWordIndex(4_000L))
        assertEquals(1.5, timing.words[0].graphemesAt(1_500L))
        assertEquals(3.0, timing.words[0].graphemesAt(2_000L))
    }

    @Test
    fun enhancedLrcSweepsAcrossWholeGraphemes() {
        val line = LrcParser.parse("[00:01.00]<00:01.00>e\u0301한字 <00:02.20>👩‍🎤끝<00:03.00>").single()
        val timing = karaokeTiming(line, 3_000L)
        val (first, second) = timing.words

        assertFalse(timing.isEstimated)
        assertEquals(listOf(0, 2, 3, 4), first.graphemeOffsets.toList())
        assertEquals(listOf(5, 10, 11), second.graphemeOffsets.toList())
        assertEquals(0.0, first.graphemesAt(999L))
        assertEquals(0.0, first.graphemesAt(1_000L))
        assertEquals(0.5, first.graphemesAt(1_200L))
        assertEquals(1.0, first.graphemesAt(1_400L))
        assertEquals(2.5, first.graphemesAt(2_000L))
        assertEquals(3.0, first.graphemesAt(2_200L))
        assertEquals(1, timing.activeWordIndex(2_200L))
        assertEquals(0.5, second.graphemesAt(2_400L))
    }

    @Test
    fun lineOnlyTimingWeightsWordsByGraphemesAndSkipsSpaces() {
        val timing = karaokeTiming(LyricLine(1_000L, "e\u0301  한글\t漢字語"), 4_000L)

        assertTrue(timing.isEstimated)
        assertEquals(listOf(1_000L to 1_500L, 1_500L to 2_500L, 2_500L to 4_000L), timing.words.map { it.startMs to it.endMs })
        assertEquals(listOf(listOf(0, 2), listOf(4, 5, 6), listOf(7, 8, 9, 10)), timing.words.map { it.graphemeOffsets.toList() })
        assertEquals(0.5, timing.words[0].graphemesAt(1_250L))
        assertEquals(1, timing.activeWordIndex(1_500L))
        assertEquals(1.5, timing.words[1].graphemesAt(2_250L))
        assertEquals(2, timing.activeWordIndex(2_500L))
    }

    @Test
    fun lineOnlyTimingLightsTheWholeLineQuicklyInsteadOfGuessingASweep() {
        val lineOnly = karaokeTiming(LyricLine(1_000L, "Some words"), 9_000L)

        assertEquals(0f, lineOnly.lineLightAt(999L))
        assertEquals(0f, lineOnly.lineLightAt(1_000L))
        assertEquals(0.5f, lineOnly.lineLightAt(1_100L))
        assertEquals(1f, lineOnly.lineLightAt(1_200L))
        // Fully lit long before the next line, however long the instrumental gap is.
        assertEquals(1f, lineOnly.lineLightAt(5_000L))
    }

    @Test
    fun wordTimedLinesDoNotUseTheWholeLineLight() {
        val line = LrcParser.parse("[00:01.00]<00:01.00>Day <00:02.00>break<00:03.00>").first()

        assertNull(karaokeTiming(line, null).lineLightAt(1_500L))
    }

    @Test
    fun lineOnlyTimingHonorsShorterEndsAndCapsLongGaps() {
        val line = LyricLine(1_000L, "Some words")

        assertEquals(5_000L, karaokeTiming(line, null).words.last().endMs)
        assertEquals(9_000L, karaokeTiming(line, 60_000L).words.last().endMs)
        assertEquals(3_000L, karaokeTiming(line.copy(endMs = 3_000L), 60_000L).words.last().endMs)
        assertEquals(2_000L, karaokeTiming(line.copy(endMs = 3_000L), 2_000L).words.last().endMs)
        assertEquals("♪", karaokeTiming(line.copy(text = ""), 3_000L).text)
    }

    @Test
    fun lineHeadMovesLinearlyOverGraphemesAndStopsAtTheLineEdges() {
        val timing = karaokeTiming(LyricLine(1_000L, "e\u0301lan", endMs = 2_000L), null)

        assertEquals(0f, timing.headAt(999L))
        assertEquals(0f, timing.headAt(1_000L))
        assertEquals(1f, timing.headAt(1_250L))
        assertEquals(3.5f, timing.headAt(1_875L))
        assertEquals(4f, timing.headAt(2_000L))
        assertEquals(4f, timing.headAt(2_100L))
    }

    @Test
    fun lineHeadEasesAcrossSpacesInARealGap() {
        val timing = karaokeTiming(
            LyricLine(
                1_000L, "Day  break", listOf(LyricWord(1_000L, 1_000L, "Day"), LyricWord(2_400L, 600L, "break")),
            ),
            null,
        )

        assertEquals(1.5f, timing.headAt(1_500L))
        assertEquals(3f, timing.headAt(2_000L))
        assertEquals(3.3125f, timing.headAt(2_100L))
        assertEquals(4f, timing.headAt(2_200L))
        assertEquals(4.6875f, timing.headAt(2_300L))
        assertEquals(5f, timing.headAt(2_400L))
        assertEquals(7.5f, timing.headAt(2_700L))
    }

    @Test
    fun estimatedLineHeadBorrowsTimeForSpacesFromThePrecedingWord() {
        val timing = karaokeTiming(LyricLine(1_000L, "Hi  world", endMs = 4_500L), null)

        assertTrue(timing.isEstimated)
        // The two letters use 2 / 2.8 of the first word's 1,000 ms; each space uses 0.4 / 2.8.
        assertEquals(1.4f, timing.headAt(1_500L), absoluteTolerance = 0.0001f)
        assertTrue(timing.headAt(1_857L) in 2.99f..3.01f)
        assertEquals(4f, timing.headAt(2_000L))
        assertTrue(abs(timing.headAt(1_999L) - timing.headAt(2_001L)) < 0.1f)
    }

    @Test
    fun lineHeadFinishesTheSpaceBeforeAGapOfThirtyMilliseconds() {
        val timing = karaokeTiming(
            LyricLine(1_000L, "Go on", listOf(LyricWord(1_000L, 600L, "Go"), LyricWord(1_630L, 600L, "on"))),
            null,
        )

        assertEquals(2.5f, timing.headAt(1_550L))
        assertTrue(abs(timing.headAt(1_599L) - timing.headAt(1_601L)) < 0.1f)
        assertEquals(3f, timing.headAt(1_600L))
        assertEquals(3f, timing.headAt(1_630L))
    }

    @Test
    fun cjkLineHeadDoesNotReserveTimeForAnAbsentSpace() {
        val timing = karaokeTiming(
            LyricLine(1_000L, "漢字學習", listOf(LyricWord(1_000L, 500L, "漢字"), LyricWord(1_500L, 500L, "學習"))),
            null,
        )

        assertEquals(1f, timing.headAt(1_250L))
        assertEquals(2f, timing.headAt(1_500L))
        assertEquals(3f, timing.headAt(1_750L))
        assertEquals(4f, timing.headAt(2_000L))
    }

    @Test
    fun lineHeadStaysContinuousWhenWordCuesOverlap() {
        val timing = karaokeTiming(
            LyricLine(1_000L, "Go on", listOf(LyricWord(1_000L, 800L, "Go"), LyricWord(1_600L, 700L, "on"))),
            null,
        )

        assertTrue(abs(timing.headAt(1_599L) - timing.headAt(1_601L)) < 0.1f)
        assertEquals(3f, timing.headAt(1_600L))
        assertEquals(1, timing.activeWordIndex(1_600L))
    }

    @Test
    fun wrappedSweepKeepsBothRowsClippedAtTheirSharedBoundary() {
        val timing = karaokeTiming(
            LyricLine(1_000L, "ay ff", listOf(LyricWord(1_000L, 1_000L, "ay"), LyricWord(2_400L, 1_000L, "ff"))),
            null,
        )
        val first = KaraokeSweepRow(
            0, floatArrayOf(0f, 10f, 20f), Rect(0f, 0f, 20f, 30f), rtl = false, bleed = 4f,
            isFirst = true, isLast = false,
        )
        val second = KaraokeSweepRow(
            3, floatArrayOf(0f, 10f, 20f), Rect(0f, 30f, 20f, 60f), rtl = false, bleed = 4f,
            isFirst = false, isLast = true,
        )

        assertEquals(Rect(-4f, -4f, 21f, 30f), first.fillBounds(timing.headAt(1_750L), 6f))
        assertEquals(Rect(-4f, -4f, 24f, 30f), first.fillBounds(timing.headAt(2_200L), 6f))
        assertNull(second.fillBounds(timing.headAt(2_200L), 6f))
        assertNull(second.fillBounds(timing.headAt(2_400L), 6f))
        assertEquals(Rect(-4f, 30f, 11f, 64f), second.fillBounds(timing.headAt(2_650L), 6f))
    }

    @Test
    fun rtlSweepInterpolatesFromTheRightEdgeOfTheRow() {
        val row = KaraokeSweepRow(
            0, floatArrayOf(20f, 10f, 0f), Rect(0f, 0f, 20f, 30f), rtl = true, bleed = 4f,
            isFirst = true, isLast = true,
        )

        assertEquals(15f, row.headXAt(0.5f))
        assertEquals(Rect(9f, -4f, 24f, 34f), row.fillBounds(0.5f, 6f))
        assertEquals(Rect(-4f, -4f, 24f, 34f), row.fillBounds(2f, 6f))
    }
}
