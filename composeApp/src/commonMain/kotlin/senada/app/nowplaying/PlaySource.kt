package senada.app.nowplaying

import kotlinx.coroutines.flow.MutableStateFlow
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.resources.Res
import senada.resources.player_playing_from
import senada.resources.player_playing_from_queue
import senada.resources.player_source_album
import senada.resources.player_source_artist
import senada.resources.player_source_favorites
import senada.resources.player_source_history
import senada.resources.player_source_imported
import senada.resources.player_source_most_played
import senada.resources.player_source_offline
import senada.resources.player_source_playlist
import senada.resources.player_source_search

internal sealed interface PlaySource {
    data object Search : PlaySource
    data object Favorites : PlaySource
    data class Playlist(val name: String, val key: String) : PlaySource {
        companion object {
            fun fromLibrary(id: String, name: String) = Playlist(name, "library:$id")
            fun fromCatalog(id: String, name: String) = Playlist(name, "catalog:$id")
        }
    }
    data object History : PlaySource
    data object Imported : PlaySource
    data object Offline : PlaySource
    data object MostPlayed : PlaySource
    data class Artist(val name: String) : PlaySource
    data class Album(val title: String) : PlaySource
}

internal fun PlaySource?.label(language: AppLanguage = AppStrings.currentLanguage): String? = when (this) {
    PlaySource.Search -> AppStrings.get(Res.string.player_source_search, language)
    PlaySource.Favorites -> AppStrings.get(Res.string.player_source_favorites, language)
    is PlaySource.Playlist -> AppStrings.get(Res.string.player_source_playlist, language, name)
    PlaySource.History -> AppStrings.get(Res.string.player_source_history, language)
    PlaySource.Imported -> AppStrings.get(Res.string.player_source_imported, language)
    PlaySource.Offline -> AppStrings.get(Res.string.player_source_offline, language)
    PlaySource.MostPlayed -> AppStrings.get(Res.string.player_source_most_played, language)
    is PlaySource.Artist -> AppStrings.get(Res.string.player_source_artist, language, name)
    is PlaySource.Album -> AppStrings.get(Res.string.player_source_album, language, title)
    null -> null
}

internal fun PlaySource?.playerLabel(language: AppLanguage = AppStrings.currentLanguage): String {
    val raw = label(language) ?: return AppStrings.get(Res.string.player_playing_from_queue, language)
    return AppStrings.get(Res.string.player_playing_from, language, raw.replaceFirstChar { it.lowercaseChar() })
}

internal object PlaySourceState {
    val current = MutableStateFlow<PlaySource?>(null)
}
