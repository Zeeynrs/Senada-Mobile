package senada.library

import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import senada.catalog.youtube.DiscoverSeeds
import senada.catalog.youtube.pickDiscoverSeeds

private const val DISCOVER_HISTORY_WINDOW = 30
private const val DISCOVER_FAVORITES_WINDOW = 30

/**
 * Mengamati benih rekomendasi dari riwayat, hitungan putar 30 hari terakhir (`play_event`), dan favorit.
 * Hanya berubah saat daftar benih berubah, sehingga rekomendasi tidak dimuat ulang tanpa perlu.
 */
internal fun LibraryRepository.discoverSeeds(): Flow<DiscoverSeeds> {
    val recentCounts = queries.rankedTrackPlays(Long.MIN_VALUE, DISCOVER_HISTORY_WINDOW.toLong()) { id, title, artists, duration, artwork, source, plays ->
        trackOf(id, title, artists, duration, artwork, source) to plays.toInt()
    }.asFlow().map {
        withContext(dispatcher) {
            queries.rankedTrackPlays(now() - PLAY_STATS_WINDOW_MS, DISCOVER_HISTORY_WINDOW.toLong()) { id, title, artists, duration, artwork, source, plays ->
                trackOf(id, title, artists, duration, artwork, source) to plays.toInt()
            }.executeAsList()
        }
    }
    return combine(
        history(DISCOVER_HISTORY_WINDOW),
        favoritesPage(DISCOVER_FAVORITES_WINDOW),
        recentCounts,
    ) { recentHistory, recentFavorites, playCounts ->
        pickDiscoverSeeds(
            history = recentHistory,
            favorites = recentFavorites,
            recentPlayCounts = playCounts,
        )
    }.distinctUntilChanged()
}
