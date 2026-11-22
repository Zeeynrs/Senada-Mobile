package senada.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import senada.catalog.CatalogException
import senada.catalog.PlaylistImportException
import senada.catalog.PlaylistLink
import senada.catalog.spotify.SpotifyPlaylistSource
import senada.catalog.spotify.SpotifyTrack
import senada.catalog.spotify.matchSpotifyTrack
import senada.catalog.youtube.CandidateSearcher
import senada.catalog.youtube.YouTubeMusicPlaylists
import senada.catalog.youtube.YouTubePlaylistSource
import senada.model.Track

/** Tahapan proses impor playlist untuk ditampilkan di dialog progres. */
internal sealed interface PlaylistImportProgress {
    data class Reading(val statusText: String) : PlaylistImportProgress
    data class Matching(
        val playlistName: String,
        val completed: Int,
        val total: Int,
    ) : PlaylistImportProgress
    data object Saving : PlaylistImportProgress
}

/** Lagu dari sumber asal yang tidak dimasukkan ke playlist karena tidak cocok atau tidak tersedia. */
internal data class SkippedImportTrack(
    val title: String,
    val artists: List<String>,
    val reason: String,
) {
    val displayLabel: String
        get() = listOfNotNull(
            title.takeIf { it.isNotBlank() },
            artists.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotEmpty() },
        ).joinToString(" — ")
}

/** Ringkasan akhir setelah playlist berhasil dibuat di library lokal. */
internal data class PlaylistImportSummary(
    val playlistId: String,
    val playlistName: String,
    val addedCount: Int,
    val skippedTracks: List<SkippedImportTrack>,
    val partialNote: String? = null,
)

/** Semua lagu dari playlist Spotify gagal dicocokkan ke YouTube Music. */
internal class NoMatchedTracksException(
    val playlistName: String,
    val skippedTracks: List<SkippedImportTrack>,
) : PlaylistImportException(
    "Tidak ada lagu dari \"$playlistName\" yang berhasil dicocokkan di YouTube Music.",
)

/**
 * Mengimpor playlist publik dari YouTube Music atau Spotify menjadi playlist lokal Senada.
 * Untuk Spotify, setiap lagu dicocokkan ke YouTube Music dengan konkurensi terbatas.
 */
