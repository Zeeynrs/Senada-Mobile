package senada.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import senada.app.contrastRatio

class SenadaTokensTest {
    @Test
    fun darkSchemeUsesSenadaColors() {
        val scheme = senadaColorScheme(dark = true, accent = null)

        assertEquals(Color(0xFF0B0C0E), scheme.background)
        assertEquals(Color(0xFF67CDB1), scheme.primary)
    }

    @Test
    fun lightSchemeUsesDarkerTeal() {
        val scheme = senadaColorScheme(dark = false, accent = null)

        assertEquals(Color(0xFF0F7B67), scheme.primary)
        assertEquals(Color(0xFFF7F6F3), scheme.background)
    }

    @Test
    fun coverAccentIsMadeReadable() {
        val scheme = senadaColorScheme(dark = false, accent = Color(0xFFFFF3B0))

        assertTrue(contrastRatio(scheme.primary, scheme.background) >= 4.5f)
    }

    @Test
    fun textOnPrimaryIsReadable() {
        for (dark in listOf(true, false)) {
            for (accent in listOf(Color(0xFF67CDB1), Color(0xFF1F5FB0))) {
                val scheme = senadaColorScheme(dark, accent)
                assertTrue(contrastRatio(scheme.onPrimary, scheme.primary) >= 4.5f, "dark=$dark accent=$accent")
            }
        }
    }

    @Test
    fun mutedTextIsReadable() {
        for (dark in listOf(true, false)) {
            val scheme = senadaColorScheme(dark, accent = null)
            assertTrue(contrastRatio(scheme.onSurfaceVariant, scheme.background) >= 4.5f, "dark=$dark")
        }
    }
}
