package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals
import senada.model.Track

class AccessibilityTest {
    @Test
    fun formatsDurationInIndonesianForScreenReaders() {
        assertEquals("0 detik", accessibleDuration(-500))
        assertEquals("0 detik", accessibleDuration(0))
        assertEquals("0 detik", accessibleDuration(999))
        assertEquals("12 detik", accessibleDuration(12_000))
        assertEquals("1 menit", accessibleDuration(60_000))
        assertEquals("1 menit 5 detik", accessibleDuration(65_000))
        assertEquals("3 menit 15 detik", accessibleDuration(195_000))
        assertEquals("1 jam", accessibleDuration(3_600_000))
        assertEquals("1 jam 5 detik", accessibleDuration(3_605_000))
        assertEquals("1 jam 1 menit 5 detik", accessibleDuration(3_665_000))
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
        assertEquals("Selepas Hujan, Senada, 12 detik", trackAccessibilityLabel(track, favorite = false))
        assertEquals("Selepas Hujan, Favorit, Senada, 12 detik", trackAccessibilityLabel(track, favorite = true))

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
