package senada.model

/** Identitas lagu stabil, misalnya yt:track:<videoId>. Alamat stream dapat berubah. */
data class Track(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val source: String,
)
