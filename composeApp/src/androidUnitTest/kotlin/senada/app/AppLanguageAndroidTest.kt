package senada.app

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import senada.resources.Res
import senada.resources.nav_home
import senada.resources.player_now_playing

class AppLanguageAndroidTest {
    private val originalLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocaleAndLanguage() {
        Locale.setDefault(originalLocale)
        AppStrings.setLanguage(AppLanguage.English)
    }

    @Test
    fun defaultAndBothSelectedLanguagesWorkRegardlessOfDeviceThirdLanguage() = runTest {
        AppStrings.ensureLoadedAsync()

        for (tag in listOf("ja-JP", "fr-FR", "id-ID")) {
            val deviceLocale = Locale.forLanguageTag(tag)
            Locale.setDefault(deviceLocale)

            // Instalasi baru / store kosong tetap default ke English terlepas dari bahasa perangkat.
            val prefs = AppearancePreferences(AppearanceStore({ null }, {}))
            prefs.restore()
            val resolved = prefs.settings!!.language
            AppStrings.setLanguage(resolved)
            assertEquals(AppLanguage.English, resolved, "Device $deviceLocale")
            assertEquals("Home", AppStrings.get(Res.string.nav_home))
            assertEquals("Now playing", AppStrings.get(Res.string.player_now_playing))

            // Pilihan Bahasa Indonesia bekerja meskipun perangkat memakai bahasa ketiga.
            prefs.selectLanguage(AppLanguage.Indonesian)
            AppStrings.setLanguage(prefs.settings!!.language)
            assertEquals(AppLanguage.Indonesian, AppStrings.currentLanguage)
            assertEquals("Beranda", AppStrings.get(Res.string.nav_home))
            assertEquals("Sedang diputar", AppStrings.get(Res.string.player_now_playing))

            // Kembali ke English juga bekerja pada perangkat berbahasa ketiga.
            prefs.selectLanguage(AppLanguage.English)
            AppStrings.setLanguage(prefs.settings!!.language)
            assertEquals(AppLanguage.English, AppStrings.currentLanguage)
            assertEquals("Home", AppStrings.get(Res.string.nav_home))
        }
    }
}
