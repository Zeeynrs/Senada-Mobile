package senada.app.nowplaying

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import senada.app.AnimationSpeed
import senada.app.contrastRatio
import senada.app.shell.NOW_PLAYING_PANEL_SETTLE_MS
import senada.app.shell.nowPlayingPanelSettleDurationMs
import senada.app.theme.SenadaLook

class NowPlayingTest {
    @Test
    fun sourceLabels() {
        assertEquals("Diputar dari Pencarian", PlaySource.Search.playerLabel())
        assertEquals("Diputar dari playlist Senja", PlaySource.Playlist.fromLibrary("senja", "Senja").playerLabel())
        assertEquals("Diputar dari Antrean", null.playerLabel())
        assertEquals("Dari Favorit", PlaySource.Favorites.label())
        assertEquals("Dari playlist Senja", PlaySource.Playlist.fromLibrary("senja", "Senja").label())
        assertEquals("Dari Riwayat", PlaySource.History.label())
        assertEquals("Dari File impor", PlaySource.Imported.label())
        assertEquals("Dari Paling sering diputar", PlaySource.MostPlayed.label())
        assertEquals("Dari artis Tulus", PlaySource.Artist("Tulus").label())
        assertEquals("Dari album Manusia", PlaySource.Album("Manusia").label())
        assertNull(null.label())
    }

    @Test
    fun alwaysDarkEvenInLightTheme() {
        assertEquals(Color(0xFF0B0C0E), nowPlayingColorScheme(null).background)
    }

    @Test
    fun coverAccentStaysReadableOnDark() {
        val scheme = nowPlayingColorScheme(Color(0xFF1A1A40))
        assertTrue(contrastRatio(scheme.primary, scheme.background) >= 4.5f)
    }

    @Test
    fun brightCoversKeepSourceLegible() {
        for (cover in listOf(Color.White, Color.Yellow, Color.Cyan, Color(0xFFB4521E), null)) {
            assertTrue(sourceLabelContrast(playerGlow(cover)) >= 4.5f, "Cover: $cover")
        }
        assertEquals(Color(0xFFB4521E), playerGlow(null))
    }

    @Test
    fun backdropCacheSizeDownsamplesSmoothlyAndHandlesEmptyFrames() {
        assertEquals(IntSize.Zero, backdropCacheSize(Size.Zero))
        assertEquals(IntSize(270, 600), backdropCacheSize(Size(1080f, 2400f)))
        assertEquals(IntSize(200, 400), backdropCacheSize(Size(200f, 400f)))
    }

    @Test
    fun largeCoverShapeUsesFullDpRadiusAndClampsNegative() {
        assertEquals(RoundedCornerShape(0.dp), largeCoverShape((-4).dp))
        assertEquals(RoundedCornerShape(0.dp), largeCoverShape(0.dp))
        assertEquals(RoundedCornerShape(20.dp), largeCoverShape(20.dp))
        assertEquals(RoundedCornerShape(28.dp), largeCoverShape(28.dp))
    }

    @Test
    fun playerGlassStyleDisablesBlurWithoutChangingFillOrBorder() {
        val withGlass = playerGlassStyle(glassEnabled = true)
        val withoutGlass = playerGlassStyle(glassEnabled = false)

        assertTrue(withGlass.useBackdropBlur)
        assertFalse(withoutGlass.useBackdropBlur)
        assertEquals(PlayerGlassTint, withGlass.fillColor)
        assertEquals(PlayerGlassTint, withoutGlass.fillColor)
        assertEquals(PlayerGlassBorder, withGlass.borderColor)
        assertEquals(PlayerGlassBorder, withoutGlass.borderColor)
    }

    @Test
    fun plainBackdropUsesSolidDarkBaseAndNoCoverGlow() {
        val fromCover = playerBackdropColors(accent = Color(0xFF3B4FA0), fromCover = true)
        val plain = playerBackdropColors(accent = Color(0xFF3B4FA0), fromCover = false)

        assertEquals(1f, fromCover.glowStrength)
        assertEquals(.55f, fromCover.shadeAlpha)
        assertEquals(Color(0xFF0B0C0E), plain.base)
        assertEquals(0f, plain.glowStrength)
        assertEquals(.15f, plain.shadeAlpha)
    }

