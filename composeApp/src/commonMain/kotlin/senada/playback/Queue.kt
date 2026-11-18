package senada.playback

import senada.model.Track

data class QueueItem(val id: Long, val track: Track)

class Queue private constructor(val entries: List<QueueItem>, private val nextId: Long) {
    constructor() : this(emptyList(), 1)

    fun append(track: Track): Queue =
        Queue(entries + QueueItem(nextId, track), nextId + 1)

    fun remove(entryId: Long): Queue =
        Queue(entries.filterNot { it.id == entryId }, nextId)
}
