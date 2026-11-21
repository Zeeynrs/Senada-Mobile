package senada.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(track.title, style = MaterialTheme.typography.subtitle1)
        when (val shown = result) {
            LyricsPanelResult.Loading -> Text("Memuat lirik…")
            is LyricsPanelResult.Error -> {
                Text(shown.message)
                TextButton(onClick = { attempt++ }) { Text("Coba lagi") }
            }
            is LyricsPanelResult.Ready -> when (val document = shown.document) {
                is LyricsDocument.Synced -> SyncedLyrics(document.lines, activeLineIndex(document.lines, positionMs))
                is LyricsDocument.Plain -> LazyColumn(
                    Modifier.fillMaxWidth().height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { items(document.lines) { Text(it) } }
                LyricsDocument.Instrumental -> Text("Instrumental")
                LyricsDocument.Unavailable -> Text("Lirik tidak tersedia")
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, active: Int) {
    val list = rememberLazyListState()
    // Bantalan menjaga baris tujuan dekat tengah, termasuk baris pertama dan terakhir.
    LazyColumn(
        Modifier.fillMaxWidth().height(200.dp),
        state = list,
        contentPadding = PaddingValues(vertical = 84.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val current = index == active
            Text(
                line.text,
                modifier = Modifier.semantics { selected = current },
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            )
        }
    }
    LaunchedEffect(active) { list.scrollToItem(active.coerceAtLeast(0)) }
}
