package senada.app.nowplaying

import senada.playback.QueueItem

/**
 * Bagian antrean yang ditampilkan: lagu yang sedang dipilih dan sesudahnya. Lagu yang sudah
 * diputar tetap ada di antrean agar tombol Sebelumnya masih bisa kembali ke sana.
 *
 * [offset] adalah indeks entri pertama yang tampil dalam urutan putar penuh. Bila [startsWithCurrent],
 * entri pertama yang tampil adalah lagu yang sedang dipilih.
 */
internal data class UpcomingQueue(
    val offset: Int,
    val entries: List<QueueItem>,
    val startsWithCurrent: Boolean,
) {
    /**
     * Indeks tujuan pada urutan putar penuh saat entri dipindah ke [visibleIndex]. Entri lain tidak
     * bisa ditaruh di atas lagu yang sedang dipilih, karena di sana ia akan dianggap sudah diputar.
     */
    fun moveTarget(visibleIndex: Int): Int =
        offset + if (startsWithCurrent) visibleIndex.coerceAtLeast(1) else visibleIndex
}

internal fun upcomingQueue(playOrder: List<QueueItem>, selectedEntryId: Long?): UpcomingQueue {
    val current = playOrder.indexOfFirst { it.id == selectedEntryId }
    if (current < 0) return UpcomingQueue(0, playOrder, startsWithCurrent = false)
    return UpcomingQueue(current, playOrder.subList(current, playOrder.size), startsWithCurrent = true)
}
