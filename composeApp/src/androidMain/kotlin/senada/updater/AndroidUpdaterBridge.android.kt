package senada.updater

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import coil3.PlatformContext
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import senada.app.SenadaSources

private const val UPDATES_CACHE_DIR = "updates"
private const val PLAY_INSTALLER_PACKAGE = "com.android.vending"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

internal actual fun updatePreferencesStore(context: PlatformContext): UpdatePreferencesStore {
    val prefs by lazy {
        context.applicationContext.getSharedPreferences("updater", Context.MODE_PRIVATE)
    }
    return UpdatePreferencesStore(
        read = { prefs.getString("preferences", null) },
        write = { prefs.edit().putString("preferences", it).apply() },
    )
}

@Volatile
private var cachedController: UpdateController? = null

@Synchronized
internal actual fun updateController(context: PlatformContext): UpdateController {
    cachedController?.let { return it }
    val appContext = context.applicationContext
    val client = GitHubReleasesClient(SenadaSources.http)
    val bridge = AndroidUpdaterBridge(appContext, SenadaSources.http, client)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    return UpdateController(
        releaseSource = client,
        preferencesStore = updatePreferencesStore(appContext),
        bridge = bridge,
        scope = scope,
    ).also { cachedController = it }
}

internal class AndroidUpdaterBridge(
    private val context: Context,
    private val http: HttpClient,
    private val releaseSource: ReleaseSource,
) : UpdatePlatformBridge {

    override val buildInfo: InstalledBuildInfo by lazy { readInstalledBuildInfo(context) }

    private val updatesDir: File get() = File(context.cacheDir, UPDATES_CACHE_DIR)

    override fun canInstallUpdatesInApp(): Boolean =
        buildInfo.installChannel == InstallChannel.AndroidSideload

    override fun openExternalUrl(url: String): Boolean {
        if (!isTrustedHttpsUrl(url)) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openUnknownSourcesSettings(): Boolean = try {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    override fun cleanupStaleFiles() {
        runCatching {
            val dir = updatesDir
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".part") || file.name.endsWith(".apkdelta")) {
                        file.delete()
                    }
                }
            }
        }
    }

    override suspend fun prepareAndInstall(
        candidate: UpdateCandidate,
        onState: (UpdateInstallState) -> Unit,
    ) {
        val apkAsset = candidate.primaryAsset
            ?: run {
                onState(UpdateInstallState.Failed("Paket APK untuk rilis ini tidak ditemukan."))
                return
            }
        if (!isTrustedHttpsUrl(apkAsset.downloadUrl)) {
            onState(UpdateInstallState.Failed("Alamat unduhan APK tidak berasal dari host tepercaya."))
            return
        }

        val stagedApk = File(updatesDir, sanitizeFileName(apkAsset.name))
        updatesDir.mkdirs()

        var transferMode: TransferMode = TransferMode.Full(apkAsset.sizeBytes)
        var stagedSha256: String? = null

        // Attempt verified delta patch transfer first when a delta manifest is published and saves bytes.
        val baseApkFile = context.applicationInfo.sourceDir?.let(::File)?.takeIf { it.isFile && it.canRead() }
        val deltaManifestAsset = candidate.deltaManifestAsset
        var deltaFallbackReason: String? = null

        if (deltaManifestAsset != null && baseApkFile != null) {
            onState(UpdateInstallState.Preparing("Memeriksa ketersediaan paket delta…"))
            val baseSha256 = withContext(Dispatchers.IO) { runCatching { ApkDelta.sha256Hex(baseApkFile) }.getOrNull() }
            val manifest = if (baseSha256 != null) {
                releaseSource.fetchDeltaManifest(deltaManifestAsset.downloadUrl, buildInfo.versionName)
            } else {
                null
            }
            val eligible = if (baseSha256 != null && manifest != null) {
                UpdateReleaseSelector.selectEligibleDelta(
                    manifest = manifest,
                    installedApkSha256 = baseSha256,
                    targetAsset = apkAsset,
                    allAssets = candidate.release.assets,
                )
            } else {
                null
            }

            if (eligible != null) {
                val deltaMode = TransferMode.Delta(
                    patchBytes = eligible.patchBytes,
                    fullBytes = apkAsset.sizeBytes,
                )
                val patchFile = File(updatesDir, sanitizeFileName(eligible.patchAsset.name))
                try {
                    val patchSha = downloadFile(
                        url = eligible.patchAsset.downloadUrl,
                        destination = patchFile,
                        expectedSize = eligible.patchBytes,
                        expectedSha256 = eligible.expectedPatchSha256,
                        mode = deltaMode,
                        onState = onState,
                    )
                    if (eligible.expectedPatchSha256 != null && patchSha != eligible.expectedPatchSha256) {
                        throw ApkDeltaException("Checksum patch delta tidak cocok.")
                    }
                    onState(UpdateInstallState.ApplyingDelta(deltaMode))
                    val reconstructedSha = withContext(Dispatchers.IO) {
                        ApkDelta.apply(baseApkFile, patchFile, stagedApk)
                    }
                    if (reconstructedSha != eligible.targetSha256) {
                        throw ApkDeltaException("Checksum APK hasil delta tidak cocok dengan rilis.")
                    }
                    transferMode = deltaMode
                    stagedSha256 = reconstructedSha
                } catch (e: CancellationException) {
                    runCatching { patchFile.delete() }
                    runCatching { stagedApk.delete() }
                    throw e
                } catch (_: Exception) {
                    runCatching { patchFile.delete() }
                    runCatching { stagedApk.delete() }
                    deltaFallbackReason = "Paket delta gagal diterapkan; beralih ke unduhan APK penuh."
                } finally {
                    runCatching { patchFile.delete() }
                }
            } else if (manifest != null) {
                deltaFallbackReason = "Build terpasang tidak cocok dengan paket delta; mengunduh APK penuh."
            }
        }

        // Full APK download (default when no delta manifest is published, or clean fallback from delta).
        if (stagedSha256 == null) {
            val fullMode = TransferMode.Full(
                fullBytes = apkAsset.sizeBytes,
                fallbackReason = deltaFallbackReason,
            )
            transferMode = fullMode
            try {
                stagedSha256 = downloadFile(
                    url = apkAsset.downloadUrl,
                    destination = stagedApk,
                    expectedSize = apkAsset.sizeBytes.takeIf { it > 0L },
                    expectedSha256 = apkAsset.sha256Digest,
                    mode = fullMode,
                    onState = onState,
                )
            } catch (e: CancellationException) {
                runCatching { stagedApk.delete() }
                throw e
            } catch (e: UpdateNetworkException) {
                runCatching { stagedApk.delete() }
                onState(UpdateInstallState.Failed(e.userMessage))
                return
            } catch (_: Exception) {
                runCatching { stagedApk.delete() }
                onState(UpdateInstallState.Failed("Unduhan APK gagal. Periksa koneksi internet lalu coba lagi."))
                return
            }
        }

        // Verify digest, package ID, version monotonicity, and signing certificate before launching OS installer.
        onState(UpdateInstallState.Verifying(transferMode))
        val verification = withContext(Dispatchers.IO) {
            AndroidApkVerifier.verifyStagedApk(
                context = context,
                apkFile = stagedApk,
                expectedSha256 = apkAsset.sha256Digest,
                precomputedSha256 = stagedSha256,
            )
        }
        when (verification) {
            is ApkVerificationResult.Incompatible -> {
                runCatching { stagedApk.delete() }
                onState(UpdateInstallState.IncompatiblePackage(verification.reason, verification.message))
                return
            }
            is ApkVerificationResult.Valid -> Unit
        }

        handOffToSystemInstaller(stagedApk, transferMode, onState)
    }

    override suspend fun resumePendingInstall(
        stagedApkPath: String,
        onState: (UpdateInstallState) -> Unit,
    ) {
        val stagedApk = File(stagedApkPath)
        if (!stagedApk.isFile || !stagedApk.canonicalPath.startsWith(updatesDir.canonicalPath)) {
            onState(UpdateInstallState.Failed("File APK yang disiapkan sudah tidak tersedia. Unduh ulang pembaruan."))
            return
        }
        val mode = TransferMode.Full(stagedApk.length())
        onState(UpdateInstallState.Verifying(mode))
        val verification = withContext(Dispatchers.IO) {
            AndroidApkVerifier.verifyStagedApk(context, stagedApk, expectedSha256 = null)
        }
        when (verification) {
            is ApkVerificationResult.Incompatible -> {
                runCatching { stagedApk.delete() }
                onState(UpdateInstallState.IncompatiblePackage(verification.reason, verification.message))
            }
            is ApkVerificationResult.Valid -> handOffToSystemInstaller(stagedApk, mode, onState)
        }
    }

    private fun handOffToSystemInstaller(
        stagedApk: File,
        mode: TransferMode,
        onState: (UpdateInstallState) -> Unit,
    ) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            onState(
                UpdateInstallState.PermissionRequired(
                    message = "Izinkan Senada memasang aplikasi dari sumber ini di Pengaturan Android, lalu ketuk Pasang sekarang.",
                    stagedApkPath = stagedApk.absolutePath,
                    mode = mode,
                ),
            )
            return
        }
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.updater.fileprovider",
                stagedApk,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            val summary = when (mode) {
                is TransferMode.Delta ->
                    "Pemasang sistem Android dibuka (unduhan delta menghemat ${mode.savedPercent}% data). Ikuti konfirmasi sistem untuk menyelesaikan pembaruan."
                is TransferMode.Full ->
                    "Pemasang sistem Android dibuka. Ikuti konfirmasi sistem untuk menyelesaikan pembaruan."
            }
            onState(UpdateInstallState.InstallerLaunched(summary, mode))
        } catch (_: Exception) {
            onState(UpdateInstallState.Failed("Gagal membuka pemasang sistem Android. Coba lagi atau unduh dari halaman rilis GitHub."))
        }
    }

    private suspend fun downloadFile(
        url: String,
        destination: File,
        expectedSize: Long?,
        expectedSha256: String?,
        mode: TransferMode,
        onState: (UpdateInstallState) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        if (!isTrustedHttpsUrl(url)) {
            throw UpdateNetworkException("Alamat unduhan tidak valid.", retryable = false)
        }
        val partFile = File(destination.parentFile, "${destination.name}.part")
        runCatching { partFile.delete() }
        var completed = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            onState(UpdateInstallState.Downloading(percent = 0, downloadedBytes = 0L, totalBytes = expectedSize, mode = mode))

            http.prepareGet(url) {
                header(HttpHeaders.UserAgent, "Senada-Mobile/${buildInfo.versionName.ifBlank { "1.0.0" }}")
                timeout {
                    requestTimeoutMillis = 600_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }.execute { response ->
                if (response.status != HttpStatusCode.OK) {
                    throw UpdateNetworkException("Server mengembalikan status ${response.status.value} saat mengunduh pembaruan.")
                }
                val total = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it > 0L } ?: expectedSize
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(64 * 1024)
                var lastPercent = -1

                FileOutputStream(partFile).use { out ->
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count == 0) continue
                        out.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        received += count
                        val percent = total?.let { ((received * 100L) / it).toInt().coerceIn(0, 99) }
                        if (percent != null && percent != lastPercent) {
                            lastPercent = percent
                            onState(UpdateInstallState.Downloading(percent, received, total, mode))
                        } else if (percent == null) {
                            onState(UpdateInstallState.Downloading(null, received, null, mode))
                        }
                    }
                }
                if (received <= 0L || (total != null && received != total)) {
                    throw UpdateNetworkException("Unduhan terputus sebelum selesai. Coba lagi.")
                }
            }

            val actualHex = digest.digest().toHex()
            val normalizedExpected = normalizeSha256Digest(expectedSha256)
            if (expectedSha256 != null && (normalizedExpected == null || actualHex != normalizedExpected)) {
                throw UpdateNetworkException("Checksum file unduhan tidak cocok dengan rilis GitHub.")
            }
            if (destination.exists()) destination.delete()
            if (!partFile.renameTo(destination)) {
                partFile.copyTo(destination, overwrite = true)
                partFile.delete()
            }
            completed = true
            actualHex
        } finally {
            if (!completed) {
                runCatching { partFile.delete() }
            }
        }
    }
}

private fun readInstalledBuildInfo(context: Context): InstalledBuildInfo {
    val pm = context.packageManager
    val pkgName = context.packageName
    val info = try {
        pm.getPackageInfo(pkgName, 0)
    } catch (_: Exception) {
        null
    }
    val versionName = info?.versionName.orEmpty().ifBlank { "1.0.0" }
    val versionCode = if (info == null) {
        0L
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }
    val installerPackage = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(pkgName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(pkgName)
        }
    } catch (_: Exception) {
        null
    }
    val channel = if (installerPackage == PLAY_INSTALLER_PACKAGE) {
        InstallChannel.GooglePlay
    } else {
        InstallChannel.AndroidSideload
    }
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    return InstalledBuildInfo(
        platform = UpdatePlatform.Android,
        versionName = versionName,
        buildNumber = versionCode,
        packageId = pkgName,
        installChannel = channel,
        storeUrl = if (channel == InstallChannel.GooglePlay) {
            "https://play.google.com/store/apps/details?id=$pkgName"
        } else {
            null
        },
        isDebugBuild = isDebug,
    )
}

private fun sanitizeFileName(raw: String): String =
    raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._-]"), "_")
