package senada.catalog

import senada.model.Track

data class ResolvedAudio(
    val location: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
    val mimeType: String? = null,
)

interface StreamResolver {
    suspend fun resolve(track: Track, saveData: Boolean = false): ResolvedAudio
}
