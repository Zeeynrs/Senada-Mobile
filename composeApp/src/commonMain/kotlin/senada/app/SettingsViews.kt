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

/** Kartu permukaan radius 24 dp dengan judul untuk satu bagian di tab Pengaturan. */
@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
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
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PendingImport?>(null) }

    fun launchTask(block: suspend () -> String?) {
        busy = true
        message = null
        scope.launch {
            message = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                e.userMessage
            } catch (e: Exception) {
                "File backup tidak dapat dibaca atau disimpan."
            } finally {
                busy = false
            }
        }
    }

    SettingsCard("Backup library") {
        Text(
            "Simpan favorit, playlist, riwayat dan item tersimpan ke satu file JSON yang juga bisa dibuka " +
                "Senada desktop. Pengaturan tidak ikut. Impor menambahkan yang belum ada tanpa menghapus apa pun.",
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
                    launchTask { if (files.save(backupFileName(), library.exportBackup())) "Backup disimpan." else null }
                },
                enabled = !busy,
                shape = CircleShape,
                modifier = Modifier.minTouchTarget(),
            ) { Text("Ekspor backup") }
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
            ) { Text("Impor backup") }
        }
        val status = if (busy) "Memproses…" else message
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
            title = { Text("Tidak ada yang baru", modifier = Modifier.semantics { heading() }) },
            text = { Text(describe(request.summary)) },
            confirmButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.minTouchTarget()) { Text("Tutup") }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Terapkan backup?", modifier = Modifier.semantics { heading() }) },
            text = { Text(describe(request.summary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        launchTask {
                            val applied = library.importBackup(request.text)
                            "Backup diterapkan. ${describe(applied).lines().first()}"
                        }
                    },
                    modifier = Modifier.minTouchTarget(),
                ) { Text("Terapkan") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }, modifier = Modifier.minTouchTarget()) { Text("Batal") }
            },
        )
    }
}

/** Ringkasan dalam kalimat; baris pertama dipakai juga sebagai pesan setelah diterapkan. */
internal fun describe(summary: ImportSummary): String {
    if (summary.changesNothing) {
        return "Semua isi backup sudah ada di library." +
            if (summary.skipped > 0) "\n${summary.skipped} entri dilewati (file lokal desktop atau sumber selain YouTube Music)." else ""
    }
    val added = listOfNotNull(
        "${summary.favoritesAdded} favorit".takeIf { summary.favoritesAdded > 0 },
        "${summary.playlistsAdded} playlist".takeIf { summary.playlistsAdded > 0 },
        "${summary.playlistTracksAdded} lagu ke playlist yang ada".takeIf { summary.playlistTracksAdded > 0 },
        "${summary.savedAdded} item tersimpan".takeIf { summary.savedAdded > 0 },
        "${summary.historyAdded} lagu riwayat".takeIf { summary.historyAdded > 0 },
    )
    return buildString {
        append("Ditambahkan: ").append(added.joinToString(", ")).append('.')
        if (summary.skipped > 0) {
            append("\n${summary.skipped} entri dilewati (file lokal desktop atau sumber selain YouTube Music).")
        }
    }
}

@OptIn(ExperimentalTime::class)
private fun backupFileName(): String = "Senada backup ${Clock.System.now().toString().take(10)}.json"
