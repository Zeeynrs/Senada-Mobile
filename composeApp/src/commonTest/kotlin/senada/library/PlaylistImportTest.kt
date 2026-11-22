package senada.library

import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import senada.catalog.CatalogException
import senada.catalog.PlaylistLink
import senada.catalog.spotify.SpotifyPlaylist
import senada.catalog.spotify.SpotifyPlaylistSource
import senada.catalog.spotify.SpotifyTrack
import senada.catalog.youtube.CandidateSearcher
import senada.catalog.youtube.MatchCandidate
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.catalog.youtube.YouTubeMusicPlaylist
import senada.catalog.youtube.YouTubePlaylistSource
import senada.db.SenadaDatabase
import senada.model.Track

class PlaylistImportTest {
    private val database = SenadaDatabase(testLibraryDriver())
    private var clock = 1_000L
    private var playlistSeq = 0

    private fun TestScope.repository() = LibraryRepository(
        database = database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock++ },
        newPlaylistId = { "local-imp${++playlistSeq}" },
    )

    private fun ytTrack(id: String, title: String, artist: String, durationMs: Long = 200_000L) =
        Track("yt:track:$id", title, listOf(artist), durationMs, source = YOUTUBE_MUSIC_SOURCE)

    private fun ytSource(block: suspend (String, Int) -> YouTubeMusicPlaylist) = object : YouTubePlaylistSource {
        override suspend fun fetchPlaylist(playlistId: String, limit: Int): YouTubeMusicPlaylist = block(playlistId, limit)
    }

    private fun spSource(block: suspend (String, Int) -> SpotifyPlaylist) = object : SpotifyPlaylistSource {
        override suspend fun fetchPlaylist(playlistId: String, limit: Int): SpotifyPlaylist = block(playlistId, limit)
    }

    @Test
    fun importsYouTubeMusicPlaylistAndSuffixesDuplicateNames() = runTest {
        val repo = repository()
        repo.createPlaylist("Favorit Sore", listOf(ytTrack("old", "Lama", "Artis")))
        val importer = PlaylistImporter(
            library = repo,
            youTubePlaylists = ytSource { _, _ ->
                YouTubeMusicPlaylist(
                    id = "PLabc",
                    name = "Favorit Sore",
                    tracks = listOf(ytTrack("v1", "Satu", "Artis 1"), ytTrack("v2", "Dua", "Artis 2")),
                    skippedTitles = listOf("Video Dihapus"),
                    truncated = false,
                )
            },
            spotifyPlaylists = spSource { _, _ -> error("Should not call Spotify") },
            candidateSearcher = CandidateSearcher { _, _ -> emptyList() },
        )

        val progressEvents = mutableListOf<PlaylistImportProgress>()
        val summary = importer.import(PlaylistLink.YouTubeMusic("PLabc")) { progressEvents += it }

        assertEquals("Favorit Sore (2)", summary.playlistName)
        assertEquals(2, summary.addedCount)
        assertEquals(1, summary.skippedTracks.size)
        assertEquals("Video Dihapus", summary.skippedTracks.single().title)
        assertTrue(progressEvents.first() is PlaylistImportProgress.Reading)
        assertTrue(progressEvents.last() is PlaylistImportProgress.Saving)

        val allPlaylists = repo.playlists.first()
        assertEquals(setOf("Favorit Sore", "Favorit Sore (2)"), allPlaylists.map { it.name }.toSet())
        val saved = repo.playlist(summary.playlistId).first()!!
        assertEquals(listOf("yt:track:v1", "yt:track:v2"), saved.entries.map { it.track.id })

        // Impor ketiga dengan nama sama mendapat akhiran (3).
        val third = importer.import(PlaylistLink.YouTubeMusic("PLabc"))
        assertEquals("Favorit Sore (3)", third.playlistName)
    }

    @Test
    fun spotifyImportRespectsConcurrencyLimitsTracksProgressAndReportsPartialFailures() = runTest {
        val repo = repository()
        val spotifyTracks = listOf(
            SpotifyTrack("sp1", "Hati-Hati di Jalan", listOf("Tulus"), durationMs = 242_000L),
            SpotifyTrack("sp2", "Lagu Hilang", listOf("Tanpa Padanan"), durationMs = 210_000L),
            SpotifyTrack("sp3", "Gagal Jaringan", listOf("Artis Error"), durationMs = 195_000L),
            SpotifyTrack("sp4", "Evaluasi", listOf("Hindia"), durationMs = 208_000L),
            SpotifyTrack("sp5", "Secukupnya", listOf("Hindia"), durationMs = 195_000L),
        )

        val lock = Mutex()
        var currentActive = 0
        var maxObservedActive = 0

        val importer = PlaylistImporter(
            library = repo,
            youTubePlaylists = ytSource { _, _ -> error("Should not call YouTube playlist") },
            spotifyPlaylists = spSource { _, _ ->
                SpotifyPlaylist("37i9dQZF1DXcBWIGoYBM5M", "Mix Spotify", spotifyTracks, complete = true)
            },
            candidateSearcher = CandidateSearcher { query, _ ->
                lock.withLock {
                    currentActive++
                    if (currentActive > maxObservedActive) maxObservedActive = currentActive
                }
                try {
                    delay(10)
                    when {
                        "hati hati" in query -> listOf(
                            MatchCandidate(ytTrack("yt1", "Hati-Hati di Jalan", "Tulus", 242_000L), "MUSIC_VIDEO_TYPE_ATV"),
                        )
                        "gagal jaringan" in query -> throw CatalogException(CatalogException.Kind.Network, "Timeout")
                        "evaluasi" in query -> listOf(
                            MatchCandidate(ytTrack("yt4", "Evaluasi", "Hindia", 208_000L), "MUSIC_VIDEO_TYPE_ATV"),
                        )
                        "secukupnya" in query -> listOf(
                            MatchCandidate(ytTrack("yt5", "Secukupnya", "Hindia", 195_000L), "MUSIC_VIDEO_TYPE_ATV"),
                        )
                        else -> emptyList()
                    }
                } finally {
                    lock.withLock { currentActive-- }
                }
            },
            matchConcurrency = 2,
        )

        val progress = mutableListOf<PlaylistImportProgress>()
        val summary = importer.import(PlaylistLink.Spotify("37i9dQZF1DXcBWIGoYBM5M")) { progress += it }

        assertTrue(maxObservedActive <= 2, "Konkurensi melebihi batas: $maxObservedActive")
        assertEquals("Mix Spotify", summary.playlistName)
        assertEquals(3, summary.addedCount)
        assertEquals(
            listOf("Lagu Hilang — Tanpa Padanan", "Gagal Jaringan — Artis Error"),
            summary.skippedTracks.map { it.displayLabel },
        )

        val matchingSteps = progress.filterIsInstance<PlaylistImportProgress.Matching>()
        assertEquals(0, matchingSteps.first().completed)
        assertEquals(5, matchingSteps.first().total)
        assertEquals(5, matchingSteps.last().completed)

        val savedTracks = repo.playlistTracks(summary.playlistId)
        assertEquals(listOf("yt:track:yt1", "yt:track:yt4", "yt:track:yt5"), savedTracks.map { it.id })
    }

    @Test
    fun failsCleanlyWithoutCreatingEmptyPlaylistWhenNothingMatches() = runTest {
        val repo = repository()
        val importer = PlaylistImporter(
            library = repo,
            youTubePlaylists = ytSource { _, _ -> error("unused") },
            spotifyPlaylists = spSource { _, _ ->
                SpotifyPlaylist(
                    "spEmptyMatch",
                    "Tidak Cocok",
                    listOf(SpotifyTrack("sp1", "Lagu Unik", listOf("Artis X"), durationMs = 200_000L)),
                )
            },
            candidateSearcher = CandidateSearcher { _, _ -> emptyList() },
        )

        val error = assertFailsWith<NoMatchedTracksException> {
            importer.import(PlaylistLink.Spotify("37i9dQZF1DXcBWIGoYBM5M"))
        }
        assertEquals(1, error.skippedTracks.size)
        assertEquals(emptyList(), repo.playlists.first())
    }

    @Test
    fun cancellingMidImportStopsMatchingAndDoesNotSavePlaylist() = runTest {
        val repo = repository()
        val firstStarted = CompletableDeferred<Unit>()
        val holdSearch = CompletableDeferred<Unit>()

        val importer = PlaylistImporter(
            library = repo,
            youTubePlaylists = ytSource { _, _ -> error("unused") },
            spotifyPlaylists = spSource { _, _ ->
                SpotifyPlaylist(
                    "37i9dQZF1DXcBWIGoYBM5M",
                    "Dibatalkan",
                    listOf(
                        SpotifyTrack("sp1", "Satu", listOf("Artis"), durationMs = 200_000L),
                        SpotifyTrack("sp2", "Dua", listOf("Artis"), durationMs = 200_000L),
                    ),
                )
            },
            candidateSearcher = CandidateSearcher { _, _ ->
                firstStarted.complete(Unit)
                holdSearch.await()
                listOf(MatchCandidate(ytTrack("yt1", "Satu", "Artis", 200_000L), "MUSIC_VIDEO_TYPE_ATV"))
            },
            matchConcurrency = 1,
        )

        var caughtCancellation = false
        val job = launch {
            try {
                importer.import(PlaylistLink.Spotify("37i9dQZF1DXcBWIGoYBM5M"))
            } catch (e: CancellationException) {
                caughtCancellation = true
                throw e
            }
        }

        firstStarted.await()
        job.cancel()
        advanceUntilIdle()

        assertTrue(caughtCancellation)
        assertEquals(emptyList(), repo.playlists.first())
    }
}
