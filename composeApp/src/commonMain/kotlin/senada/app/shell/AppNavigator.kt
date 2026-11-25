package senada.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.catalog.MoodChip
import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE
import senada.resources.Res
import senada.resources.catalog_type_album
import senada.resources.catalog_type_artist
import senada.resources.catalog_type_mood
import senada.resources.catalog_type_playlist
import senada.resources.nav_home
import senada.resources.nav_library
import senada.resources.nav_profile
import senada.resources.nav_search
import senada.resources.route_about
import senada.resources.route_appearance
import senada.resources.route_audio_lab
import senada.resources.route_backup
import senada.resources.route_developer
import senada.resources.route_equalizer
import senada.resources.route_language
import senada.resources.route_lyrics
import senada.resources.route_most_played
import senada.resources.route_playback
import senada.resources.route_sound_fx
import senada.resources.route_stats
import senada.resources.route_storage

/** Empat tujuan di navigasi bawah. */
internal enum class AppDestination {
    Home, Search, Library, Profile;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Home -> AppStrings.get(Res.string.nav_home, language)
        Search -> AppStrings.get(Res.string.nav_search, language)
        Library -> AppStrings.get(Res.string.nav_library, language)
        Profile -> AppStrings.get(Res.string.nav_profile, language)
    }

    val label: String get() = label()
}

internal enum class ProfileRoute {
    Stats,
    MostPlayed,
    AudioLab,
    Equalizer,
    SoundFx,
    Appearance,
    Lyrics,
    Language,
    Playback,
    Backup,
    Storage,
    About,
    Developer;

    fun label(language: AppLanguage = AppStrings.currentLanguage): String = when (this) {
        Stats -> AppStrings.get(Res.string.route_stats, language)
        MostPlayed -> AppStrings.get(Res.string.route_most_played, language)
        AudioLab -> AppStrings.get(Res.string.route_audio_lab, language)
        Equalizer -> AppStrings.get(Res.string.route_equalizer, language)
        SoundFx -> AppStrings.get(Res.string.route_sound_fx, language)
        Appearance -> AppStrings.get(Res.string.route_appearance, language)
        Lyrics -> AppStrings.get(Res.string.route_lyrics, language)
        Language -> AppStrings.get(Res.string.route_language, language)
        Playback -> AppStrings.get(Res.string.route_playback, language)
        Backup -> AppStrings.get(Res.string.route_backup, language)
        Storage -> AppStrings.get(Res.string.route_storage, language)
        About -> AppStrings.get(Res.string.route_about, language)
        Developer -> AppStrings.get(Res.string.route_developer, language)
    }

    val label: String get() = label()
}

/** Rute halaman katalog di atas tab (artis, album, playlist, atau mood), disusun sebagai tumpukan kembali. */
internal sealed interface CatalogRoute {
    val id: String
    val label: String
    val artworkUrl: String?
    fun label(language: AppLanguage = AppStrings.currentLanguage): String

    data class Artist(
        override val id: String,
        val name: String,
        override val artworkUrl: String? = null,
        val source: String = YOUTUBE_MUSIC_SOURCE,
    ) : CatalogRoute {
        override fun label(language: AppLanguage): String =
            name.ifBlank { AppStrings.get(Res.string.catalog_type_artist, language) }

        override val label: String get() = label()
    }

    data class Album(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override fun label(language: AppLanguage): String =
            title.ifBlank { AppStrings.get(Res.string.catalog_type_album, language) }

        override val label: String get() = label()
    }

    data class Playlist(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override fun label(language: AppLanguage): String =
            title.ifBlank { AppStrings.get(Res.string.catalog_type_playlist, language) }

        override val label: String get() = label()
    }

