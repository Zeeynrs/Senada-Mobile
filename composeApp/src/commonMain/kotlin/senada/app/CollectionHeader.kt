package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import senada.app.shell.SenadaIcons
import senada.app.tabs.PlaylistSaveStatus
import senada.app.theme.LocalSenadaLook
import senada.app.theme.MonoLabel
import senada.app.theme.SenadaType
import senada.catalog.AlbumDetail
import senada.catalog.PlaylistDetail
import senada.catalog.youtube.resizeGoogleArtwork
import senada.model.Track
import senada.resources.Res
import senada.resources.a11y_open_menu_named
import senada.resources.a11y_pause_track
import senada.resources.a11y_play_track
import senada.resources.a11y_shuffle_play
import senada.resources.action_delete
import senada.resources.action_rename
import senada.resources.action_saved
import senada.resources.action_saving
import senada.resources.catalog_type_album
import senada.resources.collection_duration_hour_short
import senada.resources.collection_duration_min_short
import senada.resources.collection_duration_under_min
import senada.resources.collection_duration_under_min_short
import senada.resources.collection_enqueue_all
import senada.resources.collection_mix_radio
import senada.resources.collection_open_artist
import senada.resources.collection_save_to_library
import senada.resources.playlist_count
import senada.resources.playlist_delete_title
import senada.resources.profile_back_to
import senada.resources.a11y_hours
import senada.resources.a11y_minutes
import senada.resources.song_count

internal fun CollectionKind.labelOrFallback(): String = ""

/** Aksi yang dapat muncul di menu ⋯ header koleksi. */
internal enum class CollectionMenuAction {
    RenamePlaylist,
    DeletePlaylist,
    EnqueueAll,
    OpenArtist,
    SaveToLibrary,
}

/** Satu item pada menu ⋯ header koleksi beserta status aktifnya. */
internal data class CollectionMenuItem(
    val action: CollectionMenuAction,
    val label: String,
    val enabled: Boolean = true,
)

/** Jenis halaman koleksi untuk menentukan isi menu ⋯ di header bersama. */
internal sealed interface CollectionKind {
    data object LibraryPlaylist : CollectionKind
    data object Favorites : CollectionKind
    data class Album(val hasArtistId: Boolean) : CollectionKind
    data class YouTubePlaylist(
        val isMix: Boolean,
        val saveStatus: PlaylistSaveStatus? = null,
    ) : CollectionKind
}

/** Menyusun daftar item menu ⋯ sesuai jenis halaman koleksi. */
internal fun collectionMenuItems(
    kind: CollectionKind,
    hasTracks: Boolean,
    language: AppLanguage = AppStrings.currentLanguage,
): List<CollectionMenuItem> = when (kind) {
    CollectionKind.LibraryPlaylist -> listOf(
        CollectionMenuItem(CollectionMenuAction.RenamePlaylist, AppStrings.get(Res.string.action_rename, language)),
        CollectionMenuItem(CollectionMenuAction.DeletePlaylist, AppStrings.get(Res.string.playlist_delete_title, language).removeSuffix("?")),
    )
    CollectionKind.Favorites -> listOf(
        CollectionMenuItem(CollectionMenuAction.EnqueueAll, AppStrings.get(Res.string.collection_enqueue_all, language), enabled = hasTracks),
    )
    is CollectionKind.Album -> buildList {
        add(CollectionMenuItem(CollectionMenuAction.EnqueueAll, AppStrings.get(Res.string.collection_enqueue_all, language), enabled = hasTracks))
        if (kind.hasArtistId) {
            add(CollectionMenuItem(CollectionMenuAction.OpenArtist, AppStrings.get(Res.string.collection_open_artist, language)))
        }
    }
    is CollectionKind.YouTubePlaylist -> buildList {
        add(CollectionMenuItem(CollectionMenuAction.EnqueueAll, AppStrings.get(Res.string.collection_enqueue_all, language), enabled = hasTracks))
        if (!kind.isMix) {
            val saveLabel = when (kind.saveStatus) {
                is PlaylistSaveStatus.Saving -> AppStrings.get(Res.string.action_saving, language)
                is PlaylistSaveStatus.Saved -> AppStrings.get(Res.string.action_saved, language)
                is PlaylistSaveStatus.Failed, null -> AppStrings.get(Res.string.collection_save_to_library, language)
            }
            val canSave = hasTracks &&
                kind.saveStatus !is PlaylistSaveStatus.Saving &&
                kind.saveStatus !is PlaylistSaveStatus.Saved
            add(CollectionMenuItem(CollectionMenuAction.SaveToLibrary, saveLabel, enabled = canSave))
        }
    }
}

/** Teks tampilan baris meta beserta versi lisan untuk pembaca layar. */
internal data class CollectionMeta(
    val display: String,
    val spoken: String,
)

