package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import senada.app.AppLanguage
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

class LyricsOffsetTest {
    @Test
    fun clampBoundsOffsetsToTenSecondsEachWay() {
        assertEquals(0, clampLyricsOffsetMs(0))
        assertEquals(300, clampLyricsOffsetMs(300))
        assertEquals(-1_200, clampLyricsOffsetMs(-1_200))
        assertEquals(MAX_LYRICS_OFFSET_MS, clampLyricsOffsetMs(MAX_LYRICS_OFFSET_MS))
        assertEquals(MIN_LYRICS_OFFSET_MS, clampLyricsOffsetMs(MIN_LYRICS_OFFSET_MS))
        assertEquals(MAX_LYRICS_OFFSET_MS, clampLyricsOffsetMs(MAX_LYRICS_OFFSET_MS + 1))
        assertEquals(MIN_LYRICS_OFFSET_MS, clampLyricsOffsetMs(MIN_LYRICS_OFFSET_MS - 1))
        assertEquals(MAX_LYRICS_OFFSET_MS, clampLyricsOffsetMs(Int.MAX_VALUE))
        assertEquals(MIN_LYRICS_OFFSET_MS, clampLyricsOffsetMs(Int.MIN_VALUE))
    }

    @Test
    fun formatOffsetUsesLocaleDecimalUnitSignAndOneDecimal() {
        assertEquals("0.0 s", formatLyricsOffset(0, AppLanguage.English))
        assertEquals("+0.1 s", formatLyricsOffset(100, AppLanguage.English))
        assertEquals("+0.3 s", formatLyricsOffset(300, AppLanguage.English))
        assertEquals("+0.5 s", formatLyricsOffset(500, AppLanguage.English))
        assertEquals("+1.0 s", formatLyricsOffset(1_000, AppLanguage.English))
        assertEquals("+10.0 s", formatLyricsOffset(10_000, AppLanguage.English))
        assertEquals("+10.0 s", formatLyricsOffset(25_000, AppLanguage.English))
        assertEquals("\u22120.1 s", formatLyricsOffset(-100, AppLanguage.English))
        assertEquals("\u22120.5 s", formatLyricsOffset(-500, AppLanguage.English))
        assertEquals("\u22121.2 s", formatLyricsOffset(-1_200, AppLanguage.English))
        assertEquals("\u221210.0 s", formatLyricsOffset(-10_000, AppLanguage.English))
        assertEquals("\u221210.0 s", formatLyricsOffset(-25_000, AppLanguage.English))
        assertEquals("0.0 s", formatLyricsOffset(40, AppLanguage.English))
        assertEquals("0.0 s", formatLyricsOffset(-40, AppLanguage.English))
        assertEquals("+0.1 s", formatLyricsOffset(50, AppLanguage.English))
        assertEquals("\u22120.1 s", formatLyricsOffset(-50, AppLanguage.English))

        assertEquals("0,0 dtk", formatLyricsOffset(0, AppLanguage.Indonesian))
        assertEquals("+0,1 dtk", formatLyricsOffset(100, AppLanguage.Indonesian))
        assertEquals("+0,3 dtk", formatLyricsOffset(300, AppLanguage.Indonesian))
        assertEquals("+0,5 dtk", formatLyricsOffset(500, AppLanguage.Indonesian))
        assertEquals("+1,0 dtk", formatLyricsOffset(1_000, AppLanguage.Indonesian))
        assertEquals("+10,0 dtk", formatLyricsOffset(10_000, AppLanguage.Indonesian))
        assertEquals("+10,0 dtk", formatLyricsOffset(25_000, AppLanguage.Indonesian))
        assertEquals("\u22120,1 dtk", formatLyricsOffset(-100, AppLanguage.Indonesian))
        assertEquals("\u22120,5 dtk", formatLyricsOffset(-500, AppLanguage.Indonesian))
        assertEquals("\u22121,2 dtk", formatLyricsOffset(-1_200, AppLanguage.Indonesian))
        assertEquals("\u221210,0 dtk", formatLyricsOffset(-10_000, AppLanguage.Indonesian))
        assertEquals("\u221210,0 dtk", formatLyricsOffset(-25_000, AppLanguage.Indonesian))
        assertEquals("0,0 dtk", formatLyricsOffset(40, AppLanguage.Indonesian))
        assertEquals("0,0 dtk", formatLyricsOffset(-40, AppLanguage.Indonesian))
        assertEquals("+0,1 dtk", formatLyricsOffset(50, AppLanguage.Indonesian))
        assertEquals("\u22120,1 dtk", formatLyricsOffset(-50, AppLanguage.Indonesian))

        assertEquals("0.5 s", formatLyricsOffsetMagnitude(-500, AppLanguage.English))
        assertEquals("0,5 dtk", formatLyricsOffsetMagnitude(-500, AppLanguage.Indonesian))
        assertEquals("0.1 s", formatLyricsOffsetMagnitude(100, AppLanguage.English))
        assertEquals("0,1 dtk", formatLyricsOffsetMagnitude(100, AppLanguage.Indonesian))
    }

