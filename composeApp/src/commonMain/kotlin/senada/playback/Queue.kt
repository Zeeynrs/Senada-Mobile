package senada.playback

import senada.model.Track

data class QueueItem(val id: Long, val track: Track)

class Queue private constructor(val entries: List<QueueItem>, internal val nextId: Long) {
    constructor() : this(emptyList(), 1)

    fun append(track: Track): Queue =
        Queue(entries + QueueItem(nextId, track), nextId + 1)

    fun remove(entryId: Long): Queue =
        Queue(entries.filterNot { it.id == entryId }, nextId)

    internal fun adjacentTo(entryId: Long?, offset: Int): QueueItem? {
        val index = entries.indexOfFirst { it.id == entryId }
        if (index < 0) return null
        return entries.getOrNull(index + offset)
    }

    internal companion object {
        fun restore(entries: List<QueueItem>, nextId: Long): Queue {
            require(nextId in 1L until Long.MAX_VALUE)
            val ids = entries.map { it.id }
            require(ids.all { it in 1L until nextId } && ids.toSet().size == ids.size)
            return Queue(entries, nextId)
        }
    }
}
