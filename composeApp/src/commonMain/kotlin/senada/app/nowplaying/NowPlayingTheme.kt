package senada.app.nowplaying

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import senada.app.theme.senadaColorScheme

internal fun nowPlayingColorScheme(accent: Color?): ColorScheme = senadaColorScheme(dark = true, accent = accent)
