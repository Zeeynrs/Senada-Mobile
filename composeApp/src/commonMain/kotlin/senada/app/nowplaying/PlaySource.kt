package senada.app.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow

internal sealed interface PlaySource {
    data object Search : PlaySource
    data object Favorites : PlaySource
    data class Playlist(val name: String) : PlaySource
    data object History : PlaySource
    data object Imported : PlaySource
    data object MostPlayed : PlaySource
    data class Artist(val name: String) : PlaySource
    data class Album(val title: String) : PlaySource
}

internal fun PlaySource?.label(): String? = when (this) {
    PlaySource.Search -> "Dari Pencarian"
    PlaySource.Favorites -> "Dari Favorit"
    is PlaySource.Playlist -> "Dari playlist $name"
    PlaySource.History -> "Dari Riwayat"
    PlaySource.Imported -> "Dari File impor"
    PlaySource.MostPlayed -> "Dari Paling sering diputar"
    is PlaySource.Artist -> "Dari artis $name"
    is PlaySource.Album -> "Dari album $title"
    null -> null
}

internal fun PlaySource?.playerLabel(): String = "Diputar " + (label() ?: "Dari Antrean").replaceFirstChar { it.lowercaseChar() }

internal object PlaySourceState {
    val current = MutableStateFlow<PlaySource?>(null)
}
