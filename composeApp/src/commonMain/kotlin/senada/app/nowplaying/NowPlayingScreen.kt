package senada.app.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import senada.app.AppearancePreferences
import senada.app.FullViewTheme
import senada.app.ArtistPickerDialog
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.LocalAppLanguage
import senada.app.OverflowActionItem
import senada.app.OverflowActionSheet
import senada.app.rememberOfflineArtwork
import senada.app.LocalArtworkAccent
import senada.app.SenadaSources
import senada.app.TrackMenu
import senada.app.appString
import senada.resources.Res
import senada.resources.action_close
import senada.resources.action_more_options
import senada.resources.appearance_full_view_theme_title
import senada.resources.player_audiolab_button
import senada.resources.player_close_lyrics
import senada.resources.player_close_queue
import senada.resources.player_like
import senada.resources.player_lyrics_button
import senada.resources.player_now_playing
import senada.resources.player_open_artist
import senada.resources.player_open_artist_named
import senada.resources.player_open_audiolab
import senada.resources.player_open_full_lyrics
import senada.resources.player_queue_button
import senada.resources.player_unlike
import senada.resources.queue_empty
import senada.app.shell.NowPlayingPanelState
import senada.app.shell.PlatformBackHandler
import senada.app.shell.SenadaIcons
import senada.app.shell.backSwipe
import senada.app.shell.nowPlayingSwipe
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaLook
import senada.app.theme.SenadaPalette
import senada.catalog.artistChoicesFor
import senada.catalog.canOpenArtist
import senada.catalog.youtube.resizeGoogleArtwork
import senada.lyrics.LyricsAppearance
import senada.lyrics.LyricsDocument
import senada.lyrics.LyricsPanel
import senada.lyrics.LyricsPanelResult
import senada.lyrics.LyricsSettingsSheet
import senada.lyrics.rememberActiveLyricIndex
import senada.lyrics.rememberLyrics
import senada.lyrics.lyricRomanization
import senada.model.Track
import senada.playback.PlaybackController
import senada.playback.PlaybackState
import senada.playback.SampleAudioStatus

private enum class PlayerSheet { Lyrics, Queue }

internal const val PLAYER_SHEET_OPEN_MS = 220
internal const val PLAYER_SHEET_CLOSE_MS = 200

internal fun playerSp(size: Int, scale: Float) = (size * scale).sp

internal fun largeCoverShape(coverRadius: Dp): RoundedCornerShape =
    RoundedCornerShape(coverRadius.coerceAtLeast(0.dp))

internal fun playerSheetTransitionSpec(opening: Boolean, look: SenadaLook): ContentTransform {
    val duration = look.durationMillis(if (opening) PLAYER_SHEET_OPEN_MS else PLAYER_SHEET_CLOSE_MS)
    if (duration == 0) {
        return ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = ExitTransition.None,
            sizeTransform = null,
        )
    }
    return if (opening) {
        ContentTransform(
            targetContentEnter = fadeIn(tween(durationMillis = duration)) +
                slideInVertically(tween(durationMillis = duration, easing = FastOutSlowInEasing)) { it / 12 },
            initialContentExit = fadeOut(tween(durationMillis = duration)),
            targetContentZIndex = 1f,
            sizeTransform = null,
        )
    } else {
        ContentTransform(
            targetContentEnter = fadeIn(tween(durationMillis = duration)),
            initialContentExit = fadeOut(tween(durationMillis = duration)) +
                slideOutVertically(tween(durationMillis = duration, easing = FastOutSlowInEasing)) { it / 12 },
            targetContentZIndex = -1f,
            sizeTransform = null,
        )
    }
}

