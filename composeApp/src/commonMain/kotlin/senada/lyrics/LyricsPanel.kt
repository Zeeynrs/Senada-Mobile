package senada.lyrics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import senada.app.minTouchTarget
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
internal fun rememberLyrics(track: Track?, repository: LyricsRepository, enabled: Boolean, attempt: Int): LyricsPanelResult {
    if (track == null) return LyricsPanelResult.Ready(LyricsDocument.Unavailable)
    var result by remember(track.id, attempt) { mutableStateOf<LyricsPanelResult>(LyricsPanelResult.Loading) }
    LaunchedEffect(track.id, repository, enabled, attempt) {
        if (enabled && result == LyricsPanelResult.Loading) result = loadLyrics(track, repository::get)
    }
    return result
}

@Composable
internal fun LyricsPanel(track: Track?, shown: LyricsPanelResult, positionMs: Long, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (track != null) {
            Text(
                track.title, color = Color.White, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        when (shown) {
            LyricsPanelResult.Loading -> LyricsMessage("Memuat lirik…")
            is LyricsPanelResult.Error -> {
                LyricsMessage(shown.message)
                TextButton(onClick = onRetry, modifier = Modifier.minTouchTarget()) { Text("Coba lagi") }
            }
            is LyricsPanelResult.Ready -> key(track?.id) {
                when (val document = shown.document) {
                    is LyricsDocument.Synced -> BoxWithConstraints(Modifier.fillMaxSize()) {
                        SyncedLyrics(document.lines, activeLineIndex(document.lines, positionMs), maxHeight * .38f)
                    }
                    is LyricsDocument.Plain -> LazyColumn(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                    ) { items(document.lines) { LyricText(it, Color.White) } }
                    LyricsDocument.Instrumental -> LyricsMessage("Instrumental")
                    LyricsDocument.Unavailable -> LyricsMessage("Lirik tidak tersedia")
                }
            }
        }
    }
}

@Composable
private fun LyricsMessage(message: String) {
    Text(message, color = Color.White, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun LyricText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, modifier.fillMaxWidth(), color = color,
        fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, active: Int, verticalPadding: Dp) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = active.coerceAtLeast(0))
    LazyColumn(
        Modifier.fillMaxSize(), state = list,
        contentPadding = PaddingValues(vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val current = index == active
            LyricText(
                line.text,
                color = Color.White.copy(alpha = if (current) 1f else .35f),
                modifier = Modifier.semantics {
                    selected = current
                    if (current) stateDescription = "Baris aktif"
                },
            )
        }
    }
    LaunchedEffect(active) { list.animateScrollToItem(active.coerceAtLeast(0)) }
}
