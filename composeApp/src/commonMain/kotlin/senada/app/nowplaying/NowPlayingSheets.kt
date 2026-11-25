package senada.app.nowplaying

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.isActive
import senada.app.LibraryActions
import senada.app.LibraryState
import senada.app.OverflowActionItem
import senada.app.TrackMenu
import senada.app.TrackRow
import senada.app.minTouchTarget
import senada.app.shell.SenadaIcons
import senada.app.theme.LocalSenadaLook
import senada.playback.PlaybackController
import senada.playback.PlaybackState

@Composable
internal fun PlayerSheetContent(
    title: String,
    closeLabel: String,
    onClose: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical))
            .padding(horizontal = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, modifier = Modifier.weight(1f).semantics { heading() },
                color = PlayerWhite, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold,
            )
            if (onOpenSettings != null) {
                PlayerGlassButton(SenadaIcons.Settings, "Pengaturan lirik", scale = 1f, onClick = onOpenSettings)
            }
            PlayerGlassButton(SenadaIcons.Close, closeLabel, scale = 1f, onClick = onClose)
        }
        content()
    }
}

@Composable
internal fun PlayerQueue(state: PlaybackState, playback: PlaybackController, library: LibraryState, actions: LibraryActions) {
    val sourceEntries = state.queue.playOrder
    if (sourceEntries.isEmpty()) {
        Text("Antrean kosong.", color = PlayerWhite)
        return
    }
    val look = LocalSenadaLook.current
    val placementSpec = look.tweenOrSnap<androidx.compose.ui.unit.IntOffset>(QUEUE_REORDER_ANIM_MS)
    val listState = rememberLazyListState()
    val reorderState = rememberQueueReorderState(sourceEntries) { entryId, toIndex ->
        playback.move(entryId, toIndex)
    }
    val displayEntries = reorderState.displayEntries(sourceEntries)
    val canReorder = displayEntries.size > 1
    val shape = RoundedCornerShape(16.dp)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var containerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val handleCoords = remember { mutableStateMapOf<Long, LayoutCoordinates>() }

    val activeDragId = reorderState.draggedId
    LaunchedEffect(reorderState, listState, activeDragId) {
        if (activeDragId == null) return@LaunchedEffect
        val edgeZonePx = with(density) { QUEUE_AUTO_SCROLL_EDGE_DP.dp.toPx() }
        val maxSpeedPxPerSec = with(density) { QUEUE_AUTO_SCROLL_SPEED_DP_PER_SEC.dp.toPx() }
        var lastFrameNanos = withFrameNanos { it }
        while (isActive && reorderState.draggedId == activeDragId) {
            val frameNanos = withFrameNanos { it }
            val dtSeconds = ((frameNanos - lastFrameNanos).coerceIn(0L, 50_000_000L)) / 1_000_000_000f
            lastFrameNanos = frameNanos
            val velocity = reorderState.autoScrollVelocity(listState, edgeZonePx, maxSpeedPxPerSec)
            if (velocity != 0f && dtSeconds > 0f) {
                val consumed = listState.scrollBy(velocity * dtSeconds)
                if (consumed != 0f) {
                    reorderState.onAutoScrolled(listState)
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { containerCoords = it }
            .pointerInput(reorderState, listState) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.isConsumed) return@awaitEachGesture
                    val host = containerCoords?.takeIf { it.isAttached } ?: return@awaitEachGesture
                    val hitEntryId = handleCoords.entries.firstOrNull { (_, coords) ->
                        if (!coords.isAttached) return@firstOrNull false
                        val rect = host.localBoundingBoxOf(coords, clipBounds = true)
                        !rect.isEmpty && down.position in rect
                    }?.key ?: return@awaitEachGesture

                    val startPos = down.position
                    var started = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        val delta = change.position - startPos
                        if (delta.getDistance() < viewConfiguration.touchSlop) continue
                        if (abs(delta.x) > abs(delta.y) * 1.5f) return@awaitEachGesture
                        started = reorderState.startDrag(hitEntryId, change.position.y, listState)
                        if (!started) return@awaitEachGesture
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        change.consume()
                        break
                    }
                    if (!started) return@awaitEachGesture

                    var committed = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if (!change.pressed) {
                                change.consume()
                                reorderState.finishDrag(listState)
                                committed = true
                                break
                            }
                            change.consume()
                            reorderState.updatePointer(change.position.y, listState)
                        }
                    } finally {
                        if (!committed) {
                            reorderState.cancelDrag()
                        }
                    }
                }
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = displayEntries,
                key = { _, entry -> entry.id },
                contentType = { _, _ -> "queue-entry" },
            ) { index, entry ->
                val isActiveRow = reorderState.isActiveItem(entry.id)
                val activeProgress = reorderState.activeProgressFor(entry.id)
                Box(
                    Modifier.fillMaxWidth()
                        .zIndex(if (isActiveRow) 1f else 0f)
                        .animateItem(
                            fadeInSpec = null,
                            placementSpec = if (isActiveRow) null else placementSpec,
                            fadeOutSpec = null,
                        )
                        .graphicsLayer {
                            translationY = reorderState.translationYFor(entry.id, listState.layoutInfo)
                            val rowScale = reorderState.scaleFor(entry.id)
                            scaleX = rowScale
                            scaleY = rowScale
                        }
                        .playerGlass(shape)
                        .then(
                            if (activeProgress > 0f) {
                                Modifier.border(1.5.dp, PlayerWhite.copy(alpha = 0.42f * activeProgress), shape)
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = 12.dp, end = if (canReorder) 4.dp else 12.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    val playing = state.audio.isActive && state.audio.selection?.queueEntryId == entry.id
                    TrackRow(
                        track = entry.track,
                        favorite = entry.track.id in library.favoriteIds,
                        onClick = { if (playing) playback.pause() else playback.playEntry(entry.id) },
                        playing = playing,
                        onClickLabel = if (playing) "Jeda ${entry.track.title}" else "Putar ${entry.track.title}",
                    ) {
                        TrackMenu(
                            entry.track,
                            library,
                            actions,
                            onRemoveFromQueue = { playback.remove(entry.id) },
                            leadingItems = { select ->
                                if (index > 0) {
                                    OverflowActionItem("Pindah ke atas", SenadaIcons.ChevronUp) {
                                        select { moveQueueEntryBy(playback, entry.id, -1) }
                                    }
                                }
                                if (index < displayEntries.lastIndex) {
                                    OverflowActionItem("Pindah ke bawah", SenadaIcons.ChevronDown) {
                                        select { moveQueueEntryBy(playback, entry.id, 1) }
                                    }
                                }
                            },
                        )
                        if (canReorder) {
                            QueueDragHandle(
                                entryId = entry.id,
                                title = entry.track.title,
                                index = index,
                                total = displayEntries.size,
                                active = reorderState.draggedId == entry.id,
                                onPositioned = { coords -> handleCoords[entry.id] = coords },
                                onDisposeHandle = { handleCoords.remove(entry.id) },
                                onMoveBy = { delta -> moveQueueEntryBy(playback, entry.id, delta) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun moveQueueEntryBy(playback: PlaybackController, entryId: Long, delta: Int): Boolean {
    val order = playback.state.value.queue.playOrder
    val currentIndex = order.indexOfFirst { it.id == entryId }
    if (currentIndex < 0) return false
    val targetIndex = (currentIndex + delta).coerceIn(0, order.lastIndex)
    if (targetIndex == currentIndex) return false
    playback.move(entryId, targetIndex)
    return true
}

@Composable
private fun QueueDragHandle(
    entryId: Long,
    title: String,
    index: Int,
    total: Int,
    active: Boolean,
    onPositioned: (LayoutCoordinates) -> Unit,
    onDisposeHandle: () -> Unit,
    onMoveBy: (Int) -> Boolean,
) {
    DisposableEffect(entryId) {
        onDispose(onDisposeHandle)
    }
    Box(
        modifier = Modifier
            .minTouchTarget()
            .onGloballyPositioned(onPositioned)
            .semantics {
                contentDescription = "Urutkan $title"
                stateDescription = "Posisi ${index + 1} dari $total"
                customActions = buildList {
                    if (index > 0) {
                        add(CustomAccessibilityAction("Pindah ke atas") { onMoveBy(-1) })
                    }
                    if (index < total - 1) {
                        add(CustomAccessibilityAction("Pindah ke bawah") { onMoveBy(1) })
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = NowPlayingIcons.DragHandle,
            contentDescription = null,
            tint = PlayerWhite.copy(alpha = if (active) 1f else .72f),
            modifier = Modifier.size(20.dp),
        )
    }
}
