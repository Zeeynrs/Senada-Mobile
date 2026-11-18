package senada.catalog

import senada.model.Track

data class TrackPage(val items: List<Track>, val nextPageToken: String? = null)

interface CatalogRepository {
    suspend fun search(query: String, pageToken: String? = null): TrackPage
}
