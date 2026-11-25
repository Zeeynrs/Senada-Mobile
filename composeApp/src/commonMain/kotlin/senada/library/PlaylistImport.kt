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
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
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
import senada.resources.Res
import senada.resources.error_playlist_no_matches
import senada.resources.playlist_import_partial_spotify
import senada.resources.playlist_import_partial_yt
import senada.resources.playlist_import_reading_spotify
import senada.resources.playlist_import_reading_yt
import senada.resources.playlist_import_skip_search_failed
import senada.resources.playlist_import_skip_unmatched
import senada.resources.playlist_import_skip_yt_unavailable

/** Tahapan proses impor playlist untuk ditampilkan di dialog progres. */
internal sealed interface PlaylistImportProgress {
    data class Reading(val uiText: UiText) : PlaylistImportProgress {
        constructor(statusText: String) : this(UiText.Raw(statusText))

        fun statusText(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

        val statusText: String get() = statusText()
    }
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
    val uiReason: UiText,
) {
    constructor(title: String, artists: List<String>, reason: String) : this(title, artists, UiText.Raw(reason))

    fun reason(language: AppLanguage = AppStrings.currentLanguage): String = uiReason.resolve(language)

    val reason: String get() = reason()

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
    val partialNoteText: UiText? = null,
) {
    constructor(
        playlistId: String,
        playlistName: String,
        addedCount: Int,
        skippedTracks: List<SkippedImportTrack>,
        partialNote: String? = null,
    ) : this(playlistId, playlistName, addedCount, skippedTracks, partialNote?.let { UiText.Raw(it) })

    fun partialNote(language: AppLanguage = AppStrings.currentLanguage): String? = partialNoteText?.resolve(language)

    val partialNote: String? get() = partialNote()
}

/** Semua lagu dari playlist Spotify gagal dicocokkan ke YouTube Music. */
internal class NoMatchedTracksException(
    val playlistName: String,
    val skippedTracks: List<SkippedImportTrack>,
) : PlaylistImportException(
    UiText.Resource(Res.string.error_playlist_no_matches, playlistName),
)

/** Ringkasan playlist lokal yang baru disimpan. */
internal data class SavedLocalPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
)

/** Menyimpan playlist ke library lokal dengan akhiran `(2)`, `(3)`, dst. bila nama sudah dipakai. */
internal suspend fun savePlaylistWithUniqueName(
    library: LibraryRepository,
    rawName: String,
    tracks: List<Track>,
): SavedLocalPlaylist {
    val existingLowerNames = library.playlists.first()
        .map { it.name.trim().lowercase() }
        .toSet()
    val uniqueName = uniquePlaylistName(rawName, existingLowerNames)
    val playlistId = library.createPlaylist(uniqueName, tracks)
    return SavedLocalPlaylist(id = playlistId, name = uniqueName, trackCount = tracks.size)
}

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
        onProgress(PlaylistImportProgress.Reading(UiText.Resource(Res.string.playlist_import_reading_yt)))
        val playlist = youTubePlaylists.fetchPlaylist(playlistId)
        val skipped = playlist.skippedTitles.map { title ->
            SkippedImportTrack(
                title = title,
                artists = emptyList(),
                uiReason = UiText.Resource(Res.string.playlist_import_skip_yt_unavailable),
            )
        }
        val partialNote = if (playlist.truncated) {
            UiText.Resource(Res.string.playlist_import_partial_yt, YouTubeMusicPlaylists.DEFAULT_PLAYLIST_LIMIT)
        } else {
            null
        }
        return saveImportedPlaylist(
            rawName = playlist.name,
            tracks = playlist.tracks,
            skipped = skipped,
            partialNoteText = partialNote,
            onProgress = onProgress,
        )
    }

    private suspend fun importSpotifyPlaylist(
        playlistId: String,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportSummary {
        onProgress(PlaylistImportProgress.Reading(UiText.Resource(Res.string.playlist_import_reading_spotify)))
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
                    uiReason = UiText.Resource(Res.string.playlist_import_skip_unmatched),
                )
                is MatchAttempt.Failed -> {
                    if (firstError == null) firstError = attempt.error
                    skipped += SkippedImportTrack(
                        title = attempt.source.title,
                        artists = attempt.source.artists,
                        uiReason = UiText.Resource(Res.string.playlist_import_skip_search_failed),
                    )
                }
            }
        }

        if (matchedTracks.isEmpty()) {
            if (firstError != null) {
                throw PlaylistImportException(firstError.uiText, firstError)
            }
            throw NoMatchedTracksException(playlist.name, skipped)
        }

        val partialNote = if (!playlist.complete) {
            UiText.Resource(Res.string.playlist_import_partial_spotify, playlist.tracks.size)
        } else {
            null
        }

        return saveImportedPlaylist(
            rawName = playlist.name,
            tracks = matchedTracks,
            skipped = skipped,
            partialNoteText = partialNote,
            onProgress = onProgress,
        )
    }

    private suspend fun saveImportedPlaylist(
        rawName: String,
        tracks: List<Track>,
        skipped: List<SkippedImportTrack>,
        partialNoteText: UiText?,
        onProgress: (PlaylistImportProgress) -> Unit,
    ): PlaylistImportSummary {
        onProgress(PlaylistImportProgress.Saving)
        val saved = savePlaylistWithUniqueName(library, rawName, tracks)
        return PlaylistImportSummary(
            playlistId = saved.id,
            playlistName = saved.name,
            addedCount = saved.trackCount,
            skippedTracks = skipped,
            partialNoteText = partialNoteText,
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
