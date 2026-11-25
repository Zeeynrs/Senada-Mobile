package senada.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import senada.catalog.artistChoicesFor
import senada.catalog.canOpenArtist
import senada.catalog.normalizeArtistName
import senada.model.Track

internal data class LibraryArtist(val key: String, val name: String, val representative: Track)

/** Hanya lagu yang ada di favorit atau playlist pengguna, termasuk yang belum dimuat di layar. */
internal fun LibraryRepository.libraryArtists(): Flow<List<LibraryArtist>> = queries.libraryArtistTracks(::trackOf)
    .asFlow()
    .mapToList(dispatcher)
    .map { tracks ->
        val artists = mutableMapOf<String, LibraryArtist>()
        for (track in tracks) {
            if (!canOpenArtist(track)) continue
            for (name in artistChoicesFor(track)) {
                val key = normalizeArtistName(name)
                if (key !in artists) artists[key] = LibraryArtist(key, name, track)
            }
        }
        artists.values.sortedBy { it.key }
    }
    .distinctUntilChanged()
    .flowOn(dispatcher)
