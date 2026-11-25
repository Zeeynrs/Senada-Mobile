package senada.playback

import kotlin.random.Random
import senada.model.Track

data class QueueItem(val id: Long, val track: Track, internal val playlistKey: String? = null)

internal enum class RepeatMode { Off, All, One }

class Queue private constructor(
    val entries: List<QueueItem>,
    internal val nextId: Long,
    internal val repeatMode: RepeatMode,
    private val shuffledEntries: List<QueueItem>?,
) {
    constructor() : this(emptyList(), 1, RepeatMode.Off, null)

    internal val shuffleEnabled get() = shuffledEntries != null
    internal val playOrder get() = shuffledEntries ?: entries

    fun append(track: Track, activeEntryId: Long? = null, random: Random = Random.Default): Queue {
        return appendAll(listOf(track), activeEntryId = activeEntryId, random = random)
    }

    internal fun appendAll(
        tracks: List<Track>,
        activeEntryId: Long?,
        playlistKey: String? = null,
        random: Random = Random.Default,
    ): Queue {
        if (tracks.isEmpty()) return this
        val added = tracks.mapIndexed { index, track -> QueueItem(nextId + index, track, playlistKey) }
        val shuffled = shuffledEntries?.toMutableList()?.apply {
            val firstFree = indexOfFirst { it.id == activeEntryId } + 1
            for (item in added) add(random.nextInt(firstFree, size + 1), item)
        }
        return Queue(entries + added, nextId + added.size, repeatMode, shuffled)
    }

    fun remove(entryId: Long): Queue =
        Queue(entries.filterNot { it.id == entryId }, nextId, repeatMode, shuffledEntries?.filterNot { it.id == entryId })

    /** Memindahkan entri ber-ID [entryId] ke [toIndex] pada urutan putar aktif ([playOrder]). */
    fun move(entryId: Long, toIndex: Int): Queue {
        val current = playOrder
        val fromIndex = current.indexOfFirst { it.id == entryId }
        if (fromIndex < 0) return this
        val targetIndex = toIndex.coerceIn(0, current.lastIndex)
        if (fromIndex == targetIndex) return this
        val reordered = current.toMutableList().apply {
            add(targetIndex, removeAt(fromIndex))
        }
        return if (shuffledEntries != null) {
            Queue(entries, nextId, repeatMode, reordered)
        } else {
            Queue(reordered, nextId, repeatMode, null)
        }
    }

    internal fun withRepeatMode(mode: RepeatMode) = Queue(entries, nextId, mode, shuffledEntries)

    internal fun withShuffle(enabled: Boolean, activeEntryId: Long?, random: Random = Random.Default): Queue {
        if (enabled == shuffleEnabled) return this
        val shuffled = if (enabled) {
            val active = entries.firstOrNull { it.id == activeEntryId }
            listOfNotNull(active) + entries.filterNot { it.id == activeEntryId }.shuffled(random)
        } else null
        return Queue(entries, nextId, repeatMode, shuffled)
    }

    internal fun adjacentTo(entryId: Long?, offset: Int): QueueItem? {
        val order = playOrder
        val index = order.indexOfFirst { it.id == entryId }
        if (index < 0) return null
        val next = index + offset
        return if (repeatMode == RepeatMode.Off) order.getOrNull(next) else order[next.mod(order.size)]
    }

    internal fun afterEnd(entryId: Long?): QueueItem? = when (repeatMode) {
        RepeatMode.One -> entries.firstOrNull { it.id == entryId }
        else -> adjacentTo(entryId, 1)
    }

    internal companion object {
        fun restore(entries: List<QueueItem>, nextId: Long, repeatMode: RepeatMode, shuffleOrder: List<Long>?): Queue {
            require(nextId in 1L until Long.MAX_VALUE)
            val byId = entries.associateBy { it.id }
            require(byId.size == entries.size && byId.keys.all { it in 1L until nextId })
            val shuffled = shuffleOrder?.let { ids ->
                require(ids.size == entries.size && ids.toSet() == byId.keys)
                ids.map(byId::getValue)
            }
            return Queue(entries, nextId, repeatMode, shuffled)
        }
    }
}
