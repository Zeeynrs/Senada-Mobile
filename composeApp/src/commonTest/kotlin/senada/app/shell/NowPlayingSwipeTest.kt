package senada.app.shell

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingSwipeTest {
    @Test
    fun opensAfterDraggingThirtyPercentOfPanelHeight() {
        assertFalse(shouldOpenNowPlaying(offset = 299f, height = 1000f, velocity = 0f, flingThreshold = 1000f))
        assertTrue(shouldOpenNowPlaying(offset = 300f, height = 1000f, velocity = 0f, flingThreshold = 1000f))
    }

    @Test
    fun closesAfterDraggingTwentyFivePercentOfPanelHeight() {
        assertFalse(shouldCloseNowPlaying(offset = 199f, height = 800f, velocity = 0f, flingThreshold = 1000f))
        assertTrue(shouldCloseNowPlaying(offset = 200f, height = 800f, velocity = 0f, flingThreshold = 1000f))
    }

    @Test
    fun opensOnAnUpwardFling() {
        assertFalse(shouldOpenNowPlaying(offset = 30f, height = 1000f, velocity = -999f, flingThreshold = 1000f))
        assertFalse(shouldOpenNowPlaying(offset = 30f, height = 1000f, velocity = 1400f, flingThreshold = 1000f))
        assertTrue(shouldOpenNowPlaying(offset = 30f, height = 1000f, velocity = -1000f, flingThreshold = 1000f))
    }

    @Test
    fun closesOnADownwardFling() {
        assertFalse(shouldCloseNowPlaying(offset = 30f, height = 800f, velocity = 999f, flingThreshold = 1000f))
        assertFalse(shouldCloseNowPlaying(offset = 30f, height = 800f, velocity = -1400f, flingThreshold = 1000f))
        assertTrue(shouldCloseNowPlaying(offset = 30f, height = 800f, velocity = 1000f, flingThreshold = 1000f))
    }

    @Test
    fun ignoresGesturesBeforePanelIsMeasured() {
        assertFalse(shouldOpenNowPlaying(offset = 0f, height = 0f, velocity = -1400f, flingThreshold = 1000f))
        assertFalse(shouldCloseNowPlaying(offset = 0f, height = 0f, velocity = 1400f, flingThreshold = 1000f))
    }
}
