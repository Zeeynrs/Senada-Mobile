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
import senada.resources.Res
import senada.resources.a11y_open_artist_named
import senada.resources.action_cancel
import senada.resources.action_close
import senada.resources.action_dismiss_notice
import senada.resources.artist_not_found
import senada.resources.artist_picker_title
import senada.resources.artist_searching

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

    var messageText by mutableStateOf<UiText?>(null)
        private set

    private var lookupJob: Job? = null
    private var dismissJob: Job? = null

    fun open(track: Track, artistName: String) {
        lookupJob?.cancel()
        dismissJob?.cancel()
        resolver.knownArtist(track, artistName)?.let { found ->
            setMessage(null)
            navigator.openArtist(found.id, found.name, found.artworkUrl)
            return
        }
        setMessage(UiText.Resource(Res.string.artist_searching, artistName))
        lookupJob = scope.launch {
            when (val result = resolver.resolve(track, artistName)) {
                is ArtistResolveResult.Found -> {
                    setMessage(null)
                    navigator.openArtist(result.id, result.name, result.artworkUrl)
                }
                ArtistResolveResult.NotFound -> showNotice(UiText.Resource(Res.string.artist_not_found))
                is ArtistResolveResult.Error -> showNotice(result.uiText)
            }
        }
    }

    fun dismissMessage() {
        dismissJob?.cancel()
        setMessage(null)
    }

    private fun setMessage(text: UiText?) {
        messageText = text
        message = text?.resolve()
    }

    private fun showNotice(text: UiText) {
        setMessage(text)
        dismissJob?.cancel()
        if (noticeDurationMs > 0) {
            dismissJob = scope.launch {
                delay(noticeDurationMs)
                if (messageText == text) setMessage(null)
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
        title = { Text(appString(Res.string.artist_picker_title), modifier = Modifier.semantics { heading() }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                artists.forEach { name ->
                    val openArtistLabel = appString(Res.string.a11y_open_artist_named, name)
                    TextButton(
                        onClick = { onPick(name) },
                        modifier = Modifier.fillMaxWidth().minTouchTarget().semantics {
                            contentDescription = openArtistLabel
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
            TextButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) {
                Text(appString(Res.string.action_cancel))
            }
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
    val dismissDesc = appString(Res.string.action_dismiss_notice)
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
                    modifier = Modifier.minTouchTarget().semantics { contentDescription = dismissDesc },
                ) { Text(appString(Res.string.action_close)) }
            },
        )
    }
}
