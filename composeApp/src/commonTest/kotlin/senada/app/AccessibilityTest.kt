package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.model.Track

class AccessibilityTest {
    @Test
    fun formatsDurationInEnglishDefaultAndIndonesianForScreenReaders() {
        assertEquals("0 seconds", accessibleDuration(-500))
        assertEquals("0 seconds", accessibleDuration(0))
        assertEquals("0 seconds", accessibleDuration(999))
        assertEquals("12 seconds", accessibleDuration(12_000))
        assertEquals("1 minute", accessibleDuration(60_000))
        assertEquals("1 minute 5 seconds", accessibleDuration(65_000))
        assertEquals("3 minutes 15 seconds", accessibleDuration(195_000))
        assertEquals("1 hour", accessibleDuration(3_600_000))
        assertEquals("1 hour 5 seconds", accessibleDuration(3_605_000))
        assertEquals("1 hour 1 minute 5 seconds", accessibleDuration(3_665_000))

        assertEquals("0 detik", accessibleDuration(0, AppLanguage.Indonesian))
        assertEquals("3 menit 15 detik", accessibleDuration(195_000, AppLanguage.Indonesian))
        assertEquals("1 jam 1 menit 5 detik", accessibleDuration(3_665_000, AppLanguage.Indonesian))
    }

    @Test
    fun buildsTrackSummaryWithoutDecorativeSymbols() {
        val track = Track(
            id = "demo:1",
            title = "Selepas Hujan",
            artists = listOf("Senada"),
            durationMs = 12_000,
            artworkUrl = null,
            source = "demo",
        )
        assertEquals("Selepas Hujan, Senada, 12 seconds", trackAccessibilityLabel(track, favorite = false))
        assertEquals("Selepas Hujan, Favorite, Senada, 12 seconds", trackAccessibilityLabel(track, favorite = true))
        assertEquals("Selepas Hujan, Senada, 12 detik", trackAccessibilityLabel(track, favorite = false, AppLanguage.Indonesian))
        assertEquals("Selepas Hujan, Favorit, Senada, 12 detik", trackAccessibilityLabel(track, favorite = true, AppLanguage.Indonesian))

        val minimal = Track(
            id = "local:1",
            title = "Rekaman",
            artists = listOf("", " "),
            durationMs = null,
            artworkUrl = null,
            source = "local",
        )
        assertEquals("Rekaman", trackAccessibilityLabel(minimal, favorite = false))
    }
}
