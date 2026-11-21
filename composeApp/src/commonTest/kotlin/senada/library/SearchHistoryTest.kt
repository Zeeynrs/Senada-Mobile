package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import senada.db.SenadaDatabase

class SearchHistoryTest {
    private val driver = testLibraryDriver()
    private var clock = 0L

    private fun TestScope.library() = LibraryRepository(
        SenadaDatabase(driver),
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { ++clock },
    )

    @Test
    fun newestSearchComesFirstAndRepeatsMoveUpWithTheirLatestSpelling() = runTest {
        val library = library()
        library.recordSearch("tulus")
        library.recordSearch("  Nadin Amizah ")
        library.recordSearch("TULUS")

        assertEquals(listOf("TULUS", "Nadin Amizah"), library.searchHistory().first())
    }

    @Test
    fun onlyTheLatestTwelveAreKept() = runTest {
        val library = library()
        for (n in 1..15) library.recordSearch("lagu $n")

        val history = library.searchHistory().first()
        assertEquals(LibraryRepository.SEARCH_HISTORY_LIMIT, history.size)
        assertEquals("lagu 15", history.first())
        assertEquals("lagu 4", history.last())
    }

    @Test
    fun blankQueriesLinksAndUrisAreNotRecorded() = runTest {
        val library = library()
        for (query in listOf("   ", "https://music.youtube.com/watch?v=x", "yt:track:abc", "local:a.mp3", "spotify:track:1")) {
            library.recordSearch(query)
        }

        assertEquals(emptyList(), library.searchHistory().first())
    }

    @Test
    fun entriesCanBeRemovedOneByOneOrAllAtOnce() = runTest {
        val library = library()
        library.recordSearch("tulus")
        library.recordSearch("hindia")
        library.recordSearch("sal priadi")

        library.removeSearch("HINDIA")
        assertEquals(listOf("sal priadi", "tulus"), library.searchHistory().first())

        library.clearSearchHistory()
        assertEquals(emptyList(), library.searchHistory().first())
    }

    @Test
    fun aVersionOneDatabaseGainsSearchHistoryWhenMigrated() = runTest {
        // Database versi 1 belum punya tabel ini; migrasi 1.sqm harus membuatnya tanpa mengusik isi lama.
        driver.execute(null, "DROP TABLE search_history", 0)
        val library = library()
        library.setFavorite(senada.model.Track("yt:track:a", "A", listOf("B"), source = "ytmusic"), true)

        SenadaDatabase.Schema.migrate(driver, 1, SenadaDatabase.Schema.version)
        library.recordSearch("tulus")

        assertEquals(2L, SenadaDatabase.Schema.version)
        assertEquals(listOf("tulus"), library.searchHistory().first())
        assertEquals(1, library.favorites.first().size)
    }
}
