package senada.app.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import senada.app.LibraryActions
import senada.app.SettingsCard
import senada.app.TrackRow
import senada.app.accessibleAccent
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.library.ListenPeriod
import senada.library.ListeningSummary

@Composable
internal fun ListeningStatsEntry(onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().minTouchTarget().semantics { role = Role.Button },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(SenadaIcons.Clock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Statistik dengar", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(SenadaIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun LazyListScope.listeningStatsScreen(
    summary: ListeningSummary?,
    period: ListenPeriod,
    favoriteIds: Set<String>,
    actions: LibraryActions,
    onPeriod: (ListenPeriod) -> Unit,
    onClose: () -> Unit,
) {
    item(key = "listening-header") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onClose,
                modifier = Modifier.minTouchTarget().semantics { contentDescription = "Kembali ke Beranda" },
            ) { Text("‹ Beranda", modifier = Modifier.clearAndSetSemantics {}) }
            Text(
                "Statistik dengar",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text("Disimpan di perangkat ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (option in ListenPeriod.entries) {
                    FilterChip(
                        selected = option == period,
                        onClick = { onPeriod(option) },
                        label = { Text(option.label) },
                        modifier = Modifier.minTouchTarget(),
                    )
                }
            }
        }
    }

    if (summary == null) {
        item(key = "listening-loading") { Text("Memuat statistik…") }
        return
    }
    item(key = "listening-totals") {
        SettingsCard("Ringkasan") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListeningTotal(listeningMinutes(summary.listenedMs), "Waktu dengar")
                ListeningTotal(summary.plays.toString(), "Kali memutar lagu")
                ListeningTotal(summary.distinctArtists.toString(), "Artis berbeda")
            }
        }
    }
    item(key = "listening-chart") { ListeningChart(summary, period) }
    if (summary.plays == 0) {
        item(key = "listening-empty") {
            Text("Belum ada pemutaran yang tercatat di periode ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    item(key = "listening-top-tracks") { ListeningHeading("Lagu teratas") }
    items(summary.topTracks, key = { "listening-track-${it.track.id}" }) { ranked ->
        val track = ranked.track
        TrackRow(track, favorite = track.id in favoriteIds) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${ranked.plays} kali", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { actions.playAll(listOf(track)) },
                    modifier = Modifier.minTouchTarget().semantics { contentDescription = "Putar ${track.title}" },
                ) { Text("Putar", modifier = Modifier.clearAndSetSemantics {}) }
            }
        }
    }

    item(key = "listening-top-artists") { ListeningHeading("Artis teratas") }
    if (summary.topArtists.isEmpty()) {
        item(key = "listening-no-artists") { Text("Belum ada nama artis yang tercatat.") }
    }
    items(summary.topArtists, key = { "listening-artist-${it.name}" }) { artist ->
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(artist.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${artist.plays} kali · ${listeningMinutes(artist.listenedMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ListeningHeading(label: String) {
    Text(label, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
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
    val bars = summary.bars
    val title = if (period.daily) "Menit per hari" else "Menit per bulan"
    val busiest = bars.maxByOrNull { it.listenedMs }?.takeIf { it.listenedMs > 0 }
    val description = if (busiest == null) "$title. Belum ada waktu dengar."
        else "$title. Terbanyak pada ${chartDate(busiest.date, period.daily)}, ${listeningMinutes(busiest.listenedMs)}. " +
            "Total ${listeningMinutes(summary.listenedMs)}."
    val colors = MaterialTheme.colorScheme
    val color = accessibleAccent(colors.primary, colors.surfaceContainer)
    val baseline = colors.onSurfaceVariant
    val maxMs = busiest?.listenedMs ?: 1L

    SettingsCard(title) {
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
                    chartDate(bars.first().date, period.daily),
                    modifier = Modifier.weight(1f).clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelMedium,
                )
                if (bars.size > 1) {
                    Text(
                        chartDate(bars.last().date, period.daily),
                        modifier = Modifier.weight(1f).clearAndSetSemantics {},
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

private val MonthNames = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")

private fun chartDate(date: LocalDate, daily: Boolean): String {
    val month = MonthNames[date.month.ordinal]
    return if (daily) "${date.day} $month" else "$month ${date.year}"
}

private fun listeningMinutes(ms: Long): String =
    if (ms in 1L..59_999L) "Kurang dari 1 menit" else "${ms / 60_000} menit"
