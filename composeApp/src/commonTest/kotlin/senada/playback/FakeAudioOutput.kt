package senada.playback

internal class FakeAudioOutput : SampleAudioOutput {
    val restarts = mutableListOf<Boolean>()
    /** Titik mulai setiap kali audio dimuat ulang. */
    val startPositions = mutableListOf<Long>()
    val seeks = mutableListOf<Long>()
    /** Akhir audio yang diteruskan setiap kali audio dimuat ulang. */
    val endPositions = mutableListOf<Long?>()
    var pauseCount = 0
    override var volume = 1f
    val volumesAtPause = mutableListOf<Float>()
    var emitEventOnPlay = false
    override var status = SampleAudioStatus.Playing
    override var positionMs = 0L
    override var durationMs: Long? = null
    override var onEvent: (() -> Unit)? = null

    override fun play(uri: String, restart: Boolean, startAtMs: Long, endAtMs: Long?) {
        restarts += restart
        status = SampleAudioStatus.Playing
        if (restart) {
            startPositions += startAtMs
            endPositions += endAtMs
            positionMs = startAtMs
        }
        if (emitEventOnPlay) emitEvent()
    }

    override fun pause() {
        pauseCount++
        volumesAtPause += volume
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
