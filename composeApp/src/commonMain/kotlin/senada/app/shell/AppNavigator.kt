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

/**
 * Posisi navigasi aplikasi: tab yang aktif dan layar yang terbuka di atasnya. Nilainya state Compose,
 * jadi layar ikut berubah saat navigator berubah.
 */
internal class AppNavigator(
    destination: AppDestination = AppDestination.Home,
    nowPlayingOpen: Boolean = false,
    openedPlaylistId: String? = null,
    mostPlayedOpen: Boolean = false,
) {
    var destination by mutableStateOf(destination)
        private set
    var nowPlayingOpen by mutableStateOf(nowPlayingOpen)
        private set
    var openedPlaylistId by mutableStateOf(openedPlaylistId)
        private set
    var mostPlayedOpen by mutableStateOf(mostPlayedOpen)
        private set

    /** Benar bila tombol kembali masih punya sesuatu untuk ditutup sebelum keluar aplikasi. */
    val canGoBack: Boolean
        get() = nowPlayingOpen || mostPlayedOpen || openedPlaylistId != null || destination != AppDestination.Home

    /** Pindah tab menutup layar detail tab sebelumnya. */
    fun select(destination: AppDestination) {
        this.destination = destination
        openedPlaylistId = null
        mostPlayedOpen = false
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
        mostPlayedOpen = true
    }

    /** Menutup lapisan paling atas: Sedang diputar, Paling sering diputar, detail playlist, lalu kembali ke Beranda. */
    fun back(): Boolean {
        when {
            nowPlayingOpen -> nowPlayingOpen = false
            mostPlayedOpen -> mostPlayedOpen = false
            openedPlaylistId != null -> openedPlaylistId = null
            destination != AppDestination.Home -> destination = AppDestination.Home
            else -> return false
        }
        return true
    }

    companion object {
        val Saver: Saver<AppNavigator, Any> = listSaver(
            save = { listOf(it.destination.name, it.nowPlayingOpen, it.openedPlaylistId, it.mostPlayedOpen) },
            restore = { saved ->
                AppNavigator(
                    destination = AppDestination.valueOf(saved[0] as String),
                    nowPlayingOpen = saved[1] as Boolean,
                    openedPlaylistId = saved[2] as String?,
                    mostPlayedOpen = saved[3] as Boolean,
                )
            },
        )
    }
}

/** Navigator yang bertahan saat layar diputar atau proses dipulihkan. */
@Composable
internal fun rememberAppNavigator(): AppNavigator = rememberSaveable(saver = AppNavigator.Saver) { AppNavigator() }
