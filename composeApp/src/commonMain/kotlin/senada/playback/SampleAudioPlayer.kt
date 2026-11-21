package senada.playback

internal enum class SampleAudioSource {
    Local,
    Https,
    YouTubeMusic,
}

internal data class SampleAudioSelection(
    val title: String,
    val source: SampleAudioSource,
    val uri: String,
    val queueEntryId: Long? = null,
    /** Batas berlaku alamat stream; null untuk file lokal dan alamat tetap. */
    val expiresAtEpochMs: Long? = null,
) {
    /** Entri antrean yang alamat audionya belum didapat. */
    val isPending get() = uri.isEmpty()

    /** Alamat dianggap habis sedikit lebih awal, agar sisa lagu masih bisa dimuat dengannya. */
    fun isExpired(nowMs: Long) = expiresAtEpochMs != null && nowMs >= expiresAtEpochMs - EXPIRY_MARGIN_MS

    companion object {
        private const val EXPIRY_MARGIN_MS = 10 * 60_000L

        /** Sumber baru diketahui setelah alamat didapat; status Loading tidak menampilkannya. */
        fun pending(title: String, queueEntryId: Long) =
            SampleAudioSelection(title, SampleAudioSource.Https, uri = "", queueEntryId)
    }
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

    /** Dipanggil pemutar native saat status berubah, termasuk saat aplikasi di latar belakang. */
    var onEvent: (() -> Unit)?

    /** Saat [restart], audio baru dimuat dan dimulai dari [startAtMs]; selain itu melanjutkan audio yang ada. */
    fun play(uri: String, restart: Boolean, startAtMs: Long = 0)
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

internal class SampleAudioPlayer(private val output: SampleAudioOutput) {
    var state = SampleAudioState()
        private set

    /** Selama alamat audio dicari, status pemutar native tidak mewakili lagu yang dipilih. */
    private var awaitingSource = false

    /**
     * Memilih lagu yang alamatnya sedang dicari: audio lama berhenti dan status menjadi Loading.
     * [positionMs] adalah titik mulai nanti, misalnya posisi terakhir sebelum alamat diganti.
     */
    fun prepare(selection: SampleAudioSelection, positionMs: Long = 0) {
        if (state.isActive && !awaitingSource) output.pause()
        awaitingSource = true
        state = SampleAudioState(selection, SampleAudioStatus.Loading, positionMs = positionMs)
    }

    /** Alamat audio tidak dapat disiapkan; pilihan tetap ada agar bisa dicoba lagi. */
    fun failPreparing() {
        awaitingSource = false
        state = state.copy(status = SampleAudioStatus.Failed)
    }

    /** Lagu baru dimulai dari [startAtMs]; lagu yang sama dan sedang dijeda dilanjutkan dari posisinya. */
    fun play(selection: SampleAudioSelection, startAtMs: Long = 0) {
        awaitingSource = false
        refresh()
        val sameSelection = state.selection == selection
        if (sameSelection && state.isActive) return

        val resume = sameSelection && state.status == SampleAudioStatus.Paused
        if (resume) {
            output.play(selection.uri, restart = false)
            state = state.copy(status = SampleAudioStatus.Loading)
        } else {
            output.play(selection.uri, restart = true, startAtMs = startAtMs)
            state = SampleAudioState(selection, SampleAudioStatus.Loading, positionMs = startAtMs)
        }
        refresh()
    }

    fun pause() {
        if (awaitingSource) {
            awaitingSource = false
            state = state.copy(status = SampleAudioStatus.Paused)
            return
        }
        refresh()
        if (!state.isActive) return

        output.pause()
        state = state.copy(status = SampleAudioStatus.Paused)
    }

    fun refresh() {
        if (awaitingSource || !state.isActive) return

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

    fun onQueueEntryRemoved(entryId: Long) {
        if (state.selection?.queueEntryId != entryId) return
        awaitingSource = false
        output.pause()
        state = SampleAudioState()
    }
}
