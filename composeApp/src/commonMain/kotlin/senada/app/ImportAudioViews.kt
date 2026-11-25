package senada.app

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import senada.resources.Res
import senada.resources.import_audio_added_to_queue
import senada.resources.import_audio_button
import senada.resources.import_audio_copying
import senada.resources.import_audio_failed
import senada.resources.import_audio_subtitle

/** Pengendali impor file audio agar status sibuk dan pesan tetap bertahan saat daftar kosong terisi. */
internal class AudioImportController(
    private val importer: AudioImporter,
    private val scope: CoroutineScope,
    private val onImported: suspend (Track) -> Unit,
) {
    var busy by mutableStateOf(false)
        private set
    var messageText by mutableStateOf<UiText?>(null)
        private set
    val message: String?
        get() = messageText?.resolve()

    fun launchImport() {
        if (busy) return
        busy = true
        messageText = null
        scope.launch {
            messageText = try {
                importedAudioLock.withLock {
                    importer.import()?.let { audio ->
                        val track = audio.toTrack()
                        withContext(NonCancellable) { onImported(track) }
                        UiText.Resource(Res.string.import_audio_added_to_queue, track.title, formatSize(audio.sizeBytes))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LocalAudioException) {
                e.uiText
            } catch (_: Exception) {
                UiText.Resource(Res.string.import_audio_failed)
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

/** Baris aksi impor satu file audio dari ponsel bergaya kartu v2; lagunya langsung masuk library dan antrean. */
@Composable
internal fun ImportAudioButton(controller: AudioImportController) {
    val colors = MaterialTheme.colorScheme
    val language = LocalAppLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SenadaCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable(enabled = !controller.busy, role = Role.Button, onClick = controller::launchImport)
                    .padding(1.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.primaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SenadaIcons.Import,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        appString(if (controller.busy) Res.string.import_audio_copying else Res.string.import_audio_button),
                        style = browseRowTitleStyle(),
                        color = colors.onSurface,
                    )
                    Text(
                        appString(Res.string.import_audio_subtitle),
                        style = browseRowSubtitleStyle(),
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = SenadaIcons.ProfileChevron,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        controller.messageText?.let { ImportAudioStatusMessage(it.resolve(language)) }
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
