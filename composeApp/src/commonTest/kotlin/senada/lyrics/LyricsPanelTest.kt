package senada.lyrics

import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import senada.model.Track

class LyricsPanelTest {
    @Test
    fun oldLyricsResponseCannotReplaceCurrentTrack() = runTest {
        val first = Track("a", "Lagu lama", listOf("Pagi"), source = "ytmusic")
        val next = first.copy(id = "b", title = "Lagu baru")
        lateinit var lateReply: Continuation<LyricsDocument>
        var shown: LyricsPanelResult = LyricsPanelResult.Loading
        val oldRequest = launch(start = CoroutineStart.UNDISPATCHED) {
            shown = loadLyrics(first) { suspendCoroutine { lateReply = it } }
        }

        oldRequest.cancel()
        val newLyrics = LyricsDocument.Plain(listOf("Langkah baru"))
        shown = loadLyrics(next) { newLyrics }
        lateReply.resume(LyricsDocument.Plain(listOf("Langkah lama")))
        oldRequest.join()
        assertEquals(LyricsPanelResult.Ready(newLyrics), shown)
    }
}
