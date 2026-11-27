package senada.catalog

import senada.app.isDebugBuild
import senada.model.Track

/** Lagu contoh untuk build debug dan tes; build rilis tidak menampilkannya. */
internal object DemoCatalog : CatalogRepository {
    private val audioPaths = listOf(
        Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), durationMs = 12_000, source = "demo") to
            "files/selepas-hujan.m4a",
        Track("demo:track:pagi", "Sebelum Pagi", listOf("Senada"), durationMs = 12_000, source = "demo") to
            "files/sebelum-pagi.m4a",
    )
    internal val debugTracks = audioPaths.map { (track, _) -> track }

    /** Lagu contoh yang tampil di aplikasi: hanya di build debug. */
    val tracks: List<Track> get() = visibleTracks(isDebugBuild())

    internal fun visibleTracks(debug: Boolean): List<Track> = if (debug) debugTracks else emptyList()

    override suspend fun search(query: String, pageToken: String?): TrackPage {
        if (pageToken != null) return TrackPage(emptyList())
        if (query.isBlank()) return TrackPage(debugTracks)

        val words = query.trim().split(Regex("\\s+"))
        return TrackPage(debugTracks.filter { track ->
            val searchable = (listOf(track.title) + track.artists).joinToString(" ")
            words.all { word -> searchable.contains(word, ignoreCase = true) }
        })
    }

    fun localAudioPath(trackId: String): String? = audioPaths.firstOrNull { (track, _) -> track.id == trackId }?.second
}
