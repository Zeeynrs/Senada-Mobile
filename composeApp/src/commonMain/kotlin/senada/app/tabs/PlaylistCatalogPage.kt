package senada.app.tabs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.AdaptiveRow
import senada.app.BrowseLoadMoreButton
import senada.app.BrowseMonoHeading
import senada.app.BrowseNoticeCard
import senada.app.BrowseRowSkeletonCard
import senada.app.BrowseSectionHeader
import senada.app.CollectionCover
import senada.app.CollectionHeader
import senada.app.CollectionKind
import senada.app.CollectionMenuAction
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.collectionMenuItems
import senada.app.minTouchTarget
import senada.app.nowplaying.PlaySource
import senada.app.shell.CatalogRoute
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.app.youTubePlaylistCollectionMeta
import senada.catalog.PlaylistDetail
import senada.catalog.PlaylistSummary
import senada.catalog.isYouTubeMixPlaylist
import senada.library.LibraryRepository
import senada.library.SavedLocalPlaylist
import senada.library.savePlaylistWithUniqueName
import senada.model.Track

internal const val MIX_SAVE_DISABLED_MESSAGE =
    "Mix YouTube tidak dapat disimpan ke Library karena daftar lagunya berubah otomatis."

/** Status penyimpanan halaman playlist YouTube Music ke Library lokal. */
internal sealed interface PlaylistSaveStatus {
    data object Saving : PlaylistSaveStatus
    data class Saved(val playlist: SavedLocalPlaylist) : PlaylistSaveStatus
    data class Failed(val message: String) : PlaylistSaveStatus
}

/** Hasil penyimpanan playlist katalog ke Library lokal. */
internal sealed interface CatalogPlaylistSaveOutcome {
    data class Saved(val playlist: SavedLocalPlaylist) : CatalogPlaylistSaveOutcome
    data class Rejected(val message: String) : CatalogPlaylistSaveOutcome
}

/**
 * Menyimpan playlist dari halaman katalog YouTube Music ke Library lokal dengan akhiran nama unik,
 * serta menolak playlist mix/radio (`RD…` selain `RDCLAK…`).
 */
internal suspend fun saveCatalogPlaylist(
    library: LibraryRepository,
    playlistId: String,
    title: String,
    tracks: List<Track>,
): CatalogPlaylistSaveOutcome {
    if (isYouTubeMixPlaylist(playlistId)) {
        return CatalogPlaylistSaveOutcome.Rejected(MIX_SAVE_DISABLED_MESSAGE)
    }
    if (tracks.isEmpty()) {
        return CatalogPlaylistSaveOutcome.Rejected("Playlist belum memiliki lagu untuk disimpan.")
    }
    val saved = savePlaylistWithUniqueName(library, title, tracks)
    return CatalogPlaylistSaveOutcome.Saved(saved)
}

/** Daftar hasil pencarian playlist di tab Cari. */
internal fun LazyListScope.playlistSearchResults(
    state: PlaylistSearchState,
    onOpenPlaylist: (PlaylistSummary) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResultUsed: () -> Unit,
) {
    if (state.query.isEmpty()) {
        item(key = "playlist-search-prompt") {
            BrowseNoticeCard(
                eyebrow = "Cari playlist",
                message = "Ketik nama atau topik playlist untuk mencari di YouTube Music.",
                polite = false,
            )
        }
        return
    }
    item(key = "playlist-search-heading") {
        BrowseMonoHeading("Playlist di YouTube Music")
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "playlist-search-loading") {
            BrowseRowSkeletonCard(statusLabel = "Mencari playlist…", rowCount = 3)
        }
    }
    state.error?.let { message ->
        item(key = "playlist-search-error") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (!state.loading && state.error == null && state.items.isEmpty()) {
        item(key = "playlist-search-empty") {
            BrowseNoticeCard(
                eyebrow = "Hasil kosong",
                message = "Playlist tidak ditemukan.",
            )
        }
    }
    items(state.items, key = { "playlist-result-${it.id}" }) { playlist ->
        val label = listOfNotNull("Buka playlist ${playlist.title}", playlist.subtitle).joinToString(", ")
        CatalogSummaryRow(
            title = playlist.title,
            subtitle = playlist.subtitle,
            artworkUrl = playlist.artworkUrl,
            artworkShape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius),
            fallbackIcon = SenadaIcons.Library,
            accessibilityLabel = label,
            onClick = {
                onResultUsed()
                onOpenPlaylist(playlist)
            },
        )
    }
    if (state.nextPageToken != null && state.error == null) {
        item(key = "playlist-search-more") {
            BrowseLoadMoreButton(loading = state.loading, onClick = onLoadMore)
        }
    }
}

