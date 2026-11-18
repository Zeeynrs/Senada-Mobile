package senada.playback

internal enum class SampleAudioSource {
    Local,
    Https,
}

internal data class SampleAudioSelection(
    val title: String,
    val source: SampleAudioSource,
    val uri: String,
    val queueEntryId: Long? = null,
)

internal enum class SampleAudioStatus {
    Idle,
    Loading,
    Playing,
    Paused,
    Ended,
    Failed,
}

internal data class SampleAudioState(
    val selection: SampleAudioSelection? = null,
    val status: SampleAudioStatus = SampleAudioStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
) {
    val isActive get() = status == SampleAudioStatus.Loading || status == SampleAudioStatus.Playing
    val canSeek get() = durationMs != null && (isActive || status == SampleAudioStatus.Paused)
}

internal interface SampleAudioOutput {
    val status: SampleAudioStatus
    val positionMs: Long
    val durationMs: Long?
    fun play(uri: String, restart: Boolean)
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

internal class SampleAudioPlayer(private val output: SampleAudioOutput) {
    var state = SampleAudioState()
        private set

    fun play(selection: SampleAudioSelection) {
        refresh()
        val sameSelection = state.selection == selection
        if (sameSelection && state.isActive) return

        val resume = sameSelection && state.status == SampleAudioStatus.Paused
        output.play(selection.uri, restart = !resume)
        state = if (resume) {
            state.copy(status = SampleAudioStatus.Loading)
        } else {
            SampleAudioState(selection, SampleAudioStatus.Loading)
        }
        refresh()
    }

    fun pause() {
        refresh()
        if (!state.isActive) return

        output.pause()
        state = state.copy(status = SampleAudioStatus.Paused)
    }

    fun refresh() {
        if (!state.isActive) return

        val status = output.status
        val duration = output.durationMs?.takeIf { it > 0 }
        val position = if (status == SampleAudioStatus.Ended && duration != null) {
            duration
        } else {
            output.positionMs.coerceIn(0, duration ?: Long.MAX_VALUE)
        }
        state = state.copy(status = status, positionMs = position, durationMs = duration)
    }

    fun seekTo(positionMs: Long) {
        if (!state.canSeek) return
        val duration = state.durationMs ?: return
        val target = positionMs.coerceIn(0, duration)
        output.seekTo(target)
        state = state.copy(positionMs = target)
    }

    fun advanceAfterEnd(queue: Queue, selectionFor: (QueueItem) -> SampleAudioSelection) {
        if (state.status != SampleAudioStatus.Ended) return
        val nextEntry = queue.adjacentTo(state.selection?.queueEntryId, 1) ?: return
        play(selectionFor(nextEntry))
    }

    fun onQueueEntryRemoved(entryId: Long) {
        if (state.selection?.queueEntryId != entryId) return
        output.pause()
        state = SampleAudioState()
    }
}
