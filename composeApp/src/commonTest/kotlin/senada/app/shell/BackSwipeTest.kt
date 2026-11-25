package senada.app.shell

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackSwipeTest {
    @Test
    fun rightwardSwipeFromLeftEdgeCommitsAtRequiredDistance() {
        assertTrue(shouldCommit(start = Offset(24f, 100f), end = Offset(88f, 120f)))
    }

    @Test
    fun swipeFromInsidePageDoesNotNavigateBack() {
        assertFalse(shouldCommit(start = Offset(25f, 100f), end = Offset(145f, 100f)))
    }

    @Test
    fun leftwardOrPredominantlyVerticalSwipeDoesNotNavigateBack() {
        assertFalse(shouldCommit(start = Offset(12f, 100f), end = Offset(-70f, 100f)))
        assertFalse(shouldCommit(start = Offset(12f, 100f), end = Offset(90f, 180f)))
    }

    @Test
    fun swipeReleasedBeforeThresholdDoesNotNavigateBack() {
        assertFalse(shouldCommit(start = Offset(12f, 100f), end = Offset(75f, 103f)))
    }

    private fun shouldCommit(start: Offset, end: Offset): Boolean =
        isBackSwipe(start, end, edgeWidth = 24f, minimumTravel = 64f)
}