    @Test
    fun storeSetsGetsClampsResetsAndRoundTripsWithFakeBackingStore() {
        var persisted: String? = null
        var writes = 0
        fun openStore() = LyricsOffsetStore(
            read = { persisted },
            write = {
                writes++
                persisted = it
            },
        )

        val firstSession = openStore()
        assertEquals(0, firstSession.get("track-a"))

        // Setting 0 for an absent track does not store an entry or write unnecessarily.
        assertEquals(0, firstSession.set("track-a", 0))
        assertEquals(0, writes)

        assertEquals(300, firstSession.set("track-a", 300))
        assertEquals(-1_200, firstSession.set("track-b", -1_200))
        assertEquals(10_000, firstSession.set("track-c", 25_000))
        assertEquals(-10_000, firstSession.set("track-d", -40_000))

        // Reopen a fresh store from the fake backing string to verify restart persistence.
        val secondSession = openStore()
        assertEquals(300, secondSession.get("track-a"))
        assertEquals(-1_200, secondSession.get("track-b"))
        assertEquals(10_000, secondSession.get("track-c"))
        assertEquals(-10_000, secondSession.get("track-d"))
        assertEquals(0, secondSession.get("unknown-track"))

        // Setting to 0 and calling reset both remove the stored entry.
        secondSession.set("track-a", 0)
        secondSession.reset("track-b")

        val jsonOffsets = Json.parseToJsonElement(requireNotNull(persisted)).jsonObject["offsets"]!!.jsonObject
        assertFalse("track-a" in jsonOffsets, "0 offset must not keep an entry")
        assertFalse("track-b" in jsonOffsets, "reset must remove the track entry")
        assertEquals(JsonPrimitive(10_000), jsonOffsets["track-c"])
        assertEquals(JsonPrimitive(-10_000), jsonOffsets["track-d"])

        val thirdSession = openStore()
        assertEquals(0, thirdSession.get("track-a"))
        assertEquals(0, thirdSession.get("track-b"))
        assertEquals(10_000, thirdSession.get("track-c"))
        assertEquals(-10_000, thirdSession.get("track-d"))
    }

    @Test
    fun storeHandlesMalformedWrongVersionAndInvalidEntriesSafely() {
        for (bad in listOf(
            null,
            "",
            "{",
            "{}",
            """{"version":2,"offsets":{"a":300}}""",
            """{"version":1,"offsets":"not-an-object"}""",
        )) {
            assertEquals(emptyMap(), LyricsOffsetStore({ bad }, {}).loadAll(), "payload=$bad")
        }
        assertEquals(emptyMap(), LyricsOffsetStore({ error("disk failure") }, {}).loadAll())

        val mixed = """{"version":1,"offsets":{"":"100","bad":"300","flag":true,"nil":null,"zero":0,"high":15000,"low":-999999999999,"ok":400}}"""
        assertEquals(
            mapOf("high" to 10_000, "low" to -10_000, "ok" to 400),
            LyricsOffsetStore({ mixed }, {}).loadAll(),
        )
    }

