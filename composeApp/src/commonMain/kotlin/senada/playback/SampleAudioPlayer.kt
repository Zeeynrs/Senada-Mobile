package senada.playback

import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class SampleAudioSource {
    Local,
    Offline,
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
    /** Kecepatan pemutaran aktual saat audio diputar (1.0f = normal). */
    val playbackSpeed: Float = 1f,
    /** Waktu jam monotonik (ms) saat [positionMs] dijangkarkan dari pemutar. */
    val sampledAtMs: Long? = null,
    /** Naik pada seek, pergantian lagu, atau lompatan posisi agar tampilan langsung sinkron ulang. */
    val discontinuityVersion: Long = 0L,
) {
    val isActive get() = status == SampleAudioStatus.Loading || status == SampleAudioStatus.Playing
    val canSeek get() = durationMs != null && (isActive || status == SampleAudioStatus.Paused)
    val canStopAfterTrack get() = selection != null && status != SampleAudioStatus.Ended

    /**
     * Menghitung posisi pemutaran saat [monotonicMs] berdasarkan sampel terakhir [positionMs],
     * [sampledAtMs], dan [playbackSpeed]. Hanya maju saat status [SampleAudioStatus.Playing].
     */
    fun positionAt(monotonicMs: Long): Long {
        val base = positionMs.coerceAtLeast(0L)
        if (status != SampleAudioStatus.Playing || selection?.isPending == true) {
            return durationMs?.let { base.coerceAtMost(it) } ?: base
        }
        val anchor = sampledAtMs ?: return durationMs?.let { base.coerceAtMost(it) } ?: base
        val elapsed = (monotonicMs - anchor).coerceAtLeast(0L)
        val rate = playbackSpeed.sanitizedPlaybackRate()
        val estimated = base + (elapsed.toDouble() * rate.toDouble()).roundToLong()
        return durationMs?.let { estimated.coerceIn(0L, it) } ?: estimated.coerceAtLeast(0L)
    }
}

internal interface SampleAudioOutput {
    val status: SampleAudioStatus
    val positionMs: Long
    val durationMs: Long?
    /** Kecepatan pemutaran aktual pemutar native (1.0f = normal). */
    val playbackRate: Float get() = 1f
    /** Gain pemutar dari 0 hingga 1, terpisah dari volume perangkat. */
    var volume: Float

    /** Dipanggil pemutar native saat status berubah, termasuk saat aplikasi di latar belakang. */
    var onEvent: (() -> Unit)?

