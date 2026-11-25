package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import senada.app.theme.MonoLabel
import senada.updater.TransferMode
import senada.updater.UpdateChannel
import senada.updater.UpdateCheckResult
import senada.updater.UpdateController
import senada.updater.UpdateInstallState
import senada.updater.describeCheckStatus
import senada.updater.describeUpdateActions
import senada.updater.formatTransferBytes

/** Halaman Tentang Senada: identitas versi, kanal distribusi, pemeriksaan rilis, dan alur pembaruan. */
@Composable
internal fun AboutSection(controller: UpdateController) {
    val state by controller.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val presentation = describeUpdateActions(state)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SenadaCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SenadaMark(Modifier.size(48.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Senada ${state.buildInfo.versionName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                    )
                    MonoLabel("Build ${state.buildInfo.buildNumber} · ${state.buildInfo.installChannel.label}")
                    Text(
                        state.buildInfo.packageId,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        SettingsCard("Pembaruan aplikasi") {
            val statusColor = if (state.checkResult is UpdateCheckResult.Error) {
                colors.error
            } else {
                colors.onSurfaceVariant
            }
            Text(
                describeCheckStatus(state),
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            if (state.checkResult is UpdateCheckResult.Checking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val available = state.availableCandidate
            if (available != null) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colors.surfaceContainerHigh,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val isBeta = available.release.isPrerelease || available.version.isPrerelease
                        MonoLabel(if (isBeta) "RILIS BETA · ${available.release.tagName}" else "RILIS STABIL · ${available.release.tagName}")
                        Text(
                            available.release.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                        )
                        available.primaryAsset?.let { asset ->
                            val deltaHint = if (available.deltaManifestAsset != null && presentation.supportsInAppInstall) {
                                " · mendukung delta"
                            } else {
                                ""
                            }
                            Text(
                                "${asset.name} (${formatTransferBytes(asset.sizeBytes)})$deltaHint",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (available.release.body.isNotBlank()) {
                            val notes = available.release.body.let {
                                if (it.length > 600) it.take(600).trimEnd() + "…" else it
                            }
                            Text(
                                notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        if (state.hasUndismissedUpdate && !state.isBusy) {
                            TextButton(
                                onClick = controller::dismissUpdateBanner,
                                modifier = Modifier.minTouchTarget(),
                            ) {
                                Text("Abaikan pemberitahuan versi ini")
                            }
                        }
                    }
                }
            }

            InstallStatePanel(
                installState = state.installState,
                onCancel = controller::cancelTransfer,
                onOpenPermissionSettings = controller::openInstallPermissionSettings,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presentation.primaryLabel?.let { label ->
                    Button(
                        onClick = {
                            if (state.installState is UpdateInstallState.PermissionRequired) {
                                controller.resumePendingInstall()
                            } else {
                                controller.startUpdateAction()
                            }
                        },
                        enabled = presentation.primaryEnabled,
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text(label)
                    }
                }

                OutlinedButton(
                    onClick = { controller.checkForUpdates(manual = true) },
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    modifier = Modifier.minTouchTarget(),
                ) {
                    Text(if (state.checkResult is UpdateCheckResult.Checking) "Memeriksa…" else "Periksa pembaruan")
                }

                val showGithubFallback = presentation.showSecondaryReleaseLink ||
                    state.installState is UpdateInstallState.IncompatiblePackage ||
                    state.installState is UpdateInstallState.Failed
                if (showGithubFallback) {
                    OutlinedButton(
                        onClick = controller::openReleasePage,
                        enabled = !state.isBusy,
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text("Buka di GitHub")
                    }
                }
            }

            state.notice?.let { notice ->
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            HorizontalDivider(color = colors.outlineVariant)

            MonoLabel("KANAL RILIS")
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (channel in UpdateChannel.entries) {
                    val selected = state.preferences.channel == channel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected,
                                enabled = !state.isBusy,
                                role = Role.RadioButton,
                                onClick = { controller.setChannel(channel) },
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(selected = selected, onClick = null, enabled = !state.isBusy)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                channel.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Text(
                                channel.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val autoCheck = state.preferences.autoCheckEnabled
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoCheck,
                        role = Role.Switch,
                        onValueChange = controller::setAutoCheck,
                    )
                    .semantics { stateDescription = if (autoCheck) "Aktif" else "Nonaktif" }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Periksa pembaruan otomatis",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                    Text(
                        "Menghormati mode Hemat data seluler dan tidak memasang tanpa persetujuan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(autoCheck, onCheckedChange = null)
            }

            HorizontalDivider(color = colors.outlineVariant)

            MonoLabel("KANAL INSTALASI · ${state.buildInfo.installChannel.label}")
            Text(
                presentation.channelExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstallStatePanel(
    installState: UpdateInstallState,
    onCancel: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (installState) {
        UpdateInstallState.Idle -> Unit
        is UpdateInstallState.Preparing -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    installState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        is UpdateInstallState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val pct = installState.percent
                if (pct != null) {
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                val summary = when (val mode = installState.mode) {
                    is TransferMode.Delta ->
                        "Mengunduh delta: ${formatTransferBytes(installState.downloadedBytes)} / ${formatTransferBytes(mode.patchBytes)} " +
                            "(hemat ${mode.savedPercent}% dari ${formatTransferBytes(mode.fullBytes)})"
                    is TransferMode.Full -> {
                        val totalText = installState.totalBytes?.let { " / ${formatTransferBytes(it)}" }.orEmpty()
                        val fallback = mode.fallbackReason?.let { "\n$it" }.orEmpty()
                        "Mengunduh APK penuh: ${formatTransferBytes(installState.downloadedBytes)}$totalText$fallback"
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = onCancel, modifier = Modifier.minTouchTarget()) {
                    Text("Batal unduhan")
                }
            }
        }
        is UpdateInstallState.ApplyingDelta -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Menerapkan paket delta (${formatTransferBytes(installState.mode.patchBytes)}, hemat ${installState.mode.savedPercent}%)…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        is UpdateInstallState.Verifying -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    "Memverifikasi SHA-256, identitas paket, dan sertifikat penandatanganan…",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        is UpdateInstallState.PermissionRequired -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        installState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    OutlinedButton(
                        onClick = onOpenPermissionSettings,
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text("Buka izin pemasangan")
                    }
                }
            }
        }
        is UpdateInstallState.InstallerLaunched -> {
            Text(
                installState.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        is UpdateInstallState.IncompatiblePackage -> {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = colors.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    installState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        is UpdateInstallState.Failed -> {
            Text(
                installState.message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
