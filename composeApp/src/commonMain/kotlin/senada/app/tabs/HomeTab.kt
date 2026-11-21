package senada.app.tabs

import androidx.compose.foundation.lazy.LazyListScope
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.favoritesTab
import senada.app.historyTab

/** Beranda sementara: Favorit dan Riwayat. Hero dan deret kartu menyusul di Fase 4. */
internal fun LazyListScope.homeTab(library: LibraryState, actions: LibraryActions, onLoadMoreFavorites: () -> Unit) {
    favoritesTab(library, actions, onLoadMoreFavorites)
    historyTab(library, actions)
}