    @Test
    fun backdropTransitionDurationScalesWithAnimationSpeed() {
        assertEquals(600, playerBackdropTransitionDurationMs(AnimationSpeed.Slow.scale))
        assertEquals(PLAYER_BACKDROP_TRANSITION_MS, playerBackdropTransitionDurationMs(AnimationSpeed.Normal.scale))
        assertEquals(240, playerBackdropTransitionDurationMs(AnimationSpeed.Fast.scale))
        assertEquals(0, playerBackdropTransitionDurationMs(AnimationSpeed.Off.scale))
    }

    @Test
    fun backdropCacheKeyTracksAppearanceSizeDensityAndDeduplicatesPlainAccent() {
        val frame = Size(1080f, 2400f)
        val blueCover = backdropAppearance(playerBackdropColors(Color(0xFF3B4FA0), fromCover = true), fromCover = true)
        val redCover = backdropAppearance(playerBackdropColors(Color(0xFFA03B3B), fromCover = true), fromCover = true)
        val bluePlain = backdropAppearance(playerBackdropColors(Color(0xFF3B4FA0), fromCover = false), fromCover = false)
        val redPlain = backdropAppearance(playerBackdropColors(Color(0xFFA03B3B), fromCover = false), fromCover = false)

        assertNull(backdropCacheKey(blueCover, Size.Zero, density = 3f, scale = 1f))

        val blueKey = backdropCacheKey(blueCover, frame, density = 3f, scale = 1f)
        val redKey = backdropCacheKey(redCover, frame, density = 3f, scale = 1f)
        val plainBlueKey = backdropCacheKey(bluePlain, frame, density = 3f, scale = 1f)
        val plainRedKey = backdropCacheKey(redPlain, frame, density = 3f, scale = 1f)

        assertNotEquals(blueKey, redKey)
        assertNotEquals(blueKey, plainBlueKey)
        assertEquals(plainBlueKey, plainRedKey)
        assertNotEquals(blueKey, backdropCacheKey(blueCover, Size(1080f, 2200f), density = 3f, scale = 1f))
        assertNotEquals(blueKey, backdropCacheKey(blueCover, frame, density = 2f, scale = 1f))
        assertNotEquals(blueKey, backdropCacheKey(blueCover, frame, density = 3f, scale = 1.1f))
        assertNotEquals(blueKey, backdropCacheKey(blueCover, frame, density = 3f, scale = 1f, glass = true))
    }

    @Test
    fun backdropCrossfadeSlotCachesRendersCrossfadesAndReleasesPrevious() {
        val frame = Size(1080f, 2400f)
        val keyA = backdropCacheKey(
            backdropAppearance(playerBackdropColors(Color(0xFF3B4FA0), fromCover = true), fromCover = true),
            frame,
            density = 3f,
            scale = 1f,
        )!!
        val keyB = backdropCacheKey(
            backdropAppearance(playerBackdropColors(Color(0xFFA03B3B), fromCover = true), fromCover = true),
            frame,
            density = 3f,
            scale = 1f,
        )!!
        val keyC = backdropCacheKey(
            backdropAppearance(playerBackdropColors(Color(0xFF2B8A5B), fromCover = false), fromCover = false),
            frame,
            density = 3f,
            scale = 1f,
        )!!

        var renders = 0
        var slot = BackdropCrossfadeSlot<String>()

        val (afterFirst, firstImg) = slot.resolve(keyA) { renders++; "bmp-a" }
        slot = afterFirst
        assertEquals("bmp-a", firstImg)
        assertEquals(1, renders)

        val (afterHit, hitImg) = slot.resolve(keyA) { renders++; "unexpected" }
        slot = afterHit
        assertEquals("bmp-a", hitImg)
        assertEquals(1, renders)

        slot = slot.beginTransition(progress = 1f)
        assertEquals("bmp-a", slot.previous)
        assertNull(slot.current)

        val (afterSecond, secondImg) = slot.resolve(keyB) { renders++; "bmp-b" }
        slot = afterSecond
        assertEquals("bmp-b", secondImg)
        assertEquals("bmp-a", slot.previous)
        assertEquals(2, renders)

        // Interupsi awal tetap mempertahankan bitmap lama agar tidak meloncat ke B sebelum tampak.
        val interruptedEarly = slot.beginTransition(progress = .2f)
        assertEquals("bmp-a", interruptedEarly.previous)
        assertNull(interruptedEarly.current)

        // Interupsi setelah melewati tengah mempromosikan B sebagai latar sebelumnya.
        val interruptedLate = slot.beginTransition(progress = .7f)
        assertEquals("bmp-b", interruptedLate.previous)
        assertNull(interruptedLate.current)

        // Interupsi ganda sebelum frame baru di-raster tetap menyimpan bitmap sebelumnya.
        val interruptedBeforeDraw = interruptedEarly.beginTransition(progress = 0f)
        assertEquals("bmp-a", interruptedBeforeDraw.previous)

        slot = slot.finishTransition()
        assertEquals("bmp-b", slot.current)
        assertNull(slot.previous)

        // Snap (animationScale = 0) langsung melepas bitmap lama dan meraster target baru sekali.
        slot = slot.snap()
        assertNull(slot.current)
        assertNull(slot.previous)
        val (afterSnap, thirdImg) = slot.resolve(keyC) { renders++; "bmp-c" }
        assertEquals("bmp-c", thirdImg)
        assertNull(afterSnap.previous)
        assertEquals(3, renders)
    }

