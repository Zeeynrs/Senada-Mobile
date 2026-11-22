package senada.library

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import senada.model.Track
import senada.playback.PlaybackState
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioStatus

private const val MAX_STEP_MS = 3_000L
private const val MIN_LISTEN_MS = 30_000L
private const val CHECKPOINT_MS = 5_000L

/** Membentuk satu catatan per putaran yang memenuhi ambang, dengan ID tetap saat durasinya diperbarui. */
internal class ListeningTracker {
    private var session: Session? = null

    @OptIn(ExperimentalUuidApi::class)
    fun sample(state: PlaybackState, now: Long): List<ListenEvent> = buildList {
        val entry = state.currentEntry
        val audio = state.audio
        val selection = audio.selection
        val playing = audio.status == SampleAudioStatus.Playing
        val previous = session
        if (previous != null) {
            val replay = playing && selection !== previous.selection &&
                audio.positionMs <= 1_000L && previous.listenedMs > 0
            if (entry?.id != previous.entryId || entry.track.id != previous.track.id || replay) {
                previous.checkpoint(force = true)?.let(::add)
                session = null
            }
        }
        if (entry == null || selection == null) return@buildList

        val current = session ?: run {
            if (playing) {
                session = Session(
                    Uuid.random().toString(), entry.id, entry.track, selection, now, audio.positionMs,
                    duration = audio.durationMs ?: entry.track.durationMs,
                )
            }
            return@buildList
        }

        val step = audio.positionMs - current.position
        val wasPlaying = current.playing
        val reachedBoundary = audio.status == SampleAudioStatus.Paused || audio.status == SampleAudioStatus.Ended
        if (wasPlaying && (playing || reachedBoundary) && step in 1..MAX_STEP_MS) current.listenedMs += step
        current.position = audio.positionMs
        current.playing = playing
        current.track = entry.track
        current.duration = audio.durationMs ?: current.duration ?: entry.track.durationMs
        if (!selection.isPending) current.selection = selection
        if (audio.status == SampleAudioStatus.Ended) session = null
        current.checkpoint(force = wasPlaying && !playing)?.let(::add)
    }

    private class Session(
        val id: String,
        val entryId: Long,
        var track: Track,
        var selection: SampleAudioSelection,
        val startedAt: Long,
        var position: Long,
        var duration: Long?,
    ) {
        var playing = true
        var listenedMs = 0L
        private var checkpointMs = 0L

        fun checkpoint(force: Boolean): ListenEvent? {
            val half = duration?.takeIf { it > 0 }?.let { it / 2 + it % 2 } ?: MIN_LISTEN_MS
            if (listenedMs < minOf(half, MIN_LISTEN_MS) || listenedMs == checkpointMs) return null
            if (!force && checkpointMs != 0L && listenedMs - checkpointMs < CHECKPOINT_MS) return null
            checkpointMs = listenedMs
            return ListenEvent(id, track.copy(durationMs = duration), startedAt, listenedMs)
        }
    }
}
