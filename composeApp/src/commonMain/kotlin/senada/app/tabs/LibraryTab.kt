package senada.app.tabs

import androidx.compose.foundation.lazy.LazyListScope
import senada.app.AppTab
import senada.app.AppTabs
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.favoritesTab
import senada.app.historyTab
import senada.app.playlistsTab
import senada.library.Playlist

/** Tab Library: Favorit, Playlist, dan Riwayat dalam sub-tab (tampilan lama sampai Fase 5). */
internal fun LazyListScope.libraryTab(
    section: AppTab,
    onSection: (AppTab) -> Unit,
    library: LibraryState,
    openedPlaylist: Playlist?,
    actions: LibraryActions,
    onOpenPlaylist: (String?) -> Unit,
    onLoadMoreFavorites: () -> Unit,
    onLoadMorePlaylist: () -> Unit,
) {
    item(key = "library-sections") { AppTabs(section, onSection) }
    when (section) {
        AppTab.Favorites -> favoritesTab(library, actions, onLoadMoreFavorites)
        AppTab.Playlists -> playlistsTab(
            library = library,
            opened = openedPlaylist,
            actions = actions,
            onOpen = onOpenPlaylist,
            onLoadMore = onLoadMorePlaylist,
        )
        AppTab.History -> historyTab(library, actions)
    }
}
