package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import senada.model.Track

@OptIn(ExperimentalCoroutinesApi::class)
class QueuePersistenceTest {
    private val stream = Track(
        id = "yt:track:k1",
        title = "Pelangi 🌈",
        artists = listOf("Senada", "Tamu"),
        durationMs = 90_000,
        artworkUrl = "https://image.example/cover.jpg",
        source = "youtube-music",
    )
    private val local = Track("demo:track:hujan", "Hujan", emptyList(), source = "demo")

    @Test
    fun restoredShuffleContinuesInSavedOrderAndWraps() = runTest {
        val store = MemoryQueueStore("""{
            "version":1,"nextId":4,"selectedEntryId":2,"positionMs":4000,
            "repeat":"all","shuffle":true,"shuffleOrder":[3,1,2],
            "entries":[
                {"id":1,"track":{"id":"a","title":"A","artists":[],"durationMs":null,"artworkUrl":null,"source":"demo"}},
                {"id":2,"track":{"id":"b","title":"B","artists":[],"durationMs":null,"artworkUrl":null,"source":"demo"}},
                {"id":3,"track":{"id":"c","title":"C","artists":[],"durationMs":null,"artworkUrl":null,"source":"demo"}}
            ]
        }""")
        val playback = controller(FakeAudioOutput(), store)
        assertEquals(2L, playback.state.value.currentEntry?.id)
        playback.next()
        assertEquals(3L, playback.state.value.currentEntry?.id)
        playback.next()
        assertEquals(1L, playback.state.value.currentEntry?.id)
        playback.previous()
        assertEquals(3L, playback.state.value.currentEntry?.id)
    }

    @Test
    fun snapshotRoundTripsMetadataOrderAndSelection() {
        val queue = Queue().append(stream).append(local).append(stream)
        val snapshot = QueueSnapshot(queue, queue.entries.last().id, 32_000)

        val text = QueueJson.encode(snapshot)
        val decoded = assertNotNull(QueueJson.decode(text))

        assertEquals(queue.entries, decoded.queue.entries)
        assertEquals(snapshot.selectedEntryId, decoded.selectedEntryId)
        assertEquals(snapshot.positionMs, decoded.positionMs)
        assertEquals(text, QueueJson.encode(decoded))
        assertTrue("\"version\":1" in text)
    }

    @Test
    fun newEntriesKeepIncreasingAfterADeletedIdAndRestore() = runTest {
        val store = MemoryQueueStore()
        val playback = controller(FakeAudioOutput(), store)
        playback.add(stream)
        playback.add(local)
        val (remaining, removed) = playback.state.value.queue.entries
        playback.remove(removed.id)

        val restored = controller(FakeAudioOutput(), store)
        restored.add(stream)

        assertEquals(remaining, restored.state.value.queue.entries.first())
        assertTrue(restored.state.value.queue.entries.last().id > removed.id)
    }

    @Test
    fun missingMalformedOrUnsupportedSnapshotsStartEmpty() = runTest {
        val queue = Queue().append(stream).append(stream)
        val valid = QueueJson.encode(QueueSnapshot(queue, queue.entries.first().id, 32_000))
        val invalidSnapshots = listOf(
            null,
            "{rusak",
            valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("\"selectedEntryId\":1", "\"selectedEntryId\":99"),
            valid.replace("\"id\":2", "\"id\":1"),
            valid.replace("\"positionMs\":32000", "\"positionMs\":-1"),
        )
        for (text in invalidSnapshots) {
            val store = MemoryQueueStore(text)
            val restored = controller(FakeAudioOutput(), store)

            assertTrue(restored.state.value.queue.entries.isEmpty(), text)
            assertNull(restored.state.value.currentEntry, text)
            assertEquals(SampleAudioStatus.Idle, restored.state.value.audio.status, text)
        }
    }

    @Test
    fun restoresPausedThenResolvesAndPlaysFromTheSavedPosition() = runTest {
        val store = MemoryQueueStore()
        val output = FakeAudioOutput()
        val playback = controller(output, store)
        playback.add(local)
        playback.add(stream)
        val selected = playback.state.value.queue.entries.last()
        playback.playEntry(selected.id)
        output.positionMs = 32_000
        playback.pause()

        val saved = assertNotNull(store.text)
        assertFalse("\"uri\"" in saved)
        assertFalse(STREAM_URI in saved)

        val reopenedOutput = FakeAudioOutput()
        val resolved = mutableListOf<QueueItem>()
        val restored = controller(reopenedOutput, store) { resolved += it }
        restored.setUiVisible(true)
        val state = restored.state.value
        assertEquals(playback.state.value.queue.entries, state.queue.entries)
        assertEquals(selected, state.currentEntry)
        assertEquals(SampleAudioStatus.Paused, state.audio.status)
        assertEquals(32_000L, state.audio.positionMs)
        assertEquals(90_000L, state.audio.durationMs)
        assertTrue(assertNotNull(state.audio.selection).isPending)
        assertTrue(reopenedOutput.restarts.isEmpty())
        assertTrue(resolved.isEmpty())

        restored.resume()

        assertEquals(listOf(selected), resolved)
        assertEquals(listOf(32_000L), reopenedOutput.startPositions)
        assertEquals(SampleAudioStatus.Playing, restored.state.value.audio.status)
    }

    @Test
    fun seekingBeforeAudioLoadsPersistsTheNewPositionForTheQueuePlayButton() = runTest {
        val queue = Queue().append(stream)
        val entry = queue.entries.single()
        val store = MemoryQueueStore(QueueJson.encode(QueueSnapshot(queue, entry.id, 32_000)))
        val output = FakeAudioOutput()
        val restored = controller(output, store)

        restored.seekTo(40_000)

        assertTrue(output.seeks.isEmpty())
        assertEquals(40_000L, QueueJson.decode(store.text)?.positionMs)

        restored.playEntry(entry.id)

        assertEquals(listOf(40_000L), output.startPositions)
    }

    @Test
    fun writesSeeksAndSelectionChangesButSkipsPositionTicks() = runTest {
        val store = MemoryQueueStore()
        val output = FakeAudioOutput().apply { durationMs = 90_000 }
        val playback = controller(output, store)
        playback.add(stream)
        playback.add(local)
        val (first, second) = playback.state.value.queue.entries
        playback.playEntry(first.id)
        playback.setUiVisible(true)
        val writesBeforeTicks = store.writes

        output.positionMs = 5_000
        advanceTimeBy(1_000)
        assertEquals(writesBeforeTicks, store.writes)

        playback.seekTo(9_000)
        assertEquals(9_000L, QueueJson.decode(store.text)?.positionMs)

        playback.playEntry(second.id)
        assertEquals(second.id, QueueJson.decode(store.text)?.selectedEntryId)
        assertEquals(0L, QueueJson.decode(store.text)?.positionMs)
    }

    private fun TestScope.controller(
        output: FakeAudioOutput,
        store: QueueStore,
        onResolve: (QueueItem) -> Unit = {},
    ) = PlaybackController(output, backgroundScope) { entry ->
        onResolve(entry)
        SampleAudioSelection(entry.track.title, SampleAudioSource.YouTubeMusic, STREAM_URI, entry.id)
    }.apply { persistQueue(store) }

    private class MemoryQueueStore(var text: String? = null) : QueueStore {
        var writes = 0

        override fun load() = text

        override fun save(text: String) {
            this.text = text
            writes++
        }
    }

    private companion object {
        const val STREAM_URI = "https://audio.example/playback?signature=ephemeral"
    }
}
