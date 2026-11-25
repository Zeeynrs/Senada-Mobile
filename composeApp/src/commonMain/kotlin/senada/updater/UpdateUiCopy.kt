package senada.updater

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
    val primaryLabel: String?,
    val primaryEnabled: Boolean,
    val showSecondaryReleaseLink: Boolean,
    val channelExplanation: String,
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
            val explanation = buildString {
                append("Pembaruan diperiksa dari GitHub Releases. Sebelum pemasang sistem Android dibuka, ")
                append("Senada memeriksa checksum SHA-256, identitas paket, kode versi, dan kecocokan sertifikat penandatanganan. ")
                append("Jika paket delta tersedia untuk build ini, hanya selisih yang diunduh.")
                if (build.isDebugBuild) {
                    append(" Build debug saat ini ditandatangani dengan kunci debug lokal; pembaruan langsung hanya dapat mempertahankan data jika rilis memakai kunci yang sama.")
                }
            }
            val primaryLabel = when {
                state.installState is UpdateInstallState.PermissionRequired -> "Pasang sekarang"
                candidate != null && candidate.primaryAsset != null -> "Unduh & pasang"
                candidate != null -> "Buka halaman rilis"
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
            primaryLabel = if (candidate != null) "Buka di Google Play" else null,
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null,
            channelExplanation = "Aplikasi ini dipasang melalui Google Play. Pembaruan dan pengurangan ukuran unduhan (delta) dikelola langsung oleh Google Play.",
            supportsInAppInstall = false,
        )

        InstallChannel.IosSideloadUnsigned -> UpdateActionPresentation(
            primaryLabel = if (candidate != null) "Buka halaman rilis" else null,
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = false,
            channelExplanation = "iOS tidak mengizinkan aplikasi memasang file .ipa dari dalam aplikasi (kebijakan Apple 2.5.2). " +
                "Paket publik iOS saat ini berupa IPA tanpa tanda tangan di GitHub Releases. Unduh IPA baru lalu pasang ulang lewat " +
                "AltStore/AltServer dengan Apple ID dan Bundle ID yang sama agar library dan antrean tetap utuh.",
            supportsInAppInstall = false,
        )

        InstallChannel.IosTestFlight -> UpdateActionPresentation(
            primaryLabel = when {
                candidate != null && build.storeUrl != null -> "Buka TestFlight"
                candidate != null -> "Buka halaman rilis"
                else -> null
            },
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null && build.storeUrl != null,
            channelExplanation = "Build ini berasal dari TestFlight. Pembaruan dan transfer delta iOS dikelola langsung oleh aplikasi TestFlight.",
            supportsInAppInstall = false,
        )

        InstallChannel.IosAppStore -> UpdateActionPresentation(
            primaryLabel = when {
                candidate != null && build.storeUrl != null -> "Buka App Store"
                candidate != null -> "Buka halaman rilis"
                else -> null
            },
            primaryEnabled = !busy && candidate != null,
            showSecondaryReleaseLink = candidate != null && build.storeUrl != null,
            channelExplanation = "Aplikasi ini dipasang melalui App Store. Pembaruan dan App Thinning/delta iOS dikelola langsung oleh App Store.",
            supportsInAppInstall = false,
        )
    }
}

internal fun describeCheckStatus(state: UpdateUiState): String = when (val result = state.checkResult) {
    UpdateCheckResult.Idle -> "Belum diperiksa pada sesi ini."
    UpdateCheckResult.Checking -> "Memeriksa rilis terbaru di GitHub…"
    is UpdateCheckResult.UpToDate ->
        "Senada sudah versi terbaru (${result.installedVersion}) pada kanal ${result.channel.label}."
    is UpdateCheckResult.NoStableRelease -> if (result.hasBetaRelease) {
        "Belum ada rilis stabil publik. Aktifkan kanal Beta untuk memeriksa rilis prarilis."
    } else {
        "Belum ada rilis publik yang tersedia di GitHub."
    }
    is UpdateCheckResult.Available -> {
        val tag = result.candidate.release.tagName
        val track = if (result.candidate.release.isPrerelease || result.candidate.version.isPrerelease) "Beta" else "Stabil"
        "Pembaruan $tag ($track) tersedia."
    }
    is UpdateCheckResult.Error -> result.message
}

internal fun formatTransferBytes(bytes: Long): String {
    if (bytes < 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${formatOneDecimal(kb)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return "${formatOneDecimal(mb)} MB"
    return "${formatOneDecimal(mb / 1024.0)} GB"
}

private fun formatOneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10.0).toLong()
    val whole = tenths / 10L
    val frac = kotlin.math.abs(tenths % 10L)
    return if (frac == 0L) "$whole" else "$whole,$frac"
}
