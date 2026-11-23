package senada.app.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import senada.app.ArtistPickerDialog
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.LocalArtworkAccent
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.minTouchTarget
import senada.app.shell.NowPlayingPanelState
import senada.app.shell.PlatformBackHandler
import senada.app.shell.nowPlayingSwipe
import senada.app.theme.SenadaPalette
import senada.catalog.artistChoicesFor
import senada.catalog.canOpenArtist
import senada.catalog.youtube.resizeGoogleArtwork
import senada.lyrics.LyricsDocument
import senada.lyrics.LyricsPanel
import senada.lyrics.LyricsPanelResult
import senada.lyrics.activeLineIndex
import senada.lyrics.rememberLyrics
import senada.model.Track
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.SampleAudioStatus

private enum class PlayerSheet { Lyrics, Queue }

internal fun playerSp(size: Int, scale: Float) = (size * scale).sp

@Composable
internal fun NowPlayingScreen(
    state: PlaybackState,
    playback: PlaybackController,
    library: LibraryState,
    actions: LibraryActions,
    screenVisible: Boolean,
    onClose: () -> Unit,
    onOpenAudioLab: () -> Unit,
    panel: NowPlayingPanelState,
    immersive: Boolean,
    backdropFromCover: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val source by PlaySourceState.current.collectAsState()
    val track = if (state.audio.selection == null) state.queue.entries.firstOrNull()?.track else state.currentEntry?.track
    val accent = LocalArtworkAccent.current
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var lyricsAttempt by remember(track?.id) { mutableStateOf(0) }
    val lyrics = rememberLyrics(track, SenadaSources.lyrics, screenVisible && immersive, lyricsAttempt)
    val synced = (lyrics as? LyricsPanelResult.Ready)?.document as? LyricsDocument.Synced
    val activeLine = synced?.lines?.let { it.getOrNull(activeLineIndex(it, state.audio.positionMs)) }
        ?.text?.takeIf { it.isNotBlank() }

    if (immersive) NowPlayingSystemBars()
    PlatformBackHandler(enabled = immersive && sheet != null) { sheet = null }
    MaterialTheme(colorScheme = nowPlayingColorScheme(accent)) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val scale = maxWidth.value / 390f
                val density = LocalDensity.current
                val insets = WindowInsets.safeDrawing
                val safeTop = with(density) { insets.getTop(this).toDp() }
                val safeBottom = with(density) { insets.getBottom(this).toDp() }
                val backdrop = remember(accent, backdropFromCover, scale) { PlayerBackdrop(accent, backdropFromCover, scale) }
                NowPlayingBackdrop(backdrop)
                CompositionLocalProvider(LocalPlayerBackdrop provides backdrop) {
                    val currentSheet = sheet
                    if (currentSheet == null) {
                        PlayerMainContent(
                            state = state, playback = playback, track = track, library = library, actions = actions,
                            sourceLabel = source.playerLabel().takeIf { track != null },
                            activeLine = activeLine, accent = accent, panel = panel, onClose = onClose,
                            onLyrics = { sheet = PlayerSheet.Lyrics }, onQueue = { sheet = PlayerSheet.Queue },
                            onOpenAudioLab = onOpenAudioLab, scale = scale, viewportHeight = maxHeight,
                            safeTop = safeTop, safeBottom = safeBottom,
                        )
                    } else {
                        val isLyrics = currentSheet == PlayerSheet.Lyrics
                        PlayerSheetContent(
                            title = if (isLyrics) "Lirik" else "Antrean",
                            closeLabel = if (isLyrics) "Tutup lirik" else "Tutup antrean",
                            onClose = { sheet = null },
                        ) {
                            if (isLyrics) {
                                LyricsPanel(track, lyrics, state.audio.positionMs) { lyricsAttempt++ }
                            } else {
                                PlayerQueue(state, playback, library, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerMainContent(
    state: PlaybackState,
    playback: PlaybackController,
    track: Track?,
    library: LibraryState,
    actions: LibraryActions,
    sourceLabel: String?,
    activeLine: String?,
    accent: Color?,
    panel: NowPlayingPanelState,
    onClose: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onOpenAudioLab: () -> Unit,
    scale: Float,
    viewportHeight: Dp,
    safeTop: Dp,
    safeBottom: Dp,
) {
    val scroll = rememberScrollState()
    val timer by playback.sleepTimerState.collectAsState()
    val coverSize = (342.dp * scale).coerceAtMost(342.dp)
    val inset = 24.dp * scale
    val glassTouchInset = ((48.dp - 44.dp * scale) / 2).coerceAtLeast(0.dp)
    val bottom = maxOf(safeBottom, 34.dp * scale - glassTouchInset)
    Column(
        Modifier.fillMaxWidth().verticalScroll(scroll)
            .nowPlayingSwipe(panel, opening = false) { !scroll.canScrollBackward }
            .heightIn(min = viewportHeight),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.fillMaxWidth()) {
            PlayerHeader(sourceLabel ?: "Sedang diputar", scale, safeTop, onClose)
            Cover(track?.artworkUrl, accent, coverSize, scale)
            Spacer(Modifier.height(372.dp * scale - coverSize))
            PlayerTrack(
                state, playback, track, library, actions, onOpenAudioLab, scale,
                Modifier.padding(start = inset, end = (inset - glassTouchInset).coerceAtLeast(0.dp)),
            )
            Spacer(Modifier.height(4.dp * scale))
            Column(Modifier.padding(horizontal = inset)) {
                ActiveLyric(activeLine, scale, onLyrics)
                NowPlayingPosition(state.audio, scale, playback::seekTo)
                Spacer(Modifier.height((70.dp * scale - (48.dp * scale).coerceAtLeast(48.dp)).coerceAtLeast(0.dp)))
                NowPlayingControls(state, playback, scale)
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = (32.dp * scale - glassTouchInset).coerceAtLeast(0.dp))
                .padding(top = 28.dp * scale, bottom = bottom),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerGlassButton(NowPlayingIcons.Lyrics, "Lirik", scale, onClick = onLyrics)
            SleepTimerButton(timer, state.audio.canStopAfterTrack, playback, scale)
            PlayerGlassButton(NowPlayingIcons.AudioLab, "Audio Lab", scale, onClick = onOpenAudioLab)
            PlayerGlassButton(NowPlayingIcons.Queue, "Antrean", scale, onClick = onQueue)
        }
    }
}

@Composable
private fun PlayerHeader(sourceLabel: String, scale: Float, safeTop: Dp, onClose: () -> Unit) {
    val touch = (48.dp * scale).coerceAtLeast(48.dp)
    val gripHeight = 5.dp * scale
    val gripInset = (touch - gripHeight) / 2
    val top = maxOf(50.dp * scale - gripInset, safeTop)
    Box(Modifier.fillMaxWidth().heightIn(min = 112.dp * scale).padding(top = top)) {
        Column(
            Modifier.fillMaxWidth().padding(top = gripInset + 15.dp * scale)
                .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                sourceLabel,
                fontSize = playerSp(13, scale), lineHeight = playerSp(17, scale), fontWeight = FontWeight.SemiBold,
                color = PlayerWhite.copy(alpha = .85f), textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).size(touch)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = "Tutup" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp * scale, gripHeight)
                    .background(Color.White.copy(alpha = .45f), RoundedCornerShape(3.dp * scale)),
            )
        }
    }
}

@Composable
private fun PlayerTrack(
    state: PlaybackState,
    playback: PlaybackController,
    track: Track?,
    library: LibraryState,
    actions: LibraryActions,
    onOpenAudioLab: () -> Unit,
    scale: Float,
    modifier: Modifier,
) {
    val title = track?.title ?: state.audio.selection?.title ?: "Antrean kosong"
    val status = playbackStatus(state)
    val choices = remember(track, actions.canOpenArtists) {
        if (track != null && actions.canOpenArtists && canOpenArtist(track)) artistChoicesFor(track) else emptyList()
    }
    var pickingArtist by remember(track?.id) { mutableStateOf(false) }
    val actionGap = (54.dp * scale - (44.dp * scale).coerceAtLeast(48.dp)).coerceAtLeast(0.dp)
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(actionGap),
        ) {
            Column(Modifier.weight(1f).align(Alignment.Top)) {
                Text(
                    title, color = PlayerWhite, fontSize = playerSp(24, scale), lineHeight = playerSp(30, scale),
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-.02).em,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics {
                        heading()
                        if (status != null) {
                            contentDescription = "$title, $status"
                            liveRegion = LiveRegionMode.Polite
                        }
                    },
                )
                if (track != null && track.artists.isNotEmpty()) {
                    val artistText = track.artists.joinToString()
                    Text(
                        artistText, color = PlayerWhite.copy(alpha = .62f),
                        fontSize = playerSp(19, scale), lineHeight = playerSp(24, scale), fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (choices.isEmpty()) Modifier else Modifier
                            .clickable(onClickLabel = "Buka artis", role = Role.Button) {
                                if (choices.size == 1) actions.openArtist(track, choices.first())
                                else pickingArtist = true
                            }
                            .semantics { contentDescription = "Buka artis $artistText" },
                    )
                }
            }
            val favorite = track != null && track.id in library.favoriteIds
            PlayerGlassButton(
                if (favorite) NowPlayingIcons.HeartFilled else NowPlayingIcons.Heart,
                if (favorite) "Hapus dari favorit" else "Tambahkan ke favorit",
                scale, enabled = track != null, tint = if (favorite) SenadaPalette.Favorite else PlayerWhite,
            ) { if (track != null) actions.toggleFavorite(track) }
            PlayerTrackMenu(track, library, actions, state, playback, onOpenAudioLab, scale)
        }
        if (state.audio.status == SampleAudioStatus.Failed && status != null) {
            Text(status, color = PlayerWhite, fontSize = playerSp(13, scale))
        }
    }
    if (pickingArtist && track != null) {
        ArtistPickerDialog(
            artists = choices,
            onPick = {
                pickingArtist = false
                actions.openArtist(track, it)
            },
            onDismiss = { pickingArtist = false },
        )
    }
}

