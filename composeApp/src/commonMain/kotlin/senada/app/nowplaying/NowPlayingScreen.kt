package senada.app.nowplaying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.LocalArtworkAccent
import senada.app.PlaybackProgress
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.shell.SenadaIcons
import senada.app.theme.SenadaPalette
import senada.catalog.youtube.resizeGoogleArtwork
import senada.lyrics.LyricsPanel
import senada.playback.PlaybackController
import senada.playback.PlaybackState

@Composable
internal fun NowPlayingScreen(
    state: PlaybackState,
    playback: PlaybackController,
    library: LibraryState,
    actions: LibraryActions,
    screenVisible: Boolean,
    onClose: () -> Unit,
) {
    val source by PlaySourceState.current.collectAsState()
    val track = if (state.audio.selection == null) state.queue.entries.firstOrNull()?.track else state.currentEntry?.track
    val accent = LocalArtworkAccent.current
    var lyricsSelected by rememberSaveable { mutableStateOf(false) }
    NowPlayingSystemBars()
    MaterialTheme(colorScheme = nowPlayingColorScheme(accent)) {
        val colors = MaterialTheme.colorScheme
        val wash = (accent ?: colors.primary).copy(alpha = 0.24f).compositeOver(colors.background)
        Surface(Modifier.fillMaxSize(), color = colors.background) {
            BoxWithConstraints(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(wash, colors.background, colors.background)))
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                val coverSize = minOf(maxWidth - 40.dp, maxHeight * 0.40f, 420.dp)
                Column(Modifier.fillMaxSize()) {
                    PlayerHeader(
                        sourceLabel = source.label().takeIf { track != null },
                        onClose = onClose,
                        menu = {
                            if (track != null) TrackMenu(track, library, actions)
                            else PlayerIcon(SenadaIcons.More, "Menu lagu", enabled = false) {}
                        },
                    )
                    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                        item("cover") { Cover(track?.artworkUrl, coverSize) }
                        item("track") {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track?.title ?: state.audio.selection?.title ?: "Antrean kosong",
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                    if (track != null && track.artists.isNotEmpty()) {
                                        Text(track.artists.joinToString(), color = colors.onSurfaceVariant)
                                    }
                                }
                                if (track != null) {
                                    val favorite = track.id in library.favoriteIds
                                    PlayerIcon(
                                        if (favorite) SenadaIcons.HeartFilled else SenadaIcons.Heart,
                                        if (favorite) "Hapus dari favorit" else "Tambah ke favorit",
                                        tint = if (favorite) SenadaPalette.Favorite else colors.onSurface,
                                    ) { actions.toggleFavorite(track) }
                                }
                            }
                            PlaybackStatus(state)
                        }
                        item("progress") { PlaybackProgress(state.audio, playback::seekTo) }
                        item("controls") {
                            Spacer(Modifier.height(8.dp))
                            NowPlayingControls(state, playback)
                            Spacer(Modifier.height(20.dp))
                        }
                        item("panels") {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                                    .background(colors.surfaceContainer)
                                    .padding(8.dp),
                            ) {
                                listOf("Lirik", "Antrean").forEachIndexed { index, label ->
                                    val showLyrics = index == 0
                                    val selected = lyricsSelected == showLyrics
                                    Tab(
                                        selected = selected,
                                        onClick = { lyricsSelected = showLyrics },
                                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.extraLarge)
                                            .background(if (selected) colors.surfaceContainerHighest else Color.Transparent),
                                        selectedContentColor = colors.onSurface,
                                        unselectedContentColor = colors.onSurfaceVariant,
                                        text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                                    )
                                }
                            }
                        }
                        if (lyricsSelected || state.queue.entries.isEmpty()) {
                            item("panel-body") {
                                Column(panelBody(last = true)) {
                                    if (!lyricsSelected) Text("Antrean kosong.")
                                    else if (track == null) Text("Lirik tidak tersedia")
                                    else if (screenVisible) LyricsPanel(track, state.audio.positionMs, SenadaSources.lyrics)
                                }
                            }
                        } else {
                            itemsIndexed(state.queue.entries, key = { _, entry -> "entry-${entry.id}" }) { index, entry ->
                                Box(panelBody(last = index == state.queue.entries.lastIndex)) {
                                    val playing = state.audio.isActive && state.audio.selection?.queueEntryId == entry.id
                                    TrackRow(entry.track, favorite = entry.track.id in library.favoriteIds) {
                                        PlayerIcon(
                                            if (playing) SenadaIcons.Pause else SenadaIcons.Play,
                                            if (playing) "Jeda ${entry.track.title}" else "Putar ${entry.track.title}",
                                        ) { if (playing) playback.pause() else playback.playEntry(entry.id) }
                                        PlayerIcon(SenadaIcons.Close, "Hapus ${entry.track.title} dari antrean") {
                                            playback.remove(entry.id)
                                        }
                                        TrackMenu(entry.track, library, actions)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ikon sistem mengikuti latar gelap selama layar pemutar terbuka. */
@Composable
internal expect fun NowPlayingSystemBars()

@Composable
private fun panelBody(last: Boolean): Modifier {
    val shape = RoundedCornerShape(bottomStart = if (last) 24.dp else 0.dp, bottomEnd = if (last) 24.dp else 0.dp)
    return Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, shape)
        .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = if (last) 12.dp else 4.dp)
}

@Composable
private fun PlayerHeader(sourceLabel: String?, onClose: () -> Unit, menu: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pointerInput(onClose) {
            var distance = 0f
            detectVerticalDragGestures(
                onDragStart = { distance = 0f },
                onVerticalDrag = { _, dy -> distance += dy },
                onDragEnd = { if (distance >= 48.dp.toPx()) onClose() },
            )
        }.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerIcon(SenadaIcons.ChevronDown, "Tutup", onClick = onClose)
        Text(
            sourceLabel ?: "Sedang diputar",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        menu()
    }
}

@Composable
private fun Cover(url: String?, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHigh))),
            contentAlignment = Alignment.Center,
        ) {
            // Piringan tetap terlihat saat lagu tidak bersampul atau gambar belum dimuat.
            Canvas(Modifier.fillMaxSize(0.74f)) {
                val radius = this.size.minDimension / 2f
                drawCircle(colors.background, radius)
                for (ring in 4..10) {
                    drawCircle(colors.surfaceContainerHigh, radius * ring / 11f, style = Stroke(1.dp.toPx()))
                }
                drawCircle(colors.primary, radius * 0.30f)
                drawCircle(colors.background, radius * 0.05f)
            }
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = remember(url) { resizeGoogleArtwork(url, 960) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
