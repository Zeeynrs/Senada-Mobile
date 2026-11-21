package senada.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import senada.catalog.CatalogException
import senada.epochMillis
import senada.model.Track

internal data class PlaybackState(
    val queue: Queue = Queue(),
    val audio: SampleAudioState = SampleAudioState(),
    /** Penjelasan untuk pengguna saat audio entri terpilih tidak dapat disiapkan. */
    val problem: String? = null,
) {
    val currentEntry: QueueItem?
        get() = audio.selection?.queueEntryId?.let { id -> queue.entries.firstOrNull { it.id == id } }

    val canGoNext: Boolean
        get() = queue.adjacentTo(audio.selection?.queueEntryId, 1) != null
}

/**
 * Pemilik antrean dan pemutaran untuk seluruh proses aplikasi. UI, notifikasi Android dan
 * kontrol layar kunci iOS memanggil objek yang sama, sehingga audio tidak bergantung pada layar.
 *
 * Alamat audio entri antrean dicari lewat [selectionFor], yang boleh menunggu jaringan. Pilihan terbaru
 * selalu menang, dan alamat entri berikutnya disiapkan lebih awal agar lanjut otomatis tetap berjalan
 * saat aplikasi di latar belakang.
 *
 * Alamat stream berlaku terbatas dan terikat ke jaringan. Setelah jeda lama alamat baru diminta, dan
 * kegagalan stream yang sedang diputar dicoba ulang sekali; keduanya melanjutkan dari posisi terakhir.
 */
