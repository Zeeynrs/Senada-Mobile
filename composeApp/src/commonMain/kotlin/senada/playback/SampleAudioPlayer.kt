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
    /** Panjang audio menurut sumbernya; null bila hanya pemutar native yang tahu. */
    val durationMs: Long? = null,
    val loudnessDb: Float? = null,
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
    val canStopAfterTrack get() = selection != null && status != SampleAudioStatus.Ended
}

internal interface SampleAudioOutput {
    val status: SampleAudioStatus
    val positionMs: Long
    val durationMs: Long?
    /** Gain pemutar dari 0 hingga 1, terpisah dari volume perangkat. */
    var volume: Float

    /** Dipanggil pemutar native saat status berubah, termasuk saat aplikasi di latar belakang. */
    var onEvent: (() -> Unit)?

    /**
     * Saat [restart], audio baru dimuat dan dimulai dari [startAtMs]; selain itu melanjutkan audio yang ada.
     * [endAtMs] adalah akhir audio yang sebenarnya bila sumbernya tahu; pemutar berhenti di sana.
     */
    fun play(uri: String, restart: Boolean, startAtMs: Long = 0, endAtMs: Long? = null)
    fun play(selection: SampleAudioSelection, startAtMs: Long) {
        play(selection.uri, restart = true, startAtMs = startAtMs, endAtMs = selection.knownDurationMs)
    }
    /** Pilihan lama sudah tidak menjadi lagu aktif, walaupun item native masih dijeda. */
    fun clearSelection() {}
    fun pause()
    fun seekTo(positionMs: Long)
    fun release()
}

internal class SampleAudioPlayer(private val output: SampleAudioOutput) {
    var state = SampleAudioState()
        private set

    /** Selama alamat audio dicari, status pemutar native tidak mewakili lagu yang dipilih. */
    private var awaitingSource = false

    fun restorePaused(entry: QueueItem, positionMs: Long) {
        state = SampleAudioState(
            selection = SampleAudioSelection.pending(entry.track.title, entry.id),
            status = SampleAudioStatus.Paused,
            positionMs = positionMs,
            durationMs = entry.track.durationMs?.takeIf { it > 0 }?.coerceAtLeast(positionMs),
        )
    }

    /**
     * Memilih lagu yang alamatnya sedang dicari: audio lama berhenti dan status menjadi Loading.
     * [positionMs] adalah titik mulai nanti, misalnya posisi terakhir sebelum alamat diganti.
     */
    fun prepare(selection: SampleAudioSelection, positionMs: Long = 0) {
        if (state.isActive && !awaitingSource) output.pause()
        output.clearSelection()
        awaitingSource = true
        state = SampleAudioState(selection, SampleAudioStatus.Loading, positionMs = positionMs)
    }

    /** Alamat audio tidak dapat disiapkan; pilihan tetap ada agar bisa dicoba lagi. */
    fun failPreparing() {
        awaitingSource = false
        state = state.copy(status = SampleAudioStatus.Failed)
    }

    /** Lagu baru dimulai dari [startAtMs]; lagu yang sama dan sedang dijeda dilanjutkan dari posisinya. */
    fun play(selection: SampleAudioSelection, startAtMs: Long = 0, forceRestart: Boolean = false) {
        awaitingSource = false
        refresh()
        val sameSelection = !forceRestart && state.selection == selection
        if (sameSelection && state.isActive) return

        val resume = sameSelection && state.status == SampleAudioStatus.Paused
        if (resume) {
            output.play(selection.uri, restart = false)
            state = state.copy(status = SampleAudioStatus.Loading)
        } else {
            output.play(selection, startAtMs)
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

    fun pauseAtEnd() {
        output.pause()
        state = state.copy(status = SampleAudioStatus.Paused)
    }

    fun refresh() {
        if (awaitingSource || !state.isActive) return

        var status = output.status
        val known = state.selection?.knownDurationMs
        val reported = output.durationMs?.takeIf { it > 0 }
        // Pemutar native bisa menebak durasi lebih panjang dari audionya; panjang dari sumber yang menang.
        val duration = if (known != null && reported != null) minOf(known, reported) else known ?: reported
        if (status == SampleAudioStatus.Playing && known != null && output.positionMs >= known + END_GRACE_MS) {
            // Pemutar terus berjalan senyap melewati akhir audio: anggap lagu selesai.
            output.pause()
            status = SampleAudioStatus.Ended
        }
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
        // Entri yang dipulihkan belum punya audio di pemutar native.
        if (state.selection?.isPending == false) output.seekTo(target)
        state = state.copy(positionMs = target)
    }

    fun onQueueEntryRemoved(entryId: Long) {
        if (state.selection?.queueEntryId != entryId) return
        awaitingSource = false
        output.pause()
        output.clearSelection()
        state = SampleAudioState()
    }
}

/** Toleransi sebelum posisi yang melewati akhir audio dianggap lagu sudah selesai. */
internal const val END_GRACE_MS = 1_500L

private val SampleAudioSelection.knownDurationMs: Long?
    get() = durationMs?.takeIf { it > 0 }
