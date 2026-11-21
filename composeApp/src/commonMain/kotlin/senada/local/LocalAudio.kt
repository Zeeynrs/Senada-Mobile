package senada.local

import kotlin.random.Random
import senada.model.Track

/** Sumber lagu dari file audio yang diimpor pengguna ke penyimpanan aplikasi. */
internal const val LOCAL_FILE_SOURCE = "local"
private const val LOCAL_TRACK_PREFIX = "local:"

/** Masalah file audio lokal yang perlu dijelaskan ke pengguna. */
internal class LocalAudioException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/**
 * Folder file audio impor milik aplikasi. [Track.id] hanya menyimpan nama file, bukan path lengkap,
 * karena folder aplikasi iOS bisa berpindah saat aplikasi diperbarui.
 */
internal fun interface LocalAudioFiles {
    /** URI `file://` untuk diputar, atau null bila file sudah tidak ada. */
    fun uriFor(fileName: String): String?
}

/** File yang baru disalin ke penyimpanan aplikasi. */
internal data class ImportedAudio(val fileName: String, val displayName: String, val sizeBytes: Long)

internal fun ImportedAudio.toTrack(): Track = Track(
    id = LOCAL_TRACK_PREFIX + fileName,
    title = displayName.substringBeforeLast('.').trim().ifEmpty { displayName.ifBlank { "File audio" } },
    artists = emptyList(),
    source = LOCAL_FILE_SOURCE,
)

/** Nama file di penyimpanan aplikasi untuk lagu impor, atau null untuk sumber lain. */
internal fun localFileNameOf(track: Track): String? =
    track.id.takeIf { track.source == LOCAL_FILE_SOURCE && it.startsWith(LOCAL_TRACK_PREFIX) }
        ?.removePrefix(LOCAL_TRACK_PREFIX)
        ?.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it && it != "." && it != ".." }

/** Nama acak untuk salinan, dengan ekstensi asli bila wajar, supaya pemutar mengenali formatnya. */
internal fun newLocalFileName(displayName: String, random: Random = Random.Default): String {
    val id = random.nextBytes(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it.length in 1..5 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }
    return if (extension == null) id else "$id.$extension"
}

/** Ruang yang disisakan setelah menyalin, supaya ponsel tidak kehabisan penyimpanan sama sekali. */
internal const val FREE_SPACE_MARGIN_BYTES = 50L * 1024 * 1024

internal fun notEnoughSpace() = LocalAudioException("Ruang penyimpanan tidak cukup untuk menyalin file ini.")
