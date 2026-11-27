package senada.app.nowplaying

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import senada.model.Track
import senada.playback.QueueItem

class UpcomingQueueTest {
    private val entries = (1L..4L).map { QueueItem(it, Track("t:$it", "Lagu $it", listOf("A"), source = "test")) }

    @Test
    fun songsBeforeTheCurrentOneAreHidden() {
        val upcoming = upcomingQueue(entries, selectedEntryId = 3)

        assertEquals(listOf(3L, 4L), upcoming.entries.map { it.id })
        assertEquals(2, upcoming.offset)
    }

    @Test
    fun moveTargetsMapBackToThePlayOrder() {
        val upcoming = upcomingQueue(entries, selectedEntryId = 2)

        assertEquals(2, upcoming.moveTarget(1))
        assertEquals(3, upcoming.moveTarget(2))
    }

    @Test
    fun nothingCanBeMovedAboveTheCurrentSong() {
        val upcoming = upcomingQueue(entries, selectedEntryId = 2)

        assertEquals(2, upcoming.moveTarget(0))
    }

    @Test
    fun theWholeQueueShowsWhenNothingIsSelected() {
        val upcoming = upcomingQueue(entries, selectedEntryId = null)

        assertEquals(entries, upcoming.entries)
        assertFalse(upcoming.startsWithCurrent)
        assertEquals(0, upcoming.moveTarget(0))
    }

    @Test
    fun theFirstSongSelectedShowsTheWholeQueue() {
        assertEquals(entries, upcomingQueue(entries, selectedEntryId = 1).entries)
    }

    @Test
    fun aSelectionOutsideTheQueueShowsTheWholeQueue() {
        val upcoming = upcomingQueue(entries, selectedEntryId = 99)

        assertEquals(entries, upcoming.entries)
        assertEquals(0, upcoming.offset)
    }
}
