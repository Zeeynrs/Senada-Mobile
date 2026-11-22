package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import senada.app.shell.SenadaIcons
import senada.catalog.PlaylistImportException
import senada.catalog.PlaylistLinkParseResult
import senada.catalog.parsePlaylistLink
import senada.library.NoMatchedTracksException
import senada.library.PlaylistImportProgress
import senada.library.PlaylistImportSummary
import senada.library.PlaylistImporter
import senada.library.SkippedImportTrack

private sealed interface ImportDialogPhase {
    data class Input(
        val errorMessage: String? = null,
        val statusMessage: String? = null,
        val unmatchedTracks: List<SkippedImportTrack> = emptyList(),
    ) : ImportDialogPhase

    data class Running(val progress: PlaylistImportProgress) : ImportDialogPhase

    data class Completed(val summary: PlaylistImportSummary) : ImportDialogPhase
}

/**
 * Dialog untuk mengimpor playlist publik dari tautan YouTube Music atau Spotify:
 * menempel tautan, validasi, status progres beserta pembatalan, lalu ringkasan akhir.
 */
@Composable
internal fun PlaylistImportDialog(
    importer: PlaylistImporter,
    onDismiss: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var linkText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<ImportDialogPhase>(ImportDialogPhase.Input()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    fun cancelActiveImport() {
        val job = activeJob ?: return
        activeJob = null
        job.cancel()
        phase = ImportDialogPhase.Input(statusMessage = "Impor dibatalkan.")
    }

    fun startImport() {
        when (val parsed = parsePlaylistLink(linkText)) {
            is PlaylistLinkParseResult.Invalid -> {
                phase = ImportDialogPhase.Input(errorMessage = parsed.message)
            }
            is PlaylistLinkParseResult.Valid -> {
                phase = ImportDialogPhase.Running(
                    PlaylistImportProgress.Reading("Menyiapkan impor playlist…"),
                )
                activeJob = scope.launch {
                    try {
                        val summary = importer.import(parsed.link) { progress ->
                            phase = ImportDialogPhase.Running(progress)
                        }
                        activeJob = null
                        phase = ImportDialogPhase.Completed(summary)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: NoMatchedTracksException) {
                        activeJob = null
                        phase = ImportDialogPhase.Input(
                            errorMessage = e.userMessage,
                            unmatchedTracks = e.skippedTracks,
                        )
                    } catch (e: PlaylistImportException) {
                        activeJob = null
                        phase = ImportDialogPhase.Input(errorMessage = e.userMessage)
                    } catch (_: Exception) {
                        activeJob = null
                        phase = ImportDialogPhase.Input(
                            errorMessage = "Playlist gagal diimpor. Periksa tautan atau koneksi internet.",
                        )
                    }
                }
            }
        }
    }

    when (val current = phase) {
        is ImportDialogPhase.Input -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Impor playlist", modifier = Modifier.semantics { heading() }) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Tempel tautan playlist publik dari YouTube Music atau Spotify.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = linkText,
                            onValueChange = { next ->
                                linkText = next.replace("\r", "").replace("\n", "")
                                if (current.errorMessage != null || current.statusMessage != null) {
                                    phase = ImportDialogPhase.Input()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            isError = current.errorMessage != null,
                            label = {
                                Text("Tautan playlist", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            placeholder = {
                                Text("https://music.youtube.com/playlist?list=…")
                            },
                            trailingIcon = if (linkText.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = {
                                            linkText = ""
                                            phase = ImportDialogPhase.Input()
                                        },
                                        modifier = Modifier.minTouchTarget().semantics {
                                            contentDescription = "Hapus tautan"
                                        },
                                    ) {
                                        Icon(SenadaIcons.Close, contentDescription = null)
                                    }
                                }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (linkText.isNotBlank()) startImport()
                            }),
                        )
                        current.errorMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        current.statusMessage?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                        if (current.unmatchedTracks.isNotEmpty()) {
                            SkippedTracksSection(
                                heading = "Lagu yang tidak ditemukan:",
                                skippedTracks = current.unmatchedTracks,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { startImport() },
                        enabled = linkText.isNotBlank(),
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Impor") }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Batal") }
                },
            )
        }

        is ImportDialogPhase.Running -> {
            AlertDialog(
                onDismissRequest = { cancelActiveImport() },
                title = { Text("Mengimpor playlist", modifier = Modifier.semantics { heading() }) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        when (val progress = current.progress) {
                            is PlaylistImportProgress.Reading -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    progress.statusText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                            is PlaylistImportProgress.Matching -> {
                                Text(
                                    progress.playlistName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val fraction = if (progress.total > 0) {
                                    (progress.completed.toFloat() / progress.total).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "Mencocokkan ${progress.completed} dari ${progress.total}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                            PlaylistImportProgress.Saving -> {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    "Menyimpan playlist…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = { cancelActiveImport() },
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Batal") }
                },
            )
        }

        is ImportDialogPhase.Completed -> {
            val summary = current.summary
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Playlist diimpor", modifier = Modifier.semantics { heading() }) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            summary.playlistName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${summary.addedCount} lagu ditambahkan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "${summary.skippedTracks.size} lagu dilewati",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        summary.partialNote?.let { note ->
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (summary.skippedTracks.isNotEmpty()) {
                            SkippedTracksSection(
                                heading = "Lagu yang dilewati:",
                                skippedTracks = summary.skippedTracks,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { onOpenPlaylist(summary.playlistId) },
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Buka playlist") }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.minTouchTarget(),
                    ) { Text("Tutup") }
                },
            )
        }
    }
}

@Composable
private fun SkippedTracksSection(
    heading: String,
    skippedTracks: List<SkippedImportTrack>,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            heading,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        for (item in skippedTracks) {
            Text(
                "• ${item.displayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
