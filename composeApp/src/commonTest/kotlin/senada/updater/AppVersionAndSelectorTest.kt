package senada.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppVersionAndSelectorTest {
    @Test
    fun parsesAndComparesSemverAndPrereleases() {
        val v100 = assertNotNull(AppVersion.parse("v1.0.0"))
        val v101 = assertNotNull(AppVersion.parse("1.0.1"))
        val v110 = assertNotNull(AppVersion.parse("v1.10.0"))
        val v19 = assertNotNull(AppVersion.parse("v1.9.9"))
        val alpha = assertNotNull(AppVersion.parse("1.0.0-alpha.1"))
        val beta2 = assertNotNull(AppVersion.parse("1.0.0-beta.2"))
        val beta10 = assertNotNull(AppVersion.parse("1.0.0-beta.10"))
        val rc1 = assertNotNull(AppVersion.parse("1.0.0-rc.1+build.42"))

        assertTrue(v101 > v100)
        assertTrue(v110 > v19)
        assertTrue(v100 > rc1)
        assertTrue(rc1 > beta10)
        assertTrue(beta10 > beta2)
        assertTrue(beta2 > alpha)
        assertEquals(0, v100.compareTo(assertNotNull(AppVersion.parse("1.0.0+99"))))
        assertNull(AppVersion.parse("not-a-version"))
    }

    @Test
    fun stableChannelIgnoresPrereleasesAndReportsBetaAvailability() {
        val installed = InstalledBuildInfo(
            platform = UpdatePlatform.Android,
            versionName = "1.0.0",
            buildNumber = 2,
            packageId = "io.github.zeeynrs.senada",
            installChannel = InstallChannel.AndroidSideload,
        )
        val releases = listOf(
            GitHubRelease(
                tagName = "v1.0.0",
                name = "Senada Mobile 1.0.0",
                body = "Beta test",
                htmlUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.0.0",
                isDraft = false,
                isPrerelease = true,
                assets = listOf(
                    ReleaseAsset("Senada-Android-1.0.0-beta.apk", "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.0.0/Senada-Android-1.0.0-beta.apk", 18_566_932),
                ),
            ),
            GitHubRelease(
                tagName = "v1.1.0-beta.1",
                name = "Senada Mobile 1.1.0 Beta 1",
                body = "New beta",
                htmlUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.1.0-beta.1",
                isDraft = false,
                isPrerelease = true,
                assets = listOf(
                    ReleaseAsset("Senada-Android-1.1.0-beta.1.apk", "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.1.0-beta.1/Senada-Android-1.1.0-beta.1.apk", 19_000_000),
                ),
            ),
        )

        val stableResult = UpdateReleaseSelector.evaluate(installed, releases, UpdateChannel.Stable, checkedAtMs = 1000L)
        val noStable = assertIs<UpdateCheckResult.NoStableRelease>(stableResult)
        assertTrue(noStable.hasBetaRelease)

        val betaResult = UpdateReleaseSelector.evaluate(installed, releases, UpdateChannel.Beta, checkedAtMs = 1000L)
        val available = assertIs<UpdateCheckResult.Available>(betaResult)
        assertEquals("v1.1.0-beta.1", available.candidate.release.tagName)
        assertEquals("Senada-Android-1.1.0-beta.1.apk", available.candidate.primaryAsset?.name)
    }

    @Test
    fun installedStableVersionDoesNotDowngradeToSameVersionPrerelease() {
        val installed = InstalledBuildInfo(
            platform = UpdatePlatform.Android,
            versionName = "1.0.0",
            buildNumber = 2,
            packageId = "io.github.zeeynrs.senada",
            installChannel = InstallChannel.AndroidSideload,
        )
        val v100BetaRelease = GitHubRelease(
            tagName = "v1.0.0",
            name = "Senada Mobile 1.0.0",
            body = "Beta test",
            htmlUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.0.0",
            isDraft = false,
            isPrerelease = true,
        )
        val result = UpdateReleaseSelector.evaluate(installed, listOf(v100BetaRelease), UpdateChannel.Beta, checkedAtMs = 2000L)
        assertIs<UpdateCheckResult.UpToDate>(result)
    }

    @Test
    fun betaBuildUpgradesToStableReleaseOfSameNumericVersion() {
        val installed = InstalledBuildInfo(
            platform = UpdatePlatform.Ios,
            versionName = "1.0.0-beta.2",
            buildNumber = 2,
            packageId = "io.github.zeeynrs.senada",
            installChannel = InstallChannel.IosSideloadUnsigned,
        )
        val stable = GitHubRelease(
            tagName = "v1.0.0",
            name = "Senada Mobile 1.0.0",
            body = "Stable",
            htmlUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.0.0",
            isDraft = false,
            isPrerelease = false,
            assets = listOf(
                ReleaseAsset("Senada-iPhone-1.0.0-unsigned.ipa", "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.0.0/Senada-iPhone-1.0.0-unsigned.ipa", 14_800_000),
            ),
        )
        val result = UpdateReleaseSelector.evaluate(installed, listOf(stable), UpdateChannel.Stable, checkedAtMs = 3000L)
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("Senada-iPhone-1.0.0-unsigned.ipa", available.candidate.primaryAsset?.name)
    }

    @Test
    fun draftsAreAlwaysIgnoredAndHighestVersionWinsRegardlessOfOrder() {
        val installed = InstalledBuildInfo(
            platform = UpdatePlatform.Android,
            versionName = "1.0.0",
            buildNumber = 2,
            packageId = "io.github.zeeynrs.senada",
            installChannel = InstallChannel.AndroidSideload,
        )
        val releases = listOf(
            GitHubRelease("v9.9.9", "Draft", "", "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v9.9.9", isDraft = true, isPrerelease = false),
            GitHubRelease("v1.0.2", "1.0.2", "", "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.0.2", isDraft = false, isPrerelease = false),
            GitHubRelease("v1.2.0", "1.2.0", "", "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.2.0", isDraft = false, isPrerelease = false),
        )
        val result = UpdateReleaseSelector.evaluate(installed, releases, UpdateChannel.Stable, checkedAtMs = 4000L)
        val available = assertIs<UpdateCheckResult.Available>(result)
        assertEquals("v1.2.0", available.candidate.release.tagName)
    }

    @Test
    fun deltaEligibilityRequiresMatchingBaseTargetAndRealByteSavings() {
        val baseSha = "a".repeat(64)
        val otherBaseSha = "b".repeat(64)
        val targetSha = "c".repeat(64)
        val patchSha = "d".repeat(64)

        val targetApk = ReleaseAsset(
            name = "Senada-Android-1.1.0.apk",
            downloadUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.1.0/Senada-Android-1.1.0.apk",
            sizeBytes = 18_500_000,
            sha256Digest = targetSha,
        )
        val smallPatch = ReleaseAsset(
            name = "Senada-Android-1.0.0-to-1.1.0.apkdelta",
            downloadUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.1.0/Senada-Android-1.0.0-to-1.1.0.apkdelta",
            sizeBytes = 900_000,
            sha256Digest = patchSha,
        )
        val oversizedPatch = smallPatch.copy(name = "oversized.apkdelta", sizeBytes = 19_000_000)

        val manifest = DeltaManifest(
            formatVersion = 1,
            targetAssetName = targetApk.name,
            targetSha256 = targetSha,
            patches = listOf(
                DeltaPatchEntry(baseSha, targetSha, smallPatch.name, smallPatch.sizeBytes, patchSha),
                DeltaPatchEntry(otherBaseSha, targetSha, oversizedPatch.name, oversizedPatch.sizeBytes, patchSha),
            ),
        )

        val eligible = UpdateReleaseSelector.selectEligibleDelta(
            manifest = manifest,
            installedApkSha256 = baseSha,
            targetAsset = targetApk,
            allAssets = listOf(targetApk, smallPatch, oversizedPatch),
        )
        assertNotNull(eligible)
        assertEquals(900_000L, eligible.patchBytes)

        // Mismatched installed APK hash falls back to full download.
        assertNull(
            UpdateReleaseSelector.selectEligibleDelta(
                manifest = manifest,
                installedApkSha256 = "f".repeat(64),
                targetAsset = targetApk,
                allAssets = listOf(targetApk, smallPatch),
            ),
        )

        // Patch >= full APK size does not reduce bytes -> rejected.
        assertNull(
            UpdateReleaseSelector.selectEligibleDelta(
                manifest = manifest,
                installedApkSha256 = otherBaseSha,
                targetAsset = targetApk,
                allAssets = listOf(targetApk, smallPatch, oversizedPatch),
            ),
        )
    }
}
