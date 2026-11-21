package senada.app.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow

internal sealed interface PlaySource {
    data object Favorites : PlaySource
    data class Playlist(val name: String) : PlaySource
    data object MostPlayed : PlaySource
}

internal fun PlaySource?.label(): String? = when (this) {
    PlaySource.Favorites -> "Dari Favorit"
    is PlaySource.Playlist -> "Dari playlist $name"
    PlaySource.MostPlayed -> "Dari Paling sering diputar"
    null -> null
}

internal object PlaySourceState {
    val current = MutableStateFlow<PlaySource?>(null)
}
