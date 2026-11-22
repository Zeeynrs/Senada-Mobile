package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.library.collectPlaylistCovers

class LibraryListsTest {
    @Test
    fun formatsPlaylistRowSubtitleWithNonNegativeCounts() {
        assertEquals("Playlist · 0 lagu", playlistRowSubtitle(0))
        assertEquals("Playlist · 1 lagu", playlistRowSubtitle(1))
        assertEquals("Playlist · 12 lagu", playlistRowSubtitle(12))
        assertEquals("Playlist · 0 lagu", playlistRowSubtitle(-3))
    }

    @Test
    fun formatsPlaylistListMetaCount() {
        assertEquals(CollectionMeta("0 playlist", "0 playlist"), playlistListMeta(0))
        assertEquals(CollectionMeta("1 playlist", "1 playlist"), playlistListMeta(1))
        assertEquals(CollectionMeta("5 playlist", "5 playlist"), playlistListMeta(5))
        assertEquals(CollectionMeta("0 playlist", "0 playlist"), playlistListMeta(-2))
    }

    @Test
    fun selectsPlaceholderWhenPlaylistHasNoValidArtwork() {
        assertEquals(PlaylistCover.Placeholder, selectPlaylistCover(emptyList()))
        assertEquals(PlaylistCover.Placeholder, selectPlaylistCover(listOf(null, "", "   ")))
    }

    @Test
    fun selectsSingleCoverWhenPlaylistHasOneToThreeDistinctArtworks() {
        assertEquals(
            PlaylistCover.Single("https://art.example/1.jpg"),
            selectPlaylistCover(listOf(null, "  https://art.example/1.jpg  ", "")),
        )
        assertEquals(
            PlaylistCover.Single("https://art.example/1.jpg"),
            selectPlaylistCover(
                listOf(
                    "https://art.example/1.jpg",
                    "https://art.example/1.jpg",
                    "https://art.example/2.jpg",
                    "https://art.example/3.jpg",
                ),
            ),
        )
    }

    @Test
    fun selectsFourTileMosaicWhenPlaylistHasAtLeastFourDistinctArtworks() {
        assertEquals(
            PlaylistCover.Mosaic(
                listOf(
                    "https://art.example/1.jpg",
                    "https://art.example/2.jpg",
                    "https://art.example/3.jpg",
                    "https://art.example/4.jpg",
                ),
            ),
            selectPlaylistCover(
                listOf(
                    null,
                    "https://art.example/1.jpg",
                    "https://art.example/1.jpg",
                    "https://art.example/2.jpg",
                    " ",
                    "https://art.example/3.jpg",
                    "https://art.example/4.jpg",
                    "https://art.example/5.jpg",
                ),
            ),
        )
    }

    @Test
    fun collectsUpToFourDistinctArtworksPerPlaylistInOrder() {
        val grouped = collectPlaylistCovers(
            listOf(
                "pl-a" to null,
                "pl-a" to " ",
                "pl-a" to "https://art.example/a1.jpg",
                "pl-a" to "https://art.example/a1.jpg",
                "pl-a" to "https://art.example/a2.jpg",
                "pl-a" to "https://art.example/a3.jpg",
                "pl-a" to "https://art.example/a4.jpg",
                "pl-a" to "https://art.example/a5.jpg",
                "pl-b" to "https://art.example/b1.jpg",
            ),
        )

        assertEquals(
            mapOf(
                "pl-a" to listOf(
                    "https://art.example/a1.jpg",
                    "https://art.example/a2.jpg",
                    "https://art.example/a3.jpg",
                    "https://art.example/a4.jpg",
                ),
                "pl-b" to listOf("https://art.example/b1.jpg"),
            ),
            grouped,
        )
    }
}
