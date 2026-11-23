package senada.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.app.theme.AccentChoice
import senada.app.theme.senadaColorScheme

class SenadaSwitchTest {
    @Test
    fun darkDefaultMatchesMockupColors() {
        val scheme = senadaColorScheme(dark = true, accent = null)

        val onColors = senadaSwitchColors(scheme, checked = true)
        assertEquals(Color(0xFF67CDB1), onColors.track)
        assertEquals(Color(0xFF0B0C0E), onColors.thumb)

        val offColors = senadaSwitchColors(scheme, checked = false)
        assertEquals(Color(0xFF2A2C31), offColors.track)
        assertEquals(Color(0xFFA3A6AD), offColors.thumb)
    }

    @Test
    fun checkedUsesPrimaryAccentAndHighContrastThumbInBothThemes() {
        for (dark in listOf(true, false)) {
            for (choice in AccentChoice.entries) {
                val scheme = senadaColorScheme(dark = dark, accent = choice.source(Color(0xFF3366AA)))
                val colors = senadaSwitchColors(scheme, checked = true)
                assertEquals(scheme.primary, colors.track)
                assertTrue(
                    contrastRatio(colors.thumb, colors.track) >= 4.5f,
                    "dark=$dark choice=$choice thumb=${colors.thumb} track=${colors.track}",
                )
            }
        }
    }

    @Test
    fun lightUncheckedMaintainsVisibleTrackAndThumbContrast() {
        val scheme = senadaColorScheme(dark = false, accent = null)
        val offColors = senadaSwitchColors(scheme, checked = false)

        assertEquals(Color(0xFFC9C6BF), offColors.track)
        assertEquals(Color(0xFF5D6168), offColors.thumb)
        assertTrue(contrastRatio(offColors.thumb, offColors.track) >= 3.0f)
        assertTrue(contrastRatio(offColors.track, scheme.surfaceContainer) >= 1.5f)
    }
}
