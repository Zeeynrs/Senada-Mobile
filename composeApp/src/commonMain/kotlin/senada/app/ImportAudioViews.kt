package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import senada.app.shell.SenadaIcons
import senada.local.LocalAudioException
import senada.local.importedAudioLock
import senada.local.toTrack
import senada.model.Track

/** Pengendali impor file audio agar status sibuk dan pesan tetap bertahan saat daftar kosong terisi. */
internal class AudioImportController(
    private val importer: AudioImporter,
    private val scope: CoroutineScope,
    private val onImported: suspend (Track) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun launchImport() {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            message = try {
                importedAudioLock.withLock {
                    importer.import()?.let { audio ->
                        val track = audio.toTrack()
                        withContext(NonCancellable) { onImported(track) }
                        "\"${track.title}\" (${formatSize(audio.sizeBytes)}) ditambahkan ke antrean."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalAudioException) {
                e.userMessage
            } catch (_: Exception) {
                "File audio gagal diimpor."
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun rememberAudioImportController(onImported: suspend (Track) -> Unit): AudioImportController {
    val importer = rememberAudioImporter()
    val scope = rememberCoroutineScope()
    val currentOnImported by rememberUpdatedState(onImported)
    return remember(importer, scope) {
        AudioImportController(importer, scope) { track -> currentOnImported(track) }
    }
}

/** Baris aksi impor satu file audio dari ponsel; lagunya langsung masuk library dan antrean. */
@Composable
internal fun ImportAudioButton(controller: AudioImportController) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(enabled = !controller.busy, role = Role.Button, onClick = controller::launchImport)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SenadaIcons.Import,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (controller.busy) "Menyalin file…" else "Impor file audio",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Tambahkan file dari ponsel ke antrean",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = SenadaIcons.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        controller.message?.let { ImportAudioStatusMessage(it) }
    }
}

/** Menampilkan pesan status hasil impor file audio secara sopan untuk pembaca layar. */
@Composable
internal fun ImportAudioStatusMessage(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
