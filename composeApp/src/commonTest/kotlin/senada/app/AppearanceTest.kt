package senada.app

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        for (coverRadius in listOf(0, 2, 12, 20, 28)) {
            for (backdrop in listOf(false, true)) {
                for (glass in listOf(false, true)) {
                    for (speed in AnimationSpeed.entries) {
                        val settings = Appearance(
                            mode = ThemeMode.Dark,
                            accent = AccentChoice.Indigo,
                            saveMobileData = true,
                            coverRadius = coverRadius,
                            playerBackdropFromCover = backdrop,
                            glass = glass,
                            animation = speed,
                        )
                        open().save(settings)
                        assertEquals(settings, open().load())
                    }
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
            assertEquals(DEFAULT_COVER_RADIUS_DP, settings.coverRadius)
            assertTrue(settings.playerBackdropFromCover)
            assertTrue(settings.glass)
            assertEquals(AnimationSpeed.Normal, settings.animation)
            store.save(settings)

            val updated = Json.parseToJsonElement(saved).jsonObject
            assertEquals(JsonPrimitive(3), updated["version"])
            assertEquals(JsonPrimitive(accent), updated["accent"])
            assertNull(updated["followCover"])
            assertEquals(JsonPrimitive(20), updated["coverRadius"])
            assertEquals(JsonPrimitive(true), updated["playerBackdropFromCover"])
            assertEquals(JsonPrimitive(true), updated["glass"])
            assertEquals(JsonPrimitive("normal"), updated["animation"])
            assertEquals(settings, store.load())
        }
    }

    @Test
    fun version_one_settings_without_data_saver_keep_full_quality() {
        val legacy = """{"version":1,"mode":"Dark","followCover":false}"""
        assertEquals(false, AppearanceStore({ legacy }, {}).load().saveMobileData)
    }

    @Test
    fun version_two_settings_restore_a_named_accent_with_v3_visual_defaults() {
        var saved = """{"version":2,"mode":"Light","accent":"mint","saveMobileData":true}"""
        val store = AppearanceStore({ saved }, { saved = it })
        val settings = store.load()
        assertEquals(ThemeMode.Light, settings.mode)
        assertEquals(AccentChoice.Mint, settings.accent)
        assertTrue(settings.saveMobileData)
        assertEquals(DEFAULT_COVER_RADIUS_DP, settings.coverRadius)
        assertTrue(settings.playerBackdropFromCover)
        assertTrue(settings.glass)
        assertEquals(AnimationSpeed.Normal, settings.animation)
        store.save(settings)

        val updated = Json.parseToJsonElement(saved).jsonObject
        assertEquals(JsonPrimitive(3), updated["version"])
        assertEquals(JsonPrimitive("mint"), updated["accent"])
        assertEquals(settings, store.load())
    }

    @Test
    fun out_of_range_cover_radius_is_clamped_on_load_and_save() {
        val negativeJson = """{"version":3,"mode":"Dark","accent":"senada","coverRadius":-12,"glass":false,"playerBackdropFromCover":false,"animation":"fast"}"""
        val loadedLow = AppearanceStore({ negativeJson }, {}).load()
        assertEquals(MIN_COVER_RADIUS_DP, loadedLow.coverRadius)
        assertFalse(loadedLow.glass)
        assertFalse(loadedLow.playerBackdropFromCover)
        assertEquals(AnimationSpeed.Fast, loadedLow.animation)

        val hugeJson = """{"version":3,"mode":"Dark","accent":"senada","coverRadius":999}"""
        assertEquals(MAX_COVER_RADIUS_DP, AppearanceStore({ hugeJson }, {}).load().coverRadius)

        val overflowJson = """{"version":3,"mode":"Dark","accent":"senada","coverRadius":999999999999}"""
        assertEquals(MAX_COVER_RADIUS_DP, AppearanceStore({ overflowJson }, {}).load().coverRadius)

        val badTypesJson = """{"version":3,"mode":"Dark","accent":"senada","coverRadius":"20","glass":"false","playerBackdropFromCover":"false","animation":"unknown"}"""
        val fallbackFields = AppearanceStore({ badTypesJson }, {}).load()
        assertEquals(DEFAULT_COVER_RADIUS_DP, fallbackFields.coverRadius)
        assertTrue(fallbackFields.glass)
        assertTrue(fallbackFields.playerBackdropFromCover)
        assertEquals(AnimationSpeed.Normal, fallbackFields.animation)

        var saved: String? = null
        val store = AppearanceStore({ saved }, { saved = it })
        store.save(Appearance(coverRadius = -8))
        assertEquals(MIN_COVER_RADIUS_DP, store.load().coverRadius)
        store.save(Appearance(coverRadius = 48))
        assertEquals(MAX_COVER_RADIUS_DP, store.load().coverRadius)
    }

    @Test
    fun cover_radius_drag_and_step_snap_to_two_dp_within_bounds() {
        assertEquals(20, dragCoverRadius(20, deltaPx = 0f, dragSpanPx = 280f))
        assertEquals(22, dragCoverRadius(20, deltaPx = 20f, dragSpanPx = 280f))
        assertEquals(18, dragCoverRadius(20, deltaPx = -20f, dragSpanPx = 280f))
        assertEquals(28, dragCoverRadius(20, deltaPx = 500f, dragSpanPx = 280f))
        assertEquals(0, dragCoverRadius(20, deltaPx = -500f, dragSpanPx = 280f))
        assertEquals(20, dragCoverRadius(20, deltaPx = 100f, dragSpanPx = 0f))

        assertEquals(22, stepCoverRadiusBy(20, 1))
        assertEquals(18, stepCoverRadiusBy(20, -1))
        assertEquals(28, stepCoverRadiusBy(28, 1))
        assertEquals(0, stepCoverRadiusBy(0, -1))
    }

    @Test
    fun follow_cover_switch_restores_last_fixed_accent_or_senada() = runTest {
        var saved: String? = null
        val preferences = AppearancePreferences(AppearanceStore({ saved }, { saved = it }))
        preferences.restore()
        assertEquals(AccentChoice.FollowCover, preferences.settings?.accent)

        // Default tanpa warna tetap sebelumnya kembali ke Senada.
        preferences.setFollowCover(false)
        assertEquals(AccentChoice.Senada, preferences.settings?.accent)

        // Memilih warna tetap lalu menyalakan & mematikan FollowCover mengembalikan warna terakhir.
        preferences.selectAccent(AccentChoice.Indigo)
        assertEquals(AccentChoice.Indigo, preferences.settings?.accent)
        preferences.setFollowCover(true)
        assertEquals(AccentChoice.FollowCover, preferences.settings?.accent)
        preferences.setFollowCover(false)
        assertEquals(AccentChoice.Indigo, preferences.settings?.accent)

        // Pengaturan visual lain tersimpan dan clamp bekerja.
        preferences.setCoverRadius(40)
        assertEquals(MAX_COVER_RADIUS_DP, preferences.settings?.coverRadius)
        preferences.setGlass(false)
        assertEquals(false, preferences.settings?.glass)
        preferences.setPlayerBackdropFromCover(false)
        assertEquals(false, preferences.settings?.playerBackdropFromCover)
        preferences.selectAnimationSpeed(AnimationSpeed.Slow)
        assertEquals(AnimationSpeed.Slow, preferences.settings?.animation)
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
            """{"version":3,"mode":"Dark","accent":"unknown"}""",
            """{"version":4,"mode":"Dark","accent":"mint"}""",
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
