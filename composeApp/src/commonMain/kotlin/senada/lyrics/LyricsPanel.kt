package senada.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.LocalAppLanguage
import senada.app.UiText
import senada.app.appString
import senada.app.minTouchTarget
import senada.app.theme.LocalSenadaLook
import senada.model.Track
import senada.playback.PlaybackController
import senada.resources.Res
import senada.resources.action_retry
import senada.resources.lyrics_active_line
import senada.resources.lyrics_instrumental
import senada.resources.lyrics_loading
import senada.resources.lyrics_seek_line
import senada.resources.lyrics_unavailable

internal sealed interface LyricsPanelResult {
    data object Loading : LyricsPanelResult
    data class Ready(val document: LyricsDocument) : LyricsPanelResult
    data class Error(val uiText: UiText) : LyricsPanelResult {
        constructor(message: String) : this(UiText.Raw(message))

        fun message(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

        val message: String get() = message()
    }
}

internal suspend fun loadLyrics(track: Track, fetch: suspend (Track) -> LyricsDocument): LyricsPanelResult {
    val result = try {
        LyricsPanelResult.Ready(fetch(track))
    } catch (e: LyricsException) {
        LyricsPanelResult.Error(e.uiText)
    }
    // Balasan yang baru tiba setelah panel ditutup atau lagu diganti harus diabaikan.
    currentCoroutineContext().ensureActive()
    return result
}

@Composable
internal fun rememberLyrics(
    track: Track?,
    repository: LyricsRepository,
    enabled: Boolean,
    attempt: Int,
    offlineLyrics: (suspend (Track) -> LyricsDocument?)? = null,
    showRomanization: Boolean = false,
): LyricsPanelResult {
    if (track == null) return LyricsPanelResult.Ready(LyricsDocument.Unavailable)
    var result by remember(track.id, attempt) { mutableStateOf<LyricsPanelResult>(LyricsPanelResult.Loading) }
    LaunchedEffect(track.id, repository, enabled, attempt, offlineLyrics) {
        if (enabled && result == LyricsPanelResult.Loading) {
            result = loadLyrics(track) { offlineLyrics?.invoke(it) ?: repository.get(it) }
        }
    }
    val original = (result as? LyricsPanelResult.Ready)?.document
    var withRomanization by remember(track.id, original) { mutableStateOf<LyricsDocument?>(null) }
    LaunchedEffect(track.id, enabled, showRomanization, original, repository) {
        if (enabled && showRomanization && original != null && withRomanization == null) {
            withRomanization = repository.withRomanization(track, original).takeIf { it != original }
        }
    }
    return withRomanization?.let(LyricsPanelResult::Ready) ?: result
}

@Composable
internal fun LyricsPanel(
    track: Track?,
    shown: LyricsPanelResult,
    playback: PlaybackController,
    appearance: LyricsAppearance,
    showTrackHeader: Boolean = true,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTrackHeader && track != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    track.title, color = Color.White, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (track.artists.isNotEmpty()) {
                    Text(
                        track.artists.joinToString(), color = Color.White.copy(alpha = .65f), fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val language = LocalAppLanguage.current
        when (shown) {
            LyricsPanelResult.Loading -> LyricsMessage(appString(Res.string.lyrics_loading))
            is LyricsPanelResult.Error -> {
                LyricsMessage(shown.message(language))
                TextButton(onClick = onRetry, modifier = Modifier.minTouchTarget()) {
                    Text(appString(Res.string.action_retry))
                }
            }
            is LyricsPanelResult.Ready -> key(track?.id) {
                when (val document = shown.document) {
                    is LyricsDocument.Synced -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        SyncedLyrics(document.lines, playback, appearance, maxHeight * .34f, maxHeight * .58f)
                    }
                    is LyricsDocument.Plain -> LazyColumn(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) {
                        itemsIndexed(document.lines) { index, line ->
                            LyricsLineText(
                                line, appearance, LyricLineState.Plain, baseSize = 24f,
                                romanization = document.romanizations[index],
                            )
                        }
                    }
                    LyricsDocument.Instrumental -> LyricsMessage(appString(Res.string.lyrics_instrumental))
                    LyricsDocument.Unavailable -> LyricsMessage(appString(Res.string.lyrics_unavailable))
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(message: String) {
    Text(message, color = Color.White, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

internal enum class LyricLineState { Plain, Upcoming, Past, Active }

@Composable
internal fun LyricsLineText(
    text: String,
    appearance: LyricsAppearance,
    state: LyricLineState,
    modifier: Modifier = Modifier,
    baseSize: Float = 28f,
    progress: Float? = null,
    sungEnd: Int? = null,
    romanization: String? = null,
) {
    val highlighting = (progress != null || sungEnd != null) && state == LyricLineState.Active &&
        appearance.highlight == LyricsHighlight.Karaoke
    val color = Color.White.copy(
        alpha = when (state) {
            LyricLineState.Plain -> .85f
            LyricLineState.Upcoming -> .36f
            LyricLineState.Past -> .42f
            LyricLineState.Active -> if (highlighting) .45f else 1f
        },
    )
    val highlightEnd = if (highlighting) sungEnd ?: sungTextEnd(text, progress ?: 0f) else 0
    val annotated = remember(text, highlightEnd) {
        buildAnnotatedString {
            append(text)
            if (highlightEnd > 0) addStyle(SpanStyle(color = Color.White), 0, highlightEnd)
        }
    }
    val glow = if (state == LyricLineState.Active && appearance.highlight == LyricsHighlight.Glow) {
        Shadow(
            color = MaterialTheme.colorScheme.primary.copy(alpha = .8f),
            blurRadius = with(LocalDensity.current) { 12.dp.toPx() },
        )
    } else {
        null
    }
    val size = baseSize * appearance.size.scale
    val font = if (appearance.font == LyricsFont.Serif) FontFamily.Serif else MaterialTheme.typography.bodyLarge.fontFamily
    val alignment = if (appearance.alignment == LyricsAlignment.Center) TextAlign.Center else TextAlign.Left
    val reading = remember(text, romanization, appearance.romanization) {
        if (appearance.romanization) lyricRomanization(text, romanization) else null
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            annotated, Modifier.fillMaxWidth(), color = color, fontSize = size.sp,
            lineHeight = (size * 1.25f).sp, fontWeight = FontWeight.Bold,
            fontFamily = font, textAlign = alignment, style = MaterialTheme.typography.bodyLarge.copy(shadow = glow),
        )
        if (reading != null) {
            val readingSize = (size * .65f).coerceAtLeast(12f)
            Text(
                reading, Modifier.fillMaxWidth(),
                color = Color.White.copy(alpha = if (state == LyricLineState.Active) .8f else color.alpha),
                fontSize = readingSize.sp, lineHeight = (readingSize * 1.25f).sp,
                fontFamily = font, textAlign = alignment,
            )
        }
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    playback: PlaybackController,
    appearance: LyricsAppearance,
    topPadding: Dp,
    bottomPadding: Dp,
) {
    val active = rememberActiveLyricIndex(lines, playback)
    val discontinuityVersion by remember(playback) {
        playback.state.map { it.audio.discontinuityVersion }.distinctUntilChanged()
    }.collectAsState(playback.state.value.audio.discontinuityVersion)
    val canSeek by remember(playback) {
        playback.state.map { it.audio.canSeek }.distinctUntilChanged()
    }.collectAsState(playback.state.value.audio.canSeek)
    val look = LocalSenadaLook.current
    val seekLineLabel = appString(Res.string.lyrics_seek_line)
    val activeLineLabel = appString(Res.string.lyrics_active_line)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = active.coerceAtLeast(0))
    LazyColumn(
        Modifier.fillMaxSize(), state = list,
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val current = index == active
            val displayText = line.text.ifBlank { "♪" }
            val sungEnd = if (current && appearance.highlight == LyricsHighlight.Karaoke) {
                rememberKaraokeSungEnd(playback, line, displayText, lines.getOrNull(index + 1)?.startMs)
            } else {
                null
            }
            LyricsLineText(
                text = displayText,
                appearance = appearance,
                state = when {
                    current -> LyricLineState.Active
                    index < active -> LyricLineState.Past
                    else -> LyricLineState.Upcoming
                },
                modifier = Modifier.heightIn(min = 48.dp)
                    .clickable(enabled = canSeek, role = Role.Button, onClickLabel = seekLineLabel) {
                        playback.seekTo(line.startMs)
                    }
                    .semantics {
                        selected = current
                        if (current) stateDescription = activeLineLabel
                    },
                sungEnd = sungEnd,
                romanization = line.romanization,
            )
        }
    }
    var lastScrollDiscontinuity by remember(lines, playback) { mutableStateOf(discontinuityVersion) }
    LaunchedEffect(active, discontinuityVersion) {
        val jumped = discontinuityVersion != lastScrollDiscontinuity
        lastScrollDiscontinuity = discontinuityVersion
        val target = active.coerceAtLeast(0)
        if (jumped) {
            list.scrollToItem(target)
        } else if (!list.isScrollInProgress) {
            if (look.durationMillis(220) == 0) list.scrollToItem(target)
            else list.animateScrollToItem(target)
        }
    }
}

@Composable
private fun rememberKaraokeSungEnd(
    playback: PlaybackController,
    line: LyricLine,
    displayText: String,
    nextStartMs: Long?,
): Int {
    val position by rememberSyncedPlaybackPositionMs(playback)
    return karaokeSungEnd(line, displayText, position, nextStartMs)
}
