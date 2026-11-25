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
    fun olderSettingsUseDesktopDefaultsAndInvalidFieldsDoNotDiscardOtherChoices() {
        fun load(json: String) = AppearanceStore({ json }, {}).load().lyricsAppearance

        assertEquals(LyricsAppearance(), load("""{"version":3,"mode":"Dark","accent":"senada"}"""))
        assertEquals(
            LyricsAppearance(alignment = LyricsAlignment.Center, highlight = LyricsHighlight.Karaoke),
            load("""{"version":3,"mode":"Light","accent":"mint","lyricsAppearance":{"size":6,"font":"other","alignment":"center","highlight":"karaoke"}}"""),
        )
    }

    @Test
    fun karaokeProgressFollowsLineTimestampsAndClampsToTheLine() {
        assertEquals(0f, lyricProgress(positionMs = 900, startMs = 1_000, nextStartMs = 3_000))
        assertEquals(.5f, lyricProgress(positionMs = 2_000, startMs = 1_000, nextStartMs = 3_000))
        assertEquals(1f, lyricProgress(positionMs = 4_000, startMs = 1_000, nextStartMs = 3_000))
        assertEquals(.5f, lyricProgress(positionMs = 3_000, startMs = 1_000, nextStartMs = null))
    }

    @Test
    fun karaokeHighlightKeepsSurrogatePairsTogether() {
        val text = "A🎵B"
        assertEquals(1, sungTextEnd(text, .5f))
        assertEquals(3, sungTextEnd(text, .75f))
        assertEquals(text.length, sungTextEnd(text, 1f))
    }
}
