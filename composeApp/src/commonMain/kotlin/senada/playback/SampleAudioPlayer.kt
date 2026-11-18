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
) {
    val isActive get() = status == SampleAudioStatus.Loading || status == SampleAudioStatus.Playing
}

internal interface SampleAudioOutput {
    val status: SampleAudioStatus
    fun play(uri: String, restart: Boolean)
    fun pause()
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
        state = SampleAudioState(selection, SampleAudioStatus.Loading)
        refresh()
    }

    fun pause() {
        refresh()
        if (!state.isActive) return

        output.pause()
        state = state.copy(status = SampleAudioStatus.Paused)
    }

    fun refresh() {
        if (state.isActive) state = state.copy(status = output.status)
    }

    fun advanceAfterEnd(queue: Queue, selectionFor: (QueueItem) -> SampleAudioSelection) {
        if (state.status != SampleAudioStatus.Ended) return
        val activeEntryId = state.selection?.queueEntryId ?: return
        val index = queue.entries.indexOfFirst { it.id == activeEntryId }
        if (index < 0) return

        val nextEntry = queue.entries.getOrNull(index + 1) ?: return
        play(selectionFor(nextEntry))
    }

    fun onQueueEntryRemoved(entryId: Long) {
        if (state.selection?.queueEntryId != entryId) return
        output.pause()
        state = SampleAudioState()
    }
}
