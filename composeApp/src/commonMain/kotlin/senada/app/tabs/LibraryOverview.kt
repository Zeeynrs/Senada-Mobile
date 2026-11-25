package senada.app.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import senada.app.LibraryActions
import senada.app.LibraryAddPlaylistButton
import senada.app.LibraryState
import senada.app.appPlural
import senada.app.appString
import senada.app.browseDisplayTitleStyle
import senada.app.browseRowSubtitleStyle
import senada.app.browseRowTitleStyle
import senada.app.libraryPlaylistRows
import senada.app.minTouchTarget
import senada.app.shell.ProfileAvatar
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.catalog.youtube.resizeGoogleArtwork
import senada.library.LibraryArtist
import senada.resources.Res
import senada.resources.a11y_open_artist_named
import senada.resources.a11y_open_profile
import senada.resources.action_open
import senada.resources.catalog_type_artist
import senada.resources.library_heading
import senada.resources.library_shortcut_favorites
import senada.resources.library_shortcut_history
import senada.resources.library_shortcut_history_sub
import senada.resources.library_shortcut_imported
import senada.resources.library_shortcut_imported_sub
import senada.resources.library_shortcut_offline
import senada.resources.library_shortcut_offline_sub
import senada.resources.library_shortcut_open_a11y
import senada.resources.song_count

@Composable
internal fun LibraryToolbar(
    profileName: String,
    profilePhoto: String?,
    onOpenProfile: () -> Unit,
    actions: LibraryActions,
    onOpenPlaylist: (String) -> Unit,
) {
    val openProfileDesc = appString(Res.string.a11y_open_profile)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onOpenProfile,
            modifier = Modifier.minTouchTarget().semantics { contentDescription = openProfileDesc },
        ) {
            ProfileAvatar(profileName, size = 36.dp, borderWidth = 0.dp, photoModel = profilePhoto)
        }
        Text(
            appString(Res.string.library_heading), style = browseDisplayTitleStyle(),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        LibraryAddPlaylistButton(actions, onOpenPlaylist)
    }
}

internal fun LazyListScope.libraryOverviewRows(
    library: LibraryState,
    artists: List<LibraryArtist>,
    actions: LibraryActions,
    onSection: (LibraryFilter) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    item(key = "library-overview-favorites") {
        LibraryShortcutRow(
            title = appString(Res.string.library_shortcut_favorites),
            subtitle = appPlural(Res.plurals.song_count, library.favoriteTotal, library.favoriteTotal),
            icon = SenadaIcons.HeartFilled,
        ) {
            onSection(LibraryFilter.Favorites)
        }
    }
    item(key = "library-overview-offline") {
        LibraryShortcutRow(
            title = appString(Res.string.library_shortcut_offline),
            subtitle = appString(Res.string.library_shortcut_offline_sub),
            icon = SenadaIcons.Download,
        ) { onSection(LibraryFilter.Offline) }
    }
    libraryPlaylistRows(library, actions, onOpenPlaylist)
    libraryArtistRows(artists, actions)
    item(key = "library-overview-history") {
        LibraryShortcutRow(
            title = appString(Res.string.library_shortcut_history),
            subtitle = appString(Res.string.library_shortcut_history_sub),
            icon = SenadaIcons.Clock,
        ) { onSection(LibraryFilter.History) }
    }
    item(key = "library-overview-imported") {
        LibraryShortcutRow(
            title = appString(Res.string.library_shortcut_imported),
            subtitle = appString(Res.string.library_shortcut_imported_sub),
            icon = SenadaIcons.Import,
        ) { onSection(LibraryFilter.Imported) }
    }
}

internal fun LazyListScope.libraryArtistRows(artists: List<LibraryArtist>, actions: LibraryActions) {
    items(artists, key = { "library-artist-${it.key}" }) { artist ->
        LibraryBrowseRow(
            title = artist.name,
            subtitle = appString(Res.string.catalog_type_artist),
            label = appString(Res.string.a11y_open_artist_named, artist.name),
            onClick = { actions.openArtist(artist.representative, artist.name) },
        ) {
            val url = artist.representative.artworkUrl
            val cover = remember(url) { url?.takeIf(String::isNotBlank)?.let { resizeGoogleArtwork(it, 144) } }
            ProfileAvatar(artist.name, size = 56.dp, borderWidth = 0.dp, photoModel = cover)
        }
    }
}

@Composable
private fun LibraryShortcutRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    LibraryBrowseRow(title, subtitle, appString(Res.string.library_shortcut_open_a11y, title), onClick) {
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(LocalSenadaLook.current.listCoverRadius)
        Box(Modifier.size(56.dp).background(colors.primaryContainer, shape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun LibraryBrowseRow(
    title: String,
    subtitle: String,
    label: String,
    onClick: () -> Unit,
    cover: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val openLabel = appString(Res.string.action_open)
    Row(
        Modifier.fillMaxWidth().minTouchTarget().clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }.padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.onSurface, style = browseRowTitleStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = colors.onSurfaceVariant, style = browseRowSubtitleStyle(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
