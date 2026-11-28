package senada.app

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import senada.catalog.ArtistIdIndex
import senada.catalog.ArtistResolver
import senada.catalog.CatalogBrowser
import senada.catalog.HomeCatalog
import senada.catalog.StreamResolver
import senada.catalog.spotify.SpotifyPlaylistReader
import senada.catalog.spotify.SpotifyPlaylistSource
import senada.catalog.youtube.CandidateSearcher
import senada.catalog.youtube.DiscoverSource
import senada.catalog.youtube.InnertubeCandidateSearcher
import senada.catalog.youtube.InnertubeClient
import senada.catalog.youtube.YouTubeMusicCatalog
import senada.catalog.youtube.YouTubeMusicDiscoverSource
import senada.catalog.youtube.YouTubeMusicHome
import senada.catalog.youtube.YouTubeMusicPlaylists
import senada.catalog.youtube.YouTubeMusicResolver
import senada.catalog.youtube.YouTubePlaylistSource
import senada.lyrics.LyricsRepository
import senada.lyrics.sources.defaultLyricsSources

/** Sumber katalog dan audio bersama untuk seluruh proses, termasuk pemutaran di latar belakang. */
internal object SenadaSources {
    /** Dipakai bersama juga untuk gambar sampul. */
    val http by lazy {
        // Engine dipilih dari dependency platform: OkHttp di Android, NSURLSession di iOS.
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
        }
    }
    private val innertube by lazy { InnertubeClient(http) }
    val artistIds = ArtistIdIndex()
    private val catalog by lazy { YouTubeMusicCatalog(innertube, artistIds) }

    val youTubeMusic: CatalogBrowser get() = catalog
    val artistResolver by lazy { ArtistResolver(youTubeMusic, artistIds) }
    val discover: DiscoverSource by lazy { YouTubeMusicDiscoverSource(innertube) }
    val homeCatalog: HomeCatalog by lazy { YouTubeMusicHome(innertube) }
    val youTubePlaylists: YouTubePlaylistSource by lazy { YouTubeMusicPlaylists(innertube) }
    val spotifyPlaylists: SpotifyPlaylistSource by lazy { SpotifyPlaylistReader(http) }
    val candidateSearcher: CandidateSearcher by lazy { InnertubeCandidateSearcher(innertube) }
    val streams: StreamResolver by lazy { YouTubeMusicResolver(innertube) }
    val lyrics by lazy { LyricsRepository(http, wordSources = defaultLyricsSources(http)) }
}
