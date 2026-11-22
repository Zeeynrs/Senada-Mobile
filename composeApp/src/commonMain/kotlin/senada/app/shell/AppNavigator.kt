package senada.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** Empat tujuan di navigasi bawah. */
internal enum class AppDestination(val label: String) {
    Home("Beranda"),
    Search("Cari"),
    Library("Library"),
    Settings("Pengaturan"),
}

/** Rute halaman katalog di atas tab (artis, album, atau playlist), disusun sebagai tumpukan kembali. */
internal sealed interface CatalogRoute {
    val id: String
    val label: String
    val artworkUrl: String?

    data class Artist(
        override val id: String,
        val name: String,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override val label: String get() = name.ifBlank { "Artis" }
    }

    data class Album(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override val label: String get() = title.ifBlank { "Album" }
    }

    data class Playlist(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override val label: String get() = title.ifBlank { "Playlist" }
    }
}

/**
 * Posisi navigasi aplikasi: tab yang aktif dan layar yang terbuka di atasnya. Nilainya state Compose,
 * jadi layar ikut berubah saat navigator berubah.
 */
internal class AppNavigator(
    destination: AppDestination = AppDestination.Home,
    nowPlayingOpen: Boolean = false,
    openedPlaylistId: String? = null,
    mostPlayedOpen: Boolean = false,
    catalogStack: List<CatalogRoute> = emptyList(),
    listeningStatsOpen: Boolean = false,
) {
    var destination by mutableStateOf(destination)
        private set
    var nowPlayingOpen by mutableStateOf(nowPlayingOpen)
        private set
    var openedPlaylistId by mutableStateOf(openedPlaylistId)
        private set
    var mostPlayedOpen by mutableStateOf(mostPlayedOpen)
        private set
    var catalogStack by mutableStateOf(catalogStack)
        private set
    var listeningStatsOpen by mutableStateOf(listeningStatsOpen)
        private set

    val currentCatalogRoute: CatalogRoute?
        get() = catalogStack.lastOrNull()

    /** Label layar sebelum rute katalog teratas, dipakai untuk tombol kembali. */
    fun previousCatalogLabel(): String =
        catalogStack.dropLast(1).lastOrNull()?.label ?: when {
            listeningStatsOpen -> "Statistik dengar"
            mostPlayedOpen -> "Paling sering diputar"
            else -> destination.label
        }

    /** Benar bila tombol kembali masih punya sesuatu untuk ditutup sebelum keluar aplikasi. */
    val canGoBack: Boolean
        get() = nowPlayingOpen || mostPlayedOpen || listeningStatsOpen || catalogStack.isNotEmpty() ||
            openedPlaylistId != null || destination != AppDestination.Home

    /** Pindah tab menutup layar detail tab sebelumnya. */
    fun select(destination: AppDestination) {
        this.destination = destination
        openedPlaylistId = null
        mostPlayedOpen = false
        catalogStack = emptyList()
        listeningStatsOpen = false
    }

    fun openNowPlaying() {
        nowPlayingOpen = true
    }

    fun closeNowPlaying() {
        nowPlayingOpen = false
    }

    fun openPlaylist(id: String) {
        openedPlaylistId = id
    }

    fun openMostPlayed() {
        listeningStatsOpen = false
        mostPlayedOpen = true
    }

    fun openArtist(id: String, name: String = "", artworkUrl: String? = null) {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) return
        nowPlayingOpen = false
        val top = catalogStack.lastOrNull()
        if (top is CatalogRoute.Artist && top.id == cleanId) return
        catalogStack = catalogStack + CatalogRoute.Artist(cleanId, name, artworkUrl)
    }

    fun openAlbum(id: String, title: String = "", subtitle: String? = null, artworkUrl: String? = null) {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) return
        nowPlayingOpen = false
        val top = catalogStack.lastOrNull()
        if (top is CatalogRoute.Album && top.id == cleanId) return
        catalogStack = catalogStack + CatalogRoute.Album(cleanId, title, subtitle, artworkUrl)
    }

    fun openCatalogPlaylist(id: String, title: String = "", subtitle: String? = null, artworkUrl: String? = null) {
        val cleanId = id.trim().removePrefix("VL")
        if (cleanId.isEmpty()) return
        nowPlayingOpen = false
        val top = catalogStack.lastOrNull()
        if (top is CatalogRoute.Playlist && top.id == cleanId) return
        catalogStack = catalogStack + CatalogRoute.Playlist(cleanId, title, subtitle, artworkUrl)
    }

    fun openListeningStats() {
        mostPlayedOpen = false
        listeningStatsOpen = true
    }

    /** Menutup lapisan paling atas: Sedang diputar, halaman artis/album/playlist, detail tab, lalu kembali ke Beranda. */
    fun back(): Boolean {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            catalogStack.isNotEmpty() -> catalogStack = catalogStack.dropLast(1)
            listeningStatsOpen -> listeningStatsOpen = false
            mostPlayedOpen -> mostPlayedOpen = false
            openedPlaylistId != null -> openedPlaylistId = null
            destination != AppDestination.Home -> destination = AppDestination.Home
            else -> return false
        }
        return true
    }

    companion object {
        private const val ROUTE_FIELDS = 5
        private const val BASE_FIELDS = 4

        val Saver: Saver<AppNavigator, Any> = listSaver(
            save = { nav ->
                buildList<Any?> {
                    add(nav.destination.name)
                    add(nav.nowPlayingOpen)
                    add(nav.openedPlaylistId)
                    add(nav.mostPlayedOpen)
                    nav.catalogStack.forEach { route ->
                        when (route) {
                            is CatalogRoute.Artist -> {
                                add("artist")
                                add(route.id)
                                add(route.name)
                                add(null)
                                add(route.artworkUrl)
                            }
                            is CatalogRoute.Album -> {
                                add("album")
                                add(route.id)
                                add(route.title)
                                add(route.subtitle)
                                add(route.artworkUrl)
                            }
                            is CatalogRoute.Playlist -> {
                                add("playlist")
                                add(route.id)
                                add(route.title)
                                add(route.subtitle)
                                add(route.artworkUrl)
                            }
                        }
                    }
                    // Simpan penanda setelah blok rute agar susunan rute lama tetap bisa dipulihkan.
                    add(nav.listeningStatsOpen)
                }
            },
            restore = { saved ->
                val routes = buildList {
                    var i = BASE_FIELDS
                    while (i + ROUTE_FIELDS - 1 < saved.size) {
                        val kind = saved[i] as? String
                        val id = saved[i + 1] as? String
                        val label = saved[i + 2] as? String ?: ""
                        val subtitle = saved[i + 3] as? String
                        val artwork = saved[i + 4] as? String
                        if (!id.isNullOrBlank()) {
                            when (kind) {
                                "artist" -> add(CatalogRoute.Artist(id, label, artwork))
                                "album" -> add(CatalogRoute.Album(id, label, subtitle, artwork))
                                "playlist" -> add(CatalogRoute.Playlist(id, label, subtitle, artwork))
                            }
                        }
                        i += ROUTE_FIELDS
                    }
                }
                AppNavigator(
                    destination = AppDestination.valueOf(saved[0] as String),
                    nowPlayingOpen = saved[1] as Boolean,
                    openedPlaylistId = saved[2] as String?,
                    mostPlayedOpen = saved[3] as Boolean,
                    catalogStack = routes,
                    listeningStatsOpen = saved.size > BASE_FIELDS && saved.lastOrNull() == true,
                )
            },
        )
    }
}

/** Navigator yang bertahan saat layar diputar atau proses dipulihkan. */
@Composable
internal fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) { AppNavigator() }
