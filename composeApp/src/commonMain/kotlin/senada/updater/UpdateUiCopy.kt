package senada.updater

import senada.app.AppLanguage
import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_action_app_store
import senada.resources.updater_action_download_install
import senada.resources.updater_action_google_play
import senada.resources.updater_action_install
import senada.resources.updater_action_release
import senada.resources.updater_action_testflight
import senada.resources.updater_explanation_android
import senada.resources.updater_explanation_android_debug
import senada.resources.updater_explanation_app_store
import senada.resources.updater_explanation_google_play
import senada.resources.updater_explanation_ios_sideload
import senada.resources.updater_explanation_testflight
import senada.resources.updater_status_available
import senada.resources.updater_status_checking
import senada.resources.updater_status_current
import senada.resources.updater_status_idle
import senada.resources.updater_status_no_release
import senada.resources.updater_status_no_stable

/**
 * Pure iOS install channel classification so it can be verified in commonTest as well as on iOS.
 */
internal fun detectIosInstallChannel(
    hasEmbeddedMobileProvision: Boolean,
    isSimulator: Boolean,
    receiptLastPathComponent: String?,
): InstallChannel = when {
    isSimulator || hasEmbeddedMobileProvision -> InstallChannel.IosSideloadUnsigned
    receiptLastPathComponent == "sandboxReceipt" -> InstallChannel.IosTestFlight
    else -> InstallChannel.IosAppStore
}

internal data class UpdateActionPresentation(
    val primaryLabel: UiText?,
    val primaryEnabled: Boolean,
    val showSecondaryReleaseLink: Boolean,
    val channelExplanation: UiText,
    val supportsInAppInstall: Boolean,
)

/**
 * Computes platform- and channel-accurate copy and actions for the About / Updates screen.
 */
internal fun describeUpdateActions(state: UpdateUiState): UpdateActionPresentation {
    val build = state.buildInfo
    val candidate = state.availableCandidate
    val busy = state.isBusy

    return when (build.installChannel) {
        InstallChannel.AndroidSideload -> {
            val sideloadExplanation = UiText.Resource(Res.string.updater_explanation_android)
            val explanation = if (build.isDebugBuild) {
                UiText.Resource(Res.string.updater_explanation_android_debug, sideloadExplanation)
            } else {
                sideloadExplanation
            }
            val primaryLabel = when {
                state.installState is UpdateInstallState.PermissionRequired -> UiText.Resource(Res.string.updater_action_install)
                candidate != null && candidate.primaryAsset != null -> UiText.Resource(Res.string.updater_action_download_install)
                candidate != null -> UiText.Resource(Res.string.updater_action_release)
                else -> null
            }
            UpdateActionPresentation(
                primaryLabel = primaryLabel,
                primaryEnabled = !busy && primaryLabel != null,
                showSecondaryReleaseLink = candidate != null && candidate.primaryAsset != null,
                channelExplanation = explanation,
                supportsInAppInstall = true,
            )
        }

        InstallChannel.GooglePlay -> UpdateActionPresentation(
            primaryLabel = if (candidate != null) UiText.Resource(Res.string.updater_action_google_play) else null,
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null,
            channelExplanation = UiText.Resource(Res.string.updater_explanation_google_play),
            supportsInAppInstall = false,
        )

        InstallChannel.IosSideloadUnsigned -> UpdateActionPresentation(
            primaryLabel = if (candidate != null) UiText.Resource(Res.string.updater_action_release) else null,
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = false,
            channelExplanation = UiText.Resource(Res.string.updater_explanation_ios_sideload),
            supportsInAppInstall = false,
        )

        InstallChannel.IosTestFlight -> UpdateActionPresentation(
            primaryLabel = when {
                candidate != null && build.storeUrl != null -> UiText.Resource(Res.string.updater_action_testflight)
                candidate != null -> UiText.Resource(Res.string.updater_action_release)
                else -> null
            },
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null && build.storeUrl != null,
            channelExplanation = UiText.Resource(Res.string.updater_explanation_testflight),
            supportsInAppInstall = false,
        )

        InstallChannel.IosAppStore -> UpdateActionPresentation(
            primaryLabel = when {
                candidate != null && build.storeUrl != null -> UiText.Resource(Res.string.updater_action_app_store)
                candidate != null -> UiText.Resource(Res.string.updater_action_release)
                else -> null
            },
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null && build.storeUrl != null,
            channelExplanation = UiText.Resource(Res.string.updater_explanation_app_store),
            supportsInAppInstall = false,
        )
    }
}

internal fun describeCheckStatus(state: UpdateUiState): UiText = when (val result = state.checkResult) {
    UpdateCheckResult.Idle -> UiText.Resource(Res.string.updater_status_idle)
    UpdateCheckResult.Checking -> UiText.Resource(Res.string.updater_status_checking)
    is UpdateCheckResult.UpToDate ->
        UiText.Resource(Res.string.updater_status_current, result.installedVersion, UiText.Resource(result.channel.label))
    is UpdateCheckResult.NoStableRelease -> if (result.hasBetaRelease) {
        UiText.Resource(Res.string.updater_status_no_stable)
    } else {
        UiText.Resource(Res.string.updater_status_no_release)
    }
    is UpdateCheckResult.Available -> {
        val tag = result.candidate.release.tagName
        val track = if (result.candidate.release.isPrerelease || result.candidate.version.isPrerelease) {
            UpdateChannel.Beta
        } else {
            UpdateChannel.Stable
        }
        UiText.Resource(Res.string.updater_status_available, tag, UiText.Resource(track.label))
    }
    is UpdateCheckResult.Error -> result.message
}

internal fun formatTransferBytes(bytes: Long, language: AppLanguage): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${formatOneDecimal(kb, language)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${formatOneDecimal(mb, language)} MB"
    return "${formatOneDecimal(mb / 1024.0, language)} GB"
}

private fun formatOneDecimal(value: Double, language: AppLanguage): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val whole = tenths / 10L
    val frac = kotlin.math.abs(tenths % 10L)
    val separator = if (language == AppLanguage.Indonesian) ',' else '.'
    return if (frac == 0L) "$whole" else "$whole$separator$frac"
}
