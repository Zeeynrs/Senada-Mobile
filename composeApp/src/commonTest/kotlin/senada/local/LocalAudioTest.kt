package senada.local

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import senada.app.queueAudioSelection
import senada.model.Track
import senada.playback.QueueItem
import senada.playback.SampleAudioSource

class LocalAudioTest {
    @Test
    fun importedFilesBecomeLocalTracksNamedAfterTheFile() {
        val track = ImportedAudio("a1b2.mp3", "Rekaman Latihan.mp3", 4_400_000).toTrack()

        assertEquals("local:a1b2.mp3", track.id)
        assertEquals("Rekaman Latihan", track.title)
        assertEquals(emptyList(), track.artists)
        assertEquals(LOCAL_FILE_SOURCE, track.source)
        assertEquals("a1b2.mp3", localFileNameOf(track))
    }

    @Test
    fun onlySafeFileNamesFromLocalTracksAreUsed() {
        fun local(id: String) = Track(id, "T", emptyList(), source = LOCAL_FILE_SOURCE)

        assertNull(localFileNameOf(local("local:../queue.json")))
        assertNull(localFileNameOf(local("local:sub/dir.mp3")))
        assertNull(localFileNameOf(local("local:")))
        assertNull(localFileNameOf(Track("local:x.mp3", "T", emptyList(), source = "ytmusic")))
    }

    @Test
    fun newFileNamesKeepReasonableExtensions() {
        val random = Random(1)
        assertTrue(newLocalFileName("Lagu.M4A", random).endsWith(".m4a"))
        assertTrue('.' !in newLocalFileName("tanpa ekstensi", random))
        assertTrue('.' !in newLocalFileName("aneh.mp3?x=1", random))
        assertEquals(16, newLocalFileName("x", random).length)
    }

    @Test
    fun localQueueEntriesPlayTheirCopyOrExplainWhyNot() = runTest {
        val track = ImportedAudio("a1b2.mp3", "Rekaman.mp3", 1).toTrack()
        val entry = QueueItem(7, track)

        val selection = queueAudioSelection(entry) { name -> "file:///app/imported-audio/$name" }
        assertEquals("file:///app/imported-audio/a1b2.mp3", selection.uri)
        assertEquals(SampleAudioSource.Local, selection.source)
        assertEquals(7L, selection.queueEntryId)

        val error = assertFailsWith<LocalAudioException> { queueAudioSelection(entry) { null } }
        assertTrue("Impor ulang" in error.userMessage)
    }
}
