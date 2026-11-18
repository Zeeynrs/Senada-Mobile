package senada.catalog

import senada.model.Track

internal object DemoCatalog : CatalogRepository {
    private val audioPaths = listOf(
        Track("demo:track:hujan", "Selepas Hujan", listOf("Senada"), durationMs = 12_000, source = "demo") to
            "files/selepas-hujan.m4a",
        Track("demo:track:pagi", "Sebelum Pagi", listOf("Senada"), durationMs = 12_000, source = "demo") to
            "files/sebelum-pagi.m4a",
    )
    val tracks = audioPaths.map { (track, _) -> track }

    override suspend fun search(query: String, pageToken: String?): TrackPage {
        if (pageToken != null) return TrackPage(emptyList())
        if (query.isBlank()) return TrackPage(tracks)

        val words = query.trim().split(Regex("\\s+"))
        return TrackPage(tracks.filter { track ->
            val searchable = (listOf(track.title) + track.artists).joinToString(" ")
            words.all { word -> searchable.contains(word, ignoreCase = true) }
        })
    }

    fun localAudioPath(trackId: String): String? = audioPaths.firstOrNull { (track, _) -> track.id == trackId }?.second
}
