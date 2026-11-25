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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import senada.app.shell.SenadaIcons
import senada.resources.Res
import senada.resources.search_history_again
import senada.resources.search_history_clear
import senada.resources.search_history_remove
import senada.resources.search_history_title

/** Pencarian terakhir saat kolom cari kosong; dikelompokkan dalam kartu v2 dengan garis pemisah. */
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
                BrowseMonoHeading(appString(Res.string.search_history_title))
            },
            trailing = {
                TextButton(onClick = onClear, modifier = Modifier.minTouchTarget()) {
                    Text(appString(Res.string.search_history_clear))
                }
            },
        )
    }
    item(key = "search-history-card") {
        val colors = MaterialTheme.colorScheme
        val searchAgainLabel = appString(Res.string.search_history_again)
        SenadaCard {
            Column(Modifier.fillMaxWidth().padding(1.dp)) {
                history.forEachIndexed { index, query ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 66.dp, end = 16.dp),
                            color = colors.outline,
                        )
                    }
                    val removeDesc = appString(Res.string.search_history_remove, query)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(onClickLabel = searchAgainLabel) { onPick(query) }
                            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = SenadaIcons.Clock,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            query,
                            style = browseRowTitleStyle(),
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onRemove(query) },
                            modifier = Modifier.minTouchTarget().semantics {
                                contentDescription = removeDesc
                            },
                        ) {
                            Icon(
                                imageVector = SenadaIcons.Close,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
