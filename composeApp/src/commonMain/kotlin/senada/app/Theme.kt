package senada.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import senada.app.theme.AccentChoice
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaShapes
import senada.app.theme.rememberSystemReduceMotion
import senada.app.theme.senadaColorScheme
import senada.app.theme.senadaLook
import senada.app.theme.senadaTypography
import senada.lyrics.LyricsAppearance

private val artworkColors = ArtworkAccentCache()

/** Aksen sampul asli untuk Sedang diputar, terlepas dari pilihan aksen aplikasi. */
internal val LocalArtworkAccent = compositionLocalOf<Color?> { null }

internal class AppearancePreferences(private val store: AppearanceStore) {
    var settings by mutableStateOf<Appearance?>(null)
        private set

    var lastFixedAccent by mutableStateOf(AccentChoice.Senada)
        private set

    suspend fun restore() {
        val loaded = withContext(Dispatchers.Default) { store.load() }
        if (loaded.accent != AccentChoice.FollowCover) {
            lastFixedAccent = loaded.accent
        }
        AppStrings.setLanguage(loaded.language)
        settings = loaded
    }

    fun selectMode(mode: ThemeMode) = update { it.copy(mode = mode) }

    fun selectAccent(accent: AccentChoice) {
        if (accent != AccentChoice.FollowCover) {
            lastFixedAccent = accent
        }
        update { it.copy(accent = accent) }
    }

    fun setFollowCover(enabled: Boolean) {
        val current = settings ?: return
        if (enabled) {
            if (current.accent != AccentChoice.FollowCover) {
                lastFixedAccent = current.accent
            }
            update { it.copy(accent = AccentChoice.FollowCover) }
        } else {
            update { it.copy(accent = lastFixedAccent) }
        }
    }

    fun saveMobileData(enabled: Boolean) = update { it.copy(saveMobileData = enabled) }

    fun setCoverRadius(radiusDp: Int) = update { it.copy(coverRadius = clampCoverRadius(radiusDp)) }

    fun setPlayerBackdropFromCover(enabled: Boolean) = update { it.copy(playerBackdropFromCover = enabled) }

    fun setGlass(enabled: Boolean) = update { it.copy(glass = enabled) }

    fun selectAnimationSpeed(speed: AnimationSpeed) = update { it.copy(animation = speed) }

    fun selectFullViewTheme(theme: FullViewTheme) = update { it.copy(fullViewTheme = theme) }

    fun updateLyricsAppearance(change: (LyricsAppearance) -> LyricsAppearance) =
        update { it.copy(lyricsAppearance = change(it.lyricsAppearance)) }

    fun selectLanguage(language: AppLanguage) {
        AppStrings.setLanguage(language)
        update { it.copy(language = language) }
    }

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
    remember(context) { registerPlatformContext(context) }
    val preferences = remember(context) { AppearancePreferences(appearanceStore(context)) }
    LaunchedEffect(preferences) {
        AppStrings.ensureLoadedAsync()
        preferences.restore()
    }
    val settings = preferences.settings ?: Appearance()
    AppStrings.setLanguage(settings.language)
    val systemReduceMotion = rememberSystemReduceMotion()
    val look = remember(settings, systemReduceMotion) { senadaLook(settings, systemReduceMotion) }
    val dark = when (settings.mode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        else -> isSystemInDarkTheme()
    }
    val url = artworkUrl?.takeIf { it.isNotBlank() }
    var cover by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url, visible) {
        if (url != null && visible) cover = artworkColors.get(url) { loadArtworkAccent(context, url) }
    }
    val source = settings.accent.source(cover)
    val colors = remember(dark, source) { senadaColorScheme(dark, source) }
    CompositionLocalProvider(
        LocalArtworkAccent provides cover,
        LocalSenadaLook provides look,
        LocalAppLanguage provides settings.language,
    ) {
        MaterialTheme(colorScheme = colors, typography = senadaTypography(), shapes = SenadaShapes) { content(preferences) }
    }
}
