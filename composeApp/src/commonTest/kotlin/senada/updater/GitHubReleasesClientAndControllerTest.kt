package senada.updater

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubReleasesClientAndControllerTest {
    @Test
    fun parsesGitHubReleaseJsonWithDigestAndUsesEtagCache() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (calls == 1) {
                respond(
                    content = """
                        [
                          {
                            "tag_name": "v1.1.0",
                            "name": "Senada Mobile 1.1.0",
                            "body": "Perbaikan audio dan updater",
                            "html_url": "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.1.0",
                            "draft": false,
                            "prerelease": false,
                            "published_at": "2026-11-26T00:00:00Z",
                            "assets": [
                              {
                                "name": "Senada-Android-1.1.0.apk",
                                "browser_download_url": "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.1.0/Senada-Android-1.1.0.apk",
                                "size": 18500000,
                                "digest": "sha256:2cf4d040058f09bf1f32265f6b74a8bfdfb10c5c26c9ab208ad254cc046cb188",
                                "content_type": "application/vnd.android.package-archive"
                              }
                            ]
                          }
                        ]
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.ETag to listOf("\"etag-1\""),
                    ),
                )
            } else {
                assertEquals("\"etag-1\"", request.headers[HttpHeaders.IfNoneMatch])
                respond(content = "", status = HttpStatusCode.NotModified)
            }
        }
        val client = GitHubReleasesClient(HttpClient(engine))
        val first = client.fetchReleases("1.0.0")
        assertEquals(1, first.size)
        assertEquals("2cf4d040058f09bf1f32265f6b74a8bfdfb10c5c26c9ab208ad254cc046cb188", first.first().assets.first().sha256Digest)

        val second = client.fetchReleases("1.0.0")
        assertEquals(first, second)
    }

    @Test
    fun handlesRateLimitAndServerErrorsCleanly() = runTest {
        val rateLimitedEngine = MockEngine {
            respond(
                content = """{"message":"API rate limit exceeded"}""",
                status = HttpStatusCode.Forbidden,
                headers = headersOf("x-ratelimit-remaining" to listOf("0")),
            )
        }
        val client = GitHubReleasesClient(HttpClient(rateLimitedEngine))
        var rateMessage: String? = null
        try {
            client.fetchReleases("1.0.0")
        } catch (e: UpdateNetworkException) {
            rateMessage = e.userMessage
        }
        assertTrue(rateMessage?.contains("Batas permintaan GitHub", ignoreCase = true) == true)

        val serverErrEngine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val client2 = GitHubReleasesClient(HttpClient(serverErrEngine))
        var errMessage: String? = null
        try {
            client2.fetchReleases("1.0.0")
        } catch (e: UpdateNetworkException) {
            errMessage = e.userMessage
        }
        assertTrue(errMessage?.contains("503") == true)
    }

    @Test
    fun rejectsUntrustedUrlsAndRecoversFromCorruptPreferences() {
        assertTrue(isTrustedHttpsUrl("https://github.com/Zeeynrs/Senada-Mobile/releases"))
        assertFalse(isTrustedHttpsUrl("http://github.com/Zeeynrs/Senada-Mobile/releases"))
        assertFalse(isTrustedHttpsUrl("https://evil.example.com/fake.apk"))

        var raw: String? = "{not valid json"
        val store = UpdatePreferencesStore(read = { raw }, write = { raw = it })
        assertEquals(UpdatePreferences(channel = UpdateChannel.Stable), store.load())

        val saved = UpdatePreferences(
            channel = UpdateChannel.Beta,
            autoCheckEnabled = false,
            lastCheckedAtMs = 123_456L,
            dismissedTag = "v1.1.0-beta.1",
        )
        store.save(saved)
        assertEquals(saved, store.load())
    }

    @Test
    fun detectsIosInstallChannelsAndNeverAssumesUnconfiguredStoreLinks() {
        assertEquals(
            InstallChannel.IosSideloadUnsigned,
            detectIosInstallChannel(hasEmbeddedMobileProvision = true, isSimulator = false, receiptLastPathComponent = "sandboxReceipt"),
        )
        assertEquals(
            InstallChannel.IosSideloadUnsigned,
            detectIosInstallChannel(hasEmbeddedMobileProvision = false, isSimulator = true, receiptLastPathComponent = "sandboxReceipt"),
        )
        assertEquals(
            InstallChannel.IosTestFlight,
            detectIosInstallChannel(hasEmbeddedMobileProvision = false, isSimulator = false, receiptLastPathComponent = "sandboxReceipt"),
        )
        assertEquals(
            InstallChannel.IosAppStore,
            detectIosInstallChannel(hasEmbeddedMobileProvision = false, isSimulator = false, receiptLastPathComponent = "receipt"),
        )
    }

    @Test
    fun controllerRespectsPlatformPoliciesAcrossAndroidPlayAndIos() = runTest {
        val release = GitHubRelease(
            tagName = "v1.2.0",
            name = "Senada 1.2.0",
            body = "Release notes",
            htmlUrl = "https://github.com/Zeeynrs/Senada-Mobile/releases/tag/v1.2.0",
            isDraft = false,
            isPrerelease = false,
            assets = listOf(
                ReleaseAsset("Senada-Android-1.2.0.apk", "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.2.0/Senada-Android-1.2.0.apk", 18_000_000),
                ReleaseAsset("Senada-iPhone-1.2.0-unsigned.ipa", "https://github.com/Zeeynrs/Senada-Mobile/releases/download/v1.2.0/Senada-iPhone-1.2.0-unsigned.ipa", 14_000_000),
            ),
        )
        val fakeSource = object : ReleaseSource {
            override suspend fun fetchReleases(userAgentVersion: String) = listOf(release)
            override suspend fun fetchDeltaManifest(manifestUrl: String, userAgentVersion: String) = null
        }

        // 1. iOS Unsigned Sideload: opens GitHub release page, never tries in-app install.
        var iosOpenedUrl: String? = null
        var iosInstallCalled = false
        val iosBridge = FakeBridge(
            buildInfo = InstalledBuildInfo(
                platform = UpdatePlatform.Ios,
                versionName = "1.0.0",
                buildNumber = 2,
                packageId = "io.github.zeeynrs.senada",
                installChannel = InstallChannel.IosSideloadUnsigned,
            ),
            canInstall = false,
            onOpenUrl = { iosOpenedUrl = it; true },
            onInstall = { iosInstallCalled = true },
        )
        val iosController = UpdateController(fakeSource, memoryStore(), iosBridge, this, nowMs = { 10_000L })
        iosController.checkForUpdates(manual = true)
        advanceUntilIdle()
        val iosPresentation = describeUpdateActions(iosController.state.value)
        assertFalse(iosPresentation.supportsInAppInstall)
        assertEquals("Buka halaman rilis", iosPresentation.primaryLabel)
        assertTrue(iosPresentation.channelExplanation.contains("2.5.2"))

        iosController.startUpdateAction()
        advanceUntilIdle()
        assertFalse(iosInstallCalled)
        assertEquals(release.htmlUrl, iosOpenedUrl)

        // 2. Android Google Play: never sideloads APK, opens Play Store listing.
        var playOpenedUrl: String? = null
        var playInstallCalled = false
        val playBridge = FakeBridge(
            buildInfo = InstalledBuildInfo(
                platform = UpdatePlatform.Android,
                versionName = "1.0.0",
                buildNumber = 2,
                packageId = "io.github.zeeynrs.senada",
                installChannel = InstallChannel.GooglePlay,
                storeUrl = "https://play.google.com/store/apps/details?id=io.github.zeeynrs.senada",
            ),
            canInstall = false,
            onOpenUrl = { playOpenedUrl = it; true },
            onInstall = { playInstallCalled = true },
        )
        val playController = UpdateController(fakeSource, memoryStore(), playBridge, this, nowMs = { 10_000L })
        playController.checkForUpdates(manual = true)
        advanceUntilIdle()
        assertEquals("Buka di Google Play", describeUpdateActions(playController.state.value).primaryLabel)
        playController.startUpdateAction()
        advanceUntilIdle()
        assertFalse(playInstallCalled)
        assertEquals("https://play.google.com/store/apps/details?id=io.github.zeeynrs.senada", playOpenedUrl)

        // 3. Android Sideload: invokes prepareAndInstall, respects dismiss and data saver on auto-check.
        var sideloadInstallCalled = false
        val sideloadBridge = FakeBridge(
            buildInfo = InstalledBuildInfo(
                platform = UpdatePlatform.Android,
                versionName = "1.0.0",
                buildNumber = 2,
                packageId = "io.github.zeeynrs.senada",
                installChannel = InstallChannel.AndroidSideload,
            ),
            canInstall = true,
            onOpenUrl = { true },
            onInstall = { cb ->
                sideloadInstallCalled = true
                cb(UpdateInstallState.InstallerLaunched("Opened", TransferMode.Delta(900_000, 18_000_000)))
            },
        )
        val sideloadController = UpdateController(fakeSource, memoryStore(), sideloadBridge, this, nowMs = { 10_000L })
        sideloadController.checkIfDue(saveDataActive = true)
        advanceUntilIdle()
        assertIs<UpdateCheckResult.Idle>(sideloadController.state.value.checkResult)

        sideloadController.checkIfDue(saveDataActive = false)
        advanceUntilIdle()
        assertTrue(sideloadController.state.value.hasUndismissedUpdate)
        sideloadController.dismissUpdateBanner()
        assertFalse(sideloadController.state.value.hasUndismissedUpdate)

        sideloadController.startUpdateAction()
        advanceUntilIdle()
        assertTrue(sideloadInstallCalled)
        assertIs<UpdateInstallState.InstallerLaunched>(sideloadController.state.value.installState)
    }

    private fun memoryStore(): UpdatePreferencesStore {
        var data: String? = null
        return UpdatePreferencesStore(read = { data }, write = { data = it })
    }

    private class FakeBridge(
        override val buildInfo: InstalledBuildInfo,
        private val canInstall: Boolean,
        private val onOpenUrl: (String) -> Boolean,
        private val onInstall: ((UpdateInstallState) -> Unit) -> Unit,
    ) : UpdatePlatformBridge {
        override fun canInstallUpdatesInApp(): Boolean = canInstall
        override fun openExternalUrl(url: String): Boolean = onOpenUrl(url)
        override suspend fun prepareAndInstall(candidate: UpdateCandidate, onState: (UpdateInstallState) -> Unit) {
            onInstall(onState)
        }
    }
}
