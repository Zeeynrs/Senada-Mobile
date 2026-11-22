package senada.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.IOException
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.testLibraryDriver
import senada.model.Track

class ImportedAudioStorageTest {
    private class FakeImportedAudioStorage(vararg initial: ImportedAudioFile) : ImportedAudioStorage {
        val files = initial.associateBy { it.fileName }.toMutableMap()
        var failList = false

        override fun listFiles(): List<ImportedAudioFile> {
            if (failList) throw IOException("Gagal membaca folder")
            return files.values.toList()
        }

        override fun deleteFile(fileName: String): Boolean = files.remove(fileName) != null
    }

    @Test
    fun onlyUnreferencedFilesWithSafeNamesCanBeRemoved() {
        val files = listOf(
            "queued.mp3", "saved.m4a", "orphan.flac", "interrupted.ogg.part",
            "valid.part", "catalog.mp3", "../outside.mp3", "sub/file.mp3", "sub\\file.mp3",
            ".", "..", "", " ", "null\u0000.mp3",
        ).map { ImportedAudioFile(it, 10L) }
        val queue = listOf(
            Track("local:queued.mp3", "Antrean", emptyList(), source = "local"),
            Track("local:catalog.mp3", "Katalog", emptyList(), source = "ytmusic"),
        )

        assertEquals(
            listOf("orphan.flac", "interrupted.ogg.part", "catalog.mp3"),
            unusedImportedAudioFileNames(files, queue, listOf("local:saved.m4a", "local:valid.part")),
        )
    }

    @Test
    fun summaryCountsFilesAndBytesIncludingLeftoverCopies() {
        assertEquals(ImportedAudioSummary(0, 0L), summarizeImportedAudio(emptyList()))
        assertEquals(
            ImportedAudioSummary(3, 3_001_000_000L),
            summarizeImportedAudio(
                listOf(
                    ImportedAudioFile("saved.mp3", 3_000_000_000L),
                    ImportedAudioFile("orphan.m4a", 700_000L),
                    ImportedAudioFile("interrupted.wav.part", 300_000L),
                    ImportedAudioFile("../outside.mp3", 1L),
                ),
            ),
        )
    }

    @Test
    fun deleteUnusedImportedAudioRemovesDeletedFilesFromLibraryList() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = LibraryRepository(
            SenadaDatabase(testLibraryDriver()),
            dispatcher = dispatcher,
            now = { 1_000L },
        )
        val queued = Track("local:antrean.mp3", "Antrean", emptyList(), source = LOCAL_FILE_SOURCE)
        val favorited = Track("local:favorit.m4a", "Favorit", emptyList(), source = LOCAL_FILE_SOURCE)
        val unused = Track("local:tidak-dipakai.flac", "Tidak Dipakai", emptyList(), source = LOCAL_FILE_SOURCE)
        val storage = FakeImportedAudioStorage(
            ImportedAudioFile("antrean.mp3", 100L),
            ImportedAudioFile("favorit.m4a", 200L),
            ImportedAudioFile("tidak-dipakai.flac", 300L),
        )
        library.recordImported(queued)
        library.recordImported(favorited)
        library.recordImported(unused)
        library.setFavorite(favorited, true)

        val result = deleteUnusedImportedAudio(
            storage = storage,
            library = library,
            queueTracks = { listOf(queued) },
            ioDispatcher = dispatcher,
        )

        assertEquals(
            ImportedAudioCleanupResult(unusedCount = 1, failedCount = 0, summary = ImportedAudioSummary(2, 300L)),
            result,
        )
        assertFalse("tidak-dipakai.flac" in storage.files)
        assertEquals(listOf(queued, favorited), library.localTracks().first())
    }

    @Test
    fun syncImportedAudioPrunesExternallyDeletedFilesAndPreservesListOnIoError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val library = LibraryRepository(
            SenadaDatabase(testLibraryDriver()),
            dispatcher = dispatcher,
            now = { 1_000L },
        )
        val first = Track("local:a.mp3", "A", emptyList(), source = LOCAL_FILE_SOURCE)
        val second = Track("local:b.mp3", "B", emptyList(), source = LOCAL_FILE_SOURCE)
        val storage = FakeImportedAudioStorage(
            ImportedAudioFile("a.mp3", 100L),
            ImportedAudioFile("b.mp3", 200L),
        )
        library.recordImported(first)
        library.recordImported(second)

        storage.failList = true
        syncImportedAudio(storage, library, ioDispatcher = dispatcher)
        assertEquals(listOf(first, second), library.localTracks().first())

        storage.failList = false
        storage.files.remove("a.mp3")
        syncImportedAudio(storage, library, ioDispatcher = dispatcher)
        assertEquals(listOf(second), library.localTracks().first())
    }
}
