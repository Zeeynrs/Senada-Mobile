package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun lineOnlyTimingHonorsShorterEndsAndCapsLongGaps() {
        val line = LyricLine(1_000L, "Some words")

        assertEquals(5_000L, karaokeTiming(line, null).words.last().endMs)
        assertEquals(9_000L, karaokeTiming(line, 60_000L).words.last().endMs)
        assertEquals(3_000L, karaokeTiming(line.copy(endMs = 3_000L), 60_000L).words.last().endMs)
        assertEquals(2_000L, karaokeTiming(line.copy(endMs = 3_000L), 2_000L).words.last().endMs)
        assertEquals("♪", karaokeTiming(line.copy(text = ""), 3_000L).text)
    }
}
