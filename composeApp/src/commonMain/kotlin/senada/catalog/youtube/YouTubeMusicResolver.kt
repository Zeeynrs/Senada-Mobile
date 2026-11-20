package senada.catalog.youtube

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.serialization.json.JsonObject
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.catalog.ResolvedAudio
import senada.catalog.StreamResolver
import senada.model.Track

@OptIn(ExperimentalTime::class)
private fun epochMillis() = Clock.System.now().toEpochMilliseconds()

/** Alamat audio AAC/MP4 langsung; URL terikat ke IP ponsel dan berlaku beberapa jam. */
internal class YouTubeMusicResolver(
    private val innertube: InnertubeClient,
    private val now: () -> Long = ::epochMillis,
) : StreamResolver {
    override suspend fun resolve(track: Track): ResolvedAudio {
        val videoId = videoIdOf(track) ?: throw CatalogException(Kind.Unavailable, "Not a YouTube track: ${track.id}")
        return parsePlayer(innertube.player(videoId), now())
    }
}

internal fun parsePlayer(response: JsonObject, nowMs: Long): ResolvedAudio {
    val status = response.string("playabilityStatus", "status")
    if (status != "OK") {
        val reason = response.string("playabilityStatus", "reason").orEmpty()
        // YouTube meminta "konfirmasi bukan bot" saat membatasi jaringan; semua lagu akan gagal sama.
        val kind = if (BOT_CHECK.containsMatchIn(reason)) Kind.RateLimited else Kind.Unavailable
        throw CatalogException(kind, "Playability $status: $reason")
    }

    val formats = response.array("streamingData", "adaptiveFormats").filter { it.string("url") != null }
    val format = formats.firstOrNull { it.at("itag")?.toString() == AAC_ITAG }
        ?: formats.firstOrNull { it.string("mimeType")?.startsWith("audio/mp4") == true }
        ?: throw CatalogException(Kind.Unavailable, "No AAC audio stream")

    val expiresInSeconds = response.string("streamingData", "expiresInSeconds")?.toLongOrNull()
    return ResolvedAudio(
        location = format.string("url")!!,
        mimeType = "audio/mp4",
        expiresAtEpochMs = expiresInSeconds?.let { nowMs + it * 1_000 },
    )
}

/** AAC-LC ±128 kbps dalam MP4, didukung Media3 dan AVPlayer. */
private const val AAC_ITAG = "140"
private val BOT_CHECK = Regex("not a bot", RegexOption.IGNORE_CASE)