    @Test
    fun backdropLayerBlendCrossfadesOnlyWhilePreviousExistsAndProgressIncomplete() {
        assertEquals(BackdropLayerBlend(drawPrevious = false, currentAlpha = 1f), backdropLayerBlend(false, 0f))
        assertEquals(BackdropLayerBlend(drawPrevious = true, currentAlpha = 0f), backdropLayerBlend(true, 0f))
        assertEquals(BackdropLayerBlend(drawPrevious = true, currentAlpha = .4f), backdropLayerBlend(true, .4f))
        assertEquals(BackdropLayerBlend(drawPrevious = false, currentAlpha = 1f), backdropLayerBlend(true, 1f))
    }

    @Test
    fun sheetTransitionUsesInstantSwapWhenAnimationScaleIsZero() {
        val off = SenadaLook(animationScale = AnimationSpeed.Off.scale)
        val openOff = playerSheetTransitionSpec(opening = true, look = off)
        val closeOff = playerSheetTransitionSpec(opening = false, look = off)
        assertEquals(EnterTransition.None, openOff.targetContentEnter)
        assertEquals(ExitTransition.None, openOff.initialContentExit)
        assertEquals(EnterTransition.None, closeOff.targetContentEnter)
        assertEquals(ExitTransition.None, closeOff.initialContentExit)

        val normal = SenadaLook(animationScale = AnimationSpeed.Normal.scale)
        val openNormal = playerSheetTransitionSpec(opening = true, look = normal)
        val closeNormal = playerSheetTransitionSpec(opening = false, look = normal)
        assertNotEquals(EnterTransition.None, openNormal.targetContentEnter)
        assertNotEquals(ExitTransition.None, openNormal.initialContentExit)
        assertNotEquals(EnterTransition.None, closeNormal.targetContentEnter)
        assertNotEquals(ExitTransition.None, closeNormal.initialContentExit)
    }

    @Test
    fun panelSettleDurationScalesWithAnimationSpeedAndReduceMotion() {
        assertEquals(413, nowPlayingPanelSettleDurationMs(AnimationSpeed.Slow.scale))
        assertEquals(NOW_PLAYING_PANEL_SETTLE_MS, nowPlayingPanelSettleDurationMs(AnimationSpeed.Normal.scale))
        assertEquals(165, nowPlayingPanelSettleDurationMs(AnimationSpeed.Fast.scale))
        assertEquals(0, nowPlayingPanelSettleDurationMs(AnimationSpeed.Off.scale))
        assertEquals(0, nowPlayingPanelSettleDurationMs(AnimationSpeed.Normal.scale, reduceMotion = true))
    }
}
