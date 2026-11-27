package senada.app.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.AudioImportController
import senada.app.CollectionPlayButtons
import senada.app.CompactCollectionHeader
import senada.app.ImportAudioButton
import senada.app.ImportAudioStatusMessage
import senada.app.LibraryActions
import senada.app.LibraryEmptyState
import senada.app.LibraryState
import senada.app.LocalAppLanguage
import senada.app.SenadaFilterPill
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.appString
import senada.app.collectionMeta
import senada.app.favoritesTab
import senada.app.historyTab
import senada.app.offlineSongsTab
import senada.app.playlistsTab
import senada.app.sumKnownDurationMs
import senada.library.FollowedArtist
import senada.library.Playlist
import senada.model.Track
import senada.offline.OfflineState
import senada.resources.Res
import senada.resources.import_audio_button
import senada.resources.import_audio_copying
import senada.resources.library_artists_empty
import senada.resources.library_artists_loading
import senada.resources.library_filter_a11y
import senada.resources.library_filter_all
import senada.resources.library_filter_artists
import senada.resources.library_filter_favorites
import senada.resources.library_filter_history
import senada.resources.library_filter_playlists
import senada.resources.library_imported_empty_short
import senada.resources.library_imported_eyebrow
import senada.resources.library_shortcut_imported
import senada.resources.library_shortcut_offline
import senada.resources.search_artists_prompt_title

/** Pilihan filter di dalam satu tab Library. */
internal enum class LibraryFilter {
    All,
    Playlists,
    Artists,
    Favorites,
    Offline,
    History,
    Imported;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        All -> AppStrings.get(Res.string.library_filter_all, language)
        Playlists -> AppStrings.get(Res.string.library_filter_playlists, language)
        Artists -> AppStrings.get(Res.string.library_filter_artists, language)
        Favorites -> AppStrings.get(Res.string.library_filter_favorites, language)
        Offline -> AppStrings.get(Res.string.library_shortcut_offline, language)
        History -> AppStrings.get(Res.string.library_filter_history, language)
        Imported -> AppStrings.get(Res.string.library_shortcut_imported, language)
    }

    val label: String get() = label()
}

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.libraryTab(
    section: LibraryFilter,
    onSection: (LibraryFilter) -> Unit,
    library: LibraryState,
    openedPlaylist: Playlist?,
    artists: List<FollowedArtist>?,
    downloadState: OfflineState?,
    importedTracks: List<Track>,
    importController: AudioImportController?,
    profileName: String,
    profilePhoto: String?,
    onOpenProfile: () -> Unit,
    actions: LibraryActions,
    onOpenPlaylist: (String?) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMorePlaylist: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenArtist: (FollowedArtist) -> Unit = {},
    onOpenArtistSearch: () -> Unit = onOpenSearch,
    reorderingPlaylist: Boolean = false,
    onToggleReorderPlaylist: () -> Unit = {},
) {
    if (openedPlaylist != null) {
        playlistsTab(
            library, openedPlaylist, actions, onOpenPlaylist, onLoadMorePlaylist, onOpenSearch,
            reordering = reorderingPlaylist, onToggleReorder = onToggleReorderPlaylist,
        )
        return
    }

    item(key = "library-toolbar") {
        LibraryToolbar(profileName, profilePhoto, onOpenProfile, actions) { onOpenPlaylist(it) }
    }
    stickyHeader(key = "library-filters") {
        LibraryFilterChips(section, onSection)
    }
    when (section) {
        LibraryFilter.All -> libraryOverviewRows(library, artists.orEmpty(), actions, onSection, { onOpenPlaylist(it) }, onOpenArtist)
        LibraryFilter.Favorites -> favoritesTab(library, actions, onLoadMoreFavorites, onOpenSearch)
        LibraryFilter.Offline -> offlineSongsTab(downloadState ?: OfflineState(), library, actions, onOpenSearch)
        LibraryFilter.Artists -> when {
            artists == null -> item(key = "library-artists-loading") {
                Text(appString(Res.string.library_artists_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            artists.isEmpty() -> item(key = "library-artists-empty") {
                LibraryEmptyState(
                    message = appString(Res.string.library_artists_empty),
                    actionLabel = appString(Res.string.search_artists_prompt_title),
                    onAction = onOpenArtistSearch,
                )
            }
            else -> libraryArtistRows(artists, onOpenArtist)
        }
        LibraryFilter.Playlists -> playlistsTab(
            library = library,
            opened = openedPlaylist,
            actions = actions,
            onOpen = onOpenPlaylist,
            onLoadMore = onLoadMorePlaylist,
            onOpenSearch = onOpenSearch,
            reordering = reorderingPlaylist,
            onToggleReorder = onToggleReorderPlaylist,
        )
        LibraryFilter.History -> historyTab(library, actions, onOpenSearch)
        LibraryFilter.Imported -> if (importController != null) {
            importedTracksTab(importedTracks, importController, library, actions)
        }
    }
}

@Composable
private fun LibraryFilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val language = LocalAppLanguage.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (filter in LibraryFilter.entries) {
            val label = filter.label(language)
            val filterDesc = appString(Res.string.library_filter_a11y, label)
            SenadaFilterPill(
                label = label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
                modifier = Modifier.semantics { contentDescription = filterDesc },
            )
        }
    }
}

/** Daftar file audio yang diimpor pengguna ke library. */
private fun LazyListScope.importedTracksTab(
    tracks: List<Track>,
    importController: AudioImportController,
    library: LibraryState,
    actions: LibraryActions,
) {
    item(key = "library-imported-header") {
        val language = LocalAppLanguage.current
        val meta = remember(tracks, language) {
            collectionMeta(trackCount = tracks.size, totalDurationMs = sumKnownDurationMs(tracks), language = language)
        }
        val importedTitle = appString(Res.string.library_shortcut_imported)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CompactCollectionHeader(
                title = importedTitle,
                meta = meta,
                eyebrow = appString(Res.string.library_imported_eyebrow),
            ) {
                CollectionPlayButtons(
                    title = importedTitle,
                    canPlay = tracks.isNotEmpty(),
                    onPlay = { actions.playImportedTracks(tracks) },
                    onShuffle = { actions.shuffleImportedTracks(tracks) },
                )
            }
            if (tracks.isEmpty()) {
                LibraryEmptyState(
                    message = appString(Res.string.library_imported_empty_short),
                    actionLabel = appString(if (importController.busy) Res.string.import_audio_copying else Res.string.import_audio_button),
                    actionEnabled = !importController.busy,
                    onAction = importController::launchImport,
                )
                importController.message?.let { ImportAudioStatusMessage(it) }
            } else {
                ImportAudioButton(controller = importController)
            }
        }
    }
    itemsIndexed(
        items = tracks,
        key = { _, track -> "library-imported-${track.id}" },
        contentType = { _, _ -> "imported-track" },
    ) { index, track ->
        TrackRow(
            track = track,
            favorite = track.id in library.favoriteIds,
            onClick = { actions.playImportedTracks(tracks, startIndex = index) },
        ) {
            TrackMenu(track, library, actions)
        }
    }
}
