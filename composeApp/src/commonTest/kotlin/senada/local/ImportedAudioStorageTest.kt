package senada.local

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.model.Track

class ImportedAudioStorageTest {
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
}