/** Merender halaman detail playlist YouTube Music dengan gaya yang sama dengan halaman album. */
internal fun LazyListScope.playlistDetailScreen(
    route: CatalogRoute.Playlist,
    state: CatalogLoadState<PlaylistDetail>,
    saveStatus: PlaylistSaveStatus?,
    onSave: (title: String, tracks: List<Track>) -> Unit,
    onOpenSavedPlaylist: (String) -> Unit,
    previousLabel: String,
    library: LibraryState,
    actions: LibraryActions,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val detail = state.data
    val displayTitle = detail?.title?.takeIf { it.isNotBlank() } ?: route.label
    val artworkUrl = detail?.artworkUrl ?: route.artworkUrl
    val tracks = detail?.tracks.orEmpty()
    val isMix = isYouTubeMixPlaylist(route.id)
    val source = PlaySource.Playlist.fromCatalog(route.id, displayTitle)
    val playlistKey = source.key

    item(key = "playlist-header-${route.id}") {
        val isPlaying by remember(actions, playlistKey) { actions.playlistPlaying(playlistKey) }.collectAsState(false)
        val meta = remember(detail, isMix, route.subtitle) {
            youTubePlaylistCollectionMeta(detail, isMix = isMix, fallbackSubtitle = route.subtitle)
        }
        val menuItems = remember(isMix, saveStatus, tracks.isNotEmpty()) {
            collectionMenuItems(
                kind = CollectionKind.YouTubePlaylist(isMix = isMix, saveStatus = saveStatus),
                hasTracks = tracks.isNotEmpty(),
            )
        }
        CollectionHeader(
            title = displayTitle,
            meta = meta,
            cover = CollectionCover(artworkUrl = artworkUrl, fallbackIcon = SenadaIcons.Library),
            menuItems = menuItems,
            onMenuAction = { action ->
                when (action) {
                    CollectionMenuAction.EnqueueAll -> actions.enqueueAll(tracks)
                    CollectionMenuAction.SaveToLibrary -> onSave(displayTitle, tracks)
                    else -> Unit
                }
            },
            canPlay = tracks.isNotEmpty(),
            onPlay = { actions.togglePlaylistPlayback(playlistKey) { actions.playAll(tracks, source, resetShuffle = true) } },
            onShuffle = { actions.playShuffled(tracks, source) },
            backLabel = previousLabel,
            onBack = onBack,
            eyebrow = if (isMix) "Mix / Radio" else "Playlist",
            isPlaying = isPlaying,
            footer = {
                if (isMix) {
                    Text(
                        MIX_SAVE_DISABLED_MESSAGE,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                } else {
                    when (saveStatus) {
                        is PlaylistSaveStatus.Saved -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                AdaptiveRow(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    minPrimaryWidth = 160.dp,
                                    primary = {
                                        Text(
                                            "Tersimpan sebagai \"${saveStatus.playlist.name}\" " +
                                                "(${saveStatus.playlist.trackCount} lagu).",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                        )
                                    },
                                    trailing = {
                                        TextButton(
                                            onClick = { onOpenSavedPlaylist(saveStatus.playlist.id) },
                                            modifier = Modifier.minTouchTarget(),
                                        ) {
                                            Text("Buka di Library")
                                        }
                                    },
                                )
                            }
                        }
                        is PlaylistSaveStatus.Failed -> {
                            Text(
                                saveStatus.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        PlaylistSaveStatus.Saving, null -> Unit
                    }
                }
                if (detail?.truncated == true && tracks.isNotEmpty()) {
                    Text(
                        "Menampilkan ${tracks.size} lagu pertama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (state.loading && detail == null) {
        item(key = "playlist-loading-${route.id}") {
            BrowseRowSkeletonCard(statusLabel = "Memuat halaman playlist…", rowCount = 5)
        }
    }
    state.error?.let { message ->
        item(key = "playlist-error-${route.id}") {
            CatalogErrorCard(message, onRetry)
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "playlist-tracks-heading-${route.id}") {
            BrowseSectionHeader(
                title = "Daftar lagu",
                monoLabel = "${tracks.size} lagu",
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        itemsIndexed(tracks, key = { index, track -> "playlist-track-${route.id}-$index-${track.id}" }) { index, track ->
            CatalogTrackRow(
                track = track,
                favorite = track.id in library.favoriteIds,
                trackNumber = index + 1,
                library = library,
                actions = actions,
                onPlay = { actions.playAll(tracks, source, startIndex = index) },
            )
        }
    }
    if (!state.loading && state.error == null && detail != null && tracks.isEmpty()) {
        item(key = "playlist-empty-${route.id}") {
            BrowseNoticeCard(
                eyebrow = "Daftar lagu",
                message = "Belum ada lagu di playlist ini.",
            )
        }
    }
}
