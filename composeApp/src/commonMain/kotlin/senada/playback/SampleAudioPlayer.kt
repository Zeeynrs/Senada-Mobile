package senada.playback

internal enum class SampleAudioSource {
    Local,
    Https,
}

internal enum class SampleAudioStatus {
    Idle,
    Loading,
    Playing,
    Paused,
    Ended,
    Failed,
}

internal data class SampleAudioState(
    val source: SampleAudioSource? = null,
    val status: SampleAudioStatus = SampleAudioStatus.Idle,
) {
    val isActive get() = status == SampleAudioStatus.Loading || status == SampleAudioStatus.Playing
}

internal interface SampleAudioOutput {
    val status: SampleAudioStatus
    fun play(source: SampleAudioSource, restart: Boolean)
    fun pause()
    fun release()
}

internal class SampleAudioPlayer(private val output: SampleAudioOutput) {
    var state = SampleAudioState()
        private set

    fun play(source: SampleAudioSource) {
        refresh()
        val sameSource = state.source == source
        if (sameSource && state.isActive) return

        val resume = sameSource && state.status == SampleAudioStatus.Paused
        output.play(source, restart = !resume)
        state = SampleAudioState(source, SampleAudioStatus.Loading)
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
}