internal class PlaybackController(
    private val output: SampleAudioOutput,
    private val scope: CoroutineScope,
    private val tickMs: Long = 250,
    private val now: () -> Long = ::epochMillis,
    private val selectionFor: suspend (QueueItem) -> SampleAudioSelection,
) {
    private val player = SampleAudioPlayer(output)
    private var queue = Queue()
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    private val listeners = mutableListOf<(PlaybackState) -> Unit>()
    private var lastAnnounced: PlaybackState? = null
    private var announceNextPublish = false
    private var uiVisible = false
    private var ticker: Job? = null
    private var resumeAfterInterruption = false

    private var preparing: Job? = null
    private var prefetched: Prefetch? = null
    private var problem: String? = null

    /** Entri stream yang boleh dicoba ulang sekali bila pemutar gagal, misalnya karena jaringan berganti. */
    private var retryEntryId: Long? = null

    init {
        // Event bisa datang di tengah panggilan ke pemutar native; tangani setelah panggilan selesai.
        output.onEvent = { scope.launch { sync() } }
    }

    /** Listener menerima perubahan lagu, status, durasi dan hasil seek, bukan setiap tick posisi. */
    fun addListener(listener: (PlaybackState) -> Unit) {
        listeners += listener
        listener(mutableState.value)
    }

    fun add(track: Track) {
        queue = queue.append(track)
        prefetchNext()
        publish()
    }

    fun remove(entryId: Long) {
        if (player.state.selection?.queueEntryId == entryId) {
            preparing?.cancel()
            problem = null
            retryEntryId = null
        }
        if (prefetched?.entryId == entryId) dropPrefetch()
        queue = queue.remove(entryId)
        player.onQueueEntryRemoved(entryId)
        prefetchNext()
        publish()
    }

    /** Memutar alamat yang sudah diketahui, misalnya tombol uji audio. */
    fun play(selection: SampleAudioSelection) {
        preparing?.cancel()
        problem = null
        retryEntryId = null
        player.play(selection)
        publish()
    }

    /** Entri yang sedang dipilih dilanjutkan dari posisinya; entri lain, atau yang sudah selesai, diputar dari awal. */
    fun playEntry(entryId: Long) {
        val audio = player.state
        if (audio.selection?.queueEntryId == entryId && audio.status != SampleAudioStatus.Ended) {
            resume()
        } else {
            entry(entryId)?.let { start(it) }
        }
    }

    /** Melanjutkan lagu terpilih, atau memulai entri pertama bila belum ada yang dipilih. */
    fun resume() {
        val audio = player.state
        val selection = audio.selection
        val entry = selection?.queueEntryId?.let(::entry)
        when {
            selection == null -> queue.entries.firstOrNull()?.let { start(it) }
            preparing?.isActive == true -> Unit
            // Alamat belum ada, gagal, atau kedaluwarsa setelah jeda lama: minta alamat baru, lanjut dari posisi terakhir.
            entry != null && (selection.isPending || audio.status == SampleAudioStatus.Failed || selection.isExpired(now())) ->
                start(entry, resumeAtMs = if (audio.status == SampleAudioStatus.Ended) 0 else audio.positionMs)
            else -> {
                play(selection)
                // Alamat masih berlaku tapi jaringan bisa sudah berganti selama jeda.
                if (entry != null && selection.source == SampleAudioSource.YouTubeMusic) retryEntryId = entry.id
            }
        }
    }

    fun pause() {
        resumeAfterInterruption = false
        retryEntryId = null
        preparing?.cancel()
        player.pause()
        publish()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        announceNextPublish = true
        publish()
    }

    fun next() {
        queue.adjacentTo(player.state.selection?.queueEntryId, 1)?.let { start(it) }
    }

    /** Seperti pemutar musik umumnya: setelah 3 detik, kembali ke awal lagu yang sama. */
    fun previous() {
        player.refresh()
        val audio = player.state
        val previousEntry = queue.adjacentTo(audio.selection?.queueEntryId, -1)
        when {
            audio.positionMs > RESTART_THRESHOLD_MS || previousEntry == null -> restartCurrent()
            else -> start(previousEntry)
        }
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        sync()
    }

    /** iOS: sistem sudah menjeda pemutar karena panggilan masuk atau aplikasi lain. */
    fun onInterruptionBegan() {
        resumeAfterInterruption = resumeAfterInterruption || player.state.isActive
        sync()
    }

    /** Lanjut hanya bila sistem mengizinkan dan audio sedang diputar sebelum interupsi. */
    fun onInterruptionEnded(shouldResume: Boolean) {
        val resumeNow = shouldResume && resumeAfterInterruption
        resumeAfterInterruption = false
        if (resumeNow) resume() else sync()
    }

    private fun entry(entryId: Long) = queue.entries.firstOrNull { it.id == entryId }

    /** Memutar [entry] dari [resumeAtMs]; percobaan ulang ([isRetry]) tidak dicoba ulang lagi. */
    private fun start(entry: QueueItem, resumeAtMs: Long = 0, isRetry: Boolean = false) {
        preparing?.cancel()
        problem = null
        retryEntryId = null
        val early = prefetched?.takeIf { it.entryId == entry.id }?.selection
        if (early != null) prefetched = null

        // Tanpa jeda bila alamat sudah ada, sehingga lanjut otomatis tidak menunggu dispatcher.
        preparing = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            player.prepare(SampleAudioSelection.pending(entry.track.title, entry.id), resumeAtMs)
            publish()

            val selection = try {
                // Alamat yang disiapkan lebih awal bisa sudah kedaluwarsa bila lagu sebelumnya lama dijeda.
                early?.await()?.takeUnless { it.isExpired(now()) } ?: selectionFor(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                player.failPreparing()
                problem = (e as? CatalogException)?.userMessage ?: "Audio gagal disiapkan. Coba lagi."
                publish()
                return@launch
            }
            if (player.state.selection?.queueEntryId != entry.id) return@launch
            player.play(selection, startAtMs = resumeAtMs)
            if (!isRetry && selection.source == SampleAudioSource.YouTubeMusic) retryEntryId = entry.id
            prefetchNext()
            publish()
        }
    }

    /** Menyiapkan alamat entri setelah lagu yang dipilih, sekali per entri. */
    private fun prefetchNext() {
        val next = queue.adjacentTo(player.state.selection?.queueEntryId, 1)
        if (next == null) {
            dropPrefetch()
            return
        }
        if (prefetched?.entryId == next.id) return
        dropPrefetch()
        prefetched = Prefetch(
            next.id,
            scope.async {
                // Kegagalan di sini tidak ditampilkan; alamat dicari ulang saat entri diputar.
                try {
                    selectionFor(next)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            },
        )
    }

    private fun dropPrefetch() {
        prefetched?.selection?.cancel()
        prefetched = null
    }

    private fun restartCurrent() {
        val audio = player.state
        if (audio.canSeek) {
            seekTo(0)
        } else {
            audio.selection?.queueEntryId?.let(::entry)?.let { start(it) } ?: audio.selection?.let(this::play)
        }
    }

    private fun sync() {
        player.refresh()
        val audio = player.state
        val entryId = audio.selection?.queueEntryId
        if (preparing?.isActive != true) {
            if (audio.status == SampleAudioStatus.Ended) {
                val nextEntry = queue.adjacentTo(entryId, 1)
                if (nextEntry != null) {
                    start(nextEntry)
                    return
                }
            }
            val retry = retryEntryId?.takeIf { it == entryId }?.let(::entry)
            if (audio.status == SampleAudioStatus.Failed && retry != null) {
                start(retry, resumeAtMs = audio.positionMs, isRetry = true)
                return
            }
        }
        publish()
    }

    private fun publish() {
        val current = PlaybackState(queue, player.state, problem?.takeIf { player.state.status == SampleAudioStatus.Failed })
        mutableState.value = current

        val announced = current.copy(audio = current.audio.copy(positionMs = 0))
        if (announceNextPublish || announced != lastAnnounced) {
            announceNextPublish = false
            lastAnnounced = announced
            listeners.forEach { it(current) }
        }
        updateTicker(current.audio)
    }

    /** Posisi hanya dibaca berkala saat layar terlihat atau audio sedang dimuat. */
    private fun updateTicker(audio: SampleAudioState) {
        val needed = audio.isActive && (uiVisible || audio.status == SampleAudioStatus.Loading)
        if (!needed) {
            ticker?.cancel()
            ticker = null
        } else if (ticker == null) {
            ticker = scope.launch {
                while (isActive) {
                    delay(tickMs)
                    sync()
                }
            }
        }
    }

    private class Prefetch(val entryId: Long, val selection: Deferred<SampleAudioSelection?>)

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
