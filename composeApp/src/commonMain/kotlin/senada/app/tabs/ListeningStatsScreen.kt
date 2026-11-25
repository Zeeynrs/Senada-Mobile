package senada.app.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.datetime.LocalDate
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.LibraryActions
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.TrackRow
import senada.app.accessibleAccent
import senada.app.appPlural
import senada.app.appString
import senada.app.minTouchTarget
import senada.library.ListenPeriod
import senada.library.ListeningSummary
import senada.resources.Res
import senada.resources.a11y_play_track
import senada.resources.action_play
import senada.resources.minute_count
import senada.resources.month_apr
import senada.resources.month_aug
import senada.resources.month_dec
import senada.resources.month_feb
import senada.resources.month_jan
import senada.resources.month_jul
import senada.resources.month_jun
import senada.resources.month_mar
import senada.resources.month_may
import senada.resources.month_nov
import senada.resources.month_oct
import senada.resources.month_sep
import senada.resources.play_count
import senada.resources.stats_artist_summary
import senada.resources.stats_chart_busiest
import senada.resources.stats_chart_no_time
import senada.resources.stats_distinct_artists_title
import senada.resources.stats_empty_period
import senada.resources.stats_less_than_minute
import senada.resources.stats_loading
import senada.resources.stats_minutes_per_day
import senada.resources.stats_minutes_per_month
import senada.resources.stats_minutes_title
import senada.resources.stats_no_artists
import senada.resources.stats_plays_title
import senada.resources.stats_saved_on_device
import senada.resources.stats_summary_heading
import senada.resources.stats_top_artists
import senada.resources.stats_top_tracks

internal fun LazyListScope.listeningStatsScreen(
    summary: ListeningSummary?,
    period: ListenPeriod,
    favoriteIds: Set<String>,
    actions: LibraryActions,
    onPeriod: (ListenPeriod) -> Unit,
) {
    item(key = "listening-header") {
        val language = LocalAppLanguage.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(appString(Res.string.stats_saved_on_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in ListenPeriod.entries) {
                    FilterChip(
                        selected = option == period,
                        onClick = { onPeriod(option) },
                        label = { Text(option.label(language)) },
                        modifier = Modifier.minTouchTarget(),
                    )
                }
            }
        }
    }

    if (summary == null) {
        item(key = "listening-loading") { Text(appString(Res.string.stats_loading)) }
        return
    }
    item(key = "listening-totals") {
        val language = LocalAppLanguage.current
        StatsCard(appString(Res.string.stats_summary_heading)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListeningTotal(listeningMinutes(summary.listenedMs, language), appString(Res.string.stats_minutes_title))
                ListeningTotal(summary.plays.toString(), appString(Res.string.stats_plays_title))
                ListeningTotal(summary.distinctArtists.toString(), appString(Res.string.stats_distinct_artists_title))
            }
        }
    }
    item(key = "listening-chart") { ListeningChart(summary, period) }
    if (summary.plays == 0) {
        item(key = "listening-empty") {
            Text(appString(Res.string.stats_empty_period), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    item(key = "listening-top-tracks") { ListeningHeading(appString(Res.string.stats_top_tracks)) }
    items(summary.topTracks, key = { "listening-track-${it.track.id}" }) { ranked ->
        val track = ranked.track
        SenadaCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TrackRow(track, favorite = track.id in favoriteIds) {
                    val playDescription = appString(Res.string.a11y_play_track, track.title)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(appPlural(Res.plurals.play_count, ranked.plays, ranked.plays), style = MaterialTheme.typography.bodyMedium)
                        TextButton(
                            onClick = { actions.playAll(listOf(track)) },
                            modifier = Modifier.minTouchTarget().semantics { contentDescription = playDescription },
                        ) { Text(appString(Res.string.action_play), modifier = Modifier.clearAndSetSemantics {}) }
                    }
                }
            }
        }
    }

    item(key = "listening-top-artists") { ListeningHeading(appString(Res.string.stats_top_artists)) }
    if (summary.topArtists.isEmpty()) {
        item(key = "listening-no-artists") { Text(appString(Res.string.stats_no_artists)) }
    }
    items(summary.topArtists, key = { "listening-artist-${it.name}" }) { artist ->
        val language = LocalAppLanguage.current
        SenadaCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(artist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    appString(
                        Res.string.stats_artist_summary,
                        appPlural(Res.plurals.play_count, artist.plays, artist.plays),
                        listeningMinutes(artist.listenedMs, language),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ListeningHeading(label: String) {
    Text(
        label, Modifier.semantics { heading() },
        style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.02).em),
    )
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    SenadaCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ListeningHeading(title)
            content()
        }
    }
}

@Composable
private fun ListeningTotal(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ListeningChart(summary: ListeningSummary, period: ListenPeriod) {
    val language = LocalAppLanguage.current
    val bars = summary.bars
    val title = appString(if (period.daily) Res.string.stats_minutes_per_day else Res.string.stats_minutes_per_month)
    val busiest = bars.maxByOrNull { it.listenedMs }?.takeIf { it.listenedMs > 0 }
    val description = if (busiest == null) {
        appString(Res.string.stats_chart_no_time, title)
    } else {
        appString(
            Res.string.stats_chart_busiest,
            title,
            chartDate(busiest.date, period.daily, language),
            listeningMinutes(busiest.listenedMs, language),
            listeningMinutes(summary.listenedMs, language),
        )
    }
    val colors = MaterialTheme.colorScheme
    val color = accessibleAccent(colors.primary, colors.surfaceContainer)
    val baseline = colors.onSurfaceVariant
    val maxMs = busiest?.listenedMs ?: 1L

    StatsCard(title) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
            val slot = size.width / bars.size.coerceAtLeast(1)
            val width = (slot * 0.65f).coerceAtMost(28.dp.toPx())
            for ((index, bar) in bars.withIndex()) {
                if (bar.listenedMs == 0L) continue
                val height = (size.height * (bar.listenedMs.toFloat() / maxMs)).coerceAtLeast(2.dp.toPx())
                drawRect(color, Offset(slot * index + (slot - width) / 2, size.height - height), Size(width, height))
            }
            drawLine(baseline, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
        }
        if (bars.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    chartDate(bars.first().date, period.daily, language),
                    modifier = Modifier.weight(1f).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelMedium,
                )
                if (bars.size > 1) {
                    Text(
                        chartDate(bars.last().date, period.daily, language),
                        modifier = Modifier.weight(1f).clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private val MonthResources = listOf(
    Res.string.month_jan,
    Res.string.month_feb,
    Res.string.month_mar,
    Res.string.month_apr,
    Res.string.month_may,
    Res.string.month_jun,
    Res.string.month_jul,
    Res.string.month_aug,
    Res.string.month_sep,
    Res.string.month_oct,
    Res.string.month_nov,
    Res.string.month_dec,
)

internal fun chartDate(
    date: LocalDate,
    daily: Boolean,
    language: AppLanguage = AppStrings.currentLanguage,
): String {
    val month = AppStrings.get(MonthResources[date.month.ordinal], language)
    return if (daily) "${date.day} $month" else "$month ${date.year}"
}

internal fun listeningMinutes(
    ms: Long,
    language: AppLanguage = AppStrings.currentLanguage,
): String = if (ms in 1L..59_999L) {
    AppStrings.get(Res.string.stats_less_than_minute, language)
} else {
    val minutes = (ms / 60_000).toInt()
    AppStrings.plural(Res.plurals.minute_count, minutes, language, minutes)
}
