package senada.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest
import senada.app.UiText
import senada.resources.Res
import senada.resources.updater_apk_checksum_mismatch
import senada.resources.updater_apk_downgrade
import senada.resources.updater_apk_invalid_archive
import senada.resources.updater_apk_invalid_checksum
import senada.resources.updater_apk_invalid_installed
import senada.resources.updater_apk_invalid_signer
import senada.resources.updater_apk_package_mismatch
import senada.resources.updater_apk_signer_mismatch

internal data class ApkPackageMetadata(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signingCertSha256s: Set<String>,
)

internal sealed interface ApkVerificationResult {
    data class Valid(val archive: ApkPackageMetadata) : ApkVerificationResult
    data class Incompatible(val reason: PackageMismatchReason, val message: UiText) : ApkVerificationResult
}

/**
 * Pure verification policy run BEFORE launching Android's system package installer so mismatches
 * in checksum, applicationId, versionCode downgrade, or signing certificate are caught cleanly.
 */
internal object AndroidApkVerifier {
    fun verifyCompatibility(
        installed: ApkPackageMetadata,
        archive: ApkPackageMetadata?,
        actualFileSha256: String,
        expectedFileSha256: String?,
    ): ApkVerificationResult {
        val normalizedActual = normalizeSha256Digest(actualFileSha256)
            ?: return ApkVerificationResult.Incompatible(
                PackageMismatchReason.DigestMismatch,
                UiText.Resource(Res.string.updater_apk_invalid_checksum),
            )
        val normalizedExpected = normalizeSha256Digest(expectedFileSha256)
        if (expectedFileSha256 != null && (normalizedExpected == null || normalizedActual != normalizedExpected)) {
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.DigestMismatch,
                UiText.Resource(Res.string.updater_apk_checksum_mismatch),
            )
        }
        if (archive == null) {
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.InvalidArchive,
                UiText.Resource(Res.string.updater_apk_invalid_archive),
            )
        }
        if (archive.packageName != installed.packageName) {
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.PackageIdMismatch,
                UiText.Resource(Res.string.updater_apk_package_mismatch, archive.packageName, installed.packageName),
            )
        }
        if (archive.versionCode < installed.versionCode) {
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.VersionDowngrade,
                UiText.Resource(Res.string.updater_apk_downgrade, archive.versionCode, installed.versionCode),
            )
        }
        if (installed.signingCertSha256s.isEmpty() || archive.signingCertSha256s.isEmpty()) {
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.SigningCertificateMismatch,
                UiText.Resource(Res.string.updater_apk_invalid_signer),
            )
        }
        val sharesSigner = installed.signingCertSha256s.any { it in archive.signingCertSha256s }
        if (!sharesSigner) {
            val installedFp = shortFingerprint(installed.signingCertSha256s.first())
            val archiveFp = shortFingerprint(archive.signingCertSha256s.first())
            return ApkVerificationResult.Incompatible(
                PackageMismatchReason.SigningCertificateMismatch,
                UiText.Resource(
                    Res.string.updater_apk_signer_mismatch,
                    archiveFp,
                    installedFp,
                ),
            )
        }
        return ApkVerificationResult.Valid(archive)
    }

    fun verifyStagedApk(
        context: Context,
        apkFile: File,
        expectedSha256: String?,
        precomputedSha256: String? = null,
    ): ApkVerificationResult {
        val installed = readInstalledMetadata(context)
            ?: return ApkVerificationResult.Incompatible(
                PackageMismatchReason.InvalidArchive,
                UiText.Resource(Res.string.updater_apk_invalid_installed),
            )
        val actualSha = precomputedSha256 ?: ApkDelta.sha256Hex(apkFile)
        val archive = readArchiveMetadata(context, apkFile)
        return verifyCompatibility(
            installed = installed,
            archive = archive,
            actualFileSha256 = actualSha,
            expectedFileSha256 = expectedSha256,
        )
    }

    fun readInstalledMetadata(context: Context): ApkPackageMetadata? = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }
        info.toMetadata()
    } catch (_: Exception) {
        null
    }

    fun readArchiveMetadata(context: Context, apkFile: File): ApkPackageMetadata? = try {
        val pm = context.packageManager
        val path = apkFile.absolutePath
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                path,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
        }
        info?.toMetadata()
    } catch (_: Exception) {
        null
    }

    private fun PackageInfo.toMetadata(): ApkPackageMetadata {
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
        return ApkPackageMetadata(
            packageName = packageName.orEmpty(),
            versionCode = versionCode,
            versionName = versionName.orEmpty(),
            signingCertSha256s = extractSigningDigests(this),
        )
    }

    private fun extractSigningDigests(info: PackageInfo): Set<String> {
        val signatures: List<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            if (signingInfo == null) {
                emptyList()
            } else {
                val combined = LinkedHashSet<Signature>()
                signingInfo.apkContentsSigners?.let { combined.addAll(it) }
                signingInfo.signingCertificateHistory?.let { combined.addAll(it) }
                combined.toList()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
        val sha256 = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            sha256.reset()
            sha256.digest(sig.toByteArray()).toHex()
        }.toSet()
    }

    private fun shortFingerprint(hex: String): String =
        if (hex.length >= 12) "${hex.take(6)}…${hex.takeLast(6)}" else hex
}
