package senada.app.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import senada.app.theme.LocalSenadaLook
import senada.app.theme.SenadaLook
import senada.app.theme.scaledAnimationDurationMillis
import senada.playback.QueueItem

internal const val QUEUE_REORDER_ANIM_MS = 200
internal const val QUEUE_SETTLE_ANIM_MS = 180
internal const val QUEUE_LIFT_ANIM_MS = 140
internal const val QUEUE_AUTO_SCROLL_EDGE_DP = 68f
internal const val QUEUE_AUTO_SCROLL_SPEED_DP_PER_SEC = 680f

internal data class QueueVisibleItem(
    val id: Long,
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun queueReorderPlacementDurationMs(look: SenadaLook): Int =
    look.durationMillis(QUEUE_REORDER_ANIM_MS)

internal fun queueSettleDurationMs(look: SenadaLook): Int =
    look.durationMillis(QUEUE_SETTLE_ANIM_MS)

/**
 * Menentukan indeks tujuan saat pusat baris yang diseret melewati titik tengah baris tetangga.
 * Perbandingan pusat-ke-pusat mencegah osilasi meski tinggi baris bervariasi.
 */
internal fun findQueueSwapTargetIndex(
    draggedId: Long,
    currentIndex: Int,
    visualTop: Float,
    draggedSize: Int,
    visibleItems: List<QueueVisibleItem>,
): Int? {
    if (visibleItems.isEmpty() || currentIndex < 0) return null
    val visualCenter = visualTop + draggedSize / 2f
    val downTarget = visibleItems.lastOrNull { item ->
        item.id != draggedId &&
            item.index > currentIndex &&
            (item.offset + item.size / 2f) < visualCenter
    }
    if (downTarget != null) return downTarget.index

    val upTarget = visibleItems.firstOrNull { item ->
        item.id != draggedId &&
            item.index < currentIndex &&
            (item.offset + item.size / 2f) > visualCenter
    }
    return upTarget?.index
}

internal fun clampVisualTop(
    desiredTop: Float,
    draggedSize: Int,
    viewportTop: Float,
    viewportBottom: Float,
): Float {
    if (viewportBottom <= viewportTop) return desiredTop
    val minTop = viewportTop - draggedSize * 0.2f
    val maxTop = (viewportBottom - draggedSize * 0.8f).coerceAtLeast(minTop)
    return desiredTop.coerceIn(minTop, maxTop)
}

internal fun computeQueueAutoScrollVelocity(
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edgeZonePx: Float,
    maxSpeedPxPerSec: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
): Float {
    val height = viewportBottom - viewportTop
    if (height <= 0f || edgeZonePx <= 0f || maxSpeedPxPerSec <= 0f) return 0f
    val zone = minOf(edgeZonePx, height * 0.25f)
    if (canScrollBackward && pointerY < viewportTop + zone) {
        val fraction = ((viewportTop + zone - pointerY) / zone).coerceIn(0f, 1f)
        return -maxSpeedPxPerSec * fraction * (0.35f + 0.65f * fraction)
    }
    if (canScrollForward && pointerY > viewportBottom - zone) {
        val fraction = ((pointerY - (viewportBottom - zone)) / zone).coerceIn(0f, 1f)
        return maxSpeedPxPerSec * fraction * (0.35f + 0.65f * fraction)
    }
    return 0f
}

/** Menyelaraskan pratinjau drag saat antrean berubah dari luar; mengembalikan null bila drag harus batal. */
internal fun reconcileDraggedPreview(
    preview: List<QueueItem>,
    draggedId: Long,
    newSource: List<QueueItem>,
): List<QueueItem>? {
    if (newSource.size <= 1) return null
    val byId = newSource.associateBy { it.id }
    val draggedItem = byId[draggedId] ?: return null
    val currentPos = preview.indexOfFirst { it.id == draggedId }.coerceIn(0, newSource.lastIndex)
    val kept = preview.mapNotNull { item ->
        if (item.id == draggedId) null else byId[item.id]
    }.toMutableList()
    val keptIds = kept.mapTo(HashSet(kept.size + 1)) { it.id }.apply { add(draggedId) }
    for (item in newSource) {
        if (item.id !in keptIds) kept.add(item)
    }
    val clampedPos = currentPos.coerceIn(0, kept.size)
    kept.add(clampedPos, draggedItem)
    return kept
}

@Composable
internal fun rememberQueueReorderState(
    sourceEntries: List<QueueItem>,
    onMove: (entryId: Long, toIndex: Int) -> Unit,
): QueueReorderState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val animationScale = LocalSenadaLook.current.animationScale
    val state = remember(scope) {
        QueueReorderState(scope) { entryId, toIndex -> currentOnMove(entryId, toIndex) }
    }
    SideEffect {
        state.updateEnvironment(sourceEntries, animationScale)
    }
    return state
}

