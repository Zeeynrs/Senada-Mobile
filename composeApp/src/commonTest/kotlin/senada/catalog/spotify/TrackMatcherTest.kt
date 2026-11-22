package senada.catalog.spotify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import senada.catalog.youtube.CandidateSearcher
import senada.catalog.youtube.MatchCandidate
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.model.Track

class TrackMatcherTest {
    private fun candidate(
        id: String,
        title: String,
        vararg artists: String,
        durationMs: Long? = 240_000L,
        videoType: String? = "MUSIC_VIDEO_TYPE_ATV",
        album: String? = null,
    ) = MatchCandidate(
        track = Track("yt:track:$id", title, artists.toList(), durationMs, source = YOUTUBE_MUSIC_SOURCE),
        videoType = videoType,
        album = album,
    )

    @Test
    fun cleansTitlesExtractsFeaturedArtistsAndBuildsSearchQuery() {
        assertEquals("hati hati di jalan", cleanTitle("Hati‐Hati di Jalan (Official Music Video)"))
        assertEquals("bohemian rhapsody", cleanTitle("Bohemian Rhapsody - Remastered 2011"))
        assertEquals("ya hit", cleanTitle("Ya Hit (feat. Drake, Travis Scott)"))
        assertEquals("lovely", cleanTitle("lovely (with Khalid)"))

        assertEquals("dance with me", cleanTitle("Dance With Me"))
        assertEquals(emptyList(), featuredArtists("Dance With Me"))
        assertEquals(listOf("khalid"), featuredArtists("lovely (with Khalid)"))

        assertEquals(setOf("karaoke"), versionTags("TULUS - Hati-Hati di Jalan (Karaoke Original Key)"))
        assertEquals(setOf("acoustic", "live"), versionTags("Layla (Acoustic; Live at MTV Unplugged)"))
        assertEquals(emptySet(), versionTags("Bohemian Rhapsody - Remastered 2011"))

        assertEquals(
            "queen bohemian rhapsody",
            searchQuery(SpotifyTrack("sp1", "Bohemian Rhapsody - Remastered 2011", listOf("Queen"))),
        )
        assertEquals(
            "future ya hit",
            searchQuery(SpotifyTrack("sp2", "Ya Hit (feat. Drake)", listOf("Future", "Drake"))),
        )
    }

    @Test
    fun foldsAccentsAndHandlesRemasterAndFeatVariants() {
        val remasterSource = SpotifyTrack(
            id = "sp1",
            title = "Bohemian Rhapsody - Remastered 2011",
            artists = listOf("Queen"),
            durationMs = 354_000L,
        )
        val remasterScore = scoreCandidate(
            remasterSource,
            candidate("q1", "Bohemian Rhapsody (2011 Remaster)", "Queen", durationMs = 355_000L),
        )
        assertTrue(remasterScore.score >= 95, "Skor remaster harus tinggi: $remasterScore")

        val featSource = SpotifyTrack(
            id = "sp2",
            title = "lovely (with Khalid)",
            artists = listOf("Billie Eilish", "Khalid"),
            durationMs = 200_000L,
        )
        val ranked = rankCandidates(
            featSource,
            listOf(
                candidate("fan", "Billie Eilish - lovely (with Khalid) (Lyrics)", "POPuLuv", durationMs = 201_000L, videoType = "MUSIC_VIDEO_TYPE_UGC"),
                candidate("ok", "lovely", "Billie Eilish", "Khalid", durationMs = 201_000L),
            ),
        )
        assertEquals("yt:track:ok", ranked.first().candidate.track.id)
        assertTrue(ranked.first().match.score >= MATCHER_ACCEPT)

        val accentedSource = SpotifyTrack("sp3", "Café del Mar", listOf("Beyoncé"), durationMs = 210_000L)
        val accentedScore = scoreCandidate(accentedSource, candidate("b1", "Cafe del Mar", "Beyonce", durationMs = 210_000L))
        assertTrue(accentedScore.score >= 95, "Skor aksen harus tinggi: $accentedScore")
    }

    @Test
    fun officialUploadBeatsKaraokeCoverAndLyrics() {
        val source = SpotifyTrack("sp1", "Hati-Hati di Jalan", listOf("Tulus"), durationMs = 242_000L)
        val ranked = rankCandidates(
            source,
            listOf(
                candidate("k", "TULUS - Hati-Hati di Jalan (Karaoke Original Key)", "WNF Production", durationMs = 247_000L, videoType = "MUSIC_VIDEO_TYPE_UGC"),
                candidate("c", "Hati - Hati Di jalan - Tulus REGGAE COVER", "HVMBLE REGGAE", durationMs = 258_000L, videoType = "MUSIC_VIDEO_TYPE_UGC"),
                candidate("l", "Hati-Hati di Jalan - Tulus | Lirik Lagu", "Indolirik", durationMs = 243_000L, videoType = "MUSIC_VIDEO_TYPE_UGC"),
                candidate("o", "Hati-Hati di Jalan", "Tulus", durationMs = 242_000L, videoType = "MUSIC_VIDEO_TYPE_ATV", album = "Manusia"),
            ),
        )

        assertEquals("yt:track:o", ranked.first().candidate.track.id)
        val scores = ranked.associate { it.candidate.track.id to it.match.score }
        assertTrue(scores.getValue("yt:track:o") >= MATCHER_ACCEPT)
        assertTrue(scores.getValue("yt:track:k") < MATCHER_MINIMUM)
        assertTrue(scores.getValue("yt:track:c") < MATCHER_MINIMUM)
    }

    @Test
    fun usesDurationClosenessAsTieBreakerAndDisqualifiesFarDurations() {
        val source = SpotifyTrack("sp1", "Evaluasi", listOf("Hindia"), durationMs = 200_000L)
        val ranked = rankCandidates(
            source,
            listOf(
                candidate("far", "Evaluasi", "Hindia", durationMs = 201_900L),
                candidate("near", "Evaluasi", "Hindia", durationMs = 200_400L),
                candidate("drift", "Evaluasi", "Hindia", durationMs = 211_000L),
            ),
        )
        assertEquals(
            listOf("yt:track:near", "yt:track:far", "yt:track:drift"),
            ranked.map { it.candidate.track.id },
        )

        val hourLong = scoreCandidate(source, candidate("hour", "Evaluasi", "Hindia", durationMs = 3_600_000L))
        assertTrue(hourLong.disqualified)
        assertTrue(hourLong.score < MATCHER_MINIMUM)
    }

    @Test
    fun fallsBackToVideosAndReturnsNullWhenNoCandidateMeetsMinimum() = runTest {
        val source = SpotifyTrack("sp1", "Lagu Langka", listOf("Artis Indie"), durationMs = 190_000L)
        val videoSearcher = CandidateSearcher { _, includeVideos ->
            if (!includeVideos) {
                emptyList()
            } else {
                listOf(candidate("mv1", "Lagu Langka", "Artis Indie", durationMs = 191_000L, videoType = "MUSIC_VIDEO_TYPE_OMV"))
            }
        }
        assertEquals("yt:track:mv1", matchSpotifyTrack(source, videoSearcher)?.id)

        val badSearcher = CandidateSearcher { _, _ ->
            listOf(candidate("wrong", "Lagu Sama Sekali Berbeda", "Orang Lain", durationMs = 310_000L))
        }
        assertNull(matchSpotifyTrack(source, badSearcher))
    }
}
