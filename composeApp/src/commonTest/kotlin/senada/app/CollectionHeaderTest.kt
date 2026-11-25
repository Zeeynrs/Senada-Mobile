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
        assertEquals("7 h 50 m", formatCollectionDuration(sevenHoursFiftyMinutes))
        assertEquals("7 hours 50 minutes", spokenCollectionDuration(sevenHoursFiftyMinutes))
        assertEquals("7 j 50 mnt", formatCollectionDuration(sevenHoursFiftyMinutes, AppLanguage.Indonesian))
        assertEquals("7 jam 50 menit", spokenCollectionDuration(sevenHoursFiftyMinutes, AppLanguage.Indonesian))

        val fortyFiveMinutes = 45L * 60_000L
        assertEquals("45 m", formatCollectionDuration(fortyFiveMinutes))
        assertEquals("45 minutes", spokenCollectionDuration(fortyFiveMinutes))

        assertEquals("2 h", formatCollectionDuration(120L * 60_000L))
        assertEquals("2 hours", spokenCollectionDuration(120L * 60_000L))

        assertEquals("1 h 1 m", formatCollectionDuration(61L * 60_000L))
        assertEquals("< 1 m", formatCollectionDuration(24_000L))
        assertEquals("less than 1 minute", spokenCollectionDuration(24_000L))
        assertEquals("kurang dari 1 menit", spokenCollectionDuration(24_000L, AppLanguage.Indonesian))

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
        assertEquals("131 songs · 7 h 50 m", longMeta.display)
        assertEquals("131 songs, 7 hours 50 minutes", longMeta.spoken)

        val singleMeta = favoritesCollectionMeta(trackCount = 1, totalDurationMs = 45L * 60_000L)
        assertEquals("1 song · 45 m", singleMeta.display)
        assertEquals("1 song, 45 minutes", singleMeta.spoken)

        val unknownMeta = favoritesCollectionMeta(trackCount = 5, totalDurationMs = null)
        assertEquals("5 songs", unknownMeta.display)
        assertEquals("5 songs", unknownMeta.spoken)

        val emptyMeta = playlistCollectionMeta(trackCount = 0, totalDurationMs = null)
        assertEquals("0 songs", emptyMeta.display)
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
        assertEquals("Album · Tulus · 2022 · 3 songs · 45 m", albumMeta?.display)
        assertEquals("Album, Tulus, 2022, 3 songs, 45 minutes", albumMeta?.spoken)

        val albumNoDuration = album.copy(tracks = listOf(track("1", null)))
        assertEquals(
            "Album · Tulus · 2022 · 1 song",
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
            "YouTube Music · 2 songs · 45 m",
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
            "Mix / Radio · 1 song · 45 m",
            youTubePlaylistCollectionMeta(mixPlaylist, isMix = true, fallbackSubtitle = null)?.display,
        )
    }

    @Test
    fun buildsHeaderMenuItemsPerPageType() {
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.RenamePlaylist, "Rename", enabled = true),
                CollectionMenuItem(CollectionMenuAction.DeletePlaylist, "Delete playlist", enabled = true),
            ),
            collectionMenuItems(CollectionKind.LibraryPlaylist, hasTracks = false),
        )

        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = false)),
            collectionMenuItems(CollectionKind.Favorites, hasTracks = false),
        )
        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true)),
            collectionMenuItems(CollectionKind.Favorites, hasTracks = true),
        )

        assertEquals(
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true)),
            collectionMenuItems(CollectionKind.Album(hasArtistId = false), hasTracks = true),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true),
                CollectionMenuItem(CollectionMenuAction.OpenArtist, "Open artist", enabled = true),
            ),
            collectionMenuItems(CollectionKind.Album(hasArtistId = true), hasTracks = true),
        )

        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Save to Library", enabled = true),
            ),
            collectionMenuItems(CollectionKind.YouTubePlaylist(isMix = false, saveStatus = null), hasTracks = true),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Saving…", enabled = false),
            ),
            collectionMenuItems(
                CollectionKind.YouTubePlaylist(isMix = false, saveStatus = PlaylistSaveStatus.Saving),
                hasTracks = true,
            ),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true),
                CollectionMenuItem(CollectionMenuAction.SaveToLibrary, "Saved", enabled = false),
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
            listOf(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Add all to queue", enabled = true)),
            collectionMenuItems(CollectionKind.YouTubePlaylist(isMix = true), hasTracks = true),
        )
        assertEquals(
            listOf(
                CollectionMenuItem(CollectionMenuAction.RenamePlaylist, "Ganti nama", enabled = true),
                CollectionMenuItem(CollectionMenuAction.DeletePlaylist, "Hapus playlist", enabled = true),
            ),
            collectionMenuItems(CollectionKind.LibraryPlaylist, hasTracks = false, language = AppLanguage.Indonesian),
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

    @Test
    fun browseV2MonochromePillsAndCardsMeetAccessibleContrastInDarkAndLightThemes() {
        val coverSample = Color(0xFF4C8AE0)
        for (dark in listOf(true, false)) {
            for (choice in AccentChoice.entries) {
                val scheme = senadaColorScheme(dark = dark, accent = choice.source(coverSample))
                // Pil filter aktif: latar onSurface, teks background.
                assertTrue(
                    contrastRatio(scheme.onSurface, scheme.background) >= 4.5f,
                    "Active pill contrast failed for dark=$dark choice=${choice.key}",
                )
                // Pil filter nonaktif & tombol kembali: latar surfaceContainerHigh, teks/ikon onSurface.
                assertTrue(
                    contrastRatio(scheme.surfaceContainerHigh, scheme.onSurface) >= 4.5f,
                    "Inactive pill contrast failed for dark=$dark choice=${choice.key}",
                )
                // Kartu SenadaCard: latar surfaceContainer dengan teks utama dan sekunder.
                assertTrue(
                    contrastRatio(scheme.surfaceContainer, scheme.onSurface) >= 4.5f,
                    "Card title contrast failed for dark=$dark choice=${choice.key}",
                )
                assertTrue(
                    contrastRatio(scheme.surfaceContainer, scheme.onSurfaceVariant) >= 4.5f,
                    "Card subtitle contrast failed for dark=$dark choice=${choice.key}",
                )
            }
        }
    }
}
