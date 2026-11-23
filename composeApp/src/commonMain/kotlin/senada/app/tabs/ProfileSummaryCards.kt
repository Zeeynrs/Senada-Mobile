@file:OptIn(kotlin.time.ExperimentalTime::class)

package senada.app.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import senada.app.SenadaCard
import senada.app.UserProfile
import senada.app.shell.ProfileAvatar
import senada.app.shell.SenadaIcons
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette
import senada.app.theme.SenadaType
import senada.library.ListeningSummary

@Composable
internal fun ListenerCard(profile: UserProfile, photo: Any?, listeningSince: Long, onEdit: () -> Unit) {
    val date = Instant.fromEpochMilliseconds(listeningSince).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val labelStyle = SenadaType.monoLabel.copy(color = SenadaPalette.LightTextMuted)
    SenadaCard(color = colors.surfaceContainerHigh) {
        Box(
            Modifier.fillMaxWidth().drawWithCache {
                val label = measurer.measure(AnnotatedString("KARTU · SENADA · KARTU"), labelStyle, softWrap = false)
                val center = Offset(size.width + 2.dp.toPx(), size.height / 2)
                onDrawBehind {
                    rotate(90f, center) {
                        drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
                    }
                }
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileAvatar(profile.name, size = 84.dp, borderWidth = 0.dp, photoModel = photo)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoLabel("Kartu pendengar")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.name.isNotEmpty()) {
                            Text(
                                profile.name, Modifier.weight(1f, fill = false),
                                style = type.headlineSmall.copy(
                                    fontSize = 22.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.02).em,
                                ),
                            )
                        }
                        Box(
                            Modifier.size(32.dp).clickable(role = Role.Button, onClick = onEdit),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                SenadaIcons.Edit, "Ubah nama dan foto", Modifier.size(16.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Mendengar sejak ${chartDate(date, daily = true)} ${date.year}",
                        style = type.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileListeningCard(summary: ListeningSummary?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val minutes = (summary?.listenedMs ?: 0L) / 60_000
    SenadaCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val numbers: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileTimeValue(minutes / 60, "j")
                        VerticalDivider(Modifier.height(38.dp).width(1.dp), color = colors.outline)
                        ProfileTimeValue(minutes % 60, "m")
                    }
                }
                val caption: @Composable () -> Unit = {
                    Text(
                        "TOTAL WAKTU\nDENGAR · 30 HARI",
                        style = SenadaType.monoLabel.copy(lineHeight = 1.5.em),
                        textAlign = TextAlign.End,
                    )
                }
                if (maxWidth / LocalDensity.current.fontScale >= 285.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        numbers()
                        caption()
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.fillMaxWidth()) { numbers() }
                        caption()
                    }
                }
            }
            ProfileListeningChart(summary)
            HorizontalDivider(color = colors.outline)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 28.dp).clickable(role = Role.Button, onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Lihat statistik lengkap", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(SenadaIcons.ChevronRight, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileTimeValue(value: Long, unit: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            append(value.toString())
            withStyle(SpanStyle(color = muted, fontSize = 20.sp)) { append(unit) }
        },
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.05).em,
        ),
        maxLines = 1,
    )
}

@Composable
private fun ProfileListeningChart(summary: ListeningSummary?) {
    val bars = summary?.bars.orEmpty().takeLast(7)
    val max = bars.maxOfOrNull { it.listenedMs } ?: 0L
    val colors = MaterialTheme.colorScheme
    val description = "Grafik waktu dengar harian, 7 hari terakhir. " +
        if (max > 0L) "Total ${listeningMinutes(bars.sumOf { it.listenedMs })}." else "Belum ada waktu dengar."
    Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = description }) {
        if (max == 0L) return@Canvas
        val gap = 6.dp.toPx()
        val width = (size.width - 6 * gap) / 7
        for ((index, bar) in bars.withIndex()) {
            if (bar.listenedMs <= 0L) continue
            val height = size.height * (bar.listenedMs.toFloat() / max)
            drawRoundRect(
                color = if (index == bars.lastIndex) colors.primary else colors.outline,
                topLeft = Offset((index + 7 - bars.size) * (width + gap), size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
}

@Composable
internal fun ProfileRankings(
    summary: ListeningSummary?,
    artistPhoto: String?,
    onOpenArtist: () -> Unit,
    onOpenTrack: () -> Unit,
) {
    val artist = summary?.topArtists?.firstOrNull()
    val track = summary?.topTracks?.firstOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileRankingCard(Modifier.weight(1f), "Artis teratas", artist?.name, artist?.plays, onOpenArtist) {
            ProfileAvatar(
                artist?.name.orEmpty(), size = 52.dp, borderWidth = 0.dp, photoModel = artistPhoto,
                fallbackBackground = Color(0xFF8E7356),
            )
        }
        ProfileRankingCard(Modifier.weight(1f), "Lagu teratas", track?.track?.title, track?.plays, onOpenTrack) {
            CatalogArtwork(
                track?.track?.artworkUrl, RoundedCornerShape(10.dp), SenadaIcons.MusicNote, imageSize = 104,
                modifier = Modifier.size(52.dp), background = Color(0xFF5A2A22),
            )
        }
    }
}

@Composable
private fun ProfileRankingCard(
    modifier: Modifier,
    title: String,
    name: String?,
    plays: Int?,
    onOpen: () -> Unit,
    artwork: @Composable () -> Unit,
) {
    SenadaCard(modifier) {
        Column(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonoLabel(title)
            artwork()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name ?: "Belum ada",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 15.sp, lineHeight = TextUnit.Unspecified, letterSpacing = 0.em,
                    ),
                )
                if (plays != null) {
                    Text("$plays putaran", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
