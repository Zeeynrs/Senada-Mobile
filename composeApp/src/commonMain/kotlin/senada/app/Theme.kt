package senada.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import senada.app.theme.SenadaShapes
import senada.app.theme.senadaColorScheme
import senada.app.theme.senadaTypography

private val artworkColors = ArtworkAccentCache()

internal class AppearancePreferences(private val store: AppearanceStore) {
    var settings by mutableStateOf<Appearance?>(null)
        private set

    suspend fun restore() {
        settings = withContext(Dispatchers.Default) { store.load() }
    }

    fun selectMode(mode: ThemeMode) = update { it.copy(mode = mode) }
    fun followCover(enabled: Boolean) = update { it.copy(followCover = enabled) }
    fun saveMobileData(enabled: Boolean) = update { it.copy(saveMobileData = enabled) }

    private fun update(change: (Appearance) -> Appearance) {
        val previous = settings ?: return
        val next = change(previous)
        if (next == previous) return
        store.save(next)
        settings = next
    }
}

@Composable
internal fun SenadaTheme(artworkUrl: String?, visible: Boolean, content: @Composable (AppearancePreferences) -> Unit) {
    val context = LocalPlatformContext.current
    val preferences = remember(context) { AppearancePreferences(appearanceStore(context)) }
    LaunchedEffect(preferences) { preferences.restore() }
    val settings = preferences.settings
    val dark = when (settings?.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }
    val url = artworkUrl?.takeIf { settings?.followCover == true && it.isNotBlank() }
    var source by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, visible) {
        if (url != null && visible) source = artworkColors.get(url) { loadArtworkAccent(context, url) }
    }
    val colors = remember(dark, source) { senadaColorScheme(dark, source) }
    MaterialTheme(colorScheme = colors, typography = senadaTypography(), shapes = SenadaShapes) { content(preferences) }
}
