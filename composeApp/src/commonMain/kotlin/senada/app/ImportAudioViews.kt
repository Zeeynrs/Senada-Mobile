package senada.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import senada.local.LocalAudioException
import senada.local.importedAudioLock
import senada.local.toTrack
import senada.model.Track

/** Tombol impor satu file audio dari ponsel; lagunya langsung masuk antrean. */
@Composable
internal fun ImportAudioButton(onImported: (Track) -> Unit) {
    val importer = rememberAudioImporter()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = {
            busy = true
            message = null
            scope.launch {
                message = try {
                    importedAudioLock.withLock {
                        importer.import()?.let { audio ->
                            val track = audio.toTrack()
                            onImported(track)
                            "\"${track.title}\" (${formatSize(audio.sizeBytes)}) ditambahkan ke antrean."
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LocalAudioException) {
                    e.userMessage
                } catch (e: Exception) {
                    "File audio gagal diimpor."
                } finally {
                    busy = false
                }
            }
        }, enabled = !busy) { Text(if (busy) "Menyalin file…" else "Impor file audio") }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
