package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.backup.ImportSummary

class BackupSummaryTest {
    @Test
    fun onlyWhatWasAddedIsListed() {
        val text = describe(ImportSummary(favoritesAdded = 3, playlistsAdded = 1, historyAdded = 12))

        assertEquals("Ditambahkan: 3 favorit, 1 playlist, 12 lagu riwayat.", text)
    }

    @Test
    fun skippedEntriesAreExplained() {
        val text = describe(ImportSummary(playlistTracksAdded = 2, savedAdded = 1, skipped = 4))

        assertEquals(
            "Ditambahkan: 2 lagu ke playlist yang ada, 1 item tersimpan.\n" +
                "4 entri dilewati (file lokal desktop atau sumber selain YouTube Music).",
            text,
        )
    }

    @Test
    fun anAlreadyImportedBackupSaysSo() {
        assertEquals("Semua isi backup sudah ada di library.", describe(ImportSummary()))
    }

    @Test
    fun followedArtistsAreDescribedInSummary() {
        assertEquals(
            "Ditambahkan: 2 artis diikuti.",
            describe(ImportSummary(followedArtistsAdded = 2)),
        )
    }
}
