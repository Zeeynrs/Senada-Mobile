package senada.catalog

import senada.model.Track

/** Ringkasan artis dari hasil pencarian katalog. */
data class ArtistSummary(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
)

/** Ringkasan album, EP, atau single dari pencarian maupun halaman artis. */
data class AlbumSummary(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val year: String? = null,
    val artworkUrl: String? = null,
)

data class ArtistPage(val items: List<ArtistSummary>, val nextPageToken: String? = null)

data class AlbumPage(val items: List<AlbumSummary>, val nextPageToken: String? = null)

/** Ringkasan playlist dari hasil pencarian katalog. */
data class PlaylistSummary(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
)

data class PlaylistPage(val items: List<PlaylistSummary>, val nextPageToken: String? = null)

/** Kelompok rilis di halaman artis, misalnya "Album" atau "Single & EP". */
data class ReleaseSection(
    val title: String,
    val items: List<AlbumSummary>,
)

/** Isi halaman artis dari katalog daring. */
data class ArtistDetail(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val monthlyListeners: String? = null,
    val topSongs: List<Track> = emptyList(),
    val topSongsPlaylistId: String? = null,
    val topSongAlbums: Map<String, AlbumSummary> = emptyMap(),
    val popularAlbums: List<AlbumSummary> = emptyList(),
    val newestAlbums: List<AlbumSummary> = emptyList(),
    val releases: List<ReleaseSection> = emptyList(),
)

/** Isi halaman album dari katalog daring. */
data class AlbumDetail(
    val id: String,
    val title: String,
    val artists: List<String> = emptyList(),
    val artistId: String? = null,
    val year: String? = null,
    val typeLabel: String? = null,
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
)

/** Isi halaman playlist dari katalog daring. */
data class PlaylistDetail(
    val id: String,
    val title: String,
    val owner: String? = null,
    val songCountText: String? = null,
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val truncated: Boolean = false,
)

/** Pencarian artis, album, dan playlist serta pembukaan halamannya di katalog daring. */
interface CatalogBrowser : CatalogRepository {
    suspend fun searchArtists(query: String, pageToken: String? = null): ArtistPage
    suspend fun searchAlbums(query: String, pageToken: String? = null): AlbumPage
    suspend fun searchPlaylists(query: String, pageToken: String? = null): PlaylistPage
    suspend fun artist(id: String): ArtistDetail
    suspend fun album(id: String): AlbumDetail
    suspend fun playlist(id: String): PlaylistDetail
}
