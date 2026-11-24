package senada.catalog.youtube

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import senada.catalog.CatalogException
import senada.catalog.CatalogException.Kind
import senada.catalog.ResolvedAudio
import senada.catalog.StreamResolver
import senada.epochMillis
import senada.model.Track

/** Alamat audio AAC/MP4 langsung; URL terikat ke IP ponsel dan berlaku beberapa jam. */
internal class YouTubeMusicResolver(
    private val innertube: InnertubeClient,
    private val now: () -> Long = ::epochMillis,
) : StreamResolver {
    override suspend fun resolve(track: Track, saveData: Boolean): ResolvedAudio {
        val videoId = videoIdOf(track) ?: throw CatalogException(Kind.Unavailable, "Not a YouTube track: ${track.id}")
        return parsePlayer(innertube.player(videoId), now(), saveData)
    }
}

internal fun parsePlayer(response: JsonObject, nowMs: Long, saveData: Boolean = false): ResolvedAudio {
    val status = response.string("playabilityStatus", "status")
    if (status != "OK") {
        val reason = response.string("playabilityStatus", "reason").orEmpty()
        // YouTube meminta "konfirmasi bukan bot" saat membatasi jaringan; semua lagu akan gagal sama.
        val kind = if (BOT_CHECK.containsMatchIn(reason)) Kind.RateLimited else Kind.Unavailable
        throw CatalogException(kind, "Playability $status: $reason")
    }

    val formats = response.array("streamingData", "adaptiveFormats").filter {
        !it.string("url").isNullOrBlank() && it.string("mimeType")?.substringBefore(';') == "audio/mp4"
    }
    val preferredItag = if (saveData) LOW_BITRATE_AAC_ITAG else AAC_ITAG
    val format = formats.firstOrNull { it.at("itag")?.toString() == preferredItag }
        ?: formats.firstOrNull { it.at("itag")?.toString() == AAC_ITAG }
        ?: formats.firstOrNull()
        ?: throw CatalogException(Kind.Unavailable, "No AAC audio stream")

    val expiresInSeconds = response.string("streamingData", "expiresInSeconds")?.toLongOrNull()
    // AVPlayer menebak durasi MP4 terfragmen YouTube terlalu panjang lalu memutar senyap sampai tebakan itu;
    // panjang dari YouTube dipakai sebagai batas akhir yang sebenarnya.
    val durationMs = format.string("approxDurationMs")?.toLongOrNull()?.takeIf { it > 0 }
        ?: response.string("videoDetails", "lengthSeconds")?.toLongOrNull()?.takeIf { it > 0 }?.let { it * 1_000 }
    return ResolvedAudio(
        location = format.string("url")!!,
        mimeType = "audio/mp4",
        expiresAtEpochMs = expiresInSeconds?.let { nowMs + it * 1_000 },
        durationMs = durationMs,
        // VISIONOS mengirim offset dari level acuan pada format AAC yang dipilih.
        loudnessDb = (format.at("loudnessDb") as? JsonPrimitive)
            ?.takeUnless { it.isString }?.floatOrNull?.takeIf { it.isFinite() },
    )
}

/** AAC-LC ±128 kbps dalam MP4, didukung Media3 dan AVPlayer. */
private const val AAC_ITAG = "140"
private const val LOW_BITRATE_AAC_ITAG = "139"
private val BOT_CHECK = Regex("not a bot", RegexOption.IGNORE_CASE)
