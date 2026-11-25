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
import senada.resources.offline_cancel_download
import senada.resources.state_off
import senada.resources.state_on
import senada.updater.TransferMode
import senada.updater.UpdateChannel
import senada.updater.UpdateCheckResult
import senada.updater.UpdateController
import senada.updater.UpdateInstallState
import senada.updater.describeCheckStatus
import senada.updater.describeUpdateActions
import senada.updater.formatTransferBytes
import senada.resources.Res
import senada.resources.updater_action_check
import senada.resources.updater_action_checking
import senada.resources.updater_action_dismiss
import senada.resources.updater_action_github
import senada.resources.updater_action_permissions
import senada.resources.updater_applying_delta
import senada.resources.updater_asset
import senada.resources.updater_asset_with_delta
import senada.resources.updater_auto_check
import senada.resources.updater_auto_check_description
import senada.resources.updater_build_info
import senada.resources.updater_downloading_delta
import senada.resources.updater_downloading_full
import senada.resources.updater_downloading_full_progress
import senada.resources.updater_install_channel
import senada.resources.updater_release_beta
import senada.resources.updater_release_channel
import senada.resources.updater_release_stable
import senada.resources.updater_title
import senada.resources.updater_verifying

/** Halaman Tentang Senada: identitas versi, kanal distribusi, pemeriksaan rilis, dan alur pembaruan. */
@Composable
internal fun AboutSection(controller: UpdateController) {
    val state by controller.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val presentation = describeUpdateActions(state)
    val language = LocalAppLanguage.current
    val installChannel = appString(state.buildInfo.installChannel.label)

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
                    MonoLabel(
                        appString(Res.string.updater_build_info, state.buildInfo.buildNumber, installChannel),
                    )
                    Text(
                        state.buildInfo.packageId,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        SettingsCard(appString(Res.string.updater_title)) {
            val statusColor = if (state.checkResult is UpdateCheckResult.Error) {
                colors.error
            } else {
                colors.onSurfaceVariant
            }
            Text(
                describeCheckStatus(state).resolve(language),
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
                        val releaseLabel = if (isBeta) {
                            Res.string.updater_release_beta
                        } else {
                            Res.string.updater_release_stable
                        }
                        MonoLabel(appString(releaseLabel, available.release.tagName))
                        Text(
                            available.release.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                        )
                        available.primaryAsset?.let { asset ->
                            val assetLabel = if (available.deltaManifestAsset != null && presentation.supportsInAppInstall) {
                                Res.string.updater_asset_with_delta
                            } else {
                                Res.string.updater_asset
                            }
                            Text(
                                appString(assetLabel, asset.name, formatTransferBytes(asset.sizeBytes, language)),
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
                                Text(appString(Res.string.updater_action_dismiss))
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
                        Text(label.resolve(language))
                    }
                }

                OutlinedButton(
                    onClick = { controller.checkForUpdates(manual = true) },
                    enabled = !state.isBusy,
                    shape = CircleShape,
                    modifier = Modifier.minTouchTarget(),
                ) {
                    val checkLabel = if (state.checkResult is UpdateCheckResult.Checking) {
                        Res.string.updater_action_checking
                    } else {
                        Res.string.updater_action_check
                    }
                    Text(appString(checkLabel))
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
                        Text(appString(Res.string.updater_action_github))
                    }
                }
            }

            state.notice?.let { notice ->
                Text(
                    notice.resolve(language),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }

            HorizontalDivider(color = colors.outlineVariant)

            MonoLabel(appString(Res.string.updater_release_channel))
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
                                appString(channel.label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                            Text(
                                appString(channel.description),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            val autoCheck = state.preferences.autoCheckEnabled
            val autoCheckDescription = appString(if (autoCheck) Res.string.state_on else Res.string.state_off)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoCheck,
                        role = Role.Switch,
                        onValueChange = controller::setAutoCheck,
                    )
                    .semantics { stateDescription = autoCheckDescription }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        appString(Res.string.updater_auto_check),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                    )
                    Text(
                        appString(Res.string.updater_auto_check_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                SenadaSwitch(autoCheck, onCheckedChange = null)
            }

            HorizontalDivider(color = colors.outlineVariant)

            MonoLabel(appString(Res.string.updater_install_channel, installChannel))
            Text(
                presentation.channelExplanation.resolve(language),
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
    val language = LocalAppLanguage.current
    when (installState) {
        UpdateInstallState.Idle -> Unit
        is UpdateInstallState.Preparing -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    installState.message.resolve(language),
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
                val downloaded = formatTransferBytes(installState.downloadedBytes, language)
                val summary = when (val mode = installState.mode) {
                    is TransferMode.Delta -> appString(
                        Res.string.updater_downloading_delta,
                        downloaded,
                        formatTransferBytes(mode.patchBytes, language),
                        mode.savedPercent,
                        formatTransferBytes(mode.fullBytes, language),
                    )
                    is TransferMode.Full -> {
                        val total = installState.totalBytes
                        val progress = if (total != null) {
                            appString(
                                Res.string.updater_downloading_full_progress,
                                downloaded,
                                formatTransferBytes(total, language),
                            )
                        } else {
                            appString(Res.string.updater_downloading_full, downloaded)
                        }
                        val fallback = mode.fallbackReason?.resolve(language)
                        if (fallback == null) progress else "$progress\n$fallback"
                    }
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = onCancel, modifier = Modifier.minTouchTarget()) {
                    Text(appString(Res.string.offline_cancel_download))
                }
            }
        }
        is UpdateInstallState.ApplyingDelta -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    appString(
                        Res.string.updater_applying_delta,
                        formatTransferBytes(installState.mode.patchBytes, language),
                        installState.mode.savedPercent,
                    ),
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
                    appString(Res.string.updater_verifying),
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
                        installState.message.resolve(language),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSecondaryContainer,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    OutlinedButton(
                        onClick = onOpenPermissionSettings,
                        shape = CircleShape,
                        modifier = Modifier.minTouchTarget(),
                    ) {
                        Text(appString(Res.string.updater_action_permissions))
                    }
                }
            }
        }
        is UpdateInstallState.InstallerLaunched -> {
            Text(
                installState.message.resolve(language),
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
                    installState.message.resolve(language),
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
                installState.message.resolve(language),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
