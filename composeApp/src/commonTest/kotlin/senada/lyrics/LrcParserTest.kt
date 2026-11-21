package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LrcParserTest {
    @Test
    fun timestampsRepeatTheTextAndSortByMilliseconds() {
        val raw = """
            [ar:Senada]
            [ti:Langkah]
            [offset:+500]
            [00:01.00][00:02.00] Hai 🌙

            [00:01.50]Halo
            [01:00.007]遠く
            [00:03]
        """.trimIndent()
        assertEquals(
            listOf(
                LyricLine(1000, "Hai 🌙"), LyricLine(1500, "Halo"), LyricLine(2000, "Hai 🌙"),
                LyricLine(3000, ""), LyricLine(60007, "遠く"),
            ),
            LrcParser.parse(raw),
        )
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
}
