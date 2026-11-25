package senada.local

import kotlin.random.Random
import senada.app.AppLanguage
import senada.app.AppStrings
import senada.app.UiText
import senada.model.Track
import senada.resources.Res
import senada.resources.error_import_not_enough_space
import senada.resources.import_audio_default_name

/** Sumber lagu dari file audio yang diimpor pengguna ke penyimpanan aplikasi. */
internal const val LOCAL_FILE_SOURCE = "local"
private const val LOCAL_TRACK_PREFIX = "local:"
private const val DAY_MS = 24L * 60 * 60 * 1_000

/** Masalah file audio lokal yang perlu dijelaskan ke pengguna. */
internal class LocalAudioException(
    internal val uiText: UiText,
    cause: Throwable? = null,
) : Exception(uiText.resolve(), cause) {
    constructor(userMessage: String, cause: Throwable? = null) : this(UiText.Raw(userMessage), cause)

    fun userMessage(language: AppLanguage = AppStrings.currentLanguage): String = uiText.resolve(language)

    val userMessage: String
        get() = userMessage()
}

/**
 * Folder file audio impor milik aplikasi. [Track.id] hanya menyimpan nama file, bukan path lengkap,
 * karena folder aplikasi iOS bisa berpindah saat aplikasi diperbarui.
 */
internal fun interface LocalAudioFiles {
    /** URI `file://` untuk diputar, atau null bila file sudah tidak ada. */
    fun uriFor(fileName: String): String?
}

/** File yang baru disalin ke penyimpanan aplikasi. */
internal data class ImportedAudio(
    val fileName: String,
    val displayName: String,
    val sizeBytes: Long,
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
)

private val COLLAB_SEPARATOR_REGEX = Regex("\\s+(?:feat\\.|ft\\.|featuring)\\s+", RegexOption.IGNORE_CASE)
private val EXTRA_WHITESPACE_REGEX = Regex("\\s+")

internal fun ImportedAudio.toTrack(): Track = Track(
    id = LOCAL_TRACK_PREFIX + fileName,
    title = title?.trim()?.takeIf { it.isNotEmpty() }
        ?: displayName.substringBeforeLast('.').trim()
            .ifEmpty { displayName.ifBlank { AppStrings.get(Res.string.import_audio_default_name) } },
    artists = parseImportedArtists(artist),
    durationMs = durationMs?.takeIf { it > 0 && it < DAY_MS },
    source = LOCAL_FILE_SOURCE,
)

/**
 * Memecah tag artis hanya pada pemisah multi-artis yang eksplisit (`;`, `/`, NUL ID3v2.4, dan
 * penanda kolaborasi) agar nama artis dengan `,` atau `&` tetap utuh.
 */
private fun parseImportedArtists(rawArtist: String?): List<String> {
    if (rawArtist.isNullOrBlank()) return emptyList()
    return rawArtist.split(';', '/', '\u0000')
        .flatMap { it.split(COLLAB_SEPARATOR_REGEX) }
        .map { it.trim().replace(EXTRA_WHITESPACE_REGEX, " ") }
        .filter { it.isNotEmpty() }
}

/** Nama file di penyimpanan aplikasi untuk lagu impor, atau null untuk sumber lain. */
internal fun localFileNameOf(track: Track): String? =
    if (track.source == LOCAL_FILE_SOURCE) localFileNameFromId(track.id) else null

internal fun localFileNameFromId(id: String): String? =
    id.takeIf { it.startsWith(LOCAL_TRACK_PREFIX) }?.removePrefix(LOCAL_TRACK_PREFIX)
        ?.takeIf(::isLocalAudioFileName)

internal fun isLocalAudioFileName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        '/' !in name && '\\' !in name && '\u0000' !in name

/** Nama acak untuk salinan, dengan ekstensi asli bila wajar, supaya pemutar mengenali formatnya. */
internal fun newLocalFileName(displayName: String, random: Random = Random.Default): String {
    val id = random.nextBytes(8).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    val extension = displayName.substringAfterLast('.', "").lowercase()
        .takeIf { it.length in 1..5 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } }
    return if (extension == null) id else "$id.$extension"
}

/** Ruang yang disisakan setelah menyalin, supaya ponsel tidak kehabisan penyimpanan sama sekali. */
internal const val FREE_SPACE_MARGIN_BYTES = 50L * 1024 * 1024

internal fun notEnoughSpace() = LocalAudioException(UiText.Resource(Res.string.error_import_not_enough_space))
