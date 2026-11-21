package senada.library

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import app.cash.sqldelight.db.SqlDriver
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import senada.db.SenadaDatabase
import senada.epochMillis
import senada.model.Track

data class PlaylistSummary(
    val id: String,
    val name: String,
    val description: String,
    val createdAtMs: Long,
    val trackCount: Int,
)

/** Satu baris playlist; [itemId] membedakan lagu yang sama yang ditambahkan lebih dari sekali. */
data class PlaylistEntry(val itemId: Long, val track: Track)

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val createdAtMs: Long,
    val entries: List<PlaylistEntry>,
)

/** Album/playlist katalog yang disimpan; [metadata] JSON dipertahankan utuh untuk backup desktop. */
data class SavedItem(val kind: String, val id: String, val metadata: String, val savedAtMs: Long)

/**
 * Library lokal: favorit, playlist, riwayat dan item tersimpan. Bacaan berupa [Flow] yang ikut berubah
 * setelah setiap penulisan. Setiap penulisan berjalan dalam satu transaksi, jadi kegagalan di tengah
 * tidak meninggalkan library setengah berubah.
 */
class LibraryRepository internal constructor(
    private val database: SenadaDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = ::epochMillis,
    private val newPlaylistId: () -> String = ::localPlaylistId,
) {
    private val queries = database.libraryQueries

    // -- Favorit --

    /** Favorit terbaru lebih dulu, seperti desktop. */
    val favorites: Flow<List<Track>> = queries.favorites(::trackOf).asFlow().mapToList(dispatcher)

    fun isFavorite(trackId: String): Flow<Boolean> =
        queries.favoriteCount(trackId).asFlow().mapToOne(dispatcher).map { it > 0 }

    suspend fun setFavorite(track: Track, favorite: Boolean) {
        write { setFavoriteNow(track, favorite) }
    }

    /** Mengembalikan status favorit yang baru. */
    suspend fun toggleFavorite(track: Track): Boolean = write {
        val favorite = queries.favoriteCount(track.id).executeAsOne() == 0L
        setFavoriteNow(track, favorite)
        favorite
    }

    // -- Playlist --

    /** Playlist terbaru lebih dulu, tanpa daftar lagunya. */
    val playlists: Flow<List<PlaylistSummary>> = queries.playlists { id, name, description, createdAt, trackCount ->
        // Jumlah dari subquery; SQLDelight bisa menganggapnya nullable.
        @Suppress("USELESS_CAST")
        PlaylistSummary(id, name, description, createdAt, (trackCount as Long?)?.toInt() ?: 0)
    }.asFlow().mapToList(dispatcher)

    /** Playlist beserta lagunya sesuai urutan, atau null bila sudah dihapus. */
    fun playlist(id: String): Flow<Playlist?> = combine(
        queries.playlistById(id) { playlistId, name, description, createdAt ->
            Playlist(playlistId, name, description, createdAt, emptyList())
        }.asFlow().mapToOneOrNull(dispatcher),
        queries.playlistItems(id) { itemId, trackId, title, artists, durationMs, artworkUrl, source ->
            PlaylistEntry(itemId, trackOf(trackId, title, artists, durationMs, artworkUrl, source))
        }.asFlow().mapToList(dispatcher),
    ) { playlist, entries -> playlist?.copy(entries = entries) }

    /** Membuat playlist baru dan mengembalikan ID-nya (`local-<hex>`, format desktop). */
    suspend fun createPlaylist(name: String, tracks: List<Track> = emptyList(), description: String = ""): String {
        val id = newPlaylistId()
        write {
            queries.insertPlaylist(id, name.trim().ifEmpty { DEFAULT_PLAYLIST_NAME }, description, now())
            appendNow(id, tracks)
        }
        return id
    }

    /** Nama kosong diabaikan; mengembalikan false bila playlist tidak ada. */
    suspend fun renamePlaylist(id: String, name: String): Boolean = write {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !playlistExists(id)) {
            false
        } else {
            queries.renamePlaylist(trimmed, id)
            true
        }
    }

    suspend fun deletePlaylist(id: String): Boolean = write {
        if (!playlistExists(id)) {
            false
        } else {
            queries.deletePlaylistItems(id)
            queries.deletePlaylist(id)
            queries.deleteUnusedTracks()
            true
        }
    }

    /** Menambahkan lagu di akhir playlist, termasuk lagu yang sudah ada; mengembalikan jumlah yang ditambahkan. */
    suspend fun addToPlaylist(playlistId: String, tracks: List<Track>): Int = write {
        if (playlistExists(playlistId)) appendNow(playlistId, tracks) else 0
    }

    suspend fun removeFromPlaylist(playlistId: String, itemId: Long): Boolean = write {
        val ids = queries.playlistItemIds(playlistId).executeAsList()
        if (itemId !in ids) {
            false
        } else {
            queries.deletePlaylistItem(itemId)
            renumber(ids - itemId)
            queries.deleteUnusedTracks()
            true
        }
    }

    /** Memindahkan entri dari indeks [from] ke [to]; indeks di luar daftar diabaikan. */
    suspend fun movePlaylistItem(playlistId: String, from: Int, to: Int): Boolean = write {
        val ids = queries.playlistItemIds(playlistId).executeAsList().toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) {
            false
        } else {
            ids.add(to, ids.removeAt(from))
            renumber(ids)
            true
        }
    }

    // -- Riwayat --

    /** Riwayat terbaru lebih dulu; setiap lagu muncul sekali. */
    fun history(limit: Int = HISTORY_LIMIT): Flow<List<Track>> =
        queries.history(limit.toLong(), ::trackOf).asFlow().mapToList(dispatcher)

    /** Mencatat lagu yang diputar; riwayat dibatasi [HISTORY_LIMIT] lagu unik. */
    suspend fun recordPlay(track: Track) {
        write {
            upsert(track)
            queries.recordPlay(track.id, now())
            queries.trimHistory(HISTORY_LIMIT.toLong())
            queries.deleteUnusedTracks()
        }
    }

    // -- Item tersimpan --

    val savedItems: Flow<List<SavedItem>> = queries.savedItems(::SavedItem).asFlow().mapToList(dispatcher)

    suspend fun setSaved(kind: String, id: String, metadata: String, saved: Boolean) {
        write {
            if (saved) queries.saveItem(kind, id, metadata, now()) else queries.unsaveItem(kind, id)
        }
    }

    // -- Bantuan --

    private suspend fun <T> write(block: () -> T): T =
        withContext(dispatcher) { database.transactionWithResult { block() } }

    private fun setFavoriteNow(track: Track, favorite: Boolean) {
        if (favorite) {
            upsert(track)
            queries.addFavorite(track.id, now())
        } else {
            queries.removeFavorite(track.id)
            queries.deleteUnusedTracks()
        }
    }

    private fun appendNow(playlistId: String, tracks: List<Track>): Int {
        var position = queries.playlistItemCount(playlistId).executeAsOne()
        for (track in tracks) {
            upsert(track)
            queries.insertPlaylistItem(playlistId, track.id, position++)
        }
        return tracks.size
    }

    private fun renumber(itemIds: List<Long>) {
        itemIds.forEachIndexed { index, itemId -> queries.setItemPosition(index.toLong(), itemId) }
    }

    private fun playlistExists(id: String) = queries.playlistById(id).executeAsOneOrNull() != null

    /** Data lagu terbaru menang, misalnya sampul yang baru tersedia. */
    private fun upsert(track: Track) {
        queries.upsertTrack(
            track.id,
            track.title,
            encodeArtists(track.artists),
            track.durationMs,
            track.artworkUrl,
            track.source,
        )
    }

    companion object {
        /** Sama dengan batas riwayat desktop. */
        const val HISTORY_LIMIT = 200
        private const val DEFAULT_PLAYLIST_NAME = "Playlist baru"
    }
}

/** Library di atas driver SQLite platform; driver membuat skema saat database pertama kali dibuka. */
fun LibraryRepository(driver: SqlDriver): LibraryRepository = LibraryRepository(SenadaDatabase(driver))

private fun trackOf(
    id: String,
    title: String,
    artists: String,
    durationMs: Long?,
    artworkUrl: String?,
    source: String,
) = Track(id, title, decodeArtists(artists), durationMs, artworkUrl, source)

internal fun encodeArtists(artists: List<String>): String = JsonArray(artists.map { JsonPrimitive(it) }).toString()

/** Data rusak tidak menggagalkan seluruh library; lagunya tampil tanpa artis. */
internal fun decodeArtists(text: String): List<String> =
    runCatching { Json.parseToJsonElement(text).jsonArray.map { it.jsonPrimitive.content } }.getOrDefault(emptyList())

private fun localPlaylistId(): String =
    "local-" + Random.nextBytes(6).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
