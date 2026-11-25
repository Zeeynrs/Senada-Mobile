package senada.app.nowplaying

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.app.Appearance
import senada.app.AppearanceStore
import senada.app.FullViewTheme
import senada.app.ThemeMode

class VinylTest {
    @Test
    fun needleCoversTheGroovesWithoutChangingTheArmLength() {
        val stage = VinylGeometry(width = 390f, height = 370f)
        val start = stage.needleAt(0f)
        val end = stage.needleAt(1f)

        assertTrue((start - stage.center).getDistance() > (end - stage.center).getDistance())
        assertEquals((start - stage.pivot).getDistance(), (end - stage.pivot).getDistance(), 0.001f)
        for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
            assertEquals(fraction, stage.progressAt(stage.needleAt(fraction)), 0.0001f)
        }
        assertEquals(1f, stage.progressAt(stage.center))
        assertEquals(0f, stage.progressAt(stage.center + Offset(1_000f, 0f)))
        assertEquals(45_000L, vinylSeekPosition(.25f, 180_000L))
    }

    @Test
    fun appearanceRestoresVinylAndKeepsLegacyOrUnknownThemesOnStandard() {
        var saved: String? = null
        val store = AppearanceStore(read = { saved }, write = { saved = it })
        val vinyl = Appearance(mode = ThemeMode.Dark, fullViewTheme = FullViewTheme.Vinyl)
        store.save(vinyl)
        assertEquals(vinyl, store.load())

        saved = """{"version":3,"mode":"Dark","accent":"senada"}"""
        assertEquals(FullViewTheme.Standard, store.load().fullViewTheme)
        saved = """{"version":3,"mode":"Dark","accent":"senada","fullViewTheme":"unknown"}"""
        assertEquals(FullViewTheme.Standard, store.load().fullViewTheme)
        assertEquals(ThemeMode.Dark, store.load().mode)
    }
}
