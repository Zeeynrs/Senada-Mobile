package senada.library

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import senada.model.Track

internal const val PLAY_STATS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
internal const val HERO_PLAYLIST_MIN_PLAYS = 3

internal sealed interface HomeHero {
    data class FromPlaylist(val summary: PlaylistSummary, val artworkUrl: String?, val plays: Int) : HomeHero
    data class FromTrack(val track: Track, val plays: Int) : HomeHero
    data object Welcome : HomeHero
}

internal suspend fun LibraryRepository.recordTrackPlay(track: Track) = recordTrackPlay(track, includeHistory = false)

/** Riwayat dan hitungan dari pemutar memakai waktu dan transaksi yang sama. */
internal suspend fun LibraryRepository.recordTrackPlay(track: Track, includeHistory: Boolean) {
    write {
        val playedAt = now()
        upsert(track)
        queries.insertTrackPlay(track.id, playedAt)
        if (includeHistory) recordHistoryNow(track.id, playedAt)
        prunePlayStats(playedAt)
    }
}

internal suspend fun LibraryRepository.recordPlaylistPlay(playlistId: String) {
    write {
        val playedAt = now()
        if (queries.playlistItemCount(playlistId).executeAsOne() > 0L) {
            queries.insertPlaylistPlay(playlistId, playedAt)
        }
        prunePlayStats(playedAt)
    }
}

internal fun LibraryRepository.homeHero(): Flow<HomeHero> {
    val playlist = observePlayStats(
        query = { cutoff ->
            queries.topPlaylistPlay(cutoff, HERO_PLAYLIST_MIN_PLAYS.toLong()) { id, name, description, createdAt, size, artwork, plays ->
                @Suppress("USELESS_CAST")
                val summary = PlaylistSummary(id, name, description, createdAt, (size as Long?)?.toInt() ?: 0)
                HomeHero.FromPlaylist(summary, artwork, plays.toInt())
            }
        },
        read = { it.executeAsOneOrNull() },
    )
    return combine(playlist, rankedTracks(1)) { topPlaylist, tracks ->
        topPlaylist ?: tracks.firstOrNull() ?: HomeHero.Welcome
    }.distinctUntilChanged()
}

internal fun LibraryRepository.mostPlayed(limit: Int = 20): Flow<List<Track>> =
    rankedTracks(limit.coerceAtLeast(0)).map { ranked -> ranked.map { it.track } }.distinctUntilChanged()

private fun LibraryRepository.rankedTracks(limit: Int): Flow<List<HomeHero.FromTrack>> = observePlayStats(
    query = { cutoff ->
        queries.rankedTrackPlays(cutoff, limit.toLong()) { id, title, artists, duration, artwork, source, plays ->
            HomeHero.FromTrack(trackOf(id, title, artists, duration, artwork, source), plays.toInt())
        }
    },
    read = { it.executeAsList() },
)

private fun <T : Any, R> LibraryRepository.observePlayStats(
    query: (cutoff: Long) -> Query<T>,
    read: (Query<T>) -> R,
): Flow<R> =
    // Pendengar hanya memakai nama tabel; batas waktu dihitung ulang saat hasil dibaca.
    query(Long.MIN_VALUE).asFlow().map {
        withContext(dispatcher) { read(query(now() - PLAY_STATS_WINDOW_MS)) }
    }

private fun LibraryRepository.prunePlayStats(playedAt: Long) {
    val cutoff = playedAt - PLAY_STATS_WINDOW_MS
    queries.deleteExpiredTrackPlays(cutoff)
    queries.deleteExpiredPlaylistPlays(cutoff)
    queries.deleteUnusedTracks()
}
