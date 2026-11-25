package senada.library

import senada.catalog.youtube.YOUTUBE_MUSIC_SOURCE

internal const val DEFAULT_ARTIST_SOURCE = YOUTUBE_MUSIC_SOURCE

/** Artis yang diikuti pengguna secara eksplisit, beserta metadata luringnya untuk Library. */
internal data class FollowedArtist(
    val id: String,
    val catalogId: String,
    val name: String,
    val artworkUrl: String? = null,
    val source: String = DEFAULT_ARTIST_SOURCE,
    val followedAtMs: Long = 0L,
)

internal fun normalizeArtistSource(source: String): String =
    source.trim().ifEmpty { DEFAULT_ARTIST_SOURCE }

internal fun normalizeArtistCatalogId(
    catalogId: String,
    source: String = DEFAULT_ARTIST_SOURCE,
): String {
    val cleanSource = normalizeArtistSource(source)
    return catalogId.trim().removePrefix("$cleanSource:").trim()
}

/** Kunci penyimpanan kanonik `<source>:<catalogId>` agar ID lintas sumber tidak bertabrakan. */
internal fun followedArtistKey(
    catalogId: String,
    source: String = DEFAULT_ARTIST_SOURCE,
): String {
    val cleanSource = normalizeArtistSource(source)
    val cleanCatalogId = normalizeArtistCatalogId(catalogId, cleanSource)
    if (cleanCatalogId.isEmpty()) return ""
    return "$cleanSource:$cleanCatalogId"
}

internal fun followedArtistOf(
    id: String,
    catalogId: String,
    name: String,
    artworkUrl: String?,
    source: String,
    followedAtMs: Long,
): FollowedArtist = FollowedArtist(
    id = id,
    catalogId = catalogId,
    name = name,
    artworkUrl = artworkUrl?.trim()?.takeIf { it.isNotEmpty() },
    source = normalizeArtistSource(source),
    followedAtMs = followedAtMs,
)
