package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import senada.app.shell.SenadaIcons
import senada.app.tabs.PlaylistSaveStatus
import senada.catalog.AlbumDetail
import senada.catalog.PlaylistDetail
import senada.catalog.youtube.resizeGoogleArtwork
import senada.model.Track

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
): List<CollectionMenuItem> = when (kind) {
    CollectionKind.LibraryPlaylist -> listOf(
        CollectionMenuItem(CollectionMenuAction.RenamePlaylist, "Ganti nama"),
        CollectionMenuItem(CollectionMenuAction.DeletePlaylist, "Hapus playlist"),
    )
    CollectionKind.Favorites -> listOf(
        CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = hasTracks),
    )
    is CollectionKind.Album -> buildList {
        add(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = hasTracks))
        if (kind.hasArtistId) {
            add(CollectionMenuItem(CollectionMenuAction.OpenArtist, "Buka artis"))
        }
    }
    is CollectionKind.YouTubePlaylist -> buildList {
        add(CollectionMenuItem(CollectionMenuAction.EnqueueAll, "Tambah semua ke antrean", enabled = hasTracks))
        if (!kind.isMix) {
            val saveLabel = when (kind.saveStatus) {
                is PlaylistSaveStatus.Saving -> "Menyimpan…"
                is PlaylistSaveStatus.Saved -> "Tersimpan"
                is PlaylistSaveStatus.Failed, null -> "Simpan ke Library"
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

/** Memformat total durasi koleksi untuk tampilan ringkas, misalnya "7 j 50 mnt" atau "45 mnt". */
internal fun formatCollectionDuration(durationMs: Long): String? =
    formatDurationWithUnits(durationMs, hourUnit = "j", minuteUnit = "mnt", underOneMinute = "< 1 mnt")

/** Memformat total durasi koleksi menjadi frasa penuh bahasa Indonesia untuk pembaca layar. */
internal fun spokenCollectionDuration(durationMs: Long): String? =
    formatDurationWithUnits(durationMs, hourUnit = "jam", minuteUnit = "menit", underOneMinute = "kurang dari 1 menit")

private fun formatDurationWithUnits(
    durationMs: Long,
    hourUnit: String,
    minuteUnit: String,
    underOneMinute: String,
): String? {
    if (durationMs <= 0L) return null
    val totalMinutes = durationMs / 60_000L
    if (totalMinutes == 0L) return underOneMinute
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return buildList {
        if (hours > 0L) add("$hours $hourUnit")
        if (minutes > 0L) add("$minutes $minuteUnit")
    }.joinToString(" ")
}

/** Menyusun baris meta koleksi dari bagian awal opsional, jumlah lagu, dan total durasi. */
internal fun collectionMeta(
    trackCount: Int,
    totalDurationMs: Long? = null,
    prefixParts: List<String> = emptyList(),
    countTextOverride: String? = null,
): CollectionMeta {
    val cleanPrefix = prefixParts.map(String::trim).filter(String::isNotEmpty)
    val countText = countTextOverride?.trim()?.takeIf(String::isNotEmpty) ?: "$trackCount lagu"
    val durationDisplay = totalDurationMs?.let(::formatCollectionDuration)
    val durationSpoken = totalDurationMs?.let(::spokenCollectionDuration)
    return CollectionMeta(
        display = (cleanPrefix + listOfNotNull(countText, durationDisplay)).joinToString(" · "),
        spoken = (cleanPrefix + listOfNotNull(countText, durationSpoken)).joinToString(", "),
    )
}

internal fun playlistCollectionMeta(trackCount: Int, totalDurationMs: Long?): CollectionMeta =
    collectionMeta(trackCount = trackCount, totalDurationMs = totalDurationMs)

internal fun favoritesCollectionMeta(trackCount: Int, totalDurationMs: Long?): CollectionMeta =
    collectionMeta(trackCount = trackCount, totalDurationMs = totalDurationMs)

internal fun albumCollectionMeta(detail: AlbumDetail?, fallbackSubtitle: String?): CollectionMeta? {
    if (detail == null) return fallbackMeta(fallbackSubtitle)
    val prefix = listOfNotNull(
        detail.typeLabel?.takeIf { it.isNotBlank() } ?: "Album",
        detail.artists.filter { it.isNotBlank() }.joinToString().takeIf { it.isNotEmpty() },
        detail.year?.takeIf { it.isNotBlank() },
    )
    if (detail.tracks.isNotEmpty()) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = sumKnownDurationMs(detail.tracks),
            prefixParts = prefix,
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
): CollectionMeta? {
    if (detail == null) return fallbackMeta(fallbackSubtitle)
    val prefix = listOfNotNull(
        "Mix / Radio".takeIf { isMix },
        detail.owner?.takeIf { it.isNotBlank() },
    )
    if (!detail.truncated) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = sumKnownDurationMs(detail.tracks),
            prefixParts = prefix,
        )
    }
    val countText = detail.songCountText?.takeIf { it.isNotBlank() }
        ?: "${detail.tracks.size} lagu".takeIf { detail.tracks.isNotEmpty() }
    if (countText != null) {
        return collectionMeta(
            trackCount = detail.tracks.size,
            totalDurationMs = null,
            prefixParts = prefix,
            countTextOverride = countText,
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

/** Tombol kembali bulat >= 48 dp berikon panah kiri untuk halaman detail koleksi dan layar sekunder. */
@Composable
internal fun CollectionBackButton(
    previousLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onBack,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.size(48.dp).semantics {
            role = Role.Button
            contentDescription = "Kembali ke $previousLabel"
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = SenadaIcons.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Header bersama untuk halaman koleksi (playlist library, Favorit, album, dan playlist YouTube Music):
 * tombol kembali bulat opsional, sampul 20 dp, judul besar, baris meta, baris aksi (menu ⋯, acak,
 * tombol Putar aksen 56 dp), serta chip opsional bila ada aksi tambahan.
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
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null && backLabel != null) {
            CollectionBackButton(
                previousLabel = backLabel,
                onBack = onBack,
                modifier = Modifier.align(Alignment.Start),
            )
        }
        CollectionCoverTile(
            cover = cover,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(188.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            if (meta != null && meta.display.isNotEmpty()) {
                Text(
                    text = meta.display,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                        contentDescription = meta.spoken
                    },
                )
            }
        }
        CollectionActionRow(
            title = title,
            menuItems = menuItems,
            onMenuAction = onMenuAction,
            canPlay = canPlay,
            onPlay = onPlay,
            onShuffle = onShuffle,
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

@Composable
private fun CollectionCoverTile(
    cover: CollectionCover,
    modifier: Modifier = Modifier,
) {
    val containerColor = cover.containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val iconColor = cover.iconColor ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    Box(
        modifier = modifier.clip(MaterialTheme.shapes.medium).background(containerColor),
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

@Composable
private fun CollectionActionRow(
    title: String,
    menuItems: List<CollectionMenuItem>,
    onMenuAction: (CollectionMenuAction) -> Unit,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val playContainer = if (canPlay) colors.primary else colors.surfaceContainerHighest
    val playContent = if (canPlay) textOnAccent(playContainer) else colors.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.minTouchTarget().semantics {
                    contentDescription = "Buka menu $title"
                },
            ) {
                Icon(
                    imageVector = SenadaIcons.More,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        enabled = item.enabled,
                        onClick = {
                            menuOpen = false
                            onMenuAction(item.action)
                        },
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = onShuffle,
                enabled = canPlay,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.dp, if (canPlay) colors.outline else colors.outlineVariant),
                modifier = Modifier.size(48.dp).semantics {
                    role = Role.Button
                    contentDescription = "Putar acak $title"
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
                    contentDescription = "Putar $title"
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = SenadaIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}