internal class PlaylistImporter(
    private val library: LibraryRepository,
    private val youTubePlaylists: YouTubePlaylistSource,
    private val spotifyPlaylists: SpotifyPlaylistSource,
    private val candidateSearcher: CandidateSearcher,
    private val matchConcurrency: Int = DEFAULT_MATCH_CONCURRENCY,
) {
    init {
        require(matchConcurrency >= 1) { "matchConcurrency harus minimal 1" }
    }

    suspend fun import(
        link: PlaylistLink,
        onProgress: (PlaylistImportProgress) -> Unit = {},
    ): PlaylistImportSummary = when (link) {
        is PlaylistLink.YouTubeMusic -> importYouTubePlaylist(link.playlistId, onProgress)
        is PlaylistLink.Spotify -> importSpotifyPlaylist(link.playlistId, onProgress)
    }

    private suspend fun importYouTubePlaylist(
        playlistId: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportSummary {
        onProgress(PlaylistImportProgress.Reading("Membaca playlist YouTube Music…"))
        val playlist = youTubePlaylists.fetchPlaylist(playlistId)
        val skipped = playlist.skippedTitles.map { title ->
            SkippedImportTrack(
                title = title,
                artists = emptyList(),
                reason = "Lagu tidak tersedia di YouTube Music",
            )
        }
        val partialNote = if (playlist.truncated) {
            "Maksimal ${YouTubeMusicPlaylists.DEFAULT_PLAYLIST_LIMIT} lagu pertama yang diimpor."
        } else {
            null
        }
        return saveImportedPlaylist(
            rawName = playlist.name,
            tracks = playlist.tracks,
            skipped = skipped,
            partialNote = partialNote,
            onProgress = onProgress,
        )
    }

    private suspend fun importSpotifyPlaylist(
        playlistId: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportSummary {
        onProgress(PlaylistImportProgress.Reading("Membaca playlist Spotify…"))
        val playlist = spotifyPlaylists.fetchPlaylist(playlistId)
        val total = playlist.tracks.size
        onProgress(PlaylistImportProgress.Matching(playlist.name, 0, total))

        val semaphore = Semaphore(matchConcurrency)
        val progressLock = Mutex()
        var completed = 0

        val attempts = coroutineScope {
            playlist.tracks.map { spotifyTrack ->
                async {
                    semaphore.withPermit {
                        val attempt = try {
                            val matched = matchSpotifyTrack(spotifyTrack, candidateSearcher)
                            if (matched != null) {
                                MatchAttempt.Matched(matched)
                            } else {
                                MatchAttempt.Unmatched(spotifyTrack)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: CatalogException) {
                            MatchAttempt.Failed(spotifyTrack, e)
                        }
                        progressLock.withLock {
                            completed++
                            onProgress(PlaylistImportProgress.Matching(playlist.name, completed, total))
                        }
                        attempt
                    }
                }
            }.awaitAll()
        }

        val matchedTracks = mutableListOf<Track>()
        val skipped = mutableListOf<SkippedImportTrack>()
        var firstError: CatalogException? = null

        for (attempt in attempts) {
            when (attempt) {
                is MatchAttempt.Matched -> matchedTracks += attempt.track
                is MatchAttempt.Unmatched -> skipped += SkippedImportTrack(
                    title = attempt.source.title,
                    artists = attempt.source.artists,
                    reason = "Tidak ditemukan padanan yang cocok",
                )
                is MatchAttempt.Failed -> {
                    if (firstError == null) firstError = attempt.error
                    skipped += SkippedImportTrack(
                        title = attempt.source.title,
                        artists = attempt.source.artists,
                        reason = "Gagal mencari di YouTube Music",
                    )
                }
            }
        }

        if (matchedTracks.isEmpty()) {
            if (firstError != null) {
                throw PlaylistImportException(firstError.userMessage, firstError)
            }
            throw NoMatchedTracksException(playlist.name, skipped)
        }

        val partialNote = if (!playlist.complete) {
            "Hanya ${playlist.tracks.size} lagu pertama yang dapat dibaca dari Spotify saat ini."
        } else {
            null
        }

        return saveImportedPlaylist(
            rawName = playlist.name,
            tracks = matchedTracks,
            skipped = skipped,
            partialNote = partialNote,
            onProgress = onProgress,
        )
    }

    private suspend fun saveImportedPlaylist(
        rawName: String,
        tracks: List<Track>,
        skipped: List<SkippedImportTrack>,
        partialNote: String?,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportSummary {
        onProgress(PlaylistImportProgress.Saving)
        val existingLowerNames = library.playlists.first()
            .map { it.name.trim().lowercase() }
            .toSet()
        val uniqueName = uniquePlaylistName(rawName, existingLowerNames)
        val playlistId = library.createPlaylist(uniqueName, tracks)
        return PlaylistImportSummary(
            playlistId = playlistId,
            playlistName = uniqueName,
            addedCount = tracks.size,
            skippedTracks = skipped,
            partialNote = partialNote,
        )
    }

    private sealed interface MatchAttempt {
        data class Matched(val track: Track) : MatchAttempt
        data class Unmatched(val source: SpotifyTrack) : MatchAttempt
        data class Failed(val source: SpotifyTrack, val error: CatalogException) : MatchAttempt
    }

    companion object {
        const val DEFAULT_MATCH_CONCURRENCY = 4
        private const val DEFAULT_PLAYLIST_NAME = "Playlist impor"
    }
}

/** Menghasilkan nama unik dengan akhiran `(2)`, `(3)`, dst. bila nama playlist sudah dipakai. */
internal fun uniquePlaylistName(rawName: String, existingLowerNames: Set<String>): String {
    val base = rawName.trim().ifEmpty { "Playlist impor" }
    if (base.lowercase() !in existingLowerNames) return base
    var suffix = 2
    while ("$base ($suffix)".lowercase() in existingLowerNames) {
        suffix++
    }
    return "$base ($suffix)"
}