    /** Mengembalikan true sekali bila pemutar native melaporkan lompatan posisi sejak refresh terakhir. */
    fun consumeDiscontinuity(): Boolean = false

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

internal class SampleAudioPlayer(
    private val output: SampleAudioOutput,
    private val monotonicNow: () -> Long = { 0L },
) {
    var state = SampleAudioState()
        private set

    /** Selama alamat audio dicari, status pemutar native tidak mewakili lagu yang dipilih. */
    private var awaitingSource = false

    /**
     * Pengguna atau antrean meminta audio berjalan. Selama true, Paused dari pemutar native hanya
     * dianggap sementara: statusnya terus dibaca agar Playing dan akhir lagu tidak terlewat.
     */
    private var playRequested = false

    fun restorePaused(entry: QueueItem, positionMs: Long) {
        playRequested = false
        state = SampleAudioState(
            selection = SampleAudioSelection.pending(entry.track.title, entry.id),
            status = SampleAudioStatus.Paused,
            positionMs = positionMs,
            durationMs = entry.track.durationMs?.takeIf { it > 0 }?.coerceAtLeast(positionMs),
            playbackSpeed = output.playbackRate.sanitizedPlaybackRate(),
            sampledAtMs = monotonicNow(),
            discontinuityVersion = state.discontinuityVersion + 1L,
        )
    }

    /**
     * Memilih lagu yang alamatnya sedang dicari: audio lama berhenti dan status menjadi Loading.
     * [positionMs] adalah titik mulai nanti, misalnya posisi terakhir sebelum alamat diganti.
     */
    fun prepare(selection: SampleAudioSelection, positionMs: Long = 0) {
        if (state.isActive && !awaitingSource) output.pause()
        output.clearSelection()
        output.consumeDiscontinuity()
        awaitingSource = true
        playRequested = true
        state = SampleAudioState(
            selection = selection,
            status = SampleAudioStatus.Loading,
            positionMs = positionMs,
            playbackSpeed = output.playbackRate.sanitizedPlaybackRate(),
            sampledAtMs = null,
            discontinuityVersion = state.discontinuityVersion + 1L,
        )
    }

    /** Alamat audio tidak dapat disiapkan; pilihan tetap ada agar bisa dicoba lagi. */
    fun failPreparing() {
        awaitingSource = false
        playRequested = false
        state = state.copy(status = SampleAudioStatus.Failed, sampledAtMs = monotonicNow())
    }

    /** Lagu baru dimulai dari [startAtMs]; lagu yang sama dan sedang dijeda dilanjutkan dari posisinya. */
    fun play(selection: SampleAudioSelection, startAtMs: Long = 0, forceRestart: Boolean = false) {
        awaitingSource = false
        refresh()
        playRequested = true
        val sameSelection = !forceRestart && state.selection == selection
        if (sameSelection && state.isActive) return

        val resume = sameSelection && state.status == SampleAudioStatus.Paused
        val nowMono = monotonicNow()
        val rate = output.playbackRate.sanitizedPlaybackRate()
        if (resume) {
            output.play(selection.uri, restart = false)
            state = state.copy(
                status = SampleAudioStatus.Loading,
                playbackSpeed = rate,
                sampledAtMs = nowMono,
            )
        } else {
            output.consumeDiscontinuity()
            output.play(selection, startAtMs)
            state = SampleAudioState(
                selection = selection,
                status = SampleAudioStatus.Loading,
                positionMs = startAtMs,
                playbackSpeed = rate,
                sampledAtMs = nowMono,
                discontinuityVersion = state.discontinuityVersion + 1L,
            )
        }
        refresh()
    }

    fun pause() {
        val nowMono = monotonicNow()
        val rate = output.playbackRate.sanitizedPlaybackRate()
        val wasNativePause = playRequested && state.status == SampleAudioStatus.Paused
        playRequested = false
        if (wasNativePause) {
            // Status sudah Paused dari pemutar native; pastikan audio benar-benar berhenti.
            output.pause()
            state = state.copy(
                positionMs = output.positionMs.coerceIn(0, state.durationMs ?: Long.MAX_VALUE),
                playbackSpeed = rate,
                sampledAtMs = nowMono,
            )
            return
        }
        if (awaitingSource) {
            awaitingSource = false
            state = state.copy(status = SampleAudioStatus.Paused, playbackSpeed = rate, sampledAtMs = nowMono)
            return
        }
        refresh()
        if (!state.isActive) return

        output.pause()
        val pausedPosition = output.positionMs.coerceIn(0, state.durationMs ?: Long.MAX_VALUE)
        state = state.copy(
            status = SampleAudioStatus.Paused,
            positionMs = pausedPosition,
            playbackSpeed = rate,
            sampledAtMs = nowMono,
        )
    }

    fun pauseAtEnd() {
        playRequested = false
        output.pause()
        state = state.copy(
            status = SampleAudioStatus.Paused,
            playbackSpeed = output.playbackRate.sanitizedPlaybackRate(),
            sampledAtMs = monotonicNow(),
        )
    }

    fun refresh(discontinuity: Boolean = false) {
        val nativeDiscontinuity = output.consumeDiscontinuity() || discontinuity
        if (awaitingSource) return
        val nowMono = monotonicNow()
        val rate = output.playbackRate.sanitizedPlaybackRate()
        if (!state.isActive && !isNativePause) {
            if (rate != state.playbackSpeed || nativeDiscontinuity) {
                val position = if (nativeDiscontinuity && state.selection?.isPending == false) {
                    output.positionMs.coerceIn(0, state.durationMs ?: Long.MAX_VALUE)
                } else {
                    state.positionMs
                }
                state = state.copy(
                    positionMs = position,
                    playbackSpeed = rate,
                    sampledAtMs = nowMono,
                    discontinuityVersion = state.discontinuityVersion + if (nativeDiscontinuity) 1L else 0L,
                )
            }
            return
        }

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
        val jumped = nativeDiscontinuity || (
            state.status == SampleAudioStatus.Playing &&
                status == SampleAudioStatus.Playing &&
                (
                    abs(position - state.positionAt(nowMono)) > DISCONTINUITY_JUMP_MS ||
                        position < state.positionMs - BACKWARD_JUMP_MS
                    )
            )
        state = state.copy(
            status = status,
            positionMs = position,
            durationMs = duration,
            playbackSpeed = rate,
            sampledAtMs = nowMono,
            discontinuityVersion = state.discontinuityVersion + if (jumped) 1L else 0L,
        )
    }

    fun seekTo(positionMs: Long) {
        if (!state.canSeek) return
        val duration = state.durationMs ?: return
        val target = positionMs.coerceIn(0, duration)
        // Entri yang dipulihkan belum punya audio di pemutar native.
        if (state.selection?.isPending == false) output.seekTo(target)
        state = state.copy(
            positionMs = target,
            playbackSpeed = output.playbackRate.sanitizedPlaybackRate(),
            sampledAtMs = monotonicNow(),
            discontinuityVersion = state.discontinuityVersion + 1L,
        )
    }

    /** Paused yang dilaporkan pemutar native tanpa permintaan jeda; bisa berubah lagi kapan saja. */
    private val isNativePause: Boolean
        get() = playRequested && state.status == SampleAudioStatus.Paused && state.selection?.isPending == false

    fun onQueueEntryRemoved(entryId: Long) {
        if (state.selection?.queueEntryId != entryId) return
        awaitingSource = false
        playRequested = false
        output.pause()
        output.clearSelection()
        output.consumeDiscontinuity()
        state = SampleAudioState()
    }

    private companion object {
        const val DISCONTINUITY_JUMP_MS = 350L
        const val BACKWARD_JUMP_MS = 50L
    }
}

private fun Float.sanitizedPlaybackRate(): Float =
    if (isFinite() && this > 0f) coerceIn(0.25f, 4f) else 1f

/** Toleransi sebelum posisi yang melewati akhir audio dianggap lagu sudah selesai. */
internal const val END_GRACE_MS = 1_500L

private val SampleAudioSelection.knownDurationMs: Long?
    get() = durationMs?.takeIf { it > 0 }
