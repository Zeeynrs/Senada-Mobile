package senada.app

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import senada.app.nowplaying.PlaySource
import senada.app.nowplaying.label
import senada.app.shell.AppDestination
import senada.app.shell.AppNavigator
import senada.app.shell.ProfileRoute
import senada.app.tabs.albumCardAccessibilityLabel
import senada.app.tabs.artistCardAccessibilityLabel
import senada.app.tabs.heroCopy
import senada.app.tabs.moodChipAccessibilityLabel
import senada.app.tabs.playlistCardAccessibilityLabel
import senada.app.theme.AccentChoice
import senada.catalog.AlbumSummary
import senada.catalog.ArtistSummary
import senada.catalog.HomeItem
import senada.catalog.MoodChip
import senada.library.HomeHero
import senada.library.PlaylistSummary
import senada.model.Track
import senada.playback.FakeAudioOutput
import senada.playback.PlaybackController
import senada.playback.SampleAudioSelection
import senada.playback.SampleAudioSource
import senada.playback.SampleAudioStatus
import senada.resources.Res
import senada.resources.language_english
import senada.resources.language_indonesian
import senada.resources.nav_home
import senada.resources.nav_library
import senada.resources.nav_profile
import senada.resources.nav_search
import senada.resources.player_empty_title
import senada.resources.player_now_playing
import senada.resources.song_count

class AppLanguageTest {
    @AfterTest
    fun resetLanguage() {
        AppStrings.setLanguage(AppLanguage.English)
    }

    @Test
    fun defaultsToEnglishOnFreshInstallAndLegacySettingsWithoutLanguage() {
        assertEquals(AppLanguage.English, Appearance().language)
        assertEquals(AppLanguage.English, AppearanceStore({ null }, {}).load().language)

        val legacyPayloads = listOf(
            """{"version":1,"mode":"Dark","followCover":false,"saveMobileData":true}""",
            """{"version":2,"mode":"Light","accent":"mint","saveMobileData":false}""",
            """{"version":3,"mode":"Dark","accent":"senada","coverRadius":12,"glass":false,"playerBackdropFromCover":false,"animation":"fast"}""",
        )
        for (payload in legacyPayloads) {
            val loaded = AppearanceStore({ payload }, {}).load()
            assertEquals(AppLanguage.English, loaded.language, payload)
        }
        assertEquals(
            ThemeMode.Dark,
            AppearanceStore({ legacyPayloads[2] }, {}).load().mode,
        )
        assertEquals(12, AppearanceStore({ legacyPayloads[2] }, {}).load().coverRadius)
    }

