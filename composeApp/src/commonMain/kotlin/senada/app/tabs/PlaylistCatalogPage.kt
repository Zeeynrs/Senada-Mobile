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
import senada.app.AppLanguage
import senada.app.AppStrings
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
import senada.app.LocalAppLanguage
import senada.app.UiText
import senada.app.appPlural
import senada.app.appString
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
import senada.resources.Res
import senada.resources.a11y_open_playlist_named
import senada.resources.catalog_mix_save_disabled
import senada.resources.catalog_open_in_library
import senada.resources.catalog_playlist_empty
import senada.resources.catalog_playlist_loading
import senada.resources.catalog_playlist_save_empty
import senada.resources.catalog_playlist_saved_note
import senada.resources.catalog_playlist_truncated
import senada.resources.catalog_tracklist_heading
import senada.resources.catalog_type_playlist
import senada.resources.collection_mix_radio
import senada.resources.search_empty_eyebrow
import senada.resources.search_playlists_empty
import senada.resources.search_playlists_heading
import senada.resources.search_playlists_loading
import senada.resources.search_playlists_prompt_desc
import senada.resources.search_playlists_prompt_title
import senada.resources.song_count

internal fun mixSaveDisabledMessage(language: AppLanguage = AppStrings.currentLanguage): String =
    AppStrings.get(Res.string.catalog_mix_save_disabled, language)

/** Status penyimpanan halaman playlist YouTube Music ke Library lokal. */
internal sealed interface PlaylistSaveStatus {
    data object Saving : PlaylistSaveStatus
    data class Saved(val playlist: SavedLocalPlaylist) : PlaylistSaveStatus
    data class Failed(val uiText: UiText) : PlaylistSaveStatus {
        constructor(message: String) : this(UiText.Raw(message))

        fun message(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

        val message: String get() = message()
    }
}

/** Hasil penyimpanan playlist katalog ke Library lokal. */
internal sealed interface CatalogPlaylistSaveOutcome {
    data class Saved(val playlist: SavedLocalPlaylist) : CatalogPlaylistSaveOutcome
    data class Rejected(val uiText: UiText) : CatalogPlaylistSaveOutcome {
        constructor(message: String) : this(UiText.Raw(message))

        fun message(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

        val message: String get() = message()
    }
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
        return CatalogPlaylistSaveOutcome.Rejected(UiText.Resource(Res.string.catalog_mix_save_disabled))
    }
    if (tracks.isEmpty()) {
        return CatalogPlaylistSaveOutcome.Rejected(UiText.Resource(Res.string.catalog_playlist_save_empty))
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
                eyebrow = appString(Res.string.search_playlists_prompt_title),
                message = appString(Res.string.search_playlists_prompt_desc),
                polite = false,
            )
        }
        return
    }
    item(key = "playlist-search-heading") {
        BrowseMonoHeading(appString(Res.string.search_playlists_heading))
    }
    if (state.loading && state.items.isEmpty()) {
        item(key = "playlist-search-loading") {
            BrowseRowSkeletonCard(statusLabel = appString(Res.string.search_playlists_loading), rowCount = 3)
        }
    }
    state.errorText?.let { errorText ->
        item(key = "playlist-search-error") {
            val language = LocalAppLanguage.current
            CatalogErrorCard(errorText.resolve(language), onRetry)
        }
    }
    if (!state.loading && state.errorText == null && state.items.isEmpty()) {
        item(key = "playlist-search-empty") {
            BrowseNoticeCard(
                eyebrow = appString(Res.string.search_empty_eyebrow),
                message = appString(Res.string.search_playlists_empty),
            )
        }
    }
    items(state.items, key = { "playlist-result-${it.id}" }) { playlist ->
        val openPlaylistPrefix = appString(Res.string.a11y_open_playlist_named, playlist.title)
        val label = listOfNotNull(openPlaylistPrefix, playlist.subtitle).joinToString(", ")
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
    if (state.nextPageToken != null && state.errorText == null) {
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
        val language = LocalAppLanguage.current
        val isPlaying by remember(actions, playlistKey) { actions.playlistPlaying(playlistKey) }.collectAsState(false)
        val meta = remember(detail, isMix, route.subtitle, language) {
            youTubePlaylistCollectionMeta(detail, isMix = isMix, fallbackSubtitle = route.subtitle)
        }
        val menuItems = remember(isMix, saveStatus, tracks.isNotEmpty(), language) {
            collectionMenuItems(
                kind = CollectionKind.YouTubePlaylist(isMix = isMix, saveStatus = saveStatus),
                hasTracks = tracks.isNotEmpty(),
                language = language,
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
            eyebrow = appString(if (isMix) Res.string.collection_mix_radio else Res.string.catalog_type_playlist),
            isPlaying = isPlaying,
            footer = {
                if (isMix) {
                    Text(
                        appString(Res.string.catalog_mix_save_disabled),
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
                                        val countText = appPlural(
                                            Res.plurals.song_count,
                                            saveStatus.playlist.trackCount,
                                            saveStatus.playlist.trackCount,
                                        )
                                        Text(
                                            appString(Res.string.catalog_playlist_saved_note, saveStatus.playlist.name, countText),
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
                                            Text(appString(Res.string.catalog_open_in_library))
                                        }
                                    },
                                )
                            }
                        }
                        is PlaylistSaveStatus.Failed -> {
                            Text(
                                saveStatus.message(language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        PlaylistSaveStatus.Saving, null -> Unit
                    }
                }
                if (detail?.truncated == true && tracks.isNotEmpty()) {
                    val countText = appPlural(Res.plurals.song_count, tracks.size, tracks.size)
                    Text(
                        appString(Res.string.catalog_playlist_truncated, countText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (state.loading && detail == null) {
        item(key = "playlist-loading-${route.id}") {
            BrowseRowSkeletonCard(statusLabel = appString(Res.string.catalog_playlist_loading), rowCount = 5)
        }
    }
    state.errorText?.let { errorText ->
        item(key = "playlist-error-${route.id}") {
            val language = LocalAppLanguage.current
            CatalogErrorCard(errorText.resolve(language), onRetry)
        }
    }
    if (tracks.isNotEmpty()) {
        item(key = "playlist-tracks-heading-${route.id}") {
            BrowseSectionHeader(
                title = appString(Res.string.catalog_tracklist_heading),
                monoLabel = appPlural(Res.plurals.song_count, tracks.size, tracks.size),
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
    if (!state.loading && state.errorText == null && detail != null && tracks.isEmpty()) {
        item(key = "playlist-empty-${route.id}") {
            BrowseNoticeCard(
                eyebrow = appString(Res.string.catalog_tracklist_heading),
                message = appString(Res.string.catalog_playlist_empty),
            )
        }
    }
}