/** Menjumlahkan durasi lagu yang diketahui dan bernilai positif; null bila tidak ada. */
internal fun sumKnownDurationMs(tracks: List<Track>): Long? {
    var total = 0L
    var found = false
    for (track in tracks) {
        val duration = track.durationMs
        if (duration != null && duration > 0L) {
            total += duration
            found = true
        }
    }
    return total.takeIf { found && it > 0L }
}

/** Memformat total durasi koleksi untuk tampilan ringkas sesuai bahasa aktif. */
internal fun formatCollectionDuration(
    durationMs: Long,
    language: AppLanguage = AppStrings.currentLanguage,
): String? = formatDurationWithUnits(
    durationMs,
    hourUnit = { AppStrings.get(Res.string.collection_duration_hour_short, language) },
    minuteUnit = { AppStrings.get(Res.string.collection_duration_min_short, language) },
    underOneMinute = AppStrings.get(Res.string.collection_duration_under_min_short, language),
)

/** Memformat total durasi koleksi menjadi frasa penuh sesuai bahasa aktif untuk pembaca layar. */
internal fun spokenCollectionDuration(
    durationMs: Long,
    language: AppLanguage = AppStrings.currentLanguage,
): String? = formatDurationWithUnits(
    durationMs,
    hourUnit = { AppStrings.plural(Res.plurals.a11y_hours, it.toInt(), language, it.toInt()) },
    minuteUnit = { AppStrings.plural(Res.plurals.a11y_minutes, it.toInt(), language, it.toInt()) },
    underOneMinute = AppStrings.get(Res.string.collection_duration_under_min, language),
    includeNumberInUnit = true,
)

private fun formatDurationWithUnits(
    durationMs: Long,
    hourUnit: (Long) -> String,
    minuteUnit: (Long) -> String,
    underOneMinute: String,
    includeNumberInUnit: Boolean = false,
): String? {
    if (durationMs <= 0L) return null
    val totalMinutes = durationMs / 60_000L
    if (totalMinutes == 0L) return underOneMinute
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return buildList {
        if (hours > 0L) add(if (includeNumberInUnit) hourUnit(hours) else "$hours ${hourUnit(hours)}")
        if (minutes > 0L) add(if (includeNumberInUnit) minuteUnit(minutes) else "$minutes ${minuteUnit(minutes)}")
    }.joinToString(" ")
}

/** Menyusun baris meta koleksi dari bagian awal opsional, jumlah lagu, dan total durasi. */
internal fun collectionMeta(
    trackCount: Int,
    totalDurationMs: Long? = null,
    prefixParts: List<String> = emptyList(),
    countTextOverride: String? = null,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta {
    val cleanPrefix = prefixParts.map(String::trim).filter(String::isNotEmpty)
    val countText = countTextOverride?.trim()?.takeIf(String::isNotEmpty)
        ?: AppStrings.plural(Res.plurals.song_count, trackCount, language, trackCount)
    val durationDisplay = totalDurationMs?.let { formatCollectionDuration(it, language) }
    val durationSpoken = totalDurationMs?.let { spokenCollectionDuration(it, language) }
    return CollectionMeta(
        display = (cleanPrefix + listOfNotNull(countText, durationDisplay)).joinToString(" · "),
        spoken = (cleanPrefix + listOfNotNull(countText, durationSpoken)).joinToString(", "),
    )
}

internal fun playlistCollectionMeta(
    trackCount: Int,
    totalDurationMs: Long?,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta = collectionMeta(trackCount = trackCount, totalDurationMs = totalDurationMs, language = language)

internal fun favoritesCollectionMeta(
    trackCount: Int,
    totalDurationMs: Long?,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta = collectionMeta(trackCount = trackCount, totalDurationMs = totalDurationMs, language = language)

/** Menyusun baris meta jumlah playlist untuk header ringkas daftar playlist. */
internal fun playlistListMeta(
    playlistCount: Int,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta {
    val count = playlistCount.coerceAtLeast(0)
    val text = AppStrings.plural(Res.plurals.playlist_count, count, language, count)
    return CollectionMeta(display = text, spoken = text)
}

internal fun albumCollectionMeta(
    detail: AlbumDetail?,
    fallbackSubtitle: String?,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta? {
    if (detail == null) return fallbackMeta(fallbackSubtitle)
    val prefix = listOfNotNull(
        detail.typeLabel?.takeIf { it.isNotBlank() } ?: AppStrings.get(Res.string.catalog_type_album, language),
        detail.artists.filter { it.isNotBlank() }.joinToString().takeIf { it.isNotEmpty() },
        detail.year?.takeIf { it.isNotBlank() },
    )
    if (detail.tracks.isNotEmpty()) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = sumKnownDurationMs(detail.tracks),
            prefixParts = prefix,
            language = language,
        )
    }
    return prefix.takeIf { it.isNotEmpty() }?.let {
        CollectionMeta(display = it.joinToString(" · "), spoken = it.joinToString(", "))
    }
}

internal fun youTubePlaylistCollectionMeta(
    detail: PlaylistDetail?,
    isMix: Boolean,
    fallbackSubtitle: String?,
    language: AppLanguage = AppStrings.currentLanguage,
): CollectionMeta? {
    if (detail == null) return fallbackMeta(fallbackSubtitle)
    val prefix = listOfNotNull(
        AppStrings.get(Res.string.collection_mix_radio, language).takeIf { isMix },
        detail.owner?.takeIf { it.isNotBlank() },
    )
    if (!detail.truncated) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = sumKnownDurationMs(detail.tracks),
            prefixParts = prefix,
            language = language,
        )
    }
    val countText = detail.songCountText?.takeIf { it.isNotBlank() }
        ?: AppStrings.plural(Res.plurals.song_count, detail.tracks.size, language, detail.tracks.size)
            .takeIf { detail.tracks.isNotEmpty() }
    if (countText != null) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = null,
            prefixParts = prefix,
            countTextOverride = countText,
            language = language,
        )
    }
    return prefix.takeIf { it.isNotEmpty() }?.let {
        CollectionMeta(display = it.joinToString(" · "), spoken = it.joinToString(", "))
    }
}

