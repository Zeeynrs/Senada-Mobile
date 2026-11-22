package senada.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import senada.app.theme.AccentChoice

class AppearanceTest {
    @Test
    fun settings_survive_reopening_the_store() {
        var saved: String? = null
        fun open() = AppearanceStore({ saved }, { saved = it })
        assertEquals(Appearance(), open().load())
        for (mode in ThemeMode.entries) {
            for (accent in AccentChoice.entries) {
                for (saveData in listOf(false, true)) {
                    val settings = Appearance(mode, accent, saveData)
                    open().save(settings)
                    assertEquals(settings, open().load())
                }
            }
        }
    }

    @Test
    fun rewriting_version_one_settings_preserves_theme_data_saver_and_accent_choice() {
        val legacyChoices = mapOf(true to "followCover", false to "senada")
        for ((follow, accent) in legacyChoices) {
            var saved = """{"version":1,"mode":"Dark","followCover":$follow,"saveMobileData":true}"""
            val store = AppearanceStore({ saved }, { saved = it })
            val settings = store.load()
            assertEquals(ThemeMode.Dark, settings.mode)
            assertTrue(settings.saveMobileData)
            store.save(settings)

            val updated = Json.parseToJsonElement(saved).jsonObject
            assertEquals(JsonPrimitive(2), updated["version"])
            assertEquals(JsonPrimitive(accent), updated["accent"])
            assertNull(updated["followCover"])
            assertEquals(settings, store.load())
        }
    }

    @Test
    fun version_one_settings_without_data_saver_keep_full_quality() {
        val legacy = """{"version":1,"mode":"Dark","followCover":false}"""
        assertEquals(false, AppearanceStore({ legacy }, {}).load().saveMobileData)
    }

    @Test
    fun version_two_settings_restore_a_named_accent_and_other_preferences() {
        var saved = """{"version":2,"mode":"Light","accent":"mint","saveMobileData":true}"""
        val store = AppearanceStore({ saved }, { saved = it })
        val settings = store.load()
        assertEquals(ThemeMode.Light, settings.mode)
        assertEquals(AccentChoice.Mint, settings.accent)
        assertTrue(settings.saveMobileData)
        store.save(settings)
        assertEquals(JsonPrimitive("mint"), Json.parseToJsonElement(saved).jsonObject["accent"])
    }

    @Test
    fun saving_data_requires_both_the_setting_and_a_metered_network() {
        assertEquals(false, dataSaverActive(false, false))
        assertEquals(false, dataSaverActive(false, true))
        assertEquals(false, dataSaverActive(true, false))
        assertEquals(true, dataSaverActive(true, true))
    }

    @Test
    fun unreadable_or_invalid_settings_restore_defaults() {
        val defaults = Appearance(ThemeMode.System, AccentChoice.FollowCover, saveMobileData = false)
        for (bad in listOf(
            "{",
            "{}",
            """{"version":1,"mode":"Unknown","followCover":false}""",
            """{"version":1,"mode":"Dark","followCover":"true"}""",
            """{"version":2,"mode":"Dark","followCover":false}""",
            """{"version":2,"mode":"Dark","accent":"unknown","saveMobileData":true}""",
            """{"version":2,"mode":"Dark","accent":null}""",
            """{"version":2,"mode":"Dark","accent":{"name":"mint"}}""",
            """{"version":3,"mode":"Dark","accent":"mint"}""",
        )) {
            assertEquals(defaults, AppearanceStore({ bad }, {}).load(), bad)
        }
        assertEquals(defaults, AppearanceStore({ null }, {}).load())
        assertEquals(defaults, AppearanceStore({ error("gagal membaca") }, {}).load())
    }

    @Test
    fun accent_uses_the_mean_of_the_dominant_group_and_ignores_transparency() {
        val pixels = intArrayOf(0xffb02130.toInt(), 0xffb42334.toInt(), 0xff2288cc.toInt(), 0, 0, 0)
        assertEquals(Color(0xffb22232), dominantAccent(pixels))
        assertNull(dominantAccent(intArrayOf(0, 0x10667788, 0x7fff0000)))
    }

    @Test
    fun accent_prefers_saturated_colors_over_neutral_backgrounds_and_weights_by_count() {
        // Sampul putih atau hampir putih dengan logo merah kecil harus memilih merah.
        val whiteCoverWithRedLogo =
            IntArray(70) { 0xffffffff.toInt() } +
                IntArray(20) { 0xfffaf6f0.toInt() } +
                IntArray(10) { 0xffd32f2f.toInt() }
        assertEquals(Color(0xffd32f2f), dominantAccent(whiteCoverWithRedLogo))

        // Sampul hitam dengan teks biru kecil harus memilih biru, bukan hitam atau bintik gelap.
        val blackCoverWithBlueText =
            IntArray(75) { 0xff000000.toInt() } +
                IntArray(10) { 0xff101010.toInt() } +
                IntArray(10) { 0xff0a0a14.toInt() } +
                IntArray(5) { 0xff1e88e5.toInt() }
        assertEquals(Color(0xff1e88e5), dominantAccent(blackCoverWithBlueText))

        // Sampul abu-abu murni tetap menghasilkan warna netral.
        val pureGrayCover = IntArray(12) { 0xff808080.toInt() } + IntArray(4) { 0xffb0b0b0.toInt() }
        assertEquals(Color(0xff808080), dominantAccent(pureGrayCover))

        // Piksel transparan diabaikan meskipun bernilai warna jenuh.
        val mostlyTransparent =
            IntArray(20) { 0x7fff0000 } + IntArray(20) { 0 } + IntArray(2) { 0xff2288cc.toInt() }
        assertEquals(Color(0xff2288cc), dominantAccent(mostlyTransparent))

        // Antara dua warna jenuh, kelompok warna dengan piksel lebih banyak menang.
        val twoSaturatedColors = IntArray(6) { 0xff26a69a.toInt() } + IntArray(4) { 0xffff0000.toInt() }
        assertEquals(Color(0xff26a69a), dominantAccent(twoSaturatedColors))
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
