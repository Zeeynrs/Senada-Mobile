package senada.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import senada.model.Track

@OptIn(ExperimentalTime::class)
class ListeningStatsTest {
    @Test
    fun day_and_year_boundaries_follow_the_device_calendar() {
        val zone = TimeZone.of("Asia/Jakarta")
        val now = Instant.parse("2026-12-31T18:30:00Z")
        assertEquals(Instant.parse("2026-12-31T17:00:00Z"), ListenPeriod.Year.start(now, zone))
        assertEquals(Instant.parse("2026-12-25T17:00:00Z"), ListenPeriod.Week.start(now, zone))
        assertEquals(Instant.parse("2026-12-02T17:00:00Z"), ListenPeriod.Month.start(now, zone))

        val dst = TimeZone.of("America/New_York")
        assertEquals(
            Instant.parse("2026-03-07T05:00:00Z"),
            ListenPeriod.Week.start(Instant.parse("2026-03-13T18:00:00Z"), dst),
        )
    }

    @Test
    fun fixture_rows_produce_totals_rankings_and_filled_day_buckets() {
        val zone = TimeZone.of("Asia/Jakarta")
        val now = Instant.parse("2026-11-22T10:00:00Z")
        val a = Track("yt:track:a", "Satu", listOf("A", "Tamu"), source = "ytmusic")
        val b = Track("yt:track:b", "Dua", listOf("B"), source = "ytmusic")
        val c = Track("yt:track:c", "Tiga", emptyList(), source = "ytmusic")
        fun row(key: String, track: Track, day: Int, ms: Long): ListenEvent =
            ListenEvent(key, track, LocalDate(2026, 11, day).atStartOfDayIn(zone).toEpochMilliseconds(), ms)
        val events = listOf(
            row("lama", b, 15, 500_000),
            row("pertama", a, 16, 35_000),
            row("kedua", a.copy(title = "Satu baru"), 21, 40_000),
            row("ketiga", b, 21, 90_000),
            row("keempat", c, 22, 20_000),
            row("mendatang", b, 23, 300_000),
        )
        val summary = summarizeListening(events, ListenPeriod.Week, now, zone)

        assertEquals(185_000L, summary.listenedMs)
        assertEquals(4, summary.plays)
        assertEquals(2, summary.distinctArtists)
        assertEquals(listOf("Satu baru", "Dua", "Tiga"), summary.topTracks.map { it.track.title })
        assertEquals(listOf(2, 1, 1), summary.topTracks.map { it.plays })
        assertEquals(listOf("A", "B"), summary.topArtists.map { it.name })
        assertEquals((16..22).toList(), summary.bars.map { it.date.day })
        assertEquals(listOf(35_000L, 0, 0, 0, 0, 130_000, 20_000), summary.bars.map { it.listenedMs })
    }

    @Test
    fun year_and_all_time_use_month_buckets_across_year_boundaries() {
        val track = Track("yt:track:x", "Lagu", listOf("A"), source = "ytmusic")
        val now = Instant.parse("2026-03-10T10:00:00Z")
        val events = listOf(
            ListenEvent("lama", track, Instant.parse("2025-12-20T10:00:00Z").toEpochMilliseconds(), 60_000),
            ListenEvent("baru", track, Instant.parse("2026-02-01T00:00:00Z").toEpochMilliseconds(), 90_000),
        )
        val year = summarizeListening(events, ListenPeriod.Year, now, TimeZone.UTC)
        assertEquals(listOf(0L, 90_000, 0), year.bars.map { it.listenedMs })
        assertEquals(1, year.plays)

        val all = summarizeListening(events, ListenPeriod.All, now, TimeZone.UTC)
        assertEquals(listOf(60_000L, 0, 90_000, 0), all.bars.map { it.listenedMs })
        assertEquals(LocalDate(2025, 12, 1), all.bars.first().date)
        assertTrue(all.bars.all { it.date.day == 1 })
    }
}