private fun fallbackMeta(subtitle: String?): CollectionMeta? {
    val clean = subtitle?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val spoken = clean.split("•", "·").map(String::trim).filter(String::isNotEmpty).joinToString(", ")
    return CollectionMeta(display = clean, spoken = spoken.ifEmpty { clean })
}

/** Deskripsi sampul header koleksi (gambar atau ikon pengisi bila tidak bersampul). */
internal data class CollectionCover(
    val artworkUrl: String? = null,
    val fallbackIcon: ImageVector,
    val containerColor: Color? = null,
    val iconColor: Color? = null,
)

/** Tombol kembali bulat 44 dp (#1D1F23) gaya v2 di dalam target sentuh minimal 48 dp. */
@Composable
internal fun CollectionBackButton(
    previousLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val backDescription = appString(Res.string.profile_back_to, previousLabel)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onBack,
            )
            .semantics {
                role = Role.Button
                contentDescription = backDescription
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .indication(interactionSource, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SenadaIcons.ChevronLeft,
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Bar atas halaman detail v2: tombol kembali bulat 44 dp di kiri dan label mono tujuan di kanan. */
@Composable
internal fun CollectionTopBar(
    previousLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        CollectionBackButton(
            previousLabel = previousLabel,
            onBack = onBack,
            modifier = Modifier.offset(x = (-2).dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = previousLabel.uppercase(),
            style = SenadaType.monoLabel,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * Header bersama gaya v2 untuk halaman koleksi (playlist library, Favorit, album, dan playlist YouTube Music):
 * bar atas tombol kembali opsional dengan label mono tujuan, diikuti kartu SenadaCard berisi label kategori,
 * sampul mengikuti pengaturan sudut sampul, judul display, baris meta, baris aksi (menu ⋯, acak,
 * tombol Putar aksen 56 dp), serta chip dan catatan kaki opsional.
 */
@Composable
internal fun CollectionHeader(
    title: String,
    meta: CollectionMeta?,
    cover: CollectionCover,
    menuItems: List<CollectionMenuItem>,
    onMenuAction: (CollectionMenuAction) -> Unit,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    chips: (@Composable () -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    eyebrow: String? = null,
    isPlaying: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null && backLabel != null) {
            CollectionTopBar(
                previousLabel = backLabel,
                onBack = onBack,
            )
        }
        SenadaCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                eyebrow?.takeIf { it.isNotBlank() }?.let { label ->
                    MonoLabel(
                        text = label,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                CollectionCoverTile(
                    cover = cover,
                    modifier = Modifier.align(Alignment.CenterHorizontally).size(184.dp),
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 28.sp,
                            lineHeight = 33.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.04).em,
                        ),
                        color = colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    if (meta != null && meta.display.isNotEmpty()) {
                        Text(
                            text = meta.display,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                                contentDescription = meta.spoken
                            },
                        )
                    }
                }
                HorizontalDivider(color = colors.outline)
                CollectionActionRow(
                    title = title,
                    subtitle = meta?.display,
                    cover = cover,
                    menuItems = menuItems,
                    onMenuAction = onMenuAction,
                    canPlay = canPlay,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    isPlaying = isPlaying,
                )
                if (chips != null) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chips()
                    }
                }
                footer?.invoke(this)
            }
        }
    }
}

