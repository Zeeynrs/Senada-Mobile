package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.model.Track
import senada.playback.PlaybackState
import senada.playback.Queue
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioState
import senada.playback.SampleAudioStatus

class ListeningTrackerTest {
    private val long = Track("yt:track:long", "Lagu panjang", listOf("Satu"), 90_000, source = "ytmusic")
    private val short = Track("yt:track:short", "Lagu pendek", listOf("Dua"), 12_001, source = "ytmusic")
    private val queue = Queue().append(long).append(short)
    private val longSelection = SampleAudioSelection("Panjang", SampleAudioSource.YouTubeMusic, "long", queue.entries[0].id)
    private val shortSelection = SampleAudioSelection("Pendek", SampleAudioSource.YouTubeMusic, "short", queue.entries[1].id)

    @Test
    fun pause_buffering_and_seek_do_not_add_listening_time() {
        val tracker = ListeningTracker()
        val updates = mutableListOf<ListenEvent>()
        fun sample(position: Long, status: SampleAudioStatus = SampleAudioStatus.Playing) {
            updates += tracker.sample(state(longSelection, position, status), 10_000)
        }
        sample(0)
        for (i in 1..10) sample(i * 3_000L)
        assertEquals(30_000L, updates.single().listenedMs)

        sample(30_000, SampleAudioStatus.Paused)
        sample(31_000, SampleAudioStatus.Paused)
        sample(31_000, SampleAudioStatus.Loading)
        sample(32_000)
        sample(65_000)
        sample(50_000)
        sample(52_000)
        updates += tracker.sample(state(shortSelection, 0), 20_000)

        assertEquals(listOf(30_000L, 32_000L), updates.map { it.listenedMs })
        assertEquals(setOf(long.id), updates.map { it.track.id }.toSet())
        assertEquals(1, updates.map { it.id }.distinct().size)
        assertEquals(10_000L, updates.last().startedAt)
    }

    @Test
    fun a_short_song_qualifies_at_half_rounded_up_and_repeat_starts_a_new_play() {
        val tracker = ListeningTracker()
        assertTrue(tracker.sample(state(shortSelection, 0), 1).isEmpty())
        assertTrue(tracker.sample(state(shortSelection, 3_000), 2).isEmpty())
        assertTrue(tracker.sample(state(shortSelection, 6_000), 3).isEmpty())
        val first = tracker.sample(state(shortSelection, 6_001), 4).single()
        assertEquals(6_001L, first.listenedMs)

        val replay = shortSelection.copy()
        assertTrue(tracker.sample(state(replay, 0), 5).isEmpty())
        tracker.sample(state(replay, 3_000), 6)
        tracker.sample(state(replay, 6_000), 7)
        val second = tracker.sample(state(replay, 6_001), 8).single()
        assertEquals(short.id, second.track.id)
        assertEquals(5L, second.startedAt)
        assertEquals(2, setOf(first.id, second.id).size)
    }

    @Test
    fun restarting_mid_song_does_not_credit_the_restored_position() {
        val beforeRestart = ListeningTracker()
        beforeRestart.sample(state(longSelection, 0), 1)
        for (i in 1..9) beforeRestart.sample(state(longSelection, i * 3_000L), 1L + i)
        val saved = beforeRestart.sample(state(longSelection, 30_000), 11).single()
        assertEquals(30_000L, saved.listenedMs)

        val afterRestart = ListeningTracker()
        assertTrue(afterRestart.sample(state(longSelection, 30_000, SampleAudioStatus.Paused), 100).isEmpty())
        assertTrue(afterRestart.sample(state(longSelection, 30_000), 101).isEmpty())
        assertTrue(afterRestart.sample(state(longSelection, 33_000), 102).isEmpty())
        assertTrue(afterRestart.sample(state(shortSelection, 0), 103).isEmpty())
    }

    private fun state(
        selection: SampleAudioSelection,
        position: Long,
        status: SampleAudioStatus = SampleAudioStatus.Playing,
    ) = PlaybackState(queue, SampleAudioState(selection, status, position))
}
