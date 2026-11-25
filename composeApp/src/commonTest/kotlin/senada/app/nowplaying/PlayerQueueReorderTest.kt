package senada.app.nowplaying

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import senada.app.AnimationSpeed
import senada.app.theme.SenadaLook
import senada.model.Track
import senada.playback.QueueItem

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerQueueReorderTest {
    private val trackA = Track("demo:a", "Lagu A", listOf("Senada"), source = "demo")
    private val trackB = Track("demo:b", "Lagu B", listOf("Senada"), source = "demo")

    private fun items(count: Int): List<QueueItem> =
        (1..count).map { idx -> QueueItem(idx.toLong(), if (idx % 2 == 0) trackB else trackA) }

    @Test
    fun swapTargetMovesBidirectionallyAndHandlesFastMultiRowJumpWithoutOscillation() {
        val visible = listOf(
            QueueVisibleItem(id = 1L, index = 0, offset = 0, size = 100),
            QueueVisibleItem(id = 2L, index = 1, offset = 116, size = 140),
            QueueVisibleItem(id = 3L, index = 2, offset = 272, size = 100),
            QueueVisibleItem(id = 4L, index = 3, offset = 388, size = 100),
        )

        // Sebelum melewati pusat baris ke-2 (116 + 70 = 186): tidak bertukar.
        assertNull(findQueueSwapTargetIndex(1L, 0, visualTop = 130f, draggedSize = 100, visible))

        // Melewati pusat baris ke-2 (visualCenter = 190 > 186): pindah ke indeks 1.
        assertEquals(1, findQueueSwapTargetIndex(1L, 0, visualTop = 140f, draggedSize = 100, visible))

        // Drag cepat melewati beberapa baris sekaligus: langsung melompat ke indeks terjauh yang terlewati.
        assertEquals(3, findQueueSwapTargetIndex(1L, 0, visualTop = 400f, draggedSize = 100, visible))

        // Setelah baris 1 (100px) dan 2 (140px) bertukar, baris 1 di offset 156..256 dan baris 2 di 0..140 (center 70).
        // Dengan visualCenter yang sama (190), tidak membalik arah kembali ke 0 (anti-osilasi).
        val afterSwap = listOf(
            QueueVisibleItem(id = 2L, index = 0, offset = 0, size = 140),
            QueueVisibleItem(id = 1L, index = 1, offset = 156, size = 100),
            QueueVisibleItem(id = 3L, index = 2, offset = 272, size = 100),
        )
        assertNull(findQueueSwapTargetIndex(1L, 1, visualTop = 140f, draggedSize = 100, afterSwap))

        // Tarik ke atas melewati pusat baris 2 (70): visualCenter = 60 < 70 -> kembali ke indeks 0.
        assertEquals(0, findQueueSwapTargetIndex(1L, 1, visualTop = 10f, draggedSize = 100, afterSwap))
    }

    @Test
    fun autoScrollVelocityActivatesNearEdgesRespectsBoundsAndAdaptsToCompactViewport() {
        // Area tengah: tidak auto-scroll.
        assertEquals(
            0f,
            computeQueueAutoScrollVelocity(
                pointerY = 400f,
                viewportTop = 0f,
                viewportBottom = 800f,
                edgeZonePx = 100f,
                maxSpeedPxPerSec = 500f,
                canScrollBackward = true,
                canScrollForward = true,
            ),
        )

        // Zona tepi atas: kecepatan negatif (scroll naik), semakin cepat mendekati tepi.
        val mildUp = computeQueueAutoScrollVelocity(80f, 0f, 800f, 100f, 500f, canScrollBackward = true, canScrollForward = true)
        val strongUp = computeQueueAutoScrollVelocity(10f, 0f, 800f, 100f, 500f, canScrollBackward = true, canScrollForward = true)
        assertTrue(mildUp < 0f)
        assertTrue(strongUp < mildUp)

        // Zona tepi bawah: kecepatan positif (scroll turun).
        val down = computeQueueAutoScrollVelocity(780f, 0f, 800f, 100f, 500f, canScrollBackward = true, canScrollForward = true)
        assertTrue(down > 0f)

        // Tidak bisa scroll lagi di batas awal/akhir -> 0.
        assertEquals(0f, computeQueueAutoScrollVelocity(10f, 0f, 800f, 100f, 500f, canScrollBackward = false, canScrollForward = true))
        assertEquals(0f, computeQueueAutoScrollVelocity(790f, 0f, 800f, 100f, 500f, canScrollBackward = true, canScrollForward = false))

        // Viewport sempit (200px): zona efektif dibatasi 25% = 50px sehingga bagian tengah (100px) tetap netral.
        assertEquals(0f, computeQueueAutoScrollVelocity(100f, 0f, 200f, 120f, 500f, canScrollBackward = true, canScrollForward = true))
    }

    @Test
    fun reconcileDraggedPreviewHandlesExternalAddRemoveAndCancelCases() {
        val source = items(4)
        val preview = listOf(source[1], source[0], source[2], source[3]) // id 2 berada di indeks 0.

        // Lagu lain (id 1) dihapus saat drag berlangsung: pratinjau menjaga urutan tersisa dan posisi id 2.
        val afterOtherRemoved = listOf(source[1], source[2], source[3])
        assertEquals(
            listOf(source[1], source[2], source[3]),
            reconcileDraggedPreview(preview, draggedId = 2L, newSource = afterOtherRemoved),
        )

        // Lagu baru ditambahkan saat drag berlangsung: disisipkan tanpa menghilangkan id 2 yang diseret.
        val extra = QueueItem(5L, trackA)
        val afterAdded = source + extra
        assertEquals(
            listOf(source[1], source[0], source[2], source[3], extra),
            reconcileDraggedPreview(preview, draggedId = 2L, newSource = afterAdded),
        )

        // Lagu yang sedang diseret dihapus dari luar -> batal.
        assertNull(reconcileDraggedPreview(preview, draggedId = 2L, newSource = listOf(source[0], source[2], source[3])))

        // Antrean menyusut jadi 1 item -> batal.
        assertNull(reconcileDraggedPreview(preview, draggedId = 2L, newSource = listOf(source[1])))
    }

    @Test
    fun dragLifecycleHandlesNormalDropSamePositionNoOpCancellationAndSingleEntry() = runTest {
        val moves = mutableListOf<Pair<Long, Int>>()
        val state = QueueReorderState(backgroundScope) { id, index -> moves += id to index }

        // Entri tunggal tidak bisa diseret.
        val single = items(1)
        state.updateEnvironment(single, scale = 1f)
        assertFalse(state.startDrag(1L, 50f, listOf(QueueVisibleItem(1L, 0, 0, 100))))

        val list = items(3)
        val visible = listOf(
            QueueVisibleItem(1L, 0, 0, 100),
            QueueVisibleItem(2L, 1, 116, 100),
            QueueVisibleItem(3L, 2, 232, 100),
        )
        state.updateEnvironment(list, scale = 0f)

        // Drop di posisi yang sama (tidak melewati tetangga) -> no-op, tidak memanggil onMove.
        assertTrue(state.startDrag(1L, 50f, visible))
        state.finishDrag(visible, viewportTop = 0f, viewportBottom = 600f)
        runCurrent()
        assertTrue(moves.isEmpty())
        assertNull(state.draggedId)
        assertNull(state.previewEntries)

        // Pembatalan gestur setelah pratinjau bertukar -> kembali ke urutan asal tanpa memanggil onMove.
        assertTrue(state.startDrag(1L, 50f, visible))
        state.updatePointer(300f, visible, viewportTop = 0f, viewportBottom = 600f)
        assertEquals(listOf(2L, 3L, 1L), state.displayEntries(list).map { it.id })
        state.cancelDrag()
        runCurrent()
        assertTrue(moves.isEmpty())
        assertEquals(list, state.displayEntries(list))

        // Drop normal di posisi baru -> memanggil onMove tepat sekali dengan indeks tujuan.
        assertTrue(state.startDrag(1L, 50f, visible))
        state.updatePointer(300f, visible, viewportTop = 0f, viewportBottom = 600f)
        state.finishDrag(visible, viewportTop = 0f, viewportBottom = 600f)
        runCurrent()
        assertEquals(listOf(1L to 2), moves)
    }

    @Test
    fun animationDurationsScaleWithSenadaLookAndSnapWhenReducedMotion() {
        assertEquals(300, queueReorderPlacementDurationMs(SenadaLook(animationScale = AnimationSpeed.Slow.scale)))
        assertEquals(QUEUE_REORDER_ANIM_MS, queueReorderPlacementDurationMs(SenadaLook(animationScale = AnimationSpeed.Normal.scale)))
        assertEquals(120, queueReorderPlacementDurationMs(SenadaLook(animationScale = AnimationSpeed.Fast.scale)))
        assertEquals(0, queueReorderPlacementDurationMs(SenadaLook(animationScale = AnimationSpeed.Off.scale)))

        assertEquals(270, queueSettleDurationMs(SenadaLook(animationScale = AnimationSpeed.Slow.scale)))
        assertEquals(QUEUE_SETTLE_ANIM_MS, queueSettleDurationMs(SenadaLook(animationScale = AnimationSpeed.Normal.scale)))
        assertEquals(108, queueSettleDurationMs(SenadaLook(animationScale = AnimationSpeed.Fast.scale)))
        assertEquals(0, queueSettleDurationMs(SenadaLook(animationScale = AnimationSpeed.Off.scale)))
    }
}
