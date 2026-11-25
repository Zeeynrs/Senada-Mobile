package senada.updater

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidUpdaterVerificationAndDeltaTest {
    @Test
    fun verifierAcceptsMatchingPackageVersionAndSigningCertificate() {
        val certA = "1".repeat(64)
        val certB = "2".repeat(64)
        val fileSha = "a".repeat(64)

        val installed = ApkPackageMetadata(
            packageName = "io.github.zeeynrs.senada",
            versionCode = 2L,
            versionName = "1.0.0",
            signingCertSha256s = setOf(certA),
        )
        val archive = ApkPackageMetadata(
            packageName = "io.github.zeeynrs.senada",
            versionCode = 3L,
            versionName = "1.1.0",
            signingCertSha256s = setOf(certB, certA), // Supports signing certificate rotation lineage.
        )

        val result = AndroidApkVerifier.verifyCompatibility(installed, archive, fileSha, fileSha)
        assertIs<ApkVerificationResult.Valid>(result)
    }

    @Test
    fun verifierRejectsMismatchedSigningPackageIdDowngradeAndDigest() {
        val certA = "1".repeat(64)
        val certB = "2".repeat(64)
        val fileSha = "a".repeat(64)
        val installed = ApkPackageMetadata("io.github.zeeynrs.senada", 2L, "1.0.0", setOf(certA))

        val signingMismatch = AndroidApkVerifier.verifyCompatibility(
            installed = installed,
            archive = installed.copy(versionCode = 3L, signingCertSha256s = setOf(certB)),
            actualFileSha256 = fileSha,
            expectedFileSha256 = fileSha,
        )
        assertEquals(
            PackageMismatchReason.SigningCertificateMismatch,
            assertIs<ApkVerificationResult.Incompatible>(signingMismatch).reason,
        )

        val pkgMismatch = AndroidApkVerifier.verifyCompatibility(
            installed = installed,
            archive = installed.copy(packageName = "com.other.app", versionCode = 3L),
            actualFileSha256 = fileSha,
            expectedFileSha256 = fileSha,
        )
        assertEquals(
            PackageMismatchReason.PackageIdMismatch,
            assertIs<ApkVerificationResult.Incompatible>(pkgMismatch).reason,
        )

        val downgrade = AndroidApkVerifier.verifyCompatibility(
            installed = installed,
            archive = installed.copy(versionCode = 1L),
            actualFileSha256 = fileSha,
            expectedFileSha256 = fileSha,
        )
        assertEquals(
            PackageMismatchReason.VersionDowngrade,
            assertIs<ApkVerificationResult.Incompatible>(downgrade).reason,
        )

        val digestMismatch = AndroidApkVerifier.verifyCompatibility(
            installed = installed,
            archive = installed.copy(versionCode = 3L),
            actualFileSha256 = fileSha,
            expectedFileSha256 = "f".repeat(64),
        )
        assertEquals(
            PackageMismatchReason.DigestMismatch,
            assertIs<ApkVerificationResult.Incompatible>(digestMismatch).reason,
        )

        val corrupt = AndroidApkVerifier.verifyCompatibility(
            installed = installed,
            archive = null,
            actualFileSha256 = fileSha,
            expectedFileSha256 = fileSha,
        )
        assertEquals(
            PackageMismatchReason.InvalidArchive,
            assertIs<ApkVerificationResult.Incompatible>(corrupt).reason,
        )
    }

    @Test
    fun apkDeltaSignificantlyReducesTransferSizeAndReconstructsBitForBit() {
        val dir = Files.createTempDirectory("senada-delta-test").toFile()
        try {
            val baseApk = File(dir, "base.apk")
            val targetApk = File(dir, "target.apk")
            val patchFile = File(dir, "update.apkdelta")
            val rebuiltApk = File(dir, "rebuilt.apk")

            // Simulate an APK with ~2 MB unchanged dependency dex + assets and a 16 KB changed app dex.
            val sharedDex1 = ByteArray(1_200_000) { i -> ((i * 31 + 7) and 0xFF).toByte() }
            val sharedDex2 = ByteArray(800_000) { i -> ((i * 17 + 53) and 0xFF).toByte() }
            val appDexV1 = ByteArray(16_000) { i -> ((i * 13 + 1) and 0xFF).toByte() }
            val appDexV2 = ByteArray(16_000) { i -> ((i * 19 + 9) and 0xFF).toByte() }

            writeSyntheticApk(baseApk, mapOf("classes.dex" to sharedDex1, "classes2.dex" to sharedDex2, "classes3.dex" to appDexV1))
            writeSyntheticApk(targetApk, mapOf("classes.dex" to sharedDex1, "classes2.dex" to sharedDex2, "classes3.dex" to appDexV2))

            val summary = ApkDelta.create(baseApk, targetApk, patchFile)
            assertTrue(summary.patchSize < summary.targetSize / 10, "Expected >90% reduction, got patch=${summary.patchSize} vs target=${summary.targetSize}")
            assertTrue(summary.savedPercent >= 90)

            val appliedSha = ApkDelta.apply(baseApk, patchFile, rebuiltApk)
            assertEquals(summary.targetSha256, appliedSha)
            assertContentEquals(targetApk.readBytes(), rebuiltApk.readBytes())

            // Wrong base APK must be rejected and leave no partial output behind.
            val wrongBaseOut = File(dir, "wrong-base.apk")
            assertFailsWith<ApkDeltaException> {
                ApkDelta.apply(targetApk, patchFile, wrongBaseOut)
            }
            assertFalse(wrongBaseOut.exists())

            // Corrupted patch must be rejected and leave no partial output behind.
            val corruptPatch = File(dir, "corrupt.apkdelta")
            val mutated = patchFile.readBytes()
            mutated[mutated.lastIndex] = (mutated[mutated.lastIndex].toInt() xor 0xFF).toByte()
            corruptPatch.writeBytes(mutated)
            val corruptOut = File(dir, "corrupt-out.apk")
            assertFailsWith<ApkDeltaException> {
                ApkDelta.apply(baseApk, corruptPatch, corruptOut)
            }
            assertFalse(corruptOut.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeSyntheticApk(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                    time = 0L
                }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
