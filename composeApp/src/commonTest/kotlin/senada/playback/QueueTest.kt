package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import senada.model.Track

class QueueTest {
    private val track = Track("demo:track:hujan", "Hujan", listOf("Senada"), source = "demo")

    @Test
    fun removingOneOccurrencePreservesTheOther() {
        val queue = Queue().append(track).append(track)
        val (first, second) = queue.entries
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(second), queue.remove(first.id).entries)
        assertEquals(listOf(first, second), queue.entries)
    }

    @Test
    fun removedIdCannotRemoveANewOccurrence() {
        val original = Queue().append(track)
        val removedId = original.entries.single().id
        val updated = original.remove(removedId).append(track)
        assertEquals(updated.entries, updated.remove(removedId).entries)
        assertEquals(track, updated.entries.single().track)
    }

    @Test
    fun navigationUsesEntryIdsAndStopsAtBothEnds() {
        val queue = Queue().append(track).append(track).append(track)
        val (first, second, third) = queue.entries

        assertEquals(first, queue.adjacentTo(second.id, -1))
        assertEquals(third, queue.adjacentTo(second.id, 1))
        assertNull(queue.adjacentTo(first.id, -1))
        assertNull(queue.adjacentTo(third.id, 1))
        assertNull(queue.remove(second.id).adjacentTo(second.id, 1))
    }
}