    data class Mood(
        override val id: String,
        val title: String,
        val params: String? = null,
        override val artworkUrl: String? = null,
    ) : CatalogRoute {
        override fun label(language: AppLanguage): String =
            title.ifBlank { AppStrings.get(Res.string.catalog_type_mood, language) }

        override val label: String get() = label()

        /** Kunci cache unik karena beberapa kategori mood berbagi browseId yang sama dan dibedakan oleh [params]. */
        fun cacheKey(): String = if (params.isNullOrBlank()) id else "$id|$params"

        fun toMoodChip(): MoodChip = MoodChip(title = label, browseId = id, params = params)
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
    catalogStack: List<CatalogRoute> = emptyList(),
    profileStack: List<ProfileRoute> = emptyList(),
) {
    var destination by mutableStateOf(destination)
        private set
    var nowPlayingOpen by mutableStateOf(nowPlayingOpen)
        private set
    var openedPlaylistId by mutableStateOf(openedPlaylistId)
        private set
    var catalogStack by mutableStateOf(catalogStack)
        private set
    var profileStack by mutableStateOf(profileStack)
        private set

    val currentProfileRoute: ProfileRoute? get() = profileStack.lastOrNull()

    val currentCatalogRoute: CatalogRoute?
        get() = catalogStack.lastOrNull()

    /** Label layar sebelum rute katalog teratas, dipakai untuk tombol kembali. */
    fun previousCatalogLabel(language: AppLanguage = AppStrings.currentLanguage): String =
        catalogStack.dropLast(1).lastOrNull()?.label(language)
            ?: currentProfileRoute?.label(language)
            ?: destination.label(language)

    /** Benar bila tombol kembali masih punya sesuatu untuk ditutup sebelum keluar aplikasi. */
    val canGoBack: Boolean
        get() = nowPlayingOpen || profileStack.isNotEmpty() || catalogStack.isNotEmpty() ||
            openedPlaylistId != null || destination != AppDestination.Home

    /** Pindah tab menutup layar detail tab sebelumnya. */
    fun select(destination: AppDestination) {
        this.destination = destination
        openedPlaylistId = null
        profileStack = emptyList()
        catalogStack = emptyList()
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

    fun openProfileRoute(route: ProfileRoute) {
        if (destination != AppDestination.Profile) select(AppDestination.Profile)
        if (route != currentProfileRoute) profileStack = profileStack + route
    }

    fun openArtist(
        id: String,
        name: String = "",
        artworkUrl: String? = null,
        source: String = YOUTUBE_MUSIC_SOURCE,
    ) {
        val cleanId = id.trim()
        val cleanSource = source.trim().ifEmpty { YOUTUBE_MUSIC_SOURCE }
        if (cleanId.isEmpty()) return
        nowPlayingOpen = false
        val top = catalogStack.lastOrNull()
        if (top is CatalogRoute.Artist && top.id == cleanId && top.source == cleanSource) return
        catalogStack = catalogStack + CatalogRoute.Artist(cleanId, name, artworkUrl, cleanSource)
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

    fun openMood(chip: MoodChip) = openMood(browseId = chip.browseId, title = chip.title, params = chip.params)

    fun openMood(browseId: String, title: String, params: String? = null) {
        val cleanId = browseId.trim()
        if (cleanId.isEmpty()) return
        val cleanParams = params?.trim()?.takeIf { it.isNotEmpty() }
        nowPlayingOpen = false
        val top = catalogStack.lastOrNull()
        if (top is CatalogRoute.Mood && top.id == cleanId && top.params == cleanParams) return
        catalogStack = catalogStack + CatalogRoute.Mood(cleanId, title, cleanParams)
    }

    /** Menutup lapisan paling atas: Sedang diputar, halaman artis/album/playlist, detail tab, lalu kembali ke Beranda. */
    fun back(): Boolean {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            catalogStack.isNotEmpty() -> catalogStack = catalogStack.dropLast(1)
            profileStack.isNotEmpty() -> profileStack = profileStack.dropLast(1)
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
                    add(nav.profileStack.joinToString(",") { it.name })
                    nav.catalogStack.forEach { route ->
                        when (route) {
                            is CatalogRoute.Artist -> {
                                add("artist")
                                add(route.id)
                                add(route.name)
                                add(route.source)
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
                            is CatalogRoute.Mood -> {
                                add("mood")
                                add(route.id)
                                add(route.title)
                                add(route.params)
                                add(route.artworkUrl)
                            }
                        }
                    }
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
                                "artist" -> add(
                                    CatalogRoute.Artist(
                                        id = id,
                                        name = label,
                                        artworkUrl = artwork,
                                        source = subtitle?.takeIf { it.isNotBlank() } ?: YOUTUBE_MUSIC_SOURCE,
                                    ),
                                )
                                "album" -> add(CatalogRoute.Album(id, label, subtitle, artwork))
                                "playlist" -> add(CatalogRoute.Playlist(id, label, subtitle, artwork))
                                "mood" -> add(CatalogRoute.Mood(id, label, params = subtitle, artworkUrl = artwork))
                            }
                        }
                        i += ROUTE_FIELDS
                    }
                }
                // Slot keempat dahulu boolean MostPlayed; Stats tersimpan setelah blok katalog.
                val profileStack = when (val field = saved[3]) {
                    is String -> field.split(",").mapNotNull { name -> ProfileRoute.entries.find { it.name == name } }
                    else -> when {
                        saved.size > BASE_FIELDS && saved.last() == true -> listOf(ProfileRoute.Stats)
                        field == true -> listOf(ProfileRoute.MostPlayed)
                        else -> emptyList()
                    }
                }
                AppNavigator(
                    destination = if (saved[0] == "Settings" || profileStack.isNotEmpty()) AppDestination.Profile
                        else AppDestination.valueOf(saved[0] as String),
                    nowPlayingOpen = saved[1] as Boolean,
                    openedPlaylistId = saved[2] as String?,
                    profileStack = profileStack,
                    catalogStack = routes,
                )
            },
        )
    }
}

/** Navigator yang bertahan saat layar diputar atau proses dipulihkan. */
@Composable
internal fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) { AppNavigator() }
