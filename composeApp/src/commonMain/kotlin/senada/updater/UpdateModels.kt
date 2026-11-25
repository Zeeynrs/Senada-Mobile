package senada.updater

import org.jetbrains.compose.resources.StringResource
import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_channel_beta
import senada.resources.updater_channel_beta_description
import senada.resources.updater_channel_stable
import senada.resources.updater_channel_stable_description
import senada.resources.updater_install_android
import senada.resources.updater_install_app_store
import senada.resources.updater_install_google_play
import senada.resources.updater_install_ios_sideload
import senada.resources.updater_install_testflight

/** Platform target for release asset selection and OS update policy handling. */
internal enum class UpdatePlatform {
    Android,
    Ios,
}

/** Release track chosen by the user. */
internal enum class UpdateChannel(val key: String, val label: StringResource, val description: StringResource) {
    Stable("stable", Res.string.updater_channel_stable, Res.string.updater_channel_stable_description),
    Beta("beta", Res.string.updater_channel_beta, Res.string.updater_channel_beta_description),
}

/**
 * Distribution channel detected at runtime so the updater strictly follows OS and store policies:
 * - [AndroidSideload]: GitHub Releases APK; may download/patch, verify signing, and hand off to OS installer.
 * - [GooglePlay]: Installed via Play Store; must not sideload APKs, delegates to Play Store.
 * - [IosSideloadUnsigned]: Sideloaded/unsigned IPA (AltStore/AltServer/Xcode) or Simulator; iOS forbids in-app IPA installs.
 * - [IosTestFlight]: Installed via Apple TestFlight; updates arrive through TestFlight.
 * - [IosAppStore]: Installed via Apple App Store; updates arrive through App Store.
 */
internal enum class InstallChannel(val label: StringResource) {
    AndroidSideload(Res.string.updater_install_android),
    GooglePlay(Res.string.updater_install_google_play),
    IosSideloadUnsigned(Res.string.updater_install_ios_sideload),
    IosTestFlight(Res.string.updater_install_testflight),
    IosAppStore(Res.string.updater_install_app_store),
}

internal data class InstalledBuildInfo(
    val platform: UpdatePlatform,
    val versionName: String,
    val buildNumber: Long,
    val packageId: String,
    val installChannel: InstallChannel,
    val storeUrl: String? = null,
    val isDebugBuild: Boolean = false,
) {
    val parsedVersion: AppVersion? get() = AppVersion.parse(versionName)
}

internal sealed interface PrereleaseId : Comparable<PrereleaseId> {
    data class Numeric(val value: Long) : PrereleaseId
    data class Text(val value: String) : PrereleaseId

    override fun compareTo(other: PrereleaseId): Int = when {
        this is Numeric && other is Numeric -> value.compareTo(other.value)
        this is Numeric && other is Text -> -1
        this is Text && other is Numeric -> 1
        this is Text && other is Text -> value.compareTo(other.value, ignoreCase = true)
        else -> 0
    }
}

/**
 * Semantic Version (SemVer 2.0.0) parser and comparator supporting tags like `v1.0.0`, `1.2.3-beta.2`,
 * `1.0.0-rc.1+42`, and GitHub release prerelease metadata.
 */
internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<PrereleaseId> = emptyList(),
    val raw: String = buildString {
        append("$major.$minor.$patch")
        if (prerelease.isNotEmpty()) {
            append('-')
            append(prerelease.joinToString(".") {
                when (it) {
                    is PrereleaseId.Numeric -> it.value.toString()
                    is PrereleaseId.Text -> it.value
                }
            })
        }
    },
) : Comparable<AppVersion> {
    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    val displayName: String get() = raw.removePrefix("v").removePrefix("V")

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        val limit = minOf(prerelease.size, other.prerelease.size)
        for (i in 0 until limit) {
            val diff = prerelease[i].compareTo(other.prerelease[i])
            if (diff != 0) return diff
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    companion object {
        private val VERSION_PATTERN = Regex(
            """^[vV]?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""",
        )

        fun parse(input: String): AppVersion? {
            val trimmed = input.trim()
            val match = VERSION_PATTERN.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            val prereleaseRaw = match.groupValues[4]
            val prerelease = if (prereleaseRaw.isEmpty()) {
                emptyList()
            } else {
                prereleaseRaw.split('.').filter { it.isNotEmpty() }.map { token ->
                    token.toLongOrNull()?.let { PrereleaseId.Numeric(it) } ?: PrereleaseId.Text(token)
                }
            }
            return AppVersion(major, minor, patch, prerelease, raw = trimmed)
        }
    }
}

internal data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256Digest: String? = null,
    val contentType: String? = null,
)

