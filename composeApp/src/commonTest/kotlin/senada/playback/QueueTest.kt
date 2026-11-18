package senada.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
}
