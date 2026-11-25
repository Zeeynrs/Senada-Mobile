package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {
    @Test
    fun timestampsRepeatTheTextAndSortByMilliseconds() {
        val raw = """
            [ar:Senada]
            [ti:Langkah]
            [00:00.10]Mula
            [00:01.00][00:02.00] Hai 🌙

            [00:01.50]Halo
            [offset:+500]
            [01:00.007]遠く
            [00:03]
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(0, "Mula"), LyricLine(500, "Hai 🌙"), LyricLine(1000, "Halo"),
                LyricLine(1500, "Hai 🌙"), LyricLine(2500, ""), LyricLine(59507, "遠く"),
            ),
            LrcParser.parse(raw),
        )
    }

    @Test
    fun negativeOffsetDelaysLyrics() {
        val raw = "[offset:-250]\n[00:00.10]Mula"
        assertEquals(listOf(LyricLine(350, "Mula")), LrcParser.parse(raw))
    }

    @Test
    fun invalidOffsetsAreIgnored() {
        val raw = "[offset:rusak]\n[offset:+]\n[offset:1.5]\n[offset:999999999999999999999]\n[00:01]Mula"
        assertEquals(listOf(LyricLine(1000, "Mula")), LrcParser.parse(raw))
    }

    @Test
    fun negativeOffsetCannotOverflowTheLineTime() {
        val raw = "[offset:${Long.MIN_VALUE}]\n[00:01]Mula"
        assertEquals(listOf(LyricLine(Long.MAX_VALUE, "Mula")), LrcParser.parse(raw))
    }

    @Test
    fun malformedAndOverflowingTimestampsAreSkipped() {
        assertEquals(emptyList(), LrcParser.parse(""))
        val raw = "tanpa waktu\n[xx:ab]rusak\n[999999999999999999:00]terlalu jauh\n[00:04.020]Pulih"
        assertEquals(listOf(LyricLine(4020, "Pulih")), LrcParser.parse(raw))
    }

    @Test
    fun seekRecomputesActiveLineFromPlayerPosition() {
        val lines = listOf(LyricLine(1000, "Satu"), LyricLine(2000, "Dua"), LyricLine(2000, "Tiga"))
        assertEquals(-1, activeLineIndex(emptyList(), 0))
        assertEquals(-1, activeLineIndex(lines, 999))
        assertEquals(2, activeLineIndex(lines, 2000))
        assertEquals(2, activeLineIndex(lines, 9000))
        assertEquals(0, activeLineIndex(lines, 1000))
    }

    @Test
    fun parsesColonCentisecondsAndLongFractions() {
        val raw = """
            [00:01:25]Baris titik dua
            [00:02.3456]Fraksi panjang
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(1250, "Baris titik dua"),
                LyricLine(2345, "Fraksi panjang"),
            ),
            LrcParser.parse(raw),
        )
    }

    @Test
    fun parsesEnhancedLrcWordTimingAndAppliesOffsetAndCompressedRepeats() {
        val raw = """
            [offset:+100]
            [00:01.10][00:05.10]<00:01.10>Halo <00:01.60>dunia<00:02.10>
            [00:03.10]<00:03.10>Satu <00:03.50>kata
        """.trimIndent()
        val parsed = LrcParser.parse(raw)
        assertEquals(3, parsed.size)

        assertEquals(1_000L, parsed[0].startMs)
        assertEquals("Halo dunia", parsed[0].text)
        assertEquals(2_000L, parsed[0].endMs)
        assertEquals(
            listOf(
                LyricWord(1_000L, 500L, "Halo "),
                LyricWord(1_500L, 500L, "dunia"),
            ),
            parsed[0].words,
        )

        assertEquals(3_000L, parsed[1].startMs)
        assertEquals("Satu kata", parsed[1].text)
        assertEquals(3_400L, parsed[1].words[1].startMs)
        assertEquals(1_600L, parsed[1].words[1].durationMs)

        // Compressed repeat shifts word timing by +4000ms.
        assertEquals(5_000L, parsed[2].startMs)
        assertEquals(5_000L, parsed[2].words[0].startMs)
        assertEquals(5_500L, parsed[2].words[1].startMs)
    }

    @Test
    fun parsesTtmlWordTimingWithBackgroundVocals() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="word">
              <body>
                <div>
                  <p begin="1.000" end="3.500">
                    <span begin="1.000" end="1.800">Malam</span> <span begin="2.000" end="2.800">tenang</span>
                    <span begin="2.900" end="3.500"><span begin="2.900" end="3.500">ooh</span></span>
                  </p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val parsed = LrcParser.parse(ttml)
        assertEquals(1, parsed.size)
        val line = parsed.single()
        assertEquals(1_000L, line.startMs)
        assertEquals(3_500L, line.endMs)
        assertEquals("Malam tenang (ooh)", line.text)
        assertEquals(
            listOf(
                LyricWord(1_000L, 800L, "Malam "),
                LyricWord(2_000L, 800L, "tenang "),
                LyricWord(2_900L, 600L, "(ooh)"),
            ),
            line.words,
        )
    }

    @Test
    fun karaokeSungEndUsesRealWordTimingAndFallsBackForLineLrc() {
        val wordLine = LyricLine(
            startMs = 1_000L,
            text = "Hai dunia 🌙",
            words = listOf(
                LyricWord(1_000L, 400L, "Hai "),
                LyricWord(2_000L, 1_000L, "dunia "),
                LyricWord(3_000L, 500L, "🌙"),
            ),
            endMs = 3_500L,
        )
        // Before cue
        assertEquals(0, karaokeSungEnd(wordLine, wordLine.text, 999L, 4_000L))
        // Gap between "Hai" (ends 1400) and "dunia" (starts 2000): holds at end of "Hai " (4 chars)
        assertEquals(4, karaokeSungEnd(wordLine, wordLine.text, 1_700L, 4_000L))
        // Mid-way through "dunia" (2500ms -> 50% of 5 non-space chars = 3 chars -> 4 + 3 = 7)
        assertEquals(7, karaokeSungEnd(wordLine, wordLine.text, 2_500L, 4_000L))
        // Mid-way through surrogate pair emoji -> keeps surrogate pair intact
        assertEquals(10, karaokeSungEnd(wordLine, wordLine.text, 3_100L, 4_000L))
        assertEquals(wordLine.text.length, karaokeSungEnd(wordLine, wordLine.text, 3_500L, 4_000L))

        // Line-based LRC fallback
        val plainLine = LyricLine(startMs = 1_000L, text = "Satu dua")
        assertEquals(4, karaokeSungEnd(plainLine, plainLine.text, 2_000L, 3_000L))
    }
}
