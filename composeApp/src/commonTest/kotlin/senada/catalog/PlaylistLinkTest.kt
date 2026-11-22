package senada.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlaylistLinkTest {
    @Test
    fun parsesYouTubeAndYouTubeMusicPlaylistLinks() {
        val cases = listOf(
            "https://music.youtube.com/playlist?list=PLabc123_XYZ&si=shareToken" to "PLabc123_XYZ",
            "https://www.youtube.com/playlist?list=PLabc123_XYZ" to "PLabc123_XYZ",
            "https://m.youtube.com/playlist?list=PLabc123_XYZ" to "PLabc123_XYZ",
            "music.youtube.com/playlist?list=PLabc123_XYZ" to "PLabc123_XYZ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123_XYZ" to "PLabc123_XYZ",
            "https://youtu.be/dQw4w9WgXcQ?list=PLabc123_XYZ" to "PLabc123_XYZ",
            "https://music.youtube.com/playlist?list=RDCLAK5uy_editorial123" to "RDCLAK5uy_editorial123",
            "https://music.youtube.com/playlist?list=VLPLabc123_XYZ" to "PLabc123_XYZ",
            "https://music.youtube.com/browse/VLPLabc123_XYZ" to "PLabc123_XYZ",
        )
        for ((input, expectedId) in cases) {
            val result = parsePlaylistLink(input)
            val valid = assertIs<PlaylistLinkParseResult.Valid>(result, "Seharusnya valid: $input")
            assertEquals(PlaylistLink.YouTubeMusic(expectedId), valid.link, input)
        }
    }

    @Test
    fun parsesSpotifyPlaylistLinksAndIntlPrefixes() {
        val id = "37i9dQZF1DXcBWIGoYBM5M"
        val cases = listOf(
            "https://open.spotify.com/playlist/$id",
            "https://open.spotify.com/playlist/$id?si=abc123",
            "https://open.spotify.com/intl-id/playlist/$id?si=abc123",
            "https://open.spotify.com/intl-pt-br/playlist/$id",
            "https://open.spotify.com/intl-en/embed/playlist/$id",
            "https://open.spotify.com/embed/playlist/$id",
            "open.spotify.com/playlist/$id",
            "spotify:playlist:$id",
            "  https://open.spotify.com/playlist/$id  ",
        )
        for (input in cases) {
            val result = parsePlaylistLink(input)
            val valid = assertIs<PlaylistLinkParseResult.Valid>(result, "Seharusnya valid: $input")
            assertEquals(PlaylistLink.Spotify(id), valid.link, input)
        }
    }

    @Test
    fun rejectsAccountListsMixesAndNonPlaylistLinksWithIndonesianMessages() {
        val accountLists = listOf(
            "https://music.youtube.com/playlist?list=LM",
            "https://www.youtube.com/playlist?list=WL",
            "https://www.youtube.com/playlist?list=LL",
            "https://music.youtube.com/playlist?list=SE",
        )
        for (link in accountLists) {
            val invalid = assertIs<PlaylistLinkParseResult.Invalid>(parsePlaylistLink(link), link)
            assertTrue(invalid.message.contains("login", ignoreCase = true), invalid.message)
        }

        val mix = assertIs<PlaylistLinkParseResult.Invalid>(
            parsePlaylistLink("https://music.youtube.com/watch?v=abc&list=RDAMVMabc"),
        )
        assertTrue(mix.message.contains("Mix", ignoreCase = true), mix.message)

        val unsupported = listOf(
            "",
            "   ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/browse/MPREb_album123",
            "https://open.spotify.com/track/37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/album/37i9dQZF1DXcBWIGoYBM5M",
            "spotify:album:37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/playlist/terlaluPendek",
            "https://spotify.link/abc123",
            "https://example.com/playlist?list=PLabc123",
            "https://notyoutube.com/playlist?list=PLabc123",
            "bukan tautan",
        )
        for (link in unsupported) {
            val invalid = assertIs<PlaylistLinkParseResult.Invalid>(parsePlaylistLink(link), link)
            assertTrue(invalid.message.isNotBlank(), link)
        }
    }
}
