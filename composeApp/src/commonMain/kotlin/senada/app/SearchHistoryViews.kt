package senada.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Pencarian terakhir saat kolom cari kosong; mengetuk satu baris mengisi kolom cari lagi. */
internal fun LazyListScope.searchHistoryItems(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) return
    item(key = "search-history-header") {
        AdaptiveRow(
            modifier = Modifier.fillMaxWidth(),
            minPrimaryWidth = 140.dp,
            primary = {
                Text(
                    "Pencarian terakhir",
                    style = MaterialTheme.typography.h6,
                    modifier = Modifier.semantics { heading() },
                )
            },
            trailing = { TextButton(onClick = onClear) { Text("Hapus riwayat") } },
        )
    }
    items(history, key = { "search-history-$it" }) { query ->
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClickLabel = "cari lagi") { onPick(query) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                query,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { onRemove(query) },
                modifier = Modifier.semantics { contentDescription = "Hapus \"$query\" dari riwayat" },
            ) {
                // Simbol dekoratif disembunyikan agar pembaca layar hanya membacakan label aksi.
                Text("×", style = MaterialTheme.typography.h6, modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
