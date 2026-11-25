package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.backup.ImportSummary

class BackupSummaryTest {
    @Test
    fun onlyWhatWasAddedIsListed() {
        val summary = ImportSummary(favoritesAdded = 3, playlistsAdded = 1, historyAdded = 12)
        assertEquals("Added: 3 favorites, 1 playlist, 12 history tracks.", describe(summary))
        assertEquals(
            "Ditambahkan: 3 favorit, 1 playlist, 12 lagu riwayat.",
            describe(summary, AppLanguage.Indonesian),
        )
    }

    @Test
    fun skippedEntriesAreExplained() {
        val summary = ImportSummary(playlistTracksAdded = 2, savedAdded = 1, skipped = 4)
        assertEquals(
            "Added: 2 songs to existing playlists, 1 saved item.\n" +
                "4 entries skipped (desktop local files or non-YouTube Music sources).",
            describe(summary),
        )
        assertEquals(
            "Ditambahkan: 2 lagu ke playlist yang ada, 1 item tersimpan.\n" +
                "4 entri dilewati (file lokal desktop atau sumber selain YouTube Music).",
            describe(summary, AppLanguage.Indonesian),
        )
    }

    @Test
    fun anAlreadyImportedBackupSaysSo() {
        assertEquals("Everything in this backup is already in your library.", describe(ImportSummary()))
        assertEquals("Semua isi backup sudah ada di library.", describe(ImportSummary(), AppLanguage.Indonesian))
    }

    @Test
    fun followedArtistsAreDescribedInSummary() {
        val summary = ImportSummary(followedArtistsAdded = 2)
        assertEquals("Added: 2 followed artists.", describe(summary, AppLanguage.English))
        assertEquals(
            "Ditambahkan: 2 artis diikuti.",
            describe(summary, AppLanguage.Indonesian),
        )
    }
}
