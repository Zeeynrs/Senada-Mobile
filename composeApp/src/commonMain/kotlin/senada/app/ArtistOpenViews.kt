package senada.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import senada.app.shell.AppNavigator
import senada.catalog.ArtistResolveResult
import senada.catalog.ArtistResolver
import senada.model.Track

private const val NOTICE_DURATION_MS = 3_500L

/**
 * Mengatur pembukaan halaman artis dari lagu: membuka langsung bila ID kanal sudah diketahui,
 * atau mencari lewat filter Artis dengan pembatalan permintaan lama dan pesan singkat saat gagal.
 */
internal class ArtistOpenController(
    private val resolver: ArtistResolver,
    private val navigator: AppNavigator,
    private val scope: CoroutineScope,
    private val noticeDurationMs: Long = NOTICE_DURATION_MS,
) {
    var message by mutableStateOf<String?>(null)
        private set

    private var lookupJob: Job? = null
    private var dismissJob: Job? = null

    fun open(track: Track, artistName: String) {
        lookupJob?.cancel()
        dismissJob?.cancel()
        resolver.knownArtist(track, artistName)?.let { found ->
            message = null
            navigator.openArtist(found.id, found.name, found.artworkUrl)
            return
        }
        message = "Mencari artis $artistName…"
        lookupJob = scope.launch {
            when (val result = resolver.resolve(track, artistName)) {
                is ArtistResolveResult.Found -> {
                    message = null
                    navigator.openArtist(result.id, result.name, result.artworkUrl)
                }
                ArtistResolveResult.NotFound -> showNotice("Artis tidak ditemukan")
                is ArtistResolveResult.Error -> showNotice(result.message)
            }
        }
    }

    fun dismissMessage() {
        dismissJob?.cancel()
        message = null
    }

    private fun showNotice(text: String) {
        message = text
        dismissJob?.cancel()
        if (noticeDurationMs > 0) {
            dismissJob = scope.launch {
                delay(noticeDurationMs)
                if (message == text) message = null
            }
        }
    }
}

/** Dialog pilihan nama saat satu lagu memiliki lebih dari satu artis. */
@Composable
internal fun ArtistPickerDialog(
    artists: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih artis", modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                artists.forEach { name ->
                    TextButton(
                        onClick = { onPick(name) },
                        modifier = Modifier.fillMaxWidth().minTouchTarget().semantics {
                            contentDescription = "Buka artis $name"
                        },
                    ) {
                        Text(
                            name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) { Text("Batal") }
        },
    )
}

/** Pemberitahuan singkat di bagian atas layar saat mencari artis atau artis tidak ditemukan. */
@Composable
internal fun ArtistOpenBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            minPrimaryWidth = 160.dp,
            primary = {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            },
            trailing = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.minTouchTarget().semantics { contentDescription = "Tutup pemberitahuan" },
                ) { Text("Tutup") }
            },
        )
    }
}