@Stable
internal class QueueReorderState(
    private val scope: CoroutineScope,
    private val onMove: (entryId: Long, toIndex: Int) -> Unit,
) {
    var draggedId by mutableStateOf<Long?>(null)
        private set
    var settlingId by mutableStateOf<Long?>(null)
        private set
    var previewEntries by mutableStateOf<List<QueueItem>?>(null)
        private set

    private var startIndex = -1
    private var startItemTop = 0f
    private var startPointerY = 0f
    private var currentPointerY by mutableFloatStateOf(0f)
    private var draggedItemSize = 0
    private var latestSource: List<QueueItem> = emptyList()
    private var lastSyncedSource: List<QueueItem> = emptyList()
    private var animationScale = 1f

    private val settleOffset = Animatable(0f)
    private val liftProgress = Animatable(0f)
    private var settleJob: Job? = null
    private var liftJob: Job? = null

    fun displayEntries(sourceEntries: List<QueueItem>): List<QueueItem> =
        previewEntries ?: sourceEntries

    fun isActiveItem(entryId: Long): Boolean =
        draggedId == entryId || settlingId == entryId

    fun activeProgressFor(entryId: Long): Float =
        if (isActiveItem(entryId)) liftProgress.value else 0f

    fun scaleFor(entryId: Long): Float =
        if (isActiveItem(entryId)) 1f + 0.02f * liftProgress.value else 1f

    fun updateEnvironment(sourceEntries: List<QueueItem>, scale: Float) {
        animationScale = scale.coerceAtLeast(0f)
        latestSource = sourceEntries
        val activeId = draggedId
        if (activeId != null) {
            if (sourceEntries != lastSyncedSource) {
                lastSyncedSource = sourceEntries
                val currentPreview = previewEntries ?: return
                val reconciled = reconcileDraggedPreview(currentPreview, activeId, sourceEntries)
                if (reconciled == null) {
                    cancelDrag()
                } else {
                    previewEntries = reconciled
                    startIndex = sourceEntries.indexOfFirst { it.id == activeId }
                }
            }
        } else if (settlingId != null) {
            val currentPreview = previewEntries
            if (currentPreview != null && sourceEntries != currentPreview) {
                previewEntries = null
            }
        }
    }

    fun startDrag(
        entryId: Long,
        pointerY: Float,
        visibleItems: List<QueueVisibleItem>,
    ): Boolean {
        val source = latestSource
        if (source.size <= 1) return false
        val index = source.indexOfFirst { it.id == entryId }
        if (index < 0) return false
        val itemInfo = visibleItems.firstOrNull { it.id == entryId } ?: return false

        settleJob?.cancel()
        settleJob = null
        settlingId = null
        liftJob?.cancel()
        liftJob = null

        lastSyncedSource = source
        previewEntries = source
        draggedId = entryId
        startIndex = index
        startItemTop = itemInfo.offset.toFloat()
        draggedItemSize = itemInfo.size
        startPointerY = pointerY
        currentPointerY = pointerY

        val liftDuration = scaledAnimationDurationMillis(QUEUE_LIFT_ANIM_MS, animationScale)
        liftJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffset.snapTo(0f)
            if (liftDuration <= 0) {
                liftProgress.snapTo(1f)
            } else {
                liftProgress.animateTo(1f, tween(liftDuration, easing = FastOutSlowInEasing))
            }
        }
        return true
    }

    fun startDrag(entryId: Long, pointerY: Float, listState: LazyListState): Boolean =
        startDrag(entryId, pointerY, listState.layoutInfo.toVisibleItems())

    fun updatePointer(pointerY: Float, listState: LazyListState) {
        if (draggedId == null) return
        currentPointerY = pointerY
        swapIfNeeded(listState)
    }

    internal fun updatePointer(
        pointerY: Float,
        visibleItems: List<QueueVisibleItem>,
        viewportTop: Float,
        viewportBottom: Float,
    ) {
        if (draggedId == null) return
        currentPointerY = pointerY
        applySwap(visibleItems, viewportTop, viewportBottom)
    }

    fun autoScrollVelocity(listState: LazyListState, edgeZonePx: Float, maxSpeedPxPerSec: Float): Float {
        if (draggedId == null) return 0f
        val layout = listState.layoutInfo
        return computeQueueAutoScrollVelocity(
            pointerY = currentPointerY,
            viewportTop = layout.viewportStartOffset.toFloat(),
            viewportBottom = layout.viewportEndOffset.toFloat(),
            edgeZonePx = edgeZonePx,
            maxSpeedPxPerSec = maxSpeedPxPerSec,
            canScrollBackward = listState.canScrollBackward,
            canScrollForward = listState.canScrollForward,
        )
    }

    fun onAutoScrolled(listState: LazyListState) {
        if (draggedId == null) return
        swapIfNeeded(listState)
    }

    /** Memindahkan posisi di pratinjau saat pusat baris melewati tetangga; mengembalikan indeks tujuan jika berubah. */
    internal fun applySwap(
        visibleItems: List<QueueVisibleItem>,
        viewportTop: Float,
        viewportBottom: Float,
    ): Pair<Int, Int>? {
        val activeId = draggedId ?: return null
        val currentPreview = previewEntries ?: return null
        val currentIndex = currentPreview.indexOfFirst { it.id == activeId }
        if (currentIndex < 0) return null

        val activeInfo = visibleItems.firstOrNull { it.id == activeId }
        val activeSize = activeInfo?.size ?: draggedItemSize
        if (activeInfo != null) draggedItemSize = activeInfo.size

        val visualTop = clampVisualTop(
            desiredTop = startItemTop + (currentPointerY - startPointerY),
            draggedSize = activeSize,
            viewportTop = viewportTop,
            viewportBottom = viewportBottom,
        )
        val targetIndex = findQueueSwapTargetIndex(
            draggedId = activeId,
            currentIndex = currentIndex,
            visualTop = visualTop,
            draggedSize = activeSize,
            visibleItems = visibleItems,
        ) ?: return null

        val reordered = currentPreview.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        previewEntries = reordered
        return currentIndex to targetIndex
    }

    private fun swapIfNeeded(listState: LazyListState) {
        val layout = listState.layoutInfo
        val swap = applySwap(
            visibleItems = layout.toVisibleItems(),
            viewportTop = layout.viewportStartOffset.toFloat(),
            viewportBottom = layout.viewportEndOffset.toFloat(),
        ) ?: return
        val (fromIndex, toIndex) = swap
        val anchorIndex = listState.firstVisibleItemIndex
        val anchorOffset = listState.firstVisibleItemScrollOffset
        if (anchorIndex in minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)) {
            listState.requestScrollToItem(anchorIndex, anchorOffset)
        }
    }

    fun translationYFor(entryId: Long, layout: LazyListLayoutInfo): Float =
        translationYFor(
            entryId = entryId,
            visibleItems = layout.toVisibleItems(),
            viewportTop = layout.viewportStartOffset.toFloat(),
            viewportBottom = layout.viewportEndOffset.toFloat(),
        )

    internal fun translationYFor(
        entryId: Long,
        visibleItems: List<QueueVisibleItem>,
        viewportTop: Float,
        viewportBottom: Float,
    ): Float {
        if (draggedId == entryId) {
            val info = visibleItems.firstOrNull { it.id == entryId } ?: return 0f
            val visualTop = clampVisualTop(
                desiredTop = startItemTop + (currentPointerY - startPointerY),
                draggedSize = info.size,
                viewportTop = viewportTop,
                viewportBottom = viewportBottom,
            )
            return visualTop - info.offset.toFloat()
        }
        if (settlingId == entryId) {
            return settleOffset.value
        }
        return 0f
    }

    fun finishDrag(listState: LazyListState) {
        val layout = listState.layoutInfo
        finishDrag(
            visibleItems = layout.toVisibleItems(),
            viewportTop = layout.viewportStartOffset.toFloat(),
            viewportBottom = layout.viewportEndOffset.toFloat(),
        )
    }

    internal fun finishDrag(
        visibleItems: List<QueueVisibleItem>,
        viewportTop: Float,
        viewportBottom: Float,
    ) {
        val activeId = draggedId ?: return
        val currentPreview = previewEntries
        val targetIndex = currentPreview?.indexOfFirst { it.id == activeId } ?: -1
        val residualY = translationYFor(activeId, visibleItems, viewportTop, viewportBottom)

        draggedId = null
        if (targetIndex >= 0 && targetIndex != startIndex) {
            onMove(activeId, targetIndex)
        }

        val settleDuration = scaledAnimationDurationMillis(QUEUE_SETTLE_ANIM_MS, animationScale)
        val liftDuration = scaledAnimationDurationMillis(QUEUE_LIFT_ANIM_MS, animationScale)

        settleJob?.cancel()
        liftJob?.cancel()

        if (settleDuration <= 0 && liftDuration <= 0) {
            settlingId = null
            previewEntries = null
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                settleOffset.snapTo(0f)
                liftProgress.snapTo(0f)
            }
            return
        }

        settlingId = activeId
        settleJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                settleOffset.snapTo(residualY)
                if (settleDuration > 0 && residualY != 0f) {
                    settleOffset.animateTo(0f, tween(settleDuration, easing = FastOutSlowInEasing))
                } else {
                    settleOffset.snapTo(0f)
                }
            } finally {
                if (settlingId == activeId) {
                    settlingId = null
                    previewEntries = null
                }
            }
        }
        liftJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (liftDuration > 0) {
                liftProgress.animateTo(0f, tween(liftDuration, easing = FastOutSlowInEasing))
            } else {
                liftProgress.snapTo(0f)
            }
        }
    }

    fun cancelDrag() {
        if (draggedId == null && settlingId == null && previewEntries == null) return
        settleJob?.cancel()
        settleJob = null
        liftJob?.cancel()
        liftJob = null
        draggedId = null
        settlingId = null
        previewEntries = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffset.snapTo(0f)
            liftProgress.snapTo(0f)
        }
    }
}

private fun LazyListLayoutInfo.toVisibleItems(): List<QueueVisibleItem> =
    visibleItemsInfo.mapNotNull { info ->
        val id = info.key as? Long ?: return@mapNotNull null
        QueueVisibleItem(id = id, index = info.index, offset = info.offset, size = info.size)
    }
