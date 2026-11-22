package senada.catalog

import senada.model.Track

/** Satu kartu atau baris di rak Beranda dari YouTube Music. */
sealed interface HomeItem {
    data class Song(val track: Track) : HomeItem
    data class Album(val album: AlbumSummary) : HomeItem
    data class Artist(val artist: ArtistSummary) : HomeItem
    data class Playlist(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val artworkUrl: String? = null,
    ) : HomeItem
}

enum class HomeShelfSource { Home, Explore, Charts }

/** Satu rak Beranda; [layout] menentukan bentuk tampilannya. */
data class HomeShelf(
    val title: String,
    val source: HomeShelfSource,
    val items: List<HomeItem>,
    val layout: HomeShelfLayout,
)

enum class HomeShelfLayout {
    QuickPicks,
    SongList,
    Cards,
    Artists,
}

/** Genre/mood dari Explore; membuka halaman berisi playlist. */
data class MoodChip(val title: String, val browseId: String, val params: String?)

data class HomeFeed(val shelves: List<HomeShelf>, val moods: List<MoodChip> = emptyList())

interface HomeCatalog {
    /** Rak Beranda dari YouTube Music. [saveData] mengurangi permintaan (hanya halaman pertama beranda, tanpa Explore/Charts). */
    suspend fun homeFeed(saveData: Boolean): HomeFeed

    /** Isi satu mood/genre: rak playlist. */
    suspend fun mood(chip: MoodChip): List<HomeShelf>
}
