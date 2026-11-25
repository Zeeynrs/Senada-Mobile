package senada.updater

import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_status_unknown_installed

/**
 * Pure release selection and delta eligibility logic shared by Android and iOS.
 */
internal object UpdateReleaseSelector {
    fun evaluate(
        installed: InstalledBuildInfo,
        releases: List<GitHubRelease>,
        channel: UpdateChannel,
        checkedAtMs: Long,
    ): UpdateCheckResult {
        val published = releases.filterNot { it.isDraft }
        val installedVersion = installed.parsedVersion
            ?: return UpdateCheckResult.Error(
                message = UiText.Resource(Res.string.updater_status_unknown_installed, installed.versionName),
                retryable = false,
            )

        val candidatesWithFlag = published.mapNotNull { release ->
            val parsed = AppVersion.parse(release.tagName) ?: return@mapNotNull null
            val effectivePrerelease = release.isPrerelease || parsed.isPrerelease
            val effectiveVersion = if (effectivePrerelease && !parsed.isPrerelease) {
                // GitHub marks the release as prerelease even when the tag has no `-beta` suffix (e.g. `v1.0.0` Beta test).
                parsed.copy(prerelease = listOf(PrereleaseId.Text("beta")))
            } else {
                parsed
            }
            ParsedRelease(release = release, version = effectiveVersion, isPrerelease = effectivePrerelease)
        }

        val eligible = when (channel) {
            UpdateChannel.Stable -> candidatesWithFlag.filterNot { it.isPrerelease }
            UpdateChannel.Beta -> candidatesWithFlag
        }

        if (eligible.isEmpty()) {
            val hasBeta = candidatesWithFlag.any { it.isPrerelease }
            return if (channel == UpdateChannel.Stable) {
                UpdateCheckResult.NoStableRelease(
                    installedVersion = installed.versionName,
                    hasBetaRelease = hasBeta,
                    checkedAtMs = checkedAtMs,
                )
            } else {
                UpdateCheckResult.UpToDate(
                    installedVersion = installed.versionName,
                    channel = channel,
                    latestRelease = null,
                    checkedAtMs = checkedAtMs,
                )
            }
        }

        val best = eligible.maxWith(compareBy<ParsedRelease> { it.version }.thenBy { it.release.publishedAt.orEmpty() })
        val isNewer = isUpdateNewer(installedVersion, best.version)
        if (!isNewer) {
            return UpdateCheckResult.UpToDate(
                installedVersion = installed.versionName,
                channel = channel,
                latestRelease = best.release,
                checkedAtMs = checkedAtMs,
            )
        }

        val primaryAsset = selectPrimaryAsset(best.release.assets, installed.platform)
        val deltaManifestAsset = if (installed.platform == UpdatePlatform.Android) {
            selectDeltaManifestAsset(best.release.assets)
        } else {
            null
        }

        return UpdateCheckResult.Available(
            candidate = UpdateCandidate(
                release = best.release,
                version = best.version,
                primaryAsset = primaryAsset,
                deltaManifestAsset = deltaManifestAsset,
            ),
            channel = channel,
            checkedAtMs = checkedAtMs,
        )
    }

    /**
     * Returns true strictly when [remote] represents a newer release than [installed].
     * Note: per SemVer 2.0.0, stable `1.0.0` > prerelease `1.0.0-beta`, so an installed stable `1.0.0`
     * will not treat a `1.0.0` prerelease as an upgrade, while an installed `1.0.0-beta` will upgrade to stable `1.0.0`.
     */
    fun isUpdateNewer(installed: AppVersion, remote: AppVersion): Boolean = remote > installed

    fun selectPrimaryAsset(assets: List<ReleaseAsset>, platform: UpdatePlatform): ReleaseAsset? = when (platform) {
        UpdatePlatform.Android -> {
            val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            apks.firstOrNull { it.name.contains("android", ignoreCase = true) && !it.name.contains("delta", ignoreCase = true) }
                ?: apks.firstOrNull { !it.name.contains("delta", ignoreCase = true) }
                ?: apks.firstOrNull()
        }
        UpdatePlatform.Ios -> {
            val ipas = assets.filter { it.name.endsWith(".ipa", ignoreCase = true) }
            ipas.firstOrNull { it.name.contains("iphone", ignoreCase = true) || it.name.contains("ios", ignoreCase = true) }
                ?: ipas.firstOrNull()
        }
    }

    fun selectDeltaManifestAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull {
            it.name.endsWith(".json", ignoreCase = true) &&
                it.name.contains("delta", ignoreCase = true)
        }

    /**
     * Picks a valid delta patch only if it strictly reduces transfer size and matches both
     * the installed base APK digest and the target release APK digest.
     */
    fun selectEligibleDelta(
        manifest: DeltaManifest,
        installedApkSha256: String,
        targetAsset: ReleaseAsset,
        allAssets: List<ReleaseAsset>,
    ): EligibleDelta? {
        val normalizedBase = normalizeSha256Digest(installedApkSha256) ?: return null
        val targetDigest = targetAsset.sha256Digest ?: manifest.targetSha256
        if (manifest.targetAssetName != targetAsset.name) return null
        if (manifest.targetSha256 != targetDigest) return null

        val entry = manifest.patches.firstOrNull {
            it.baseSha256 == normalizedBase && it.targetSha256 == targetDigest
        } ?: return null

        val patchAsset = allAssets.firstOrNull { it.name == entry.patchAssetName } ?: return null
        val patchBytes = if (patchAsset.sizeBytes > 0L) patchAsset.sizeBytes else entry.patchSizeBytes
        if (patchBytes <= 0L) return null
        if (targetAsset.sizeBytes > 0L && patchBytes >= targetAsset.sizeBytes) {
            // Only use delta if it genuinely saves downloaded bytes.
            return null
        }
        val expectedPatchSha = patchAsset.sha256Digest ?: entry.patchSha256
        return EligibleDelta(
            entry = entry,
            patchAsset = patchAsset,
            patchBytes = patchBytes,
            expectedPatchSha256 = expectedPatchSha,
            targetSha256 = targetDigest,
        )
    }

    private data class ParsedRelease(
        val release: GitHubRelease,
        val version: AppVersion,
        val isPrerelease: Boolean,
    )
}

internal data class EligibleDelta(
    val entry: DeltaPatchEntry,
    val patchAsset: ReleaseAsset,
    val patchBytes: Long,
    val expectedPatchSha256: String?,
    val targetSha256: String,
)
