package senada.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.app.tabs.PlaylistSaveStatus
import senada.app.theme.AccentChoice
import senada.app.theme.senadaColorScheme
import senada.catalog.AlbumDetail
import senada.catalog.PlaylistDetail
import senada.library.SavedLocalPlaylist
import senada.model.Track

class CollectionHeaderTest {
    private fun track(id: String, durationMs: Long?) = Track(
        id = id,
        title = "Lagu $id",
        artists = listOf("Senada"),
        durationMs = durationMs,
        source = "ytmusic",
    )

    @Test
    fun formatsDurationsForHoursMinutesAndShortCollections() {
        val sevenHoursFiftyMinutes = (7 * 60L + 50L) * 60_000L
        assertEquals("7 j 50 mnt", formatCollectionDuration(sevenHoursFiftyMinutes))
        assertEquals("7 jam 50 menit", spokenCollectionDuration(sevenHoursFiftyMinutes))

        val fortyFiveMinutes = 45L * 60_000L
        assertEquals("45 mnt", formatCollectionDuration(fortyFiveMinutes))
        assertEquals("45 menit", spokenCollectionDuration(fortyFiveMinutes))

        assertEquals("2 j", formatCollectionDuration(120L * 60_000L))
        assertEquals("2 jam", spokenCollectionDuration(120L * 60_000L))

        assertEquals("1 j 1 mnt", formatCollectionDuration(61L * 60_000L))
        assertEquals("< 1 mnt", formatCollectionDuration(24_000L))
        assertEquals("kurang dari 1 menit", spokenCollectionDuration(24_000L))

        assertNull(formatCollectionDuration(0L))
        assertNull(formatCollectionDuration(-1_000L))
    }

    @Test
    fun sumsKnownDurationsAndSkipsUnknownOrNonPositiveValues() {
        val tracks = listOf(
            track("1", 20L * 60_000L),
            track("2", null),
            track("3", 0L),
            track("4", -5_000L),
            track("5", 25L * 60_000L),
        )
        assertEquals(45L * 60_000L, sumKnownDurationMs(tracks))
        assertNull(sumKnownDurationMs(listOf(track("a", null), track("b", 0L))))
        assertNull(sumKnownDurationMs(emptyList()))
    }

    @Test
    fun formatsPlaylistAndFavoritesMetaLineWithCountDurationSingularPluralAndUnknowns() {
        val longMeta = playlistCollectionMeta(
            trackCount = 131,
            totalDurationMs = (7 * 60L + 50L) * 60_000L,
        )
        assertEquals("131 lagu · 7 j 50 mnt", longMeta.display)
        assertEquals("131 lagu, 7 jam 50 menit", longMeta.spoken)

        val singleMeta = favoritesCollectionMeta(trackCount = 1, totalDurationMs = 45L * 60_000L)
        assertEquals("1 lagu · 45 mnt", singleMeta.display)
        assertEquals("1 lagu, 45 menit", singleMeta.spoken)

        val unknownMeta = favoritesCollectionMeta(trackCount = 5, totalDurationMs = null)
        assertEquals("5 lagu", unknownMeta.display)
        assertEquals("5 lagu", unknownMeta.spoken)

        val emptyMeta = playlistCollectionMeta(trackCount = 0, totalDurationMs = null)
        assertEquals("0 lagu", emptyMeta.display)
    }

