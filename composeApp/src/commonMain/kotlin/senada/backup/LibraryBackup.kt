package senada.backup

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.withContext
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.library.LibraryRepository
import senada.library.encodeArtists
import senada.library.trackOf
import senada.model.Track

/** Apa yang ditambahkan impor gabung; [skipped] adalah entri backup yang tidak bisa dipakai di ponsel. */
internal data class ImportSummary(
    val favoritesAdded: Int = 0,
    val playlistsAdded: Int = 0,
    val playlistTracksAdded: Int = 0,
    val savedAdded: Int = 0,
    val historyAdded: Int = 0,
    val skipped: Int = 0,
) {
    val changesNothing get() =
        favoritesAdded + playlistsAdded + playlistTracksAdded + savedAdded + historyAdded == 0
}

/**
 * Menggabungkan backup ke library, seperti "merge" di desktop: tidak ada yang dihapus atau diganti nama.
 * Backup diperiksa seluruhnya sebelum menulis, lalu semua perubahan masuk dalam satu transaksi.
 * Dengan [apply] = false, ringkasannya dihitung lalu transaksi dibatalkan, jadi library tidak berubah.
 */
internal suspend fun LibraryRepository.importBackup(text: String, apply: Boolean = true): ImportSummary =
    withContext(dispatcher) {
        val document = BackupCodec.decode(text)
        database.transactionWithResult {
            val summary = mergeNow(document)
            if (!apply) rollback(summary)
            summary
        }
    }

/** Seluruh library dalam format backup desktop. */
internal suspend fun LibraryRepository.exportBackup(createdIso: String = isoNow()): String =
    withContext(dispatcher) {
        val document = database.transactionWithResult {
            val liked = queries.favoritesForBackup { addedAt, id, title, artists, durationMs, artworkUrl, source ->
                BackupTrack(trackOf(id, title, artists, durationMs, artworkUrl, source), addedAt)
            }.executeAsList()
            val history = queries.historyForBackup { playedAt, id, title, artists, durationMs, artworkUrl, source ->
                BackupTrack(trackOf(id, title, artists, durationMs, artworkUrl, source), playedAt)
            }.executeAsList()
            val items = queries.playlistItemsForBackup { playlistId, id, title, artists, durationMs, artworkUrl, source ->
                playlistId to trackOf(id, title, artists, durationMs, artworkUrl, source)
            }.executeAsList().groupBy({ it.first }, { it.second })
            val playlists = queries.playlists { id, name, description, createdAt, _ ->
                BackupPlaylist(id, name, description, createdAt, items[id].orEmpty())
            }.executeAsList()
            val saved = queries.savedItems { kind, id, metadata, _ -> BackupSavedItem(kind, id, metadata) }.executeAsList()
            // Backup tidak membawa file audio; lagu impor lokal hanya berlaku di ponsel ini.
            fun List<Track>.streamable() = filter { it.source == YOUTUBE_MUSIC_SOURCE }
            BackupDocument(
                liked = liked.filter { it.track.source == YOUTUBE_MUSIC_SOURCE },
                playlists = playlists.map { it.copy(tracks = it.tracks.streamable()) },
                saved = saved,
                history = history.filter { it.track.source == YOUTUBE_MUSIC_SOURCE },
            )
        }
        BackupCodec.encode(document, createdIso)
    }

private fun LibraryRepository.mergeNow(document: BackupDocument): ImportSummary {
    // Favorit dan riwayat memakai lagu yang sama dengan ID yang sama; cukup satu baris lagu.
    var favoritesAdded = 0
    for (item in document.liked) {
        if (queries.favoriteCount(item.track.id).executeAsOne() > 0) continue
        insertTrack(item.track)
        queries.addFavorite(item.track.id, item.atMs ?: now())
        favoritesAdded++
    }

    var playlistsAdded = 0
    var playlistTracksAdded = 0
    for (playlist in document.playlists) {
        if (queries.playlistById(playlist.id).executeAsOneOrNull() == null) {
            queries.insertPlaylist(
                playlist.id,
                playlist.name.trim().ifEmpty { DEFAULT_PLAYLIST_NAME },
                playlist.description,
                playlist.createdAtMs ?: now(),
            )
            // Playlist baru disalin apa adanya, termasuk lagu yang sengaja dimasukkan dua kali.
            append(playlist.id, playlist.tracks)
            playlistsAdded++
        } else {
            // Playlist yang sudah ada: judul, urutan dan semua entri lama tetap; hanya lagu baru yang ditambah.
            val present = queries.playlistTrackIds(playlist.id).executeAsList().toMutableSet()
            val missing = playlist.tracks.filter { present.add(it.id) }
            append(playlist.id, missing)
            playlistTracksAdded += missing.size
        }
    }

    // Urutan desktop: terbaru di depan. Disisipkan dari belakang agar yang pertama tetap tampil pertama.
    var savedAdded = 0
    for (item in document.saved.asReversed()) {
        if (queries.savedItemCount(item.kind, item.id).executeAsOne() > 0) continue
        queries.saveItem(item.kind, item.id, item.metadata, now())
        savedAdded++
    }

    // Riwayat digabung per lagu dengan waktu putar terbaru, lalu dibatasi seperti biasa.
    var historyAdded = 0
    for (item in document.history) {
        val playedAt = item.atMs ?: 0L
        val current = queries.historyPlayedAt(item.track.id).executeAsOneOrNull()
        if (current == null) historyAdded++
        if (current == null || playedAt > current) {
            insertTrack(item.track)
            queries.recordPlay(item.track.id, playedAt)
        }
    }
    queries.trimHistory(LibraryRepository.HISTORY_LIMIT.toLong())
    queries.deleteUnusedTracks()

    return ImportSummary(favoritesAdded, playlistsAdded, playlistTracksAdded, savedAdded, historyAdded, document.skipped)
}

private fun LibraryRepository.append(playlistId: String, tracks: List<Track>) {
    var position = queries.playlistItemCount(playlistId).executeAsOne()
    for (track in tracks) {
        insertTrack(track)
        queries.insertPlaylistItem(playlistId, track.id, position++)
    }
}

private fun LibraryRepository.insertTrack(track: Track) {
    queries.insertTrackIfMissing(
        track.id,
        track.title,
        encodeArtists(track.artists),
        track.durationMs,
        track.artworkUrl,
        track.source,
    )
}

@OptIn(ExperimentalTime::class)
private fun isoNow(): String = Clock.System.now().toString()

private const val DEFAULT_PLAYLIST_NAME = "Playlist baru"
