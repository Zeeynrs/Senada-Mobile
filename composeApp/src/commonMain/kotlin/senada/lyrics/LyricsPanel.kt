package senada.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import senada.model.Track

internal sealed interface LyricsPanelResult {
    data object Loading : LyricsPanelResult
    data class Ready(val document: LyricsDocument) : LyricsPanelResult
    data class Error(val message: String) : LyricsPanelResult
}

internal suspend fun loadLyrics(track: Track, fetch: suspend (Track) -> LyricsDocument): LyricsPanelResult {
    val result = try {
        LyricsPanelResult.Ready(fetch(track))
    } catch (e: LyricsException) {
        LyricsPanelResult.Error(e.userMessage)
    }
    // Balasan yang baru tiba setelah panel ditutup atau lagu diganti harus diabaikan.
    currentCoroutineContext().ensureActive()
    return result
}

@Composable
internal fun LyricsPanel(track: Track, positionMs: Long, repository: LyricsRepository) {
    var attempt by remember(track.id) { mutableStateOf(0) }
    var result by remember(track.id, attempt) { mutableStateOf<LyricsPanelResult>(LyricsPanelResult.Loading) }
    LaunchedEffect(track.id, attempt) { result = loadLyrics(track, repository::get) }

    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    // Tinggi panel ikut melebar pada skala font besar agar baris lirik panjang tidak terpotong.
    val panelHeight = (200f * fontScale.coerceAtMost(1.5f)).dp
    val syncedVerticalPadding = if (fontScale > 1.3f) 56.dp else 84.dp

    var expanded by remember(track.id) { mutableStateOf(false) }
    val document = (result as? LyricsPanelResult.Ready)?.document
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            LyricsContent(track, result, positionMs, panelHeight, syncedVerticalPadding) { attempt++ }
            if (document is LyricsDocument.Plain || document is LyricsDocument.Synced) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Text("Buka lirik penuh")
                }
            }
        }
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
                    TextButton(onClick = { expanded = false }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Text("Tutup lirik")
                    }
                    BoxWithConstraints {
                        LyricsContent(track, result, positionMs, maxHeight, syncedVerticalPadding) { attempt++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsContent(
    track: Track,
    shown: LyricsPanelResult,
    positionMs: Long,
    panelHeight: Dp,
    syncedVerticalPadding: Dp,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            track.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        when (shown) {
            LyricsPanelResult.Loading -> Text(
                "Memuat lirik…",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            is LyricsPanelResult.Error -> {
                Text(shown.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                ) { Text("Coba lagi") }
            }
            is LyricsPanelResult.Ready -> when (val document = shown.document) {
                is LyricsDocument.Synced -> SyncedLyrics(
                    lines = document.lines,
                    active = activeLineIndex(document.lines, positionMs),
                    panelHeight = panelHeight,
                    verticalPadding = syncedVerticalPadding,
                )
                is LyricsDocument.Plain -> LazyColumn(
                    Modifier.fillMaxWidth().height(panelHeight),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { items(document.lines) { Text(it, modifier = Modifier.fillMaxWidth()) } }
                LyricsDocument.Instrumental -> Text(
                    "Instrumental",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                LyricsDocument.Unavailable -> Text(
                    "Lirik tidak tersedia",
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, active: Int, panelHeight: Dp, verticalPadding: Dp) {
    val list = rememberLazyListState()
    // Bantalan menjaga baris tujuan dekat tengah, termasuk baris pertama dan terakhir.
    LazyColumn(
        Modifier.fillMaxWidth().height(panelHeight),
        state = list,
        contentPadding = PaddingValues(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val current = index == active
            Text(
                line.text,
                modifier = Modifier.fillMaxWidth().semantics {
                    selected = current
                    if (current) stateDescription = "Baris aktif"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (current) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    LaunchedEffect(active) { list.scrollToItem(active.coerceAtLeast(0)) }
}