@Composable
private fun CollectionCoverTile(
    cover: CollectionCover,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.5f
    val containerColor = cover.containerColor ?: colors.surfaceContainerHigh
    val iconColor = cover.iconColor ?: colors.onSurfaceVariant.copy(alpha = 0.65f)
    val shape = RoundedCornerShape(LocalSenadaLook.current.coverRadius)
    val borderColor = if (dark) Color.White.copy(alpha = 0.08f) else colors.outlineVariant
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = cover.fallbackIcon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(60.dp),
        )
        cover.artworkUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(
                model = remember(url) { resizeGoogleArtwork(url, 544) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * Header ringkas gaya v2 berbasis [SenadaCard] untuk bagian di tab Library (Playlist, Riwayat, File impor):
 * label kategori mono opsional, judul tebal, baris meta mono, dan aksi kanan yang turun ke bawah pada font besar.
 */
@Composable
internal fun CompactCollectionHeader(
    title: String,
    meta: CollectionMeta?,
    minPrimaryWidth: Dp = 140.dp,
    eyebrow: String? = null,
    trailing: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SenadaCard {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(1.dp).padding(18.dp),
            minPrimaryWidth = minPrimaryWidth,
            horizontalSpacing = 12.dp,
            verticalSpacing = 12.dp,
            primary = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    eyebrow?.takeIf { it.isNotBlank() }?.let { MonoLabel(it) }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.02).em,
                        ),
                        color = colors.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (meta != null && meta.display.isNotEmpty()) {
                        Text(
                            text = meta.display.uppercase(),
                            style = SenadaType.monoLabel,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = meta.spoken
                            },
                        )
                    }
                }
            },
            trailing = trailing,
        )
    }
}

@Composable
private fun CollectionActionRow(
    title: String,
    subtitle: String?,
    cover: CollectionCover,
    menuItems: List<CollectionMenuItem>,
    onMenuAction: (CollectionMenuAction) -> Unit,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isPlaying: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    val openMenuDesc = appString(Res.string.a11y_open_menu_named, title)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = { menuOpen = true },
                    )
                    .semantics {
                        role = Role.Button
                        contentDescription = openMenuDesc
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceContainerHigh)
                        .indication(interactionSource, LocalIndication.current),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SenadaIcons.More,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (menuOpen) {
                OverflowActionSheet(
                    title = title,
                    subtitle = subtitle,
                    artwork = { OverflowActionArtwork(cover) },
                    onDismiss = { menuOpen = false },
                ) { select ->
                    menuItems.forEach { item ->
                        val icon = when (item.action) {
                            CollectionMenuAction.RenamePlaylist -> SenadaIcons.Edit
                            CollectionMenuAction.DeletePlaylist -> SenadaIcons.Trash
                            CollectionMenuAction.EnqueueAll -> SenadaIcons.Queue
                            CollectionMenuAction.OpenArtist -> SenadaIcons.Person
                            CollectionMenuAction.SaveToLibrary -> SenadaIcons.Library
                        }
                        OverflowActionItem(item.label, icon, enabled = item.enabled) {
                            select { onMenuAction(item.action) }
                        }
                    }
                }
            }
        }
        CollectionPlayButtons(
            title = title,
            canPlay = canPlay,
            onPlay = onPlay,
            onShuffle = onShuffle,
            isPlaying = isPlaying,
        )
    }
}

/** Sepasang tombol Acak (48 dp) dan Putar aksen (56 dp) bersama untuk header koleksi penuh dan ringkas. */
@Composable
internal fun CollectionPlayButtons(
    title: String,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    isPlaying: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val playContainer = if (canPlay) colors.primary else colors.surfaceContainerHighest
    val playContent = if (canPlay) textOnAccent(playContainer) else colors.onSurface.copy(alpha = 0.38f)

    val shuffleLabel = appString(Res.string.a11y_shuffle_play, title)
    val playPauseLabel = appString(if (isPlaying) Res.string.a11y_pause_track else Res.string.a11y_play_track, title)
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onShuffle,
            enabled = canPlay,
            shape = CircleShape,
            color = if (canPlay) colors.surfaceContainerHigh else Color.Transparent,
            border = BorderStroke(1.dp, if (canPlay) colors.outline else colors.outlineVariant),
            modifier = Modifier.size(48.dp).semantics {
                role = Role.Button
                contentDescription = shuffleLabel
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = SenadaIcons.Shuffle,
                    contentDescription = null,
                    tint = if (canPlay) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                )
            }
        }
        Surface(
            onClick = onPlay,
            enabled = canPlay,
            shape = CircleShape,
            color = playContainer,
            contentColor = playContent,
            modifier = Modifier.size(56.dp).semantics {
                role = Role.Button
                contentDescription = playPauseLabel
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) SenadaIcons.Pause else SenadaIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}
