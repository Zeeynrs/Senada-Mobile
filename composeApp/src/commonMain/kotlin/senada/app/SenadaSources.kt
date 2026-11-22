package senada.app

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import senada.catalog.CatalogBrowser
import senada.catalog.StreamResolver
import senada.catalog.youtube.InnertubeClient
import senada.catalog.youtube.YouTubeMusicCatalog
import senada.catalog.youtube.YouTubeMusicResolver
import senada.lyrics.LyricsRepository

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

    val youTubeMusic: CatalogBrowser by lazy { YouTubeMusicCatalog(innertube) }
    val streams: StreamResolver by lazy { YouTubeMusicResolver(innertube) }
    val lyrics by lazy { LyricsRepository(http) }
}