    @Test
    fun invalidLanguageValuesFallbackToEnglishWhilePreservingValidSettings() {
        for (raw in listOf(null, "", "   ", "fr", "ja", "in", "en-US", "id-ID", "unknown")) {
            assertEquals(AppLanguage.English, AppLanguage.fromTag(raw), "fromTag($raw)")
        }
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag(" EN "))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromTag("id"))
        assertEquals(AppLanguage.Indonesian, AppLanguage.fromTag(" ID "))

        val invalidJsonVariants = listOf(
            """{"version":3,"mode":"Dark","accent":"mint","language":"fr"}""",
            """{"version":3,"mode":"Dark","accent":"mint","language":""}""",
            """{"version":3,"mode":"Dark","accent":"mint","language":null}""",
            """{"version":3,"mode":"Dark","accent":"mint","language":123}""",
            """{"version":3,"mode":"Dark","accent":"mint","language":true}""",
            """{"version":3,"mode":"Dark","accent":"mint","language":{"tag":"id"}}""",
        )
        for (jsonText in invalidJsonVariants) {
            val loaded = AppearanceStore({ jsonText }, {}).load()
            assertEquals(AppLanguage.English, loaded.language, jsonText)
            assertEquals(ThemeMode.Dark, loaded.mode, jsonText)
            assertEquals(AccentChoice.Mint, loaded.accent, jsonText)
        }
    }

    @Test
    fun indonesianChoicePersistsAcrossRestartOfflineAndSwitchesBackToEnglish() = runTest {
        var persistedJson: String? = null
        fun openPrefs() = AppearancePreferences(AppearanceStore({ persistedJson }, { persistedJson = it }))

        // Instalasi baru dimulai dalam bahasa Inggris.
        val firstSession = openPrefs()
        firstSession.restore()
        AppStrings.setLanguage(firstSession.settings!!.language)
        assertEquals(AppLanguage.English, firstSession.settings?.language)
        assertEquals(AppLanguage.English, AppStrings.currentLanguage)

        // Pengguna memilih Bahasa Indonesia: tersimpan ke JSON dan langsung aktif.
        firstSession.selectLanguage(AppLanguage.Indonesian)
        AppStrings.setLanguage(firstSession.settings!!.language)
        assertEquals(AppLanguage.Indonesian, AppStrings.currentLanguage)
        assertEquals(
            JsonPrimitive("id"),
            Json.parseToJsonElement(requireNotNull(persistedJson)).jsonObject["language"],
        )

        // Simulasikan restart aplikasi offline: state memori direset ke English sebelum membaca penyimpanan.
        AppStrings.setLanguage(AppLanguage.English)
        val restartedSession = openPrefs()
        restartedSession.restore()
        AppStrings.setLanguage(restartedSession.settings!!.language)
        assertEquals(AppLanguage.Indonesian, restartedSession.settings?.language)
        assertEquals(AppLanguage.Indonesian, AppStrings.currentLanguage)
        assertEquals("Beranda", AppStrings.get(Res.string.nav_home))
        assertEquals("Belum ada lagu diputar", AppStrings.get(Res.string.player_empty_title))

        // Pengguna mengganti kembali ke English -> tersimpan "en" dan bertahan lintas restart lagi.
        restartedSession.selectLanguage(AppLanguage.English)
        AppStrings.setLanguage(restartedSession.settings!!.language)
        assertEquals(AppLanguage.English, AppStrings.currentLanguage)
        assertEquals("Home", AppStrings.get(Res.string.nav_home))
        assertEquals("Nothing playing", AppStrings.get(Res.string.player_empty_title))
        assertEquals(
            JsonPrimitive("en"),
            Json.parseToJsonElement(requireNotNull(persistedJson)).jsonObject["language"],
        )

        val thirdSession = openPrefs()
        thirdSession.restore()
        assertEquals(AppLanguage.English, thirdSession.settings?.language)
    }

    @Test
    fun englishAndIndonesianResourceKeysAndPlaceholdersHaveFullParity() = runTest {
        AppStrings.ensureLoadedAsync()

        val enKeys = AppStrings.loadedKeys(AppLanguage.English)
        val idKeys = AppStrings.loadedKeys(AppLanguage.Indonesian)
        assertTrue(enKeys.isNotEmpty(), "English string table should be loaded")
        assertEquals(enKeys, idKeys, "String keys in values/strings.xml and values-id/strings.xml must match")

        val enPlurals = AppStrings.loadedPluralKeys(AppLanguage.English)
        val idPlurals = AppStrings.loadedPluralKeys(AppLanguage.Indonesian)
        assertTrue(enPlurals.isNotEmpty(), "English plurals table should be loaded")
        assertEquals(enPlurals, idPlurals, "Plural keys in values/strings.xml and values-id/strings.xml must match")

        val placeholderRegex = Regex("%[0-9]+\\$[sd]")
        for (key in enKeys) {
            val enTemplate = requireNotNull(AppStrings.rawTemplate(key, AppLanguage.English))
            val idTemplate = requireNotNull(AppStrings.rawTemplate(key, AppLanguage.Indonesian))
            assertTrue(enTemplate.isNotBlank(), "Empty English string for $key")
            assertTrue(idTemplate.isNotBlank(), "Empty Indonesian string for $key")
            val enPlaceholders = placeholderRegex.findAll(enTemplate).map { it.value }.sorted().toList()
            val idPlaceholders = placeholderRegex.findAll(idTemplate).map { it.value }.sorted().toList()
            assertEquals(enPlaceholders, idPlaceholders, "Placeholder mismatch for string '$key'")
        }

        for (key in enPlurals) {
            val enForms = AppStrings.rawPluralForms(key, AppLanguage.English)
            val idForms = AppStrings.rawPluralForms(key, AppLanguage.Indonesian)
            assertTrue(enForms.containsKey("ONE") && enForms.containsKey("OTHER"), "English plural '$key' missing ONE/OTHER")
            assertTrue(idForms.containsKey("OTHER"), "Indonesian plural '$key' missing OTHER")
            val expectedPlaceholders = placeholderRegex.findAll(enForms.getValue("OTHER")).map { it.value }.sorted().toList()
            for ((cat, form) in enForms + idForms.mapKeys { "id:${it.key}" }) {
                assertTrue(form.isNotBlank(), "Blank plural form $cat for '$key'")
                val actual = placeholderRegex.findAll(form).map { it.value }.sorted().toList()
                assertEquals(expectedPlaceholders, actual, "Placeholder mismatch in plural '$key' ($cat)")
            }
        }

        // Nama kedua pilihan bahasa selalu dalam bahasanya masing-masing terlepas dari bahasa aktif.
        for (lang in AppLanguage.entries) {
            assertEquals("English", AppStrings.get(Res.string.language_english, lang))
            assertEquals("Bahasa Indonesia", AppStrings.get(Res.string.language_indonesian, lang))
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun bidirectionalSwitchUpdatesRepresentativeSurfacesWithoutResettingNavigationOrPlayback() = runTest {
        AppStrings.ensureLoadedAsync()

        val navigator = AppNavigator()
        navigator.select(AppDestination.Profile)
        navigator.openProfileRoute(ProfileRoute.Language)

        val output = FakeAudioOutput()
        val controller = PlaybackController(output = output, scope = backgroundScope) { entry ->
            SampleAudioSelection(entry.track.title, SampleAudioSource.Local, "file:///${entry.id}.m4a", entry.id)
        }
        val track = Track(
            id = "demo:horizon",
            title = "Senja di Pelabuhan",
            artists = listOf("Nadia"),
            durationMs = 185_000L,
            source = "demo",
        )
        controller.add(track)
        controller.resume()
        runCurrent()
        val initialStatus = controller.state.value.audio.status
        assertTrue(initialStatus == SampleAudioStatus.Playing || initialStatus == SampleAudioStatus.Loading)

        fun snapshotSurfaceLabels(lang: AppLanguage): List<String> = listOf(
            AppStrings.get(Res.string.nav_home, lang),
            AppStrings.get(Res.string.nav_search, lang),
            AppStrings.get(Res.string.nav_library, lang),
            AppStrings.get(Res.string.nav_profile, lang),
            ProfileRoute.Language.label(lang),
            PlaySource.Favorites.label(lang)!!,
            AppStrings.get(Res.string.player_now_playing, lang),
            heroCopy(HomeHero.FromPlaylist(PlaylistSummary("pl1", "Pagi", "", 0, 2), null, 3), lang).primaryAction,
            albumCardAccessibilityLabel(AlbumSummary("a1", "Manusia", subtitle = "Tulus"), lang),
            artistCardAccessibilityLabel(ArtistSummary("ar1", "Tulus"), lang),
            playlistCardAccessibilityLabel(HomeItem.Playlist("pl1", "Mix"), lang),
            moodChipAccessibilityLabel(MoodChip("Chill", "b1", null), lang),
            AppStrings.plural(Res.plurals.song_count, 1, lang, 1),
            AppStrings.plural(Res.plurals.song_count, 3, lang, 3),
        )

        AppStrings.setLanguage(AppLanguage.Indonesian)
        val idLabels = snapshotSurfaceLabels(AppStrings.currentLanguage)
        assertEquals(
            listOf(
                "Beranda", "Cari", "Library", "Profil", "Bahasa",
                "Dari Favorit", "Sedang diputar", "Putar",
                "Album Manusia oleh Tulus", "Artis Tulus", "Playlist Mix", "Mood Chill",
                "1 lagu", "3 lagu",
            ),
            idLabels,
        )
        // Navigasi dan pemutaran tetap utuh saat berpindah ke Bahasa Indonesia.
        assertEquals(AppDestination.Profile, navigator.destination)
        assertEquals(ProfileRoute.Language, navigator.currentProfileRoute)
        assertEquals(track.title, controller.state.value.audio.selection?.title)
        assertEquals(1, controller.state.value.queue.entries.size)

        AppStrings.setLanguage(AppLanguage.English)
        val enLabels = snapshotSurfaceLabels(AppStrings.currentLanguage)
        assertEquals(
            listOf(
                "Home", "Search", "Library", "Profile", "Language",
                "From Favorites", "Now playing", "Play",
                "Album Manusia by Tulus", "Artist Tulus", "Playlist Mix", "Mood Chill",
                "1 song", "3 songs",
            ),
            enLabels,
        )
        // Navigasi dan pemutaran tetap utuh saat berpindah kembali ke English.
        assertEquals(AppDestination.Profile, navigator.destination)
        assertEquals(ProfileRoute.Language, navigator.currentProfileRoute)
        assertEquals(track.title, controller.state.value.audio.selection?.title)
        assertEquals(1, controller.state.value.queue.entries.size)
    }
}
