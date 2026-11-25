@file:OptIn(kotlin.time.ExperimentalTime::class)

package senada.updater

import coil3.PlatformContext
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_check_failed
import senada.resources.updater_download_cancelled
import senada.resources.updater_open_app_store_failed
import senada.resources.updater_open_app_store_manually
import senada.resources.updater_open_github_failed
import senada.resources.updater_open_google_play_failed
import senada.resources.updater_open_permissions_manually
import senada.resources.updater_open_testflight_failed
import senada.resources.updater_open_testflight_manually
import senada.resources.updater_prepare_failed

/** Platform-specific capabilities for checking build identity, opening official URLs, and Android install handoff. */
internal interface UpdatePlatformBridge {
    val buildInfo: InstalledBuildInfo

    fun canInstallUpdatesInApp(): Boolean

    fun openExternalUrl(url: String): Boolean

    fun openUnknownSourcesSettings(): Boolean = false

    suspend fun prepareAndInstall(
        candidate: UpdateCandidate,
        onState: (UpdateInstallState) -> Unit,
    )

    suspend fun resumePendingInstall(
        stagedApkPath: String,
        onState: (UpdateInstallState) -> Unit,
    ) {
        onState(UpdateInstallState.Idle)
    }

    fun cleanupStaleFiles() {}
}

internal const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L

/**
 * Coordinates version checks, channel preferences, download/delta/verify/install states, and OS fallbacks.
 */
