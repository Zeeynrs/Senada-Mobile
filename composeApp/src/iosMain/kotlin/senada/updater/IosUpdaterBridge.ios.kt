package senada.updater

import coil3.PlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import senada.app.SenadaSources

private const val UPDATER_DEFAULTS_KEY = "senada.updater.preferences"

internal actual fun updatePreferencesStore(context: PlatformContext): UpdatePreferencesStore {
    val prefs = NSUserDefaults.standardUserDefaults
    return UpdatePreferencesStore(
        read = { prefs.stringForKey(UPDATER_DEFAULTS_KEY) },
        write = { prefs.setObject(it, forKey = UPDATER_DEFAULTS_KEY) },
    )
}

private var cachedIosController: UpdateController? = null

internal actual fun updateController(context: PlatformContext): UpdateController {
    cachedIosController?.let { return it }
    val client = GitHubReleasesClient(SenadaSources.http)
    val bridge = IosUpdaterBridge()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    return UpdateController(
        releaseSource = client,
        preferencesStore = updatePreferencesStore(context),
        bridge = bridge,
        scope = scope,
    ).also { cachedIosController = it }
}

internal class IosUpdaterBridge : UpdatePlatformBridge {
    override val buildInfo: InstalledBuildInfo by lazy { readIosBuildInfo() }

    /**
     * Apple App Store Review Guideline 2.5.2 forbids downloading/installing executable binaries (.ipa)
     * from inside an iOS app. Updates must go through official Apple channels or external re-signing.
     */
    override fun canInstallUpdatesInApp(): Boolean = false

    override fun openExternalUrl(url: String): Boolean {
        if (!isTrustedHttpsUrl(url)) return false
        val nsUrl = NSURL.URLWithString(url) ?: return false
        return try {
            UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun prepareAndInstall(
        candidate: UpdateCandidate,
        onState: (UpdateInstallState) -> Unit,
    ) {
        // iOS cannot install unsigned IPAs from inside the app; direct user to the official release/channel.
        onState(UpdateInstallState.Idle)
    }
}

private fun readIosBuildInfo(): InstalledBuildInfo {
    val bundle = NSBundle.mainBundle
    val versionName = (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
        ?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val buildRaw = (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String).orEmpty()
    val buildNumber = buildRaw.substringBefore('.').toLongOrNull() ?: 0L
    val bundleId = bundle.bundleIdentifier ?: "io.github.zeeynrs.senada"

    val hasProvision = bundle.pathForResource("embedded", "mobileprovision") != null
    val env = NSProcessInfo.processInfo.environment
    val isSimulator = env["SIMULATOR_DEVICE_NAME"] != null || env["SIMULATOR_UDID"] != null
    val receiptName = bundle.appStoreReceiptURL?.lastPathComponent

    val channel = detectIosInstallChannel(
        hasEmbeddedMobileProvision = hasProvision,
        isSimulator = isSimulator,
        receiptLastPathComponent = receiptName,
    )

    // Optional Info.plist links are used ONLY when explicitly configured; never assumed.
    val configuredUrl = when (channel) {
        InstallChannel.IosTestFlight -> bundle.objectForInfoDictionaryKey("SenadaTestFlightURL") as? String
        InstallChannel.IosAppStore -> bundle.objectForInfoDictionaryKey("SenadaAppStoreURL") as? String
        else -> null
    }?.takeIf { isTrustedHttpsUrl(it) }

    return InstalledBuildInfo(
        platform = UpdatePlatform.Ios,
        versionName = versionName,
        buildNumber = buildNumber,
        packageId = bundleId,
        installChannel = channel,
        storeUrl = configuredUrl,
        isDebugBuild = isSimulator,
    )
}
