package senada.app.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import senada.app.shell.AppDestination
import senada.catalog.CatalogException
import senada.catalog.HomeCatalog
import senada.catalog.HomeFeed
import senada.epochMillis

internal const val HOME_SHELVES_TTL_MS = 10L * 60 * 1_000

/** Menentukan apakah rak YouTube Music di Beranda sedang terlihat sehingga boleh dimuat atau disegarkan. */
internal fun shouldRefreshHomeShelves(
    screenVisible: Boolean,
    destination: AppDestination,
    hasCatalogRoute: Boolean,
    mostPlayedOpen: Boolean,
    listeningStatsOpen: Boolean,
): Boolean = screenVisible &&
    destination == AppDestination.Home &&
    !hasCatalogRoute &&
    !mostPlayedOpen &&
    !listeningStatsOpen

/** Status tampilan rak Beranda dari YouTube Music. */
internal data class HomeShelvesUiState(
    val feed: HomeFeed? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val deferredByDataSaver: Boolean = false,
)

/**
 * Mengelola rak YouTube Music di Beranda: memuat sekali per sesi, membatasi penyegaran otomatis lewat [ttlMs],
 * menghormati mode Hemat data di jaringan berbayar, dan tidak menghalangi bagian pribadi Beranda bila gagal.
 */
internal class HomeShelvesController(
    private val catalog: HomeCatalog,
    private val scope: CoroutineScope,
    private val now: () -> Long = ::epochMillis,
    private val ttlMs: Long = HOME_SHELVES_TTL_MS,
    private val workContext: CoroutineContext = Dispatchers.Default,
) {
    var state by mutableStateOf(HomeShelvesUiState())
        private set

    private var job: Job? = null
    private var lastLoadedAtMs: Long = 0L
    private var hasLoadedOnce: Boolean = false

    fun refresh(saveData: Boolean) = refreshIfNeeded(saveData = saveData, force = true)

    fun refreshIfNeeded(saveData: Boolean, force: Boolean = false) {
        if (state.loading) return
        val existing = state.feed
        if (saveData && !force) {
            if (existing == null) {
                state = state.copy(deferredByDataSaver = true, error = null)
            }
            return
        }
        val hasContent = existing != null && (existing.shelves.isNotEmpty() || existing.moods.isNotEmpty())
        val fresh = hasLoadedOnce && hasContent && (now() - lastLoadedAtMs) < ttlMs
        if (!force && fresh) {
            if (state.deferredByDataSaver) state = state.copy(deferredByDataSaver = false)
            return
        }

        job?.cancel()
        state = state.copy(loading = true, error = null, deferredByDataSaver = false)
        job = scope.launch {
            try {
                // Pengambilan dan parsing rak berjalan di luar thread UI agar guliran Beranda tetap mulus.
                val loaded = withContext(workContext) {
                    catalog.homeFeed(saveData = saveData)
                }
                lastLoadedAtMs = now()
                hasLoadedOnce = true
                state = HomeShelvesUiState(
                    feed = loaded,
                    loading = false,
                    error = null,
                    deferredByDataSaver = false,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = (e as? CatalogException)?.userMessage
                    ?: "Rekomendasi YouTube Music sedang tidak tersedia. Periksa koneksi internet."
                state = state.copy(loading = false, error = message)
            }
        }
    }
}
