package senada.app.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow

internal sealed interface PlaySource {
    data object Favorites : PlaySource
    data class Playlist(val name: String) : PlaySource
    data object MostPlayed : PlaySource
    data class Artist(val name: String) : PlaySource
    data class Album(val title: String) : PlaySource
}

internal fun PlaySource?.label(): String? = when (this) {
    PlaySource.Favorites -> "Dari Favorit"
    is PlaySource.Playlist -> "Dari playlist $name"
    PlaySource.MostPlayed -> "Dari Paling sering diputar"
    is PlaySource.Artist -> "Dari artis $name"
    is PlaySource.Album -> "Dari album $title"
    null -> null
}

internal object PlaySourceState {
    val current = MutableStateFlow<PlaySource?>(null)
}