    @Test
    fun controllerRestoresAdjustsResetsAndPreservesPreRestoreEdits() = runTest {
        var persisted: String? = null
        LyricsOffsetStore({ persisted }, { persisted = it }).apply {
            set("track-1", 300)
            set("track-2", -500)
        }

        val controller = LyricsOffsetController(LyricsOffsetStore({ persisted }, { persisted = it }))
        // Simulate a fast user edit before async restore finishes.
        assertEquals(100, controller.adjustOffset("track-2", 100))
        controller.restore()

        assertEquals(300, controller.offsetFor("track-1"))
        assertEquals(100, controller.offsetFor("track-2"))

        assertEquals(600, controller.adjustOffset("track-2", 500))
        controller.resetOffset("track-1")
        assertEquals(0, controller.offsetFor("track-1"))

        val restarted = LyricsOffsetController(LyricsOffsetStore({ persisted }, { persisted = it }))
        restarted.restore()
        assertEquals(0, restarted.offsetFor("track-1"))
        assertEquals(600, restarted.offsetFor("track-2"))
    }

    @Test
    fun framePositionAndActiveLineIndexAddOffsetImmediatelyWithoutDisturbingFilter() {
        val lines = listOf(
            LyricLine(0L, "Line zero"),
            LyricLine(2_000L, "Line one"),
            LyricLine(4_000L, "Line two"),
        )

        assertEquals(1_500L, applyLyricsOffset(1_000L, 500))
        assertEquals(500L, applyLyricsOffset(1_000L, -500))
        assertEquals(-200L, applyLyricsOffset(300L, -500))

        // Positive offset shows lyrics earlier; negative offset delays them (including a 0 ms first line).
        assertEquals(0, activeLineIndex(lines, positionMs = 1_700L, offsetMs = 0))
        assertEquals(1, activeLineIndex(lines, positionMs = 1_700L, offsetMs = 300))
        assertEquals(0, activeLineIndex(lines, positionMs = 2_100L, offsetMs = -200))
        assertEquals(0, activeLineIndex(lines, positionMs = 300L, offsetMs = 0))
        assertEquals(-1, activeLineIndex(lines, positionMs = 300L, offsetMs = -500))

        // Seeking a lyric line while offset is active compensates so playback lands on that line.
        assertEquals(10_000L, lyricSeekPositionMs(lineStartMs = 12_000L, offsetMs = 2_000))
        assertEquals(0L, lyricSeekPositionMs(lineStartMs = 1_000L, offsetMs = 2_000))
        assertEquals(14_000L, lyricSeekPositionMs(lineStartMs = 12_000L, offsetMs = -2_000))

        // Adding offset after LyricsPositionFilter takes effect immediately and leaves filter state intact.
        val filter = LyricsPositionFilter()
        val playing = SampleAudioState(
            selection = SampleAudioSelection("Song", SampleAudioSource.Local, "file:///song.m4a", 1L),
            status = SampleAudioStatus.Playing,
            positionMs = 1_700L,
            sampledAtMs = 100L,
        )

        val baseFrame = filter.resolveWithOffset(playing, monotonicMs = 100L, offsetMs = 0)
        assertEquals(1_700L, baseFrame)
        assertEquals(0, activeLineIndex(lines, baseFrame))

        // Same timestamp, offset changed to +500 ms (no seek): frame position and active line shift immediately.
        val shiftedEarlier = filter.resolveWithOffset(playing, monotonicMs = 100L, offsetMs = 500)
        assertEquals(2_200L, shiftedEarlier)
        assertEquals(1, activeLineIndex(lines, shiftedEarlier))

        // Offset changed to -500 ms (no seek): shifts backward immediately even while playing.
        val shiftedLater = filter.resolveWithOffset(playing, monotonicMs = 100L, offsetMs = -500)
        assertEquals(1_200L, shiftedLater)
        assertEquals(0, activeLineIndex(lines, shiftedLater))

        // Next frame back at 0 offset continues smoothly from 1700 + 16 = 1716 ms.
        assertEquals(1_716L, filter.resolveWithOffset(playing, monotonicMs = 116L, offsetMs = 0))

        // Next cue wait accounts for offset-shifted position.
        assertEquals(300L, nextCueWaitWallMs(lines, currentIndex = 0, positionMs = applyLyricsOffset(1_200L, 500), playbackSpeed = 1f))
        assertEquals(1_300L, nextCueWaitWallMs(lines, currentIndex = 0, positionMs = applyLyricsOffset(1_200L, -500), playbackSpeed = 1f))
    }
}
