@file:OptIn(kotlin.time.ExperimentalTime::class)

package senada.library

import app.cash.sqldelight.coroutines.asFlow
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import senada.model.Track

internal data class ListenEvent(val id: String, val track: Track, val startedAt: Long, val listenedMs: Long)

internal enum class ListenPeriod(val label: String, private val days: Int? = null) {
    Week("7 hari", 7),
    Month("30 hari", 30),
    Year("Tahun ini"),
    All("Semua");

    val daily: Boolean get() = days != null

    fun start(now: Instant, zone: TimeZone): Instant {
        val today = now.toLocalDateTime(zone).date
        return when (this) {
            Week, Month -> today.minus(checkNotNull(days) - 1, DateTimeUnit.DAY).atStartOfDayIn(zone)
            Year -> LocalDate(today.year, 1, 1).atStartOfDayIn(zone)
            All -> Instant.DISTANT_PAST
        }
    }
}

internal data class ListeningBar(val date: LocalDate, val listenedMs: Long)
internal data class ListeningTrack(val track: Track, val plays: Int, val listenedMs: Long)
internal data class ListeningArtist(val name: String, val plays: Int, val listenedMs: Long)

internal data class ListeningSummary(
    val listenedMs: Long,
    val plays: Int,
    val distinctArtists: Int,
    val topTracks: List<ListeningTrack>,
    val topArtists: List<ListeningArtist>,
    val bars: List<ListeningBar>,
)

internal suspend fun LibraryRepository.recordListening(event: ListenEvent) = write {
    val track = event.track
    database.listeningQueries.saveListenEvent(
        event.id, track.id, track.title, encodeArtists(track.artists), track.durationMs,
        track.artworkUrl, track.source, event.startedAt, event.listenedMs,
    )
}

internal fun LibraryRepository.listeningStats(period: ListenPeriod): Flow<ListeningSummary> {
    val queries = database.listeningQueries
    return queries.listenEvents(0, 0).asFlow().map {
        withContext(dispatcher) {
            val current = Instant.fromEpochMilliseconds(now())
            val zone = TimeZone.currentSystemDefault()
            val cutoff = period.start(current, zone).toEpochMilliseconds()
            val events = queries.listenEvents(cutoff, current.toEpochMilliseconds()) {
                id, trackId, title, artists, duration, artwork, source, startedAt, listenedMs ->
                ListenEvent(id, trackOf(trackId, title, artists, duration, artwork, source), startedAt, listenedMs)
            }.executeAsList()
            summarizeListening(events, period, current, zone)
        }
    }
}

internal fun summarizeListening(
    events: List<ListenEvent>,
    period: ListenPeriod,
    now: Instant,
    zone: TimeZone,
): ListeningSummary {
    val cutoff = period.start(now, zone).toEpochMilliseconds()
    val today = now.toLocalDateTime(zone).date
    val inPeriod = events.filter { it.startedAt in cutoff..now.toEpochMilliseconds() }
    val songs = inPeriod.groupBy { it.track.id }.values.map { plays ->
        ListeningTrack(plays.maxBy { it.startedAt }.track, plays.size, plays.sumOf { it.listenedMs })
    }.sortedWith(compareByDescending<ListeningTrack> { it.plays }.thenByDescending { it.listenedMs }.thenBy { it.track.id })
    val artists = inPeriod.groupBy { it.track.artists.firstOrNull()?.trim().orEmpty() }
        .filterKeys { it.isNotEmpty() }.map { (name, plays) ->
            ListeningArtist(name, plays.size, plays.sumOf { it.listenedMs })
        }.sortedWith(compareByDescending<ListeningArtist> { it.plays }.thenByDescending { it.listenedMs }.thenBy { it.name })
    val buckets = mutableMapOf<LocalDate, Long>()
    for (event in inPeriod) {
        val date = Instant.fromEpochMilliseconds(event.startedAt).toLocalDateTime(zone).date
        val key = if (period.daily) date else LocalDate(date.year, date.month, 1)
        buckets[key] = (buckets[key] ?: 0L) + event.listenedMs
    }
    val end = if (period.daily) today else LocalDate(today.year, today.month, 1)
    val step = if (period.daily) DateTimeUnit.DAY else DateTimeUnit.MONTH
    val start = if (period == ListenPeriod.All) buckets.keys.minOrNull() ?: end
        else Instant.fromEpochMilliseconds(cutoff).toLocalDateTime(zone).date
    val bars = generateSequence(start) { date -> date.plus(1, step) }
        .takeWhile { it <= end }
        .map { ListeningBar(it, buckets[it] ?: 0L) }
        .toList()

    return ListeningSummary(inPeriod.sumOf { it.listenedMs }, inPeriod.size, artists.size, songs.take(10), artists.take(10), bars)
}