internal data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val publishedAt: String? = null,
    val assets: List<ReleaseAsset> = emptyList(),
)

internal data class DeltaPatchEntry(
    val baseSha256: String,
    val targetSha256: String,
    val patchAssetName: String,
    val patchSizeBytes: Long,
    val patchSha256: String? = null,
    val targetVersion: String? = null,
    val targetVersionCode: Long? = null,
)

internal data class DeltaManifest(
    val formatVersion: Int,
    val targetAssetName: String,
    val targetSha256: String,
    val patches: List<DeltaPatchEntry>,
)

internal data class UpdateCandidate(
    val release: GitHubRelease,
    val version: AppVersion,
    val primaryAsset: ReleaseAsset?,
    val deltaManifestAsset: ReleaseAsset? = null,
)

internal sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class UpToDate(
        val installedVersion: String,
        val channel: UpdateChannel,
        val latestRelease: GitHubRelease?,
        val checkedAtMs: Long,
    ) : UpdateCheckResult

    data class NoStableRelease(
        val installedVersion: String,
        val hasBetaRelease: Boolean,
        val checkedAtMs: Long,
    ) : UpdateCheckResult

    data class Available(
        val candidate: UpdateCandidate,
        val channel: UpdateChannel,
        val checkedAtMs: Long,
    ) : UpdateCheckResult

    data class Error(
        val message: UiText,
        val retryable: Boolean = true,
    ) : UpdateCheckResult
}

internal sealed interface TransferMode {
    data class Delta(val patchBytes: Long, val fullBytes: Long) : TransferMode {
        val savedPercent: Int
            get() = if (fullBytes <= 0L) 0 else (((fullBytes - patchBytes) * 100L) / fullBytes).toInt().coerceIn(0, 99)
    }

    data class Full(val fullBytes: Long, val fallbackReason: UiText? = null) : TransferMode
}

internal enum class PackageMismatchReason {
    DigestMismatch,
    PackageIdMismatch,
    SigningCertificateMismatch,
    VersionDowngrade,
    InvalidArchive,
}

internal sealed interface UpdateInstallState {
    data object Idle : UpdateInstallState
    data class Preparing(val message: UiText) : UpdateInstallState
    data class Downloading(
        val percent: Int?,
        val downloadedBytes: Long,
        val totalBytes: Long?,
        val mode: TransferMode,
    ) : UpdateInstallState

    data class ApplyingDelta(val mode: TransferMode.Delta) : UpdateInstallState
    data class Verifying(val mode: TransferMode) : UpdateInstallState
    data class PermissionRequired(
        val message: UiText,
        val stagedApkPath: String,
        val mode: TransferMode,
    ) : UpdateInstallState

    data class InstallerLaunched(
        val message: UiText,
        val mode: TransferMode,
    ) : UpdateInstallState

    data class IncompatiblePackage(
        val reason: PackageMismatchReason,
        val message: UiText,
    ) : UpdateInstallState

    data class Failed(val message: UiText) : UpdateInstallState
}

internal data class UpdatePreferences(
    val channel: UpdateChannel = UpdateChannel.Stable,
    val autoCheckEnabled: Boolean = true,
    val lastCheckedAtMs: Long? = null,
    val dismissedTag: String? = null,
)

internal data class UpdateUiState(
    val buildInfo: InstalledBuildInfo,
    val preferences: UpdatePreferences,
    val checkResult: UpdateCheckResult = UpdateCheckResult.Idle,
    val installState: UpdateInstallState = UpdateInstallState.Idle,
    val notice: UiText? = null,
) {
    val availableCandidate: UpdateCandidate?
        get() = (checkResult as? UpdateCheckResult.Available)?.candidate

    val hasUndismissedUpdate: Boolean
        get() = availableCandidate?.let { it.release.tagName != preferences.dismissedTag } == true

    val isBusy: Boolean
        get() = checkResult is UpdateCheckResult.Checking ||
            installState is UpdateInstallState.Preparing ||
            installState is UpdateInstallState.Downloading ||
            installState is UpdateInstallState.ApplyingDelta ||
            installState is UpdateInstallState.Verifying
}
