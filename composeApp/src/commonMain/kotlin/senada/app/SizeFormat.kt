package senada.app

import kotlin.math.roundToLong

/**
 * Ukuran untuk pengguna dengan satuan desimal seperti pengelola file ponsel, misalnya "12.4 MB" / "12,4 MB".
 * Desimal nol tidak ditulis ("1 MB", bukan "1.0 MB").
 */
internal fun formatSize(
    bytes: Long,
    language: AppLanguage = AppStrings.currentLanguage,
): String {
    if (bytes < 1_000) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var divisor = 1_000.0
    for ((index, unit) in units.withIndex()) {
        val tenths = (bytes / (divisor / 10)).roundToLong()
        // Naik satuan bila pembulatan sudah mencapai 1000 (misalnya 999.950 B → "1 MB").
        if (tenths < 10_000 || index == units.lastIndex) return formatTenths(tenths, unit, language)
        divisor *= 1_000
    }
    error("tidak tercapai")
}

private fun formatTenths(tenths: Long, unit: String, language: AppLanguage): String {
    val whole = tenths / 10
    val fraction = tenths % 10
    val sep = if (language == AppLanguage.Indonesian) ',' else '.'
    return if (fraction == 0L) "$whole $unit" else "$whole$sep$fraction $unit"
}
