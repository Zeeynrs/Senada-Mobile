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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
    fun oldSnapshotDefaultsToLinearPlaybackWithoutRepeat() = runTest {
        val store = MemoryQueueStore("""{
            "version":1,"nextId":2,"selectedEntryId":1,"positionMs":7000,
            "entries":[{"id":1,"track":{"id":"a","title":"A","artists":[],
                "durationMs":null,"artworkUrl":null,"source":"demo"}}]
        }""")
        val state = controller(FakeAudioOutput(), store).state.value
        assertEquals(1L, state.currentEntry?.id)
        assertEquals(7000L, state.audio.positionMs)
        assertEquals(RepeatMode.Off, state.queue.repeatMode)
        assertFalse(state.queue.shuffleEnabled)
        assertFalse(state.canGoNext)
    }

    @Test
    fun modeChangesAndExactPermutationSurviveReopening() = runTest {
        val store = MemoryQueueStore()
        val playback = controller(FakeAudioOutput(), store)
        repeat(6) { playback.add(local) }
        playback.playEntry(playback.state.value.queue.entries.last().id)
        playback.setRepeatMode(RepeatMode.One)
        playback.setShuffleEnabled(true)
        playback.next()
        val saved = playback.state.value
        val restored = controller(FakeAudioOutput(), store).state.value
        assertEquals(saved.currentEntry, restored.currentEntry)
        assertEquals(saved.queue.entries, restored.queue.entries)
        assertEquals(saved.queue.playOrder, restored.queue.playOrder)
        assertTrue(restored.queue.shuffleEnabled)
        assertEquals(RepeatMode.One, restored.queue.repeatMode)
    }

    @Test
    fun invalidModeOrPermutationRejectsSnapshot() {
        val queue = Queue().append(local).append(stream)
        val text = QueueJson.encode(QueueSnapshot(queue, null, 0))
        for (fields in listOf(
            """"repeat":"invalid"""",
            """"shuffle":"true"""",
            """"shuffle":true,"shuffleOrder":[1,1]""",
            """"shuffle":true,"shuffleOrder":[2]""",
            """"shuffle":true,"shuffleOrder":[1,7]""",
        )) {
            val corrupt = Json.parseToJsonElement(text).jsonObject.toMutableMap()
            corrupt.putAll(Json.parseToJsonElement("{$fields}").jsonObject)
            assertNull(QueueJson.decode(JsonObject(corrupt).toString()), fields)
        }
    }

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

    @Test
    fun checkpointsSaveOnlyChangedPositionsWhileTheQueueIsPlaying() = runTest {
        val store = MemoryQueueStore()
        val output = FakeAudioOutput()
        val playback = controller(output, store)
        playback.add(stream)
        playback.resume()
        val initialWrites = store.writes

        output.positionMs = 14_000
        advanceTimeBy(14_999)
        assertEquals(initialWrites, store.writes)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(14_000L, QueueJson.decode(store.text)?.positionMs)
        advanceTimeBy(15_001)
        assertEquals(initialWrites + 1, store.writes)

        output.positionMs = 31_000
        advanceTimeBy(15_000)
        assertEquals(31_000L, QueueJson.decode(store.text)?.positionMs)
        playback.pause()
        val pausedWrites = store.writes
        output.positionMs = 32_000
        advanceTimeBy(60_000)
        assertEquals(pausedWrites, store.writes)
        playback.resume()
        output.positionMs = 44_000
        advanceTimeBy(15_001)
        assertEquals(44_000L, QueueJson.decode(store.text)?.positionMs)

        output.status = SampleAudioStatus.Ended
        output.emitEvent()
        runCurrent()
        val endedWrites = store.writes
        output.positionMs = 0
        advanceTimeBy(60_000)
        assertEquals(endedWrites, store.writes)
    }

    @Test
    fun backgroundWritesAreDeferredAndLifecycleSaveFlushesTheLatestPosition() = runTest {
        val store = MemoryQueueStore()
        val pending = ArrayDeque<() -> Unit>()
        val deferredStore = object : QueueStore by store {
            override fun scheduleSave(flush: Boolean, write: () -> Unit) {
                pending.addLast(write)
                if (flush) {
                    while (pending.isNotEmpty()) pending.removeFirst().invoke()
                }
            }
        }
        val output = FakeAudioOutput()
        val playback = controller(output, deferredStore)
        playback.add(stream)
        playback.resume()
        output.positionMs = 4_250L

        assertEquals(0, store.writes)
        playback.saveQueueNow()
        assertEquals(4_250L, QueueJson.decode(store.text)?.positionMs)
        assertEquals(playback.state.value.currentEntry?.id, QueueJson.decode(store.text)?.selectedEntryId)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun saveNowReadsTheLoadedPlayerAndPreservesThePositionBeforeReloadingIt() = runTest {
        val store = MemoryQueueStore()
        val output = FakeAudioOutput()
        val playback = controller(output, store)
        playback.add(stream)
        playback.resume()
        output.positionMs = 4_250
        assertEquals(0L, playback.state.value.audio.positionMs)

        playback.saveQueueNow()
        assertEquals(4_250L, QueueJson.decode(store.text)?.positionMs)
        val writes = store.writes
        playback.saveQueueNow()
        assertEquals(writes, store.writes)

        val reopened = controller(FakeAudioOutput(), store)
        reopened.saveQueueNow()
        assertEquals(writes, store.writes)
        assertEquals(4_250L, reopened.state.value.audio.positionMs)

        playback.pause()
        output.positionMs = 4_275
        playback.saveQueueNow()
        assertEquals(4_275L, QueueJson.decode(store.text)?.positionMs)
    }

    @Test
    fun reorderedQueuePersistsAndRestoresOrderSelectionAndNavigation() = runTest {
        val third = Track("demo:track:senja", "Senja", listOf("Senada"), source = "demo")
        val store = MemoryQueueStore()
        val playback = controller(FakeAudioOutput(), store)
        playback.addAll(listOf(stream, local), playlistKey = "library:utama")
        playback.add(third)
        playback.add(stream)
        val (copy1, mid, last, copy2) = playback.state.value.queue.entries

        playback.setRepeatMode(RepeatMode.All)
        playback.playEntry(mid.id)
        // Pindahkan salinan kedua ke posisi teratas, dan lagu aktif ke posisi kedua.
        playback.move(copy2.id, 0)
        playback.move(mid.id, 1)
        val expectedOrder = playback.state.value.queue.playOrder
        assertEquals(listOf(copy2, mid, copy1, last), expectedOrder)

        val restored = controller(FakeAudioOutput(), store)
        val restoredState = restored.state.value
        assertEquals(expectedOrder, restoredState.queue.entries)
        assertEquals(mid.id, restoredState.currentEntry?.id)
        assertEquals("library:utama", restoredState.currentEntry?.playlistKey)
        assertEquals(RepeatMode.All, restoredState.queue.repeatMode)

        restored.next()
        assertEquals(copy1.id, restored.state.value.currentEntry?.id)
    }

    @Test
    fun reorderedShuffledQueuePersistsEffectiveOrderAndBaseOrderAcrossRestore() = runTest {
        val third = Track("demo:track:senja", "Senja", listOf("Senada"), source = "demo")
        val store = MemoryQueueStore()
        val playback = controller(FakeAudioOutput(), store)
        playback.add(stream)
        playback.add(local)
        playback.add(third)
        val baseEntries = playback.state.value.queue.entries

        playback.playEntry(baseEntries.first().id)
        playback.setShuffleEnabled(true)
        val lastShuffled = playback.state.value.queue.playOrder.last()
        playback.move(lastShuffled.id, 1)
        val savedOrder = playback.state.value.queue.playOrder

        val restored = controller(FakeAudioOutput(), store)
        assertTrue(restored.state.value.queue.shuffleEnabled)
        assertEquals(baseEntries, restored.state.value.queue.entries)
        assertEquals(savedOrder, restored.state.value.queue.playOrder)

        restored.next()
        assertEquals(lastShuffled.id, restored.state.value.currentEntry?.id)

        restored.setShuffleEnabled(false)
        assertEquals(baseEntries, restored.state.value.queue.playOrder)
        assertEquals(lastShuffled.id, restored.state.value.currentEntry?.id)
    }

    @Test
    fun noOpMoveDoesNotWriteToStore() = runTest {
        val store = MemoryQueueStore()
        val playback = controller(FakeAudioOutput(), store)
        playback.add(stream)
        playback.add(local)
        val first = playback.state.value.queue.entries.first()
        val writesBefore = store.writes

        playback.move(first.id, 0)
        playback.move(first.id, -5)
        playback.move(999L, 1)

        assertEquals(writesBefore, store.writes)
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
