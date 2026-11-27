package senada.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.app.Appearance
import senada.app.AppearanceStore

class LyricsAppearanceTest {
    @Test
    fun appearanceStoreKeepsLyricsChoicesAcrossSessions() {
        val appearance = LyricsAppearance(
            LyricsSize.Large, LyricsFont.Serif, LyricsAlignment.Center, LyricsHighlight.Glow,
            romanization = true,
        )
        var saved: String? = null
        AppearanceStore({ saved }, { saved = it }).save(Appearance(lyricsAppearance = appearance))

        assertEquals(appearance, AppearanceStore({ saved }, {}).load().lyricsAppearance)
    }

    @Test
    fun olderSettingsDefaultToKaraokeAndKeepExplicitChoices() {
        fun load(json: String) = AppearanceStore({ json }, {}).load().lyricsAppearance

        assertEquals(LyricsAppearance(), load("""{"version":3,"mode":"Dark","accent":"senada"}"""))
        assertEquals(LyricsHighlight.Karaoke, load("""{"version":3,"mode":"Dark","accent":"senada"}""").highlight)
        assertEquals(
            LyricsHighlight.Bright,
            load("""{"version":3,"mode":"Dark","accent":"senada","lyricsAppearance":{"highlight":"bright"}}""").highlight,
        )
        assertEquals(
            LyricsAppearance(alignment = LyricsAlignment.Center, highlight = LyricsHighlight.Karaoke),
            load("""{"version":3,"mode":"Light","accent":"mint","lyricsAppearance":{"size":6,"font":"other","alignment":"center","highlight":"karaoke"}}"""),
        )
    }
}
