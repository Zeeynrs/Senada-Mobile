package senada.playback

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import senada.model.Track

class QueueTest {
    private val track = Track("demo:track:hujan", "Hujan", listOf("Senada"), source = "demo")
    private val secondTrack = Track("demo:track:pagi", "Pagi", listOf("Senada"), source = "demo")
    private val thirdTrack = Track("demo:track:senja", "Senja", listOf("Senada"), source = "demo")

    @Test
    fun shuffleVariesBothRemainingOrderAndInsertionPosition() {
        val original = Queue().append(track).append(track).append(track).append(track)
        val active = original.entries[1]
        val orders = mutableSetOf<List<QueueItem>>()
        val positions = mutableSetOf<Int>()
        for (seed in 0..63) {
            val random = Random(seed)
            val shuffled = original.withShuffle(true, active.id, random)
            assertEquals(active, shuffled.playOrder.first())
            orders.add(shuffled.playOrder)
            val appended = shuffled.append(track, active.id, random)
            positions.add(appended.playOrder.indexOf(appended.entries.last()))
        }
        assertNotEquals(1, orders.size)
        assertEquals(setOf(1, 2, 3, 4), positions)
    }

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

    @Test
    fun moveUsesEntryIdToReorderDuplicateTracksIndependently() {
        val queue = Queue().append(track).append(secondTrack).append(track)
        val (firstCopy, middle, secondCopy) = queue.entries

        val movedSecondCopyUp = queue.move(secondCopy.id, 0)
        assertEquals(listOf(secondCopy, firstCopy, middle), movedSecondCopyUp.entries)

        val movedFirstCopyDown = movedSecondCopyUp.move(firstCopy.id, 2)
        assertEquals(listOf(secondCopy, middle, firstCopy), movedFirstCopyDown.entries)
    }

    @Test
    fun moveSupportsBidirectionalMovesAndPreservesMetadata() {
        val queue = Queue()
            .appendAll(listOf(track, secondTrack), activeEntryId = null, playlistKey = "library:pl1")
            .appendAll(listOf(thirdTrack), activeEntryId = null, playlistKey = "library:pl2")
            .withRepeatMode(RepeatMode.All)
        val (a, b, c) = queue.entries

        val movedDown = queue.move(a.id, 2)
        assertEquals(listOf(b, c, a), movedDown.entries)
        assertEquals(RepeatMode.All, movedDown.repeatMode)
        assertEquals("library:pl1", movedDown.entries.last().playlistKey)
        assertEquals("library:pl2", movedDown.entries[1].playlistKey)

        val movedUp = movedDown.move(a.id, 0)
        assertEquals(listOf(a, b, c), movedUp.entries)
    }

    @Test
    fun moveNoOpReturnsSameInstanceForSamePositionInvalidIdSingleAndEmpty() {
        val empty = Queue()
        assertSame(empty, empty.move(1L, 0))

        val single = empty.append(track)
        val onlyId = single.entries.single().id
        assertSame(single, single.move(onlyId, 0))
        assertSame(single, single.move(onlyId, 5))
        assertSame(single, single.move(onlyId, -3))

        val multi = single.append(secondTrack).append(thirdTrack)
        val (a, _, c) = multi.entries
        assertSame(multi, multi.move(a.id, 0))
        assertSame(multi, multi.move(a.id, -2))
        assertSame(multi, multi.move(c.id, 2))
        assertSame(multi, multi.move(c.id, 99))
        assertSame(multi, multi.move(999L, 1))
    }

    @Test
    fun moveInShuffleModeReordersPlayOrderAndRestoresBaseOrderWhenDisabled() {
        val base = Queue().append(track).append(secondTrack).append(thirdTrack)
        val (a, b, c) = base.entries
        val shuffled = base.withShuffle(enabled = true, activeEntryId = b.id, random = Random(7))
        assertTrue(shuffled.shuffleEnabled)
        assertEquals(b, shuffled.playOrder.first())

        val lastInShuffle = shuffled.playOrder.last()
        val reordered = shuffled.move(lastInShuffle.id, 0)
        assertTrue(reordered.shuffleEnabled)
        assertEquals(lastInShuffle, reordered.playOrder.first())
        assertEquals(listOf(a, b, c), reordered.entries)

        val unshuffled = reordered.withShuffle(enabled = false, activeEntryId = b.id)
        assertFalse(unshuffled.shuffleEnabled)
        assertEquals(listOf(a, b, c), unshuffled.playOrder)
    }
}
