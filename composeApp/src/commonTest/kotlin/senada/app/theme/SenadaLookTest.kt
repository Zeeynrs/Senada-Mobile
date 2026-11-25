package senada.app.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import senada.app.AnimationSpeed
import senada.app.Appearance
import senada.app.shell.AppDestination
import senada.app.shell.ProfileRoute
import senada.app.shell.ShellPageKey
import senada.app.shell.ShellTransitionKind
import senada.app.shell.shellTransitionKind

class SenadaLookTest {
    @Test
    fun default_artwork_corners_are_smaller_for_large_and_list_covers() {
        val look = senadaLook(Appearance(), systemReduceMotion = false)

        assertEquals(16.dp, look.coverRadius)
        assertEquals(9.6f.dp, look.listCoverRadius)
        assertEquals(look, SenadaLook())
    }

    @Test
    fun list_cover_radius_scales_proportionally_and_clamps_non_negative() {
        assertEquals(0f, listCoverRadiusDp(-10))
        assertEquals(0f, listCoverRadiusDp(0))
        assertEquals(6f, listCoverRadiusDp(10))
        assertEquals(12f, listCoverRadiusDp(20))
        assertEquals(16.8f, listCoverRadiusDp(28))
        assertEquals(16.8f, listCoverRadiusDp(50))
    }

    @Test
    fun animation_speed_and_system_reduce_motion_compute_effective_scale() {
        assertEquals(1.5f, effectiveAnimationScale(AnimationSpeed.Slow, systemReduceMotion = false))
        assertEquals(1.0f, effectiveAnimationScale(AnimationSpeed.Normal, systemReduceMotion = false))
        assertEquals(0.6f, effectiveAnimationScale(AnimationSpeed.Fast, systemReduceMotion = false))
        assertEquals(0f, effectiveAnimationScale(AnimationSpeed.Off, systemReduceMotion = false))

        for (speed in AnimationSpeed.entries) {
            assertEquals(0f, effectiveAnimationScale(speed, systemReduceMotion = true), speed.name)
        }
    }

    @Test
    fun duration_scaling_returns_zero_when_off_and_scales_otherwise() {
        assertEquals(300, scaledAnimationDurationMillis(200, 1.5f))
        assertEquals(200, scaledAnimationDurationMillis(200, 1.0f))
        assertEquals(120, scaledAnimationDurationMillis(200, 0.6f))
        assertEquals(0, scaledAnimationDurationMillis(200, 0f))
        assertEquals(0, scaledAnimationDurationMillis(0, 1.5f))
    }

    @Test
    fun senada_look_projects_all_appearance_fields_and_clamps_radius() {
        val look = senadaLook(
            Appearance(
                coverRadius = 10,
                playerBackdropFromCover = false,
                glass = false,
                animation = AnimationSpeed.Slow,
            ),
            systemReduceMotion = false,
        )
        assertEquals(10.dp, look.coverRadius)
        assertEquals(6.dp, look.listCoverRadius)
        assertFalse(look.glass)
        assertFalse(look.playerBackdropFromCover)
        assertEquals(1.5f, look.animationScale)
        assertFalse(look.systemReduceMotion)
        assertEquals(225, look.durationMillis(150))

        val reduced = senadaLook(
            Appearance(coverRadius = 99, animation = AnimationSpeed.Fast),
            systemReduceMotion = true,
        )
        assertEquals(28.dp, reduced.coverRadius)
        assertEquals(16.8f.dp, reduced.listCoverRadius)
        assertEquals(0f, reduced.animationScale)
        assertTrue(reduced.systemReduceMotion)
        assertEquals(0, reduced.durationMillis(150))
    }

    @Test
    fun shell_transition_kind_distinguishes_tabs_and_profile_subpages() {
        val home = ShellPageKey(AppDestination.Home, 0, null, 0, null, null)
        val profile = ShellPageKey(AppDestination.Profile, 0, null, 0, null, null)
        val appearance = ShellPageKey(AppDestination.Profile, 1, ProfileRoute.Appearance, 0, null, null)
        val audioLab = ShellPageKey(AppDestination.Profile, 1, ProfileRoute.AudioLab, 0, null, null)
        val equalizer = ShellPageKey(AppDestination.Profile, 2, ProfileRoute.Equalizer, 0, null, null)

        assertEquals(ShellTransitionKind.None, shellTransitionKind(home, home))
        assertEquals(ShellTransitionKind.Fade, shellTransitionKind(home, profile))
        assertEquals(ShellTransitionKind.SlideForward, shellTransitionKind(profile, appearance))
        assertEquals(ShellTransitionKind.SlideBackward, shellTransitionKind(appearance, profile))
        assertEquals(ShellTransitionKind.SlideForward, shellTransitionKind(audioLab, equalizer))
        assertEquals(ShellTransitionKind.SlideBackward, shellTransitionKind(equalizer, audioLab))
    }
}
