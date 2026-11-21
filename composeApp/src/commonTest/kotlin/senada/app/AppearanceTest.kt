package senada.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AppearanceTest {
    @Test
    fun settings_survive_reopening_the_store() {
        var saved: String? = null
        fun open() = AppearanceStore({ saved }, { saved = it })
        assertEquals(Appearance(), open().load())
        for (mode in ThemeMode.entries) {
            for (follow in listOf(false, true)) {
                val settings = Appearance(mode, follow)
                open().save(settings)
                assertEquals(settings, open().load())
            }
        }
    }

    @Test
    fun unreadable_or_invalid_settings_restore_both_defaults() {
        for (bad in listOf("{", "{}", """{"version":2,"mode":"Dark","followCover":false}""",
            """{"version":1,"mode":"Unknown","followCover":false}""",
            """{"version":1,"mode":"Dark","followCover":"yes"}""")) {
            assertEquals(Appearance(), AppearanceStore({ bad }, {}).load())
        }
        assertEquals(Appearance(), AppearanceStore({ error("gagal membaca") }, {}).load())
    }

    @Test
    fun accent_uses_the_mean_of_the_dominant_group_and_ignores_transparency() {
        val pixels = intArrayOf(0xffb02130.toInt(), 0xffb42334.toInt(), 0xff2288cc.toInt(), 0, 0, 0)
        assertEquals(Color(0xffb22232), dominantAccent(pixels))
        assertNull(dominantAccent(intArrayOf(0, 0x10667788)))
    }

    @Test
    fun accent_and_button_text_are_legible_in_both_modes() {
        for (background in listOf(Color.White, Color(0xff121212))) {
            for (source in listOf(Color.White, Color.Black, Color.Red, Color.Blue, Color.Yellow, Color.Gray)) {
                val accent = accessibleAccent(source, background)
                assertTrue(contrastRatio(accent, background) >= 4.5f)
                assertTrue(contrastRatio(accent, textOnAccent(accent)) >= 4.5f)
                if (contrastRatio(source, background) >= 4.5f) assertEquals(source, accent)
            }
        }
    }

    @Test
    fun each_cover_is_sampled_once_including_missing_images() = runTest {
        val cache = ArtworkAccentCache()
        val calls = mutableListOf<String>()
        suspend fun sample(url: String) = cache.get(url) {
            calls += url
            Color.Red.takeIf { url == "a" }
        }
        assertEquals(Color.Red, sample("a"))
        assertNull(sample("b"))
        assertEquals(Color.Red, sample("a"))
        assertNull(sample("b"))
        assertEquals(listOf("a", "b"), calls)
    }
}
