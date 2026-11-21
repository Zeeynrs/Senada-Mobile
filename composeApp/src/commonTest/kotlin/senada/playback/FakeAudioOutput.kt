package senada.playback

internal class FakeAudioOutput : SampleAudioOutput {
    val restarts = mutableListOf<Boolean>()
    /** Titik mulai setiap kali audio dimuat ulang. */
    val startPositions = mutableListOf<Long>()
    val seeks = mutableListOf<Long>()
    var pauseCount = 0
    var emitEventOnPlay = false
    override var status = SampleAudioStatus.Playing
    override var positionMs = 0L
    override var durationMs: Long? = null
    override var onEvent: (() -> Unit)? = null

    override fun play(uri: String, restart: Boolean, startAtMs: Long) {
        restarts += restart
        status = SampleAudioStatus.Playing
        if (restart) {
            startPositions += startAtMs
            positionMs = startAtMs
        }
        if (emitEventOnPlay) emitEvent()
    }

    override fun pause() {
        pauseCount++
        status = SampleAudioStatus.Paused
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        this.positionMs = positionMs
    }

    override fun release() = Unit

    fun emitEvent() {
        onEvent?.invoke()
    }
}
