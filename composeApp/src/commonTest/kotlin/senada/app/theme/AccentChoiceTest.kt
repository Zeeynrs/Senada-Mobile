package senada.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.app.contrastRatio

class AccentChoiceTest {
    @Test
    fun senada_keeps_its_theme_specific_teal_even_with_a_cover() {
        val source = AccentChoice.Senada.source(Color.Red)
        assertEquals(Color(0xFF67CDB1), senadaColorScheme(dark = true, accent = source).primary)
        assertEquals(Color(0xFF0F7B67), senadaColorScheme(dark = false, accent = source).primary)
    }

    @Test
    fun following_the_cover_uses_its_color_or_falls_back_to_senada_teal() {
        val cover = Color(0xFF26A69A)
        val source = AccentChoice.FollowCover.source(cover)
        assertEquals(cover, source)
        assertEquals(cover, senadaColorScheme(dark = true, accent = source).primary)

        val missing = AccentChoice.FollowCover.source(null)
        assertEquals(Color(0xFF67CDB1), senadaColorScheme(dark = true, accent = missing).primary)
        assertEquals(Color(0xFF0F7B67), senadaColorScheme(dark = false, accent = missing).primary)
    }

    @Test
    fun named_accents_resolve_to_desktop_colors_and_ignore_the_cover() {
        val expected = mapOf(
            AccentChoice.Mint to Color(0xFF1ED760),
            AccentChoice.Indigo to Color(0xFF5865F2),
            AccentChoice.Sky to Color(0xFF2AABEE),
            AccentChoice.Aurora to Color(0xFF8B5CF6),
            AccentChoice.Sunset to Color(0xFFFF6B5A),
            AccentChoice.Ocean to Color(0xFF14B8A6),
            AccentChoice.Forest to Color(0xFF65A30D),
            AccentChoice.Berry to Color(0xFFD946EF),
            AccentChoice.Amber to Color(0xFFF59E0B),
            AccentChoice.Rose to Color(0xFFF43F5E),
            AccentChoice.Carbon to Color(0xFF8A8F98),
            AccentChoice.Chrome to Color(0xFFA78BFA),
        )
        for ((choice, color) in expected) {
            assertEquals(color, choice.source(Color.Red), choice.label)
            assertEquals(color, choice.source(null), choice.label)
        }
    }

    @Test
    fun every_accent_and_text_on_it_pass_contrast_in_both_themes() {
        for (dark in listOf(false, true)) {
            for (choice in AccentChoice.entries) {
                val scheme = senadaColorScheme(dark, choice.source(Color(0xFF26A69A)))
                val context = "${choice.label}, gelap=$dark"
                assertTrue(contrastRatio(scheme.primary, scheme.background) >= 3f, context)
                assertTrue(contrastRatio(scheme.primary, scheme.onPrimary) >= 4.5f, context)
            }
        }
    }
}
