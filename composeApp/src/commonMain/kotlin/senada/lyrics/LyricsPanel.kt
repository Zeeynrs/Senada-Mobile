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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            track.title,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        when (val shown = result) {
            LyricsPanelResult.Loading -> Text(
                "Memuat lirik…",
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            is LyricsPanelResult.Error -> {
                Text(shown.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = { attempt++ }) { Text("Coba lagi") }
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
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
            )
        }
    }
    LaunchedEffect(active) { list.scrollToItem(active.coerceAtLeast(0)) }
}