    @Test
    fun formatsAlbumAndYouTubePlaylistMetaLines() {
        val album = AlbumDetail(
            id = "MPREb_manusia",
            title = "Manusia",
            artists = listOf("Tulus"),
            artistId = "UC_tulus",
            year = "2022",
            typeLabel = "Album",
            tracks = listOf(
                track("1", 20L * 60_000L),
                track("2", null),
                track("3", 25L * 60_000L),
            ),
        )
        val albumMeta = albumCollectionMeta(album, fallbackSubtitle = null)
        assertEquals("Album · Tulus · 2022 · 3 lagu · 45 mnt", albumMeta?.display)
        assertEquals("Album, Tulus, 2022, 3 lagu, 45 menit", albumMeta?.spoken)

        val albumNoDuration = album.copy(tracks = listOf(track("1", null)))
        assertEquals(
            "Album · Tulus · 2022 · 1 lagu",
            albumCollectionMeta(albumNoDuration, fallbackSubtitle = null)?.display,
        )

        val ytPlaylist = PlaylistDetail(
            id = "PL_senja",
            title = "Senja",
            owner = "YouTube Music",
            songCountText = "2 lagu",
            tracks = listOf(track("1", 30L * 60_000L), track("2", 15L * 60_000L)),
            truncated = false,
        )
        assertEquals(
            "YouTube Music · 2 lagu · 45 mnt",
            youTubePlaylistCollectionMeta(ytPlaylist, isMix = false, fallbackSubtitle = null)?.display,
        )

        val truncatedPlaylist = ytPlaylist.copy(songCountText = "800 lagu", truncated = true)
        assertEquals(
            "YouTube Music · 800 lagu",
            youTubePlaylistCollectionMeta(truncatedPlaylist, isMix = false, fallbackSubtitle = null)?.display,
        )

        val mixPlaylist = PlaylistDetail(
            id = "RDAMVM1",
            title = "Mix Saya",
            owner = null,
            songCountText = "2 lagu",
            tracks = listOf(track("1", 45L * 60_000L)),
        )
        assertEquals(
            "Mix / Radio · 1 lagu · 45 mnt",
            youTubePlaylistCollectionMeta(mixPlaylist, isMix = true, fallbackSubtitle = null)?.display,
        )
    }

    @Test
    fun buildsHeaderMenuItemsPerPageType() {
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.RenamePlaylist, "Ganti nama", enabled = true),
                CollectionMenuItem(CollectionMenuAction.DeletePlaylist, "Hapus playlist", enabled = true),
            ),
            collectionMenuItems(CollectionKind.LibraryPlaylist, hasTracks = false),
        )

        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = false)),
            collectionMenuItems(CollectionKind.Favorites, hasTracks = false),
        )
        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true)),
            collectionMenuItems(CollectionKind.Favorites, hasTracks = true),
        )

        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true)),
            collectionMenuItems(CollectionKind.Album(hasArtistId = false), hasTracks = true),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true),
                CollectionMenuItem(CollectionMenuAction.OpenArtist, "Buka artis", enabled = true),
            ),
            collectionMenuItems(CollectionKind.Album(hasArtistId = true), hasTracks = true),
        )

        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Simpan ke Library", enabled = true),
            ),
            collectionMenuItems(CollectionKind.YouTubePlaylist(isMix = false, saveStatus = null), hasTracks = true),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Menyimpan…", enabled = false),
            ),
            collectionMenuItems(
                CollectionKind.YouTubePlaylist(isMix = false, saveStatus = PlaylistSaveStatus.Saving),
                hasTracks = true,
            ),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Tersimpan", enabled = false),
            ),
            collectionMenuItems(
                CollectionKind.YouTubePlaylist(
                    isMix = false,
                    saveStatus = PlaylistSaveStatus.Saved(SavedLocalPlaylist("local-1", "Senja", 2)),
                ),
                hasTracks = true,
            ),
        )
        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = true)),
            collectionMenuItems(CollectionKind.YouTubePlaylist(isMix = true), hasTracks = true),
        )
    }

    @Test
    fun playButtonIconHasAccessibleContrastForEveryThemeAndAccent() {
        val coverSample = Color(0xFFE0B84C)
        for (dark in listOf(true, false)) {
            for (choice in AccentChoice.entries) {
                val scheme = senadaColorScheme(dark = dark, accent = choice.source(coverSample))
                val iconTint = textOnAccent(scheme.primary)
                assertTrue(
                    contrastRatio(scheme.primary, iconTint) >= 4.5f,
                    "Contrast failed for dark=$dark choice=${choice.key}",
                )
            }
        }
    }
}
