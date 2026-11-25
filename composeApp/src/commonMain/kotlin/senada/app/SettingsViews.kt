package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import senada.backup.BackupException
import senada.backup.ImportSummary
import senada.backup.exportBackup
import senada.backup.importBackup
import senada.library.LibraryRepository
import senada.resources.Res
import senada.resources.action_apply
import senada.resources.action_cancel
import senada.resources.action_close
import senada.resources.backup_added_prefix
import senada.resources.backup_all_present
import senada.resources.backup_applied
import senada.resources.backup_apply_title
import senada.resources.backup_desc
import senada.resources.backup_export
import senada.resources.backup_favorites_imported
import senada.resources.backup_history_imported
import senada.resources.backup_import
import senada.resources.backup_io_error
import senada.resources.backup_nothing_new_title
import senada.resources.backup_playlist_tracks_imported
import senada.resources.backup_playlists_imported
import senada.resources.backup_processing
import senada.resources.backup_saved
import senada.resources.backup_saved_imported
import senada.resources.backup_skipped_entries
import senada.resources.backup_title

@Composable
internal fun SenadaCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/** Kartu permukaan radius 24 dp dengan judul untuk satu bagian di tab Pengaturan. */
@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    SenadaCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

/** Backup yang sudah dibaca dan diringkas, menunggu persetujuan pengguna. */
private class PendingImport(val text: String, val summary: ImportSummary)

/**
 * Ekspor dan impor library dalam format Senada desktop. Impor selalu menampilkan ringkasan lebih dulu,
 * dan baru menulis setelah pengguna menekan Terapkan.
 */
@Composable
internal fun BackupSection(library: LibraryRepository) {
    val files = rememberBackupFiles()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<UiText?>(null) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }
    var lastAppliedSummary by remember { mutableStateOf<ImportSummary?>(null) }
    val language = LocalAppLanguage.current

    fun launchTask(block: suspend () -> UiText?) {
        busy = true
        message = null
        lastAppliedSummary = null
        scope.launch {
            message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                e.uiText
            } catch (_: Exception) {
                UiText.Resource(Res.string.backup_io_error)
            } finally {
                busy = false
            }
        }
    }

    SettingsCard(appString(Res.string.backup_title)) {
        Text(
            appString(Res.string.backup_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    launchTask {
                        if (files.save(backupFileName(), library.exportBackup())) {
                            UiText.Resource(Res.string.backup_saved)
                        } else {
                            null
                        }
                    }
                },
                enabled = !busy,
                shape = CircleShape,
                modifier = Modifier.minTouchTarget(),
            ) { Text(appString(Res.string.backup_export)) }
            OutlinedButton(
                onClick = {
                    launchTask {
                        val text = files.open() ?: return@launchTask null
                        pending = PendingImport(text, library.importBackup(text, apply = false))
                        null
                    }
                },
                enabled = !busy,
                shape = CircleShape,
                modifier = Modifier.minTouchTarget(),
            ) { Text(appString(Res.string.backup_import)) }
        }
        val status = when {
            busy -> appString(Res.string.backup_processing)
            lastAppliedSummary != null -> {
                val summaryLine = describe(lastAppliedSummary!!, language).lines().first()
                appString(Res.string.backup_applied, summaryLine)
            }
            else -> message?.resolve(language)
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    val request = pending ?: return
    if (request.summary.changesNothing) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(appString(Res.string.backup_nothing_new_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(describe(request.summary, language)) },
            confirmButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.minTouchTarget()) {
                    Text(appString(Res.string.action_close))
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(appString(Res.string.backup_apply_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(describe(request.summary, language)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        launchTask {
                            val applied = library.importBackup(request.text)
                            lastAppliedSummary = applied
                            null
                        }
                    },
                    modifier = Modifier.minTouchTarget(),
                ) { Text(appString(Res.string.action_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.minTouchTarget()) {
                    Text(appString(Res.string.action_cancel))
                }
            },
        )
    }
}

/** Ringkasan dalam kalimat; baris pertama dipakai juga sebagai pesan setelah diterapkan. */
internal fun describe(summary: ImportSummary, language: AppLanguage = AppStrings.currentLanguage): String {
    if (summary.changesNothing) {
        return AppStrings.get(Res.string.backup_all_present, language) +
            if (summary.skipped > 0) {
                "\n" + AppStrings.plural(Res.plurals.backup_skipped_entries, summary.skipped, language, summary.skipped)
            } else {
                ""
            }
    }
    val added = listOfNotNull(
        AppStrings.plural(Res.plurals.backup_favorites_imported, summary.favoritesAdded, language, summary.favoritesAdded)
            .takeIf { summary.favoritesAdded > 0 },
        AppStrings.plural(Res.plurals.backup_playlists_imported, summary.playlistsAdded, language, summary.playlistsAdded)
            .takeIf { summary.playlistsAdded > 0 },
        AppStrings.plural(Res.plurals.backup_playlist_tracks_imported, summary.playlistTracksAdded, language, summary.playlistTracksAdded)
            .takeIf { summary.playlistTracksAdded > 0 },
        AppStrings.plural(Res.plurals.backup_saved_imported, summary.savedAdded, language, summary.savedAdded)
            .takeIf { summary.savedAdded > 0 },
        AppStrings.plural(Res.plurals.backup_history_imported, summary.historyAdded, language, summary.historyAdded)
            .takeIf { summary.historyAdded > 0 },
    )
    return buildString {
        append(AppStrings.get(Res.string.backup_added_prefix, language, added.joinToString(", ")))
        if (summary.skipped > 0) {
            append('\n')
            append(AppStrings.plural(Res.plurals.backup_skipped_entries, summary.skipped, language, summary.skipped))
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun backupFileName(): String = "Senada backup ${Clock.System.now().toString().take(10)}.json"