@Composable
private fun PlayerTrackMenu(
    track: Track?, library: LibraryState, actions: LibraryActions,
    state: PlaybackState, playback: PlaybackController, onOpenAudioLab: () -> Unit, scale: Float,
) {
    val trigger: @Composable (() -> Unit) -> Unit = { open ->
        PlayerGlassButton(NowPlayingIcons.More, "Menu lainnya", scale, onClick = open)
    }
    val playbackItems: @Composable (() -> Unit) -> Unit = { close ->
        PlayerPlaybackMenu(state, playback, close)
    }
    val audioLabItem: @Composable (() -> Unit) -> Unit = { close ->
        DropdownMenuItem(
            text = { Text("Buka Audio Lab") },
            onClick = {
                close()
                onOpenAudioLab()
            },
            modifier = Modifier.minTouchTarget(),
        )
    }
    if (track != null) {
        TrackMenu(
            track, library, actions,
            trigger = trigger,
            leadingItems = playbackItems,
        ) { close -> audioLabItem(close) }
    } else {
        var expanded by remember { mutableStateOf(false) }
        Box {
            trigger { expanded = true }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                playbackItems { expanded = false }
                audioLabItem { expanded = false }
            }
        }
    }
}

@Composable
private fun ActiveLyric(line: String?, scale: Float, onClick: () -> Unit) {
    val target = (48.dp * scale).coerceAtLeast(48.dp)
    if (line == null) {
        Spacer(Modifier.height(target))
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = target)
                .clickable(onClickLabel = "Buka lirik penuh", role = Role.Button, onClick = onClick)
                .padding(top = 14.dp * scale, bottom = 4.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
        ) {
            Text(
                line, fontSize = playerSp(15, scale), lineHeight = playerSp(20, scale), fontWeight = FontWeight.Bold,
                color = PlayerWhite.copy(alpha = .90f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(NowPlayingIcons.Chevron, null, Modifier.padding(top = 3.dp * scale).size(14.dp * scale),
                tint = PlayerWhite.copy(alpha = .90f))
        }
    }
}

@Composable
private fun Cover(url: String?, accent: Color?, size: Dp, scale: Float) {
    val glow = LocalPlayerBackdrop.current.glow
    val backdrop = if (accent == null) Color(0xFF3A1C10) else Color(glow.red * .32f, glow.green * .32f, glow.blue * .32f)
    val shape = RoundedCornerShape(20.dp * scale)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .coverShadow(scale)
                .clip(shape)
                .background(backdrop),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val unit = this.size.width / 342f
                drawCircle(glow, radius = 85f * unit, center = Offset(181f * unit, 145f * unit))
                drawRect(Color.Black.copy(alpha = .25f), Offset(0f, 232f * unit), Size(this.size.width, 110f * unit))
            }
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url) { resizeGoogleArtwork(url, 960) },
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// Sampel Gaussian untuk box-shadow 0 30px 60px rgba(0,0,0,.45).
// Gambar dari luar ke dalam agar alpha bertambah sesuai kurva tanpa bitmap sebesar sampul.
private val CoverShadowProfile = listOf(
    3f to .0013f, 2.5f to .0062f, 2f to .0228f, 1.5f to .0668f, 1f to .1587f,
    .5f to .3085f, 0f to .5f, -.5f to .6915f, -1f to .8413f, -1.5f to .9332f,
    -2f to .9772f, -2.5f to .9938f, -3f to .9987f,
)

private fun Modifier.coverShadow(scale: Float): Modifier = drawWithCache {
    val deviation = (30.dp * scale).toPx()
    val corner = (20.dp * scale).toPx()
    onDrawBehind {
        var previousAlpha = 0f
        for ((distance, coverage) in CoverShadowProfile) {
            val spread = distance * deviation
            val shadowSize = Size(size.width + 2f * spread, size.height + 2f * spread)
            if (shadowSize.width <= 0f || shadowSize.height <= 0f) break
            val opacity = .45f * coverage
            drawRoundRect(
                color = Color.Black.copy(alpha = (opacity - previousAlpha) / (1f - previousAlpha)),
                topLeft = Offset(-spread, deviation - spread),
                size = shadowSize,
                cornerRadius = CornerRadius((corner + spread).coerceAtLeast(0f)),
            )
            previousAlpha = opacity
        }
    }
}

/** Ikon sistem mengikuti latar gelap selama layar pemutar terbuka. */
@Composable
internal expect fun NowPlayingSystemBars()