internal class UpdateController(
    private val releaseSource: ReleaseSource,
    private val preferencesStore: UpdatePreferencesStore,
    private val bridge: UpdatePlatformBridge,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val defaultChannel = with(bridge.buildInfo) {
        when {
            isDebugBuild || parsedVersion?.isPrerelease == true -> UpdateChannel.Beta
            installChannel == InstallChannel.IosTestFlight || installChannel == InstallChannel.IosSideloadUnsigned ->
                UpdateChannel.Beta
            else -> UpdateChannel.Stable
        }
    }

    private val mutableState = MutableStateFlow(
        UpdateUiState(
            buildInfo = bridge.buildInfo,
            preferences = preferencesStore.load(defaultChannel),
        ),
    )
    val state = mutableState.asStateFlow()

    private var checkJob: Job? = null
    private var installJob: Job? = null

    init {
        bridge.cleanupStaleFiles()
    }

    fun checkIfDue(saveDataActive: Boolean) {
        val prefs = state.value.preferences
        if (!prefs.autoCheckEnabled || saveDataActive) return
        val last = prefs.lastCheckedAtMs
        if (last != null && nowMs() - last < AUTO_CHECK_INTERVAL_MS) return
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (state.value.isBusy) return
        val channel = state.value.preferences.channel
        mutableState.update {
            it.copy(
                checkResult = UpdateCheckResult.Checking,
                installState = if (it.installState is UpdateInstallState.PermissionRequired) it.installState else UpdateInstallState.Idle,
                notice = null,
            )
        }
        checkJob = scope.launch {
            val checkedAt = nowMs()
            try {
                val releases = releaseSource.fetchReleases(bridge.buildInfo.versionName)
                val result = UpdateReleaseSelector.evaluate(
                    installed = bridge.buildInfo,
                    releases = releases,
                    channel = channel,
                    checkedAtMs = checkedAt,
                )
                val updatedPrefs = state.value.preferences.copy(lastCheckedAtMs = checkedAt)
                preferencesStore.save(updatedPrefs)
                mutableState.update {
                    it.copy(
                        preferences = updatedPrefs,
                        checkResult = result,
                        notice = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateNetworkException) {
                mutableState.update {
                    it.copy(
                        checkResult = UpdateCheckResult.Error(e.userMessage, e.retryable),
                        notice = if (manual) null else it.notice,
                    )
                }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        checkResult = UpdateCheckResult.Error(UiText.Resource(Res.string.updater_check_failed)),
                    )
                }
            }
        }
    }

    fun setChannel(channel: UpdateChannel) {
        if (state.value.preferences.channel == channel || state.value.isBusy) return
        val updatedPrefs = state.value.preferences.copy(channel = channel, dismissedTag = null)
        preferencesStore.save(updatedPrefs)
        mutableState.update {
            it.copy(
                preferences = updatedPrefs,
                checkResult = UpdateCheckResult.Idle,
                installState = UpdateInstallState.Idle,
                notice = null,
            )
        }
        checkForUpdates(manual = true)
    }

    fun setAutoCheck(enabled: Boolean) {
        val updatedPrefs = state.value.preferences.copy(autoCheckEnabled = enabled)
        preferencesStore.save(updatedPrefs)
        mutableState.update { it.copy(preferences = updatedPrefs) }
    }

    fun dismissUpdateBanner() {
        val tag = state.value.availableCandidate?.release?.tagName ?: return
        val updatedPrefs = state.value.preferences.copy(dismissedTag = tag)
        preferencesStore.save(updatedPrefs)
        mutableState.update { it.copy(preferences = updatedPrefs) }
    }

    fun startUpdateAction() {
        val snapshot = state.value
        if (snapshot.isBusy) return

        // Official store channels must never attempt sideloading.
        when (snapshot.buildInfo.installChannel) {
            InstallChannel.GooglePlay -> {
                val url = snapshot.buildInfo.storeUrl
                    ?: "https://play.google.com/store/apps/details?id=${snapshot.buildInfo.packageId}"
                openUrlWithNotice(url, UiText.Resource(Res.string.updater_open_google_play_failed))
                return
            }
            InstallChannel.IosTestFlight -> {
                val url = snapshot.buildInfo.storeUrl
                if (url != null) {
                    openUrlWithNotice(url, UiText.Resource(Res.string.updater_open_testflight_failed))
                } else {
                    mutableState.update {
                        it.copy(notice = UiText.Resource(Res.string.updater_open_testflight_manually))
                    }
                }
                return
            }
            InstallChannel.IosAppStore -> {
                val url = snapshot.buildInfo.storeUrl
                if (url != null) {
                    openUrlWithNotice(url, UiText.Resource(Res.string.updater_open_app_store_failed))
                } else {
                    mutableState.update {
                        it.copy(notice = UiText.Resource(Res.string.updater_open_app_store_manually))
                    }
                }
                return
            }
            InstallChannel.IosSideloadUnsigned -> {
                openReleasePage()
                return
            }
            InstallChannel.AndroidSideload -> Unit
        }

        val candidate = snapshot.availableCandidate
        if (candidate == null) {
            checkForUpdates(manual = true)
            return
        }
        if (candidate.primaryAsset == null) {
            openReleasePage()
            return
        }
        if (!bridge.canInstallUpdatesInApp()) {
            openReleasePage()
            return
        }

        installJob?.cancel()
        installJob = scope.launch {
            try {
                bridge.prepareAndInstall(candidate) { installState ->
                    mutableState.update { it.copy(installState = installState, notice = null) }
                }
            } catch (e: CancellationException) {
                mutableState.update {
                    it.copy(installState = UpdateInstallState.Idle, notice = UiText.Resource(Res.string.updater_download_cancelled))
                }
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(installState = UpdateInstallState.Failed(UiText.Resource(Res.string.updater_prepare_failed)))
                }
            }
        }
    }

    fun openInstallPermissionSettings() {
        if (!bridge.openUnknownSourcesSettings()) {
            mutableState.update {
                it.copy(notice = UiText.Resource(Res.string.updater_open_permissions_manually))
            }
        }
    }

    fun resumePendingInstall() {
        val pending = state.value.installState as? UpdateInstallState.PermissionRequired ?: return
        if (state.value.isBusy) return
        installJob?.cancel()
        installJob = scope.launch {
            bridge.resumePendingInstall(pending.stagedApkPath) { next ->
                mutableState.update { it.copy(installState = next, notice = null) }
            }
        }
    }

    fun cancelTransfer() {
        installJob?.cancel()
        installJob = null
        mutableState.update { it.copy(installState = UpdateInstallState.Idle, notice = UiText.Resource(Res.string.updater_download_cancelled)) }
    }

    fun openReleasePage() {
        val url = state.value.availableCandidate?.release?.htmlUrl ?: SENADA_RELEASES_WEB_URL
        openUrlWithNotice(url, UiText.Resource(Res.string.updater_open_github_failed))
    }

    private fun openUrlWithNotice(url: String, failureMessage: UiText) {
        if (!isTrustedHttpsUrl(url) || !bridge.openExternalUrl(url)) {
            mutableState.update { it.copy(notice = failureMessage) }
        } else {
            mutableState.update { it.copy(notice = null) }
        }
    }
}

internal expect fun updateController(context: PlatformContext): UpdateController
