package senada.catalog

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoCatalogTest {
    @Test
    fun searchMatchesTitleAndArtistWordsIgnoringCaseAndWhitespace() = runBlocking {
        val page = DemoCatalog.search("  SeNaDa   PAgi ")

        assertEquals(listOf("Sebelum Pagi"), page.items.map { it.title })
        assertNull(page.nextPageToken)
    }

    @Test
    fun noMatchReturnsAnEmptyPage() = runBlocking {
        assertTrue(DemoCatalog.search("lagu tidak dikenal").items.isEmpty())
    }

    @Test
    fun bothTracksHaveDifferentBundledAudioPaths() = runBlocking {
        val tracks = DemoCatalog.search("").items
        val paths = tracks.map { DemoCatalog.localAudioPath(it.id) }

        assertEquals(listOf("files/selepas-hujan.m4a", "files/sebelum-pagi.m4a"), paths)
    }
}
