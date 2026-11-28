package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsDuetAndBackingTest {

    @Test
    fun ttmlReadsAgentsFromDivAndParagraphAndSplitsXbgWithBracketsRemoved() {
        val parsed = LrcParser.parse(DUET_TTML_FIXTURE)
        assertEquals(4, parsed.size)

        val first = parsed[0]
        assertEquals("v1", first.agent)
        assertEquals(1_000L, first.startMs)
        assertEquals(4_200L, first.endMs)
        assertEquals("Malam tenang", first.text)
        assertEquals(
            listOf(
                LyricWord(1_000L, 800L, "Malam "),
                LyricWord(2_000L, 800L, "tenang"),
            ),
            first.words,
        )
        val firstBg = assertNotNull(first.background)
        assertEquals(2_900L, firstBg.startMs)
        assertEquals(4_200L, firstBg.endMs)
        assertEquals("ooh yeah", firstBg.text)
        assertEquals(
            listOf(
                LyricWord(2_900L, 500L, "ooh "),
                LyricWord(3_500L, 700L, "yeah"),
            ),
            firstBg.words,
        )
        val firstBgTiming = karaokeTiming(firstBg, null)
        assertFalse(firstBgTiming.isEstimated)
        assertEquals(listOf(2_900L to 3_400L, 3_500L to 4_200L), firstBgTiming.words.map { it.startMs to it.endMs })

        val second = parsed[1]
        assertEquals("v2", second.agent)
        assertEquals(4_500L, second.startMs)
        assertEquals(7_000L, second.endMs)
        assertEquals("Aku datang", second.text)
        assertEquals(
            listOf(
                LyricWord(4_500L, 600L, "Aku "),
                LyricWord(5_100L, 600L, "datang"),
            ),
            second.words,
        )
        val secondBg = assertNotNull(second.background)
        assertEquals(5_800L, secondBg.startMs)
        assertEquals(6_900L, secondBg.endMs)
        assertEquals("oh stay", secondBg.text)
        assertEquals(
            listOf(
                LyricWord(5_800L, 500L, "oh "),
                LyricWord(6_400L, 500L, "stay"),
            ),
            secondBg.words,
        )
        val secondBgTiming = karaokeTiming(secondBg, null)
        assertFalse(secondBgTiming.isEstimated)
        assertEquals(listOf(5_800L to 6_300L, 6_400L to 6_900L), secondBgTiming.words.map { it.startMs to it.endMs })

        val third = parsed[2]
        assertEquals("v2", third.agent)
        assertEquals(7_500L, third.startMs)
        assertEquals(9_500L, third.endMs)
        assertEquals("Sampai pagi", third.text)
        assertTrue(third.words.isEmpty())
        assertNull(third.background)

        val fourth = parsed[3]
        assertEquals("v1000", fourth.agent)
        assertEquals("Bersama lagi", fourth.text)
        assertNull(fourth.background)
    }

    @Test
    fun ttmlPlainParagraphWithXbgSpanSplitsBackgroundAndStripsBrackets() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div ttm:agent="v1">
                  <p begin="1.000" end="4.000">Main line <span ttm:role="x-bg" begin="2.500" end="3.800">(backing vocal)</span></p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val line = LrcParser.parse(ttml).single()
        assertEquals("v1", line.agent)
        assertEquals("Main line", line.text)
        assertTrue(line.words.isEmpty())
        assertEquals(
            LyricLine(startMs = 2_500L, text = "backing vocal", words = emptyList(), endMs = 3_800L),
            line.background,
        )
    }

    @Test
    fun lrcReadsAgentTagsSplitsBackingVocalsAndRemovesBrackets() {
        val parsed = LrcParser.parse(DUET_LRC_FIXTURE)
        assertEquals(4, parsed.size)

        val first = parsed[0]
        assertEquals("v1", first.agent)
        assertEquals(1_000L, first.startMs)
        assertEquals(3_500L, first.endMs)
        assertEquals("Hello world", first.text)
        assertEquals(
            listOf(
                LyricWord(1_000L, 600L, "Hello "),
                LyricWord(1_600L, 600L, "world"),
            ),
            first.words,
        )
        val firstTiming = karaokeTiming(first, parsed[1].startMs)
        assertFalse(firstTiming.isEstimated)
        assertEquals(listOf(1_000L to 1_600L, 1_600L to 2_200L), firstTiming.words.map { it.startMs to it.endMs })

        val firstBg = assertNotNull(first.background)
        assertEquals(2_200L, firstBg.startMs)
        assertEquals(3_500L, firstBg.endMs)
        assertEquals("ooh yeah", firstBg.text)
        assertEquals(
            listOf(
                LyricWord(2_200L, 600L, "ooh "),
                LyricWord(2_800L, 700L, "yeah"),
            ),
            firstBg.words,
        )
        val firstBgTiming = karaokeTiming(firstBg, null)
        assertFalse(firstBgTiming.isEstimated)
        assertEquals(listOf(2_200L to 2_800L, 2_800L to 3_500L), firstBgTiming.words.map { it.startMs to it.endMs })

        val second = parsed[1]
        assertEquals("v2", second.agent)
        assertEquals(4_000L, second.startMs)
        assertEquals(5_400L, second.endMs)
        assertEquals("Calling back", second.text)
        assertEquals(
            listOf(
                LyricWord(4_000L, 700L, "Calling "),
                LyricWord(4_700L, 700L, "back"),
            ),
            second.words,
        )
        val secondBg = assertNotNull(second.background)
        assertEquals(5_500L, secondBg.startMs)
        assertEquals(6_800L, secondBg.endMs)
        assertEquals("echo now", secondBg.text)
        assertEquals(
            listOf(
                LyricWord(5_500L, 600L, "echo "),
                LyricWord(6_100L, 700L, "now"),
            ),
            secondBg.words,
        )
        val secondBgTiming = karaokeTiming(secondBg, null)
        assertFalse(secondBgTiming.isEstimated)
        assertEquals(listOf(5_500L to 6_100L, 6_100L to 6_800L), secondBgTiming.words.map { it.startMs to it.endMs })

        val third = parsed[2]
        assertEquals("v1", third.agent)
        assertEquals(7_500L, third.startMs)
        assertEquals("Stay right here", third.text)
        assertTrue(third.words.isEmpty())
        assertEquals(LyricLine(startMs = 7_500L, text = "mmm yeah"), third.background)

        val fourth = parsed[3]
        assertEquals("v2", fourth.agent)
        assertEquals(10_000L, fourth.startMs)
        assertEquals("Until the dawn", fourth.text)
        assertNull(fourth.background)
    }

    @Test
    fun lrcBracketEdgeCasesAndCompressedRepeatsWithAgent() {
        val raw = """
            [00:01.00]Hello (unclosed
            [00:02.00](Instrumental)
            [00:03.00]<00:03.00>Sing <00:03.50>[la <00:04.00>la]<00:04.50>
            [00:05.00]<00:05.00>Go <00:05.50>(<00:05.60>yeah<00:06.00>)<00:06.10>
            [00:07.00][00:09.00]{agent:v2}Again
        """.trimIndent()
        val parsed = LrcParser.parse(raw)
        assertEquals(6, parsed.size)

        assertEquals("Hello (unclosed", parsed[0].text)
        assertNull(parsed[0].background)

        assertEquals("(Instrumental)", parsed[1].text)
        assertNull(parsed[1].background)

        assertEquals("Sing", parsed[2].text)
        assertEquals(listOf(LyricWord(3_000L, 500L, "Sing")), parsed[2].words)
        val squareBg = assertNotNull(parsed[2].background)
        assertEquals("la la", squareBg.text)
        assertEquals(listOf(LyricWord(3_500L, 500L, "la "), LyricWord(4_000L, 500L, "la")), squareBg.words)

        assertEquals("Go", parsed[3].text)
        val splitTokenBg = assertNotNull(parsed[3].background)
        assertEquals("yeah", splitTokenBg.text)
        assertEquals(listOf(LyricWord(5_600L, 400L, "yeah")), splitTokenBg.words)
        assertFalse(karaokeTiming(splitTokenBg, null).isEstimated)

        assertEquals(LyricLine(7_000L, "Again", agent = "v2"), parsed[4])
        assertEquals(LyricLine(9_000L, "Again", agent = "v2"), parsed[5])
    }

    @Test
    fun oneAgentSongProducesNoAlignmentChange() {
        val untagged = listOf(LyricLine(1_000L, "One"), LyricLine(2_000L, "Two"))
        assertEquals(
            listOf(LyricLineAlignment.Start, LyricLineAlignment.Start),
            resolveLineAlignments(untagged, LyricsAlignment.Left),
        )
        assertEquals(
            listOf(LyricLineAlignment.Center, LyricLineAlignment.Center),
            resolveLineAlignments(untagged, LyricsAlignment.Center),
        )

        val singleV1 = listOf(
            LyricLine(1_000L, "One", agent = "v1"),
            LyricLine(2_000L, "Two", agent = "v1"),
        )
        assertEquals(
            listOf(LyricLineAlignment.Start, LyricLineAlignment.Start),
            resolveLineAlignments(singleV1, LyricsAlignment.Left),
        )
        assertEquals(
            listOf(LyricLineAlignment.Center, LyricLineAlignment.Center),
            resolveLineAlignments(singleV1, LyricsAlignment.Center),
        )

        val singleV2 = listOf(
            LyricLine(1_000L, "One", agent = "v2"),
            LyricLine(2_000L, "Two", agent = "v2"),
        )
        assertEquals(
            listOf(LyricLineAlignment.Start, LyricLineAlignment.Start),
            resolveLineAlignments(singleV2, LyricsAlignment.Left),
        )

        val mixedNullAndV1WithV2Background = listOf(
            LyricLine(1_000L, "One", agent = null, background = LyricLine(1_500L, "ooh", agent = "v2")),
            LyricLine(2_000L, "Two", agent = "v1"),
        )
        assertEquals(
            listOf(LyricLineAlignment.Start, LyricLineAlignment.Start),
            resolveLineAlignments(mixedNullAndV1WithV2Background, LyricsAlignment.Left),
        )
        assertEquals(
            listOf(LyricLineAlignment.Center, LyricLineAlignment.Center),
            resolveLineAlignments(mixedNullAndV1WithV2Background, LyricsAlignment.Center),
        )
    }

    @Test
    fun multiAgentSongAlignsFirstVoiceStartSecondVoiceEndAndGroupCenter() {
        val ttmlLines = LrcParser.parse(DUET_TTML_FIXTURE)
        assertEquals(
            listOf(
                LyricLineAlignment.Start,
                LyricLineAlignment.End,
                LyricLineAlignment.End,
                LyricLineAlignment.Center,
            ),
            resolveLineAlignments(ttmlLines, LyricsAlignment.Left),
        )
        assertEquals(
            listOf(
                LyricLineAlignment.Start,
                LyricLineAlignment.End,
                LyricLineAlignment.End,
                LyricLineAlignment.Center,
            ),
            resolveLineAlignments(ttmlLines, LyricsAlignment.Center),
        )

        val lrcLines = LrcParser.parse(DUET_LRC_FIXTURE)
        assertEquals(
            listOf(
                LyricLineAlignment.Start,
                LyricLineAlignment.End,
                LyricLineAlignment.Start,
                LyricLineAlignment.End,
            ),
            resolveLineAlignments(lrcLines, LyricsAlignment.Left),
        )

        val v2Opens = listOf(
            LyricLine(1_000L, "Second opens", agent = "v2"),
            LyricLine(2_000L, "First answers", agent = "v1"),
        )
        assertEquals(
            listOf(LyricLineAlignment.End, LyricLineAlignment.Start),
            resolveLineAlignments(v2Opens, LyricsAlignment.Left),
        )

        val customAgents = listOf(
            LyricLine(1_000L, "Lead", agent = "alice"),
            LyricLine(2_000L, "Guest", agent = "bob"),
        )
        assertEquals(
            listOf(LyricLineAlignment.Start, LyricLineAlignment.End),
            resolveLineAlignments(customAgents, LyricsAlignment.Left),
        )
    }

    @Test
    fun activeLineIndexUsesMainLinesOnly() {
        val ttmlLines = LrcParser.parse(DUET_TTML_FIXTURE)
        assertEquals(0, activeLineIndex(ttmlLines, 3_200L))
        assertEquals(1, activeLineIndex(ttmlLines, 6_000L))

        val lrcLines = LrcParser.parse(DUET_LRC_FIXTURE)
        assertEquals(0, activeLineIndex(lrcLines, 2_900L))
        assertEquals(1, activeLineIndex(lrcLines, 5_800L))
    }

    @Test
    fun backgroundVocalVisibilityMatchesParentActiveOrHalfSecondWindow() {
        val timedBg = LyricLine(
            startMs = 3_000L,
            text = "ooh yeah",
            words = listOf(
                LyricWord(3_000L, 400L, "ooh "),
                LyricWord(3_500L, 500L, "yeah"),
            ),
            endMs = 4_000L,
        )

        assertTrue(isBackgroundVocalVisible(parentActive = true, background = timedBg, parentEndMs = 4_200L, positionMs = 1_000L))
        assertFalse(isBackgroundVocalVisible(parentActive = false, background = timedBg, parentEndMs = 4_200L, positionMs = 2_499L))
        assertTrue(isBackgroundVocalVisible(parentActive = false, background = timedBg, parentEndMs = 4_200L, positionMs = 2_500L))
        assertTrue(isBackgroundVocalVisible(parentActive = false, background = timedBg, parentEndMs = 4_200L, positionMs = 3_500L))
        assertTrue(isBackgroundVocalVisible(parentActive = false, background = timedBg, parentEndMs = 4_200L, positionMs = 4_500L))
        assertFalse(isBackgroundVocalVisible(parentActive = false, background = timedBg, parentEndMs = 4_200L, positionMs = 4_501L))

        val untimedBg = LyricLine(startMs = 7_500L, text = "mmm yeah")
        assertTrue(isBackgroundVocalVisible(parentActive = false, background = untimedBg, parentEndMs = 10_000L, positionMs = 7_000L))
        assertFalse(isBackgroundVocalVisible(parentActive = false, background = untimedBg, parentEndMs = 10_000L, positionMs = 6_999L))
        assertTrue(isBackgroundVocalVisible(parentActive = false, background = untimedBg, parentEndMs = 10_000L, positionMs = 10_500L))
        assertFalse(isBackgroundVocalVisible(parentActive = false, background = untimedBg, parentEndMs = 10_000L, positionMs = 10_501L))
    }

    private companion object {
        val DUET_TTML_FIXTURE = """
            <tt xmlns="http://www.w3.org/ns/ttml"
                xmlns:ttm="http://www.w3.org/ns/ttml#metadata"
                xmlns:itunes="http://music.apple.com/lyric-ttml-internal"
                itunes:timing="word">
              <body>
                <div ttm:agent="v1">
                  <p begin="1.000" end="4.200">
                    <span begin="1.000" end="1.800">Malam</span> <span begin="2.000" end="2.800">tenang</span>
                    <span ttm:role="x-bg" begin="2.900" end="4.200">
                      <span begin="2.900" end="3.400">(ooh</span> <span begin="3.500" end="4.200">yeah)</span>
                    </span>
                  </p>
                  <p begin="4.500" end="7.000" ttm:agent="v2">
                    <span begin="4.500" end="5.100">Aku</span> <span begin="5.100" end="5.700">datang</span>
                    <span ttm:role="x-bg">(<span begin="5.800" end="6.300">oh</span> <span begin="6.400" end="6.900">stay</span>)</span>
                  </p>
                </div>
                <div ttm:agent="v2">
                  <p begin="7.500" end="9.500">Sampai pagi</p>
                </div>
                <div ttm:agent="v1">
                  <p begin="10.000" end="12.000" ttm:agent="v1000">Bersama lagi</p>
                </div>
              </body>
            </tt>
        """.trimIndent()

        val DUET_LRC_FIXTURE = """
            [00:01.00]{agent:v1}<00:01.00>Hello <00:01.60>world <00:02.20>(ooh <00:02.80>yeah)<00:03.50>
            [00:04.00]{agent:v2}<00:04.00>Calling <00:04.70>back<00:05.40>
            [00:05.50]{bg}<00:05.50>(echo <00:06.10>now)<00:06.80>
            [00:07.50]{agent:v1}Stay right here (mmm yeah)
            [00:10.00]{agent:v2}Until the dawn
        """.trimIndent()
    }
}
