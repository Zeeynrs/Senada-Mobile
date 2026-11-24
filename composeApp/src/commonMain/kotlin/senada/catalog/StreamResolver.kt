package senada.catalog

import senada.model.Track

data class ResolvedAudio(
    val location: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
    val mimeType: String? = null,
    /** Panjang audio menurut sumbernya; lebih tepat daripada perkiraan pemutar native. */
    val durationMs: Long? = null,
    /** Loudness format yang dipilih, dalam dB relatif terhadap level acuan sumber. */
    val loudnessDb: Float? = null,
)

interface StreamResolver {
    suspend fun resolve(track: Track, saveData: Boolean = false): ResolvedAudio
}