@Composable
internal fun NowPlayingScreen(
    state: PlaybackState,
    playback: PlaybackController,
    library: LibraryState,
    actions: LibraryActions,
    appearance: AppearancePreferences,
    screenVisible: Boolean,
    onClose: () -> Unit,
    onOpenAudioLab: () -> Unit,
    panel: NowPlayingPanelState,
    immersive: Boolean,
    backdropFromCover: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val source by PlaySourceState.current.collectAsState()
    val track = if (state.audio.selection == null) state.queue.playOrder.firstOrNull()?.track else state.currentEntry?.track
    val accent = LocalArtworkAccent.current
    var sheet by rememberSaveable { mutableStateOf<PlayerSheet?>(null) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var vinylLyricsSettingsOpen by rememberSaveable { mutableStateOf(false) }
    var lyricsAttempt by remember(track?.id) { mutableStateOf(0) }
    val downloads = actions.downloads
    val localLyrics = remember(downloads) { downloads?.let { it::lyrics } }
    val lyricsAppearance = appearance.settings?.lyricsAppearance ?: LyricsAppearance()
    val lyrics = rememberLyrics(
        track, SenadaSources.lyrics, screenVisible && immersive, lyricsAttempt, localLyrics,
        showRomanization = lyricsAppearance.romanization,
    )
    val coverUrl = rememberOfflineArtwork(track?.id, downloads) ?: track?.artworkUrl
    val synced = (lyrics as? LyricsPanelResult.Ready)?.document as? LyricsDocument.Synced
    val fullViewTheme = appearance.settings?.fullViewTheme ?: FullViewTheme.Standard
    val showThemePicker = { if (appearance.settings != null) themePickerOpen = true }
    val language = LocalAppLanguage.current
    val sourceLabel = source.label(language)

    if (immersive) NowPlayingSystemBars()
    val back: () -> Unit = { if (sheet == null) onClose() else sheet = null }
    PlatformBackHandler(enabled = immersive && sheet != null, onBack = back)
    MaterialTheme(colorScheme = nowPlayingColorScheme(accent)) {
        Surface(
            modifier = modifier.fillMaxSize().backSwipe(enabled = immersive, onBack = back),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                val scale = maxWidth.value / 390f
                val density = LocalDensity.current
                val insets = WindowInsets.safeDrawing
                val safeTop = with(density) { insets.getTop(this).toDp() }
                val safeBottom = with(density) { insets.getBottom(this).toDp() }
                val look = LocalSenadaLook.current
                val backdrop = rememberPlayerBackdrop(accent, backdropFromCover, scale)
                NowPlayingBackdrop(backdrop)
                CompositionLocalProvider(LocalPlayerBackdrop provides backdrop) {
                    AnimatedContent(
                        targetState = sheet,
                        transitionSpec = { playerSheetTransitionSpec(opening = targetState != null, look = look) },
                        modifier = Modifier.fillMaxSize(),
                    ) { currentSheet ->
                        if (currentSheet == null && fullViewTheme == FullViewTheme.Vinyl) {
                            VinylNowPlaying(
                                state = state, playback = playback, artwork = coverUrl,
                                visible = screenVisible && immersive && sheet == null,
                                safeTop = safeTop, safeBottom = safeBottom, panel = panel,
                                onClose = onClose, onTheme = showThemePicker,
                                onLyricsSettings = { vinylLyricsSettingsOpen = true },
                                onQueue = { sheet = PlayerSheet.Queue },
                                lyrics = {
                                    LyricsPanel(track, lyrics, playback, lyricsAppearance, showTrackHeader = false) {
                                        lyricsAttempt++
                                    }
                                },
                                trackInfo = { trackScale ->
                                    PlayerTrack(state, playback, track, library, actions, onOpenAudioLab, trackScale, Modifier)
                                },
                            )
                        } else if (currentSheet == null) {
                            PlayerMainContent(
                                state = state, playback = playback, track = track, library = library, actions = actions,
                                coverUrl = coverUrl,
                                sourceLabel = source.playerLabel().takeIf { track != null },
                                synced = synced, accent = accent, panel = panel, onClose = onClose,
                                onTheme = showThemePicker,
                                onLyrics = { sheet = PlayerSheet.Lyrics }, onQueue = { sheet = PlayerSheet.Queue },
                                onOpenAudioLab = onOpenAudioLab, scale = scale, viewportHeight = maxHeight,
                                lyricsAppearance = lyricsAppearance,
                                safeTop = safeTop, safeBottom = safeBottom,
                            )
                        } else {
                            val isLyrics = currentSheet == PlayerSheet.Lyrics
                            var settingsOpen by rememberSaveable { mutableStateOf(false) }
                            PlayerSheetContent(
                                title = appString(if (isLyrics) Res.string.player_lyrics_button else Res.string.player_queue_button),
                                closeLabel = appString(if (isLyrics) Res.string.player_close_lyrics else Res.string.player_close_queue),
                                onClose = { sheet = null },
                                onOpenSettings = if (isLyrics) ({ settingsOpen = true }) else null,
                            ) {
                                if (isLyrics) {
                                    LyricsPanel(track, lyrics, playback, lyricsAppearance) { lyricsAttempt++ }
                                } else {
                                    PlayerQueue(state, playback, library, actions)
                                }
                            }
                            if (immersive && isLyrics && settingsOpen) {
                                LyricsSettingsSheet(appearance) { settingsOpen = false }
                            }
                        }
                    }
                    if (immersive && themePickerOpen) {
                        FullViewThemeSheet(fullViewTheme, appearance::selectFullViewTheme) { themePickerOpen = false }
                    }
                    if (immersive && fullViewTheme == FullViewTheme.Vinyl && vinylLyricsSettingsOpen) {
                        LyricsSettingsSheet(appearance) { vinylLyricsSettingsOpen = false }
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
    coverUrl: String?,
    library: LibraryState,
    actions: LibraryActions,
    sourceLabel: String?,
    synced: LyricsDocument.Synced?,
    accent: Color?,
    panel: NowPlayingPanelState,
    onClose: () -> Unit,
    onTheme: () -> Unit,
    onLyrics: () -> Unit,
    onQueue: () -> Unit,
    onOpenAudioLab: () -> Unit,
    scale: Float,
    lyricsAppearance: LyricsAppearance,
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
            PlayerHeader(sourceLabel ?: appString(Res.string.player_now_playing), scale, safeTop, onClose, onTheme)
            Cover(coverUrl, accent, coverSize)
            Spacer(Modifier.height(372.dp * scale - coverSize))
            PlayerTrack(
                state, playback, track, library, actions, onOpenAudioLab, scale,
                Modifier.padding(start = inset, end = (inset - glassTouchInset).coerceAtLeast(0.dp)),
            )
            Spacer(Modifier.height(4.dp * scale))
            Column(Modifier.padding(horizontal = inset)) {
                PlayerActiveLyric(synced, playback, scale, lyricsAppearance, onLyrics)
                NowPlayingPosition(state.audio, playback, scale)
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
            PlayerGlassButton(NowPlayingIcons.Lyrics, appString(Res.string.player_lyrics_button), scale, onClick = onLyrics)
            SleepTimerButton(timer, state.audio.canStopAfterTrack, playback, scale)
            PlayerGlassButton(NowPlayingIcons.AudioLab, appString(Res.string.player_audiolab_button), scale, onClick = onOpenAudioLab)
            PlayerGlassButton(NowPlayingIcons.Queue, appString(Res.string.player_queue_button), scale, onClick = onQueue)
        }
    }
}

@Composable
private fun PlayerHeader(sourceLabel: String, scale: Float, safeTop: Dp, onClose: () -> Unit, onTheme: () -> Unit) {
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
        val closeLabel = appString(Res.string.action_close)
        val themeLabel = appString(Res.string.appearance_full_view_theme_title)
        Box(
            Modifier.align(Alignment.TopCenter).size(touch)
                .clickable(role = Role.Button, onClick = onClose)
                .semantics { contentDescription = closeLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(40.dp * scale, gripHeight)
                    .background(Color.White.copy(alpha = .45f), RoundedCornerShape(3.dp * scale)),
            )
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 14.dp)) {
            PlayerGlassButton(SenadaIcons.Appearance, themeLabel, scale = .85f, onClick = onTheme)
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
    val language = LocalAppLanguage.current
    val title = track?.title ?: state.audio.selection?.title ?: appString(Res.string.queue_empty)
    val status = playbackStatus(state, language)
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
                    val openArtistLabel = appString(Res.string.player_open_artist)
                    val openArtistNamed = appString(Res.string.player_open_artist_named, artistText)
                    Text(
                        artistText, color = PlayerWhite.copy(alpha = .62f),
                        fontSize = playerSp(19, scale), lineHeight = playerSp(24, scale), fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = if (choices.isEmpty()) Modifier else Modifier
                            .clickable(onClickLabel = openArtistLabel, role = Role.Button) {
                                if (choices.size == 1) actions.openArtist(track, choices.first())
                                else pickingArtist = true
                            }
                            .semantics { contentDescription = openArtistNamed },
                    )
                }
            }
            val favorite = track != null && track.id in library.favoriteIds
            PlayerGlassButton(
                if (favorite) NowPlayingIcons.HeartFilled else NowPlayingIcons.Heart,
                appString(if (favorite) Res.string.player_unlike else Res.string.player_like),
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
        PlayerGlassButton(NowPlayingIcons.More, appString(Res.string.action_more_options), scale, onClick = open)
    }
    val playbackItems: @Composable ((() -> Unit) -> Unit) -> Unit = { select ->
        PlayerPlaybackMenu(state, playback, select)
    }
    val audioLabItem: @Composable ((() -> Unit) -> Unit) -> Unit = { select ->
        OverflowActionItem(appString(Res.string.player_open_audiolab), SenadaIcons.AudioLab) { select(onOpenAudioLab) }
    }
    if (track != null) {
        TrackMenu(
            track, library, actions,
            trigger = trigger,
            leadingItems = playbackItems,
        ) { select -> audioLabItem(select) }
    } else {
        var expanded by remember { mutableStateOf(false) }
        Box {
            trigger { expanded = true }
            if (expanded) {
                OverflowActionSheet(
                    title = state.audio.selection?.title ?: appString(Res.string.player_now_playing),
                    onDismiss = { expanded = false },
                ) { select ->
                    playbackItems(select)
                    audioLabItem(select)
                }
            }
        }
    }
}

@Composable
private fun PlayerActiveLyric(
    synced: LyricsDocument.Synced?,
    playback: PlaybackController,
    scale: Float,
    appearance: LyricsAppearance,
    onClick: () -> Unit,
) {
    val line = if (synced != null) {
        val index = rememberActiveLyricIndex(synced.lines, playback)
        synced.lines.getOrNull(index)?.takeIf { it.text.isNotBlank() }
    } else {
        null
    }
    val reading = remember(line, appearance.romanization) {
        if (appearance.romanization) line?.let { lyricRomanization(it.text, it.romanization) } else null
    }
    ActiveLyric(line?.text, reading, scale, appearance.size.scale, onClick)
}

@Composable
private fun ActiveLyric(line: String?, reading: String?, scale: Float, textScale: Float, onClick: () -> Unit) {
    val target = (48.dp * scale).coerceAtLeast(48.dp)
    if (line == null) {
        Spacer(Modifier.height(target))
    } else {
        Row(
            Modifier.fillMaxWidth().heightIn(min = target)
                .clickable(onClickLabel = appString(Res.string.player_open_full_lyrics), role = Role.Button, onClick = onClick)
                .padding(top = 14.dp * scale, bottom = 4.dp * scale),
            horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    line, fontSize = (15f * scale * textScale).sp, lineHeight = (20f * scale * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = PlayerWhite.copy(alpha = .90f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (reading != null) {
                    Text(
                        reading, fontSize = (12f * scale * textScale).sp, lineHeight = (16f * scale * textScale).sp,
                        color = PlayerWhite.copy(alpha = .72f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(NowPlayingIcons.Chevron, null, Modifier.padding(top = 3.dp * scale).size(14.dp * scale),
                tint = PlayerWhite.copy(alpha = .90f))
        }
    }
}

@Composable
private fun Cover(url: String?, accent: Color?, size: Dp) {
    val backdropState = LocalPlayerBackdrop.current
    val shape = largeCoverShape(LocalSenadaLook.current.coverRadius)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .shadow(
                    elevation = 30.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = .45f),
                    spotColor = Color.Black.copy(alpha = .45f),
                )
                .clip(shape)
                .drawBehind {
                    val glow = backdropState.glow
                    val backdrop = if (accent == null) {
                        SenadaPalette.DarkSurfaceHigh
                    } else {
                        Color(glow.red * .32f, glow.green * .32f, glow.blue * .32f)
                    }
                    drawRect(backdrop)
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SenadaIcons.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = .35f),
                modifier = Modifier.size(size * .30f),
            )
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

/** Ikon sistem mengikuti latar gelap selama layar pemutar terbuka. */
@Composable
internal expect fun NowPlayingSystemBars()
