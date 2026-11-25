@file:OptIn(kotlin.time.ExperimentalTime::class)

package senada.app.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.semantics.role
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
import senada.app.LocalAppLanguage
import senada.app.SenadaCard
import senada.app.UserProfile
import senada.app.appPlural
import senada.app.appString
import senada.app.shell.ProfileAvatar
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaPalette
import senada.app.theme.SenadaType
import senada.library.ListeningSummary
import senada.resources.Res
import senada.resources.play_count
import senada.resources.profile_chart_a11y_empty
import senada.resources.profile_chart_a11y_prefix
import senada.resources.profile_chart_a11y_total
import senada.resources.profile_edit_name_photo_a11y
import senada.resources.profile_listening_since
import senada.resources.profile_none_yet
import senada.resources.profile_pass_stamp
import senada.resources.profile_pass_title
import senada.resources.profile_see_full_stats
import senada.resources.profile_top_artist_label
import senada.resources.profile_top_track_label
import senada.resources.profile_total_time_caption
import senada.resources.profile_unit_hour_short
import senada.resources.profile_unit_minute_short

@Composable
internal fun ListenerCard(profile: UserProfile, photo: Any?, listeningSince: Long, onEdit: () -> Unit) {
    val date = Instant.fromEpochMilliseconds(listeningSince).toLocalDateTime(TimeZone.currentSystemDefault()).date
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val labelStyle = SenadaType.monoLabel.copy(color = SenadaPalette.LightTextMuted)
    val stampText = appString(Res.string.profile_pass_stamp)
    val language = LocalAppLanguage.current
    SenadaCard(color = colors.surfaceContainerHigh) {
        Box(
            Modifier.fillMaxWidth().drawWithCache {
                val label = measurer.measure(AnnotatedString(stampText), labelStyle, softWrap = false)
                val center = Offset(size.width + 2.dp.toPx(), size.height / 2)
                onDrawBehind {
                    rotate(90f, center) {
                        drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
                    }
                }
            },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileAvatar(profile.name, size = 84.dp, borderWidth = 0.dp, photoModel = photo)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MonoLabel(appString(Res.string.profile_pass_title))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.name.isNotEmpty()) {
                            Text(
                                profile.name, Modifier.weight(1f, fill = false),
                                style = type.headlineSmall.copy(
                                    fontSize = 22.sp, lineHeight = TextUnit.Unspecified, letterSpacing = (-0.02).em,
                                ),
                                color = colors.onSurface,
                            )
                        }
                        Box(
                            Modifier.size(32.dp).clickable(role = Role.Button, onClick = onEdit),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                SenadaIcons.Edit, appString(Res.string.profile_edit_name_photo_a11y), Modifier.size(16.dp),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        appString(Res.string.profile_listening_since, chartDate(date, daily = true, language), date.year),
                        style = type.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold, lineHeight = TextUnit.Unspecified, letterSpacing = 0.em,
                        ),
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
        Column(Modifier.padding(1.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val hourShort = appString(Res.string.profile_unit_hour_short)
                val minShort = appString(Res.string.profile_unit_minute_short)
                val numbers: @Composable () -> Unit = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileTimeValue(minutes / 60, hourShort)
                        VerticalDivider(Modifier.height(38.dp).width(1.dp), color = colors.outline)
                        ProfileTimeValue(minutes % 60, minShort)
                    }
                }
                val caption: @Composable () -> Unit = {
                    Text(
                        appString(Res.string.profile_total_time_caption),
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
                    appString(Res.string.profile_see_full_stats), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge.copy(lineHeight = TextUnit.Unspecified, letterSpacing = 0.em),
                )
                Icon(SenadaIcons.ProfileChevron, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProfileTimeValue(value: Long, unit: String) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val numberLine = with(LocalDensity.current) { 48.sp.toDp() }
    Text(
        buildAnnotatedString {
            append(value.toString())
            withStyle(SpanStyle(color = muted, fontSize = 20.sp)) { append(unit) }
        },
        modifier = Modifier.height(numberLine).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = 48.sp, lineHeight = 48.sp, letterSpacing = (-0.05).em,
        ),
        maxLines = 1,
    )
}

@Composable
private fun ProfileListeningChart(summary: ListeningSummary?) {
    val dayCount = 7
    val bars = summary?.bars.orEmpty().takeLast(dayCount)
    val max = bars.maxOfOrNull { it.listenedMs } ?: 0L
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    val description = appString(Res.string.profile_chart_a11y_prefix) + " " +
        if (max > 0L) {
            appString(Res.string.profile_chart_a11y_total, listeningMinutes(bars.sumOf { it.listenedMs }, language))
        } else {
            appString(Res.string.profile_chart_a11y_empty)
        }
    Canvas(Modifier.fillMaxWidth().height(56.dp).semantics {
        contentDescription = description
        role = Role.Image
    }) {
        val gap = 6.dp.toPx()
        val width = (size.width - (dayCount - 1) * gap).coerceAtLeast(0f) / dayCount
        repeat(dayCount) { index ->
            val listenedMs = bars.getOrNull(index + bars.size - dayCount)?.listenedMs ?: 0L
            val height = (size.height * (listenedMs.toFloat() / max.coerceAtLeast(1L))).coerceAtLeast(4.dp.toPx())
            drawRoundRect(
                color = if (index == dayCount - 1) colors.primary else colors.outline,
                topLeft = Offset(index * (width + gap), size.height - height),
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
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileRankingCard(Modifier.weight(1f), appString(Res.string.profile_top_artist_label), artist?.name, artist?.plays, onOpenArtist) {
            ProfileAvatar(
                artist?.name.orEmpty(), size = 52.dp, borderWidth = 0.dp, photoModel = artistPhoto,
                fallbackBackground = Color(0xFF8E7356),
            )
        }
        ProfileRankingCard(Modifier.weight(1f), appString(Res.string.profile_top_track_label), track?.track?.title, track?.plays, onOpenTrack) {
            CatalogArtwork(
                track?.track?.artworkUrl, RoundedCornerShape(LocalSenadaLook.current.listCoverRadius), SenadaIcons.MusicNote, imageSize = 104,
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
    SenadaCard(modifier.fillMaxHeight()) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight().clickable(role = Role.Button, onClick = onOpen)
                .padding(1.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonoLabel(title)
            artwork()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    name ?: appString(Res.string.profile_none_yet),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 15.sp, lineHeight = TextUnit.Unspecified, letterSpacing = 0.em,
                    ),
                )
                if (plays != null) {
                    Text(
                        appPlural(Res.plurals.play_count, plays, plays),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = TextUnit.Unspecified, letterSpacing = 0.em),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
