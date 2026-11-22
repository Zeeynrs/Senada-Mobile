package senada.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import senada.model.Track

/** Batas kartu di rak "Baru diputar" pada tab Beranda. */
internal const val HOME_RECENT_LIMIT = 15

/** Lagu dari riwayat pemutaran beserta waktu putar terakhirnya. */
internal data class PlayedTrack(
    val track: Track,
    val playedAtMs: Long,
)

/** Playlist yang pernah diputar beserta sampul dan waktu putar terakhirnya. */
internal data class PlayedPlaylist(
    val summary: PlaylistSummary,
    val artworkUrl: String?,
    val playedAtMs: Long,
)

/** Satu kartu di rak "Baru diputar" pada tab Beranda. */
internal sealed interface RecentHomeItem {
    val key: String
    val playedAtMs: Long

    data class Song(
        val track: Track,
        override val playedAtMs: Long,
    ) : RecentHomeItem {
        override val key: String get() = "recent-track-${track.id}"
    }

    data class Playlist(
        val summary: PlaylistSummary,
        val artworkUrl: String?,
        override val playedAtMs: Long,
    ) : RecentHomeItem {
        override val key: String get() = "recent-playlist-${summary.id}"
    }
}

/**
 * Menyusun isi "Baru diputar" dari riwayat lagu dan playlist yang pernah diputar:
 * terbaru lebih dulu, bebas duplikat (menyimpan waktu putar terbaru per ID), dan tidak
 * pernah menyertakan koleksi kosong (jumlah lagu <= 0). Bila waktu putar persis sama,
 * playlist didahulukan agar koleksi yang baru dipilih tetap tampil di depan, lalu urutan
 * masukan dipertahankan.
 */
internal fun selectRecentHomeItems(
    tracks: List<PlayedTrack>,
    playlists: List<PlayedPlaylist> = emptyList(),
    limit: Int = HOME_RECENT_LIMIT,
): List<RecentHomeItem> {
    val maxItems = limit.coerceAtLeast(0)
    if (maxItems == 0) return emptyList()

    data class Ranked(val item: RecentHomeItem, val kindOrder: Int, val inputOrder: Int)

    val latestTracks = linkedMapOf<String, Pair<PlayedTrack, Int>>()
    tracks.forEachIndexed { index, entry ->
        if (entry.track.id.isBlank()) return@forEachIndexed
        val existing = latestTracks[entry.track.id]
        if (existing == null || entry.playedAtMs > existing.first.playedAtMs) {
            latestTracks[entry.track.id] = entry to index
        }
    }

    val latestPlaylists = linkedMapOf<String, Pair<PlayedPlaylist, Int>>()
    playlists.forEachIndexed { index, entry ->
        if (entry.summary.id.isBlank()) return@forEachIndexed
        val existing = latestPlaylists[entry.summary.id]
        if (existing == null || entry.playedAtMs > existing.first.playedAtMs) {
            latestPlaylists[entry.summary.id] = entry to index
        }
    }

    val merged = buildList {
        for ((entry, order) in latestPlaylists.values) {
            if (entry.summary.trackCount > 0) {
                add(
                    Ranked(
                        item = RecentHomeItem.Playlist(
                            summary = entry.summary,
                            artworkUrl = entry.artworkUrl?.takeIf { it.isNotBlank() },
                            playedAtMs = entry.playedAtMs,
                        ),
                        kindOrder = 0,
                        inputOrder = order,
                    ),
                )
            }
        }
        for ((entry, order) in latestTracks.values) {
            add(
                Ranked(
                    item = RecentHomeItem.Song(
                        track = entry.track,
                        playedAtMs = entry.playedAtMs,
                    ),
                    kindOrder = 1,
                    inputOrder = order,
                ),
            )
        }
    }

    return merged
        .sortedWith(
            compareByDescending<Ranked> { it.item.playedAtMs }
                .thenBy { it.kindOrder }
                .thenBy { it.inputOrder },
        )
        .take(maxItems)
        .map { it.item }
}

/** Mengamati lagu dan playlist yang baru diputar dari database, terbaru lebih dulu tanpa koleksi kosong. */
internal fun LibraryRepository.recentHomeItems(limit: Int = HOME_RECENT_LIMIT): Flow<List<RecentHomeItem>> {
    val safeLimit = limit.coerceAtLeast(0).toLong()
    val recentTracks = queries.recentHistory(safeLimit) { playedAt, id, title, artists, durationMs, artworkUrl, source ->
        PlayedTrack(trackOf(id, title, artists, durationMs, artworkUrl, source), playedAt)
    }.asFlow().mapToList(dispatcher)
    val recentPlaylists = queries.recentPlaylistPlays(safeLimit) { id, name, description, createdAt, size, artwork, lastPlayedAt ->
        @Suppress("USELESS_CAST")
        val trackCount = (size as Long?)?.toInt() ?: 0
        @Suppress("USELESS_CAST")
        val playedAt = (lastPlayedAt as Long?) ?: 0L
        PlayedPlaylist(
            summary = PlaylistSummary(id, name, description, createdAt, trackCount),
            artworkUrl = artwork?.takeIf { it.isNotBlank() },
            playedAtMs = playedAt,
        )
    }.asFlow().mapToList(dispatcher)
    return combine(recentTracks, recentPlaylists) { tracks, playlists ->
        selectRecentHomeItems(tracks = tracks, playlists = playlists, limit = limit)
    }.distinctUntilChanged()
}
