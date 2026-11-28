// Portions adapted from Mochy Music (https://github.com/realidkroo/Mochy-Music, commit 3071023), GPL-3.0.
package senada.lyrics.sources

import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonObject
import senada.lyrics.LrcParser
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.lyrics.stripBackingBrackets
import senada.model.Track

internal class LyricsPlusSource(private val http: HttpClient) : LyricsSource {
    override val id = "lyricsplus"

    override suspend fun search(track: Track): List<LyricsCandidate> {
        val query = track.lyricsQuery()
        if (query.title.isBlank() || query.artist.isBlank()) return emptyList()
        val parameters = mapOf(
            "title" to query.title,
            "artist" to query.artist,
            "duration" to query.durationSeconds,
            "id" to track.youtubeVideoId(),
        )
        val candidate = http.getLyricsJsonFromMirrors(id, ENDPOINTS, parameters, ::parseCandidate)
            ?: return emptyList()
        return listOf(candidate)
    }

    private fun parseCandidate(response: JsonObject): LyricsCandidate? {
        val type = response.lyricsString("type")
        val structured = if (type.equals("None", ignoreCase = true)) {
            emptyList()
        } else {
            response.lyricsArray("lyrics").mapNotNull(::parseLine)
                .sortedWith(compareBy({ it.startMs }, { it.text.isBlank() }))
                .attachBackingRows()
        }
        val lines = if (structured.hasWordTiming()) {
            structured
        } else {
            val synced = response.lyricsString("syncedLyrics")?.let(LrcParser::parse)
                .orEmpty().filter { it.text.isNotBlank() || it.background != null }
            synced.takeIf { it.hasWordTiming() } ?: structured.ifEmpty { synced }
        }
        if (lines.isEmpty()) return null
        val metadata = response.lyricsObject("metadata")
        return LyricsCandidate(
            sourceId = id,
            lines = lines,
            wordSynced = lines.hasWordTiming(),
            title = metadata?.lyricsString("title") ?: response.lyricsString("trackName"),
            artist = metadata?.lyricsString("artist") ?: response.lyricsString("artistName"),
            durationMs = response.lyricsDouble("duration")?.lyricsDurationMs(),
        )
    }

    private companion object {
        val ENDPOINTS = listOf(
            "https://lyricsplus.binimum.org/v2/lyrics/get",
            "https://lyricsplus.prjktla.my.id/v2/lyrics/get",
        )
    }
}

private data class Syllable(
    val text: String,
    val start: Long?,
    val duration: Long?,
    val backing: Boolean,
)

private fun parseLine(row: JsonObject): LyricLine? {
    val text = row.lyricsString("text").orEmpty().trim()
    val start = row.lyricsLong("time")?.takeIf { it >= 0L }
    val end = start?.let { lineEnd(it, row.lyricsLong("duration")) }
    val element = row.lyricsObject("element")
    val agent = element?.lyricsString("singer")?.trim()?.takeIf { it.isNotEmpty() }
    val backingRow = element?.lyricsFlag("isBackground") == true
    val parts = row.lyricsArray("syllabus").mapNotNull { item ->
        val partText = item.lyricsString("text")?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Syllable(
            text = partText,
            start = item.lyricsLong("time")?.takeIf { it >= 0L },
            duration = item.lyricsLong("duration"),
            backing = backingRow || item.lyricsFlag("isBackground"),
        )
    }
    val lead = parts.filterNot { it.backing }
    val backing = parts.filter { it.backing }
    val leadLine = when {
        lead.isNotEmpty() -> voiceLine(lead, text, start, end, agent)
        backing.isEmpty() && !backingRow -> voiceLine(emptyList(), text, start, end, agent)
        else -> null
    }
    val background = when {
        backing.isNotEmpty() -> voiceLine(backing, text, backing.firstNotNullOfOrNull { it.start } ?: start, end, agent, true)
        backingRow -> voiceLine(emptyList(), text, start, end, agent, true)
        else -> null
    }
    return leadLine?.copy(background = background) ?: background?.let {
        LyricLine(startMs = it.startMs, text = "", endMs = it.endMs, agent = it.agent, background = it)
    }
}

private fun lineEnd(start: Long, duration: Long?): Long? =
    duration?.takeIf { it >= 0L }?.let { start + it.coerceAtMost(Long.MAX_VALUE - start) }

private fun voiceLine(
    syllables: List<Syllable>,
    sourceText: String,
    start: Long?,
    end: Long?,
    agent: String?,
    backing: Boolean = false,
): LyricLine? {
    val grouped = groupWords(syllables, sourceText, end)
    val text = if (grouped.isEmpty()) sourceText else grouped.joinToString("") { it.text }
    val displayText = if (backing) stripBackingBrackets(text) else text
    if (displayText.isBlank()) return null
    val hasMissingTime = syllables.any { it.text.isNotBlank() && it.start == null }
    val words = when {
        hasMissingTime -> emptyList()
        backing -> stripBackingBrackets(grouped)
        else -> grouped
    }
    val wordEnd = words.maxOfOrNull { it.endMs }
    return LyricLine(
        startMs = start ?: syllables.firstNotNullOfOrNull { it.start } ?: return null,
        text = displayText,
        words = words,
        endMs = if (backing) wordEnd ?: end else end ?: wordEnd,
        agent = agent,
    )
}

private fun groupWords(syllables: List<Syllable>, lineText: String, end: Long?): List<LyricWord> {
    val words = mutableListOf<LyricWord>()
    var cursor = 0
    var spaceAfterPrevious = false
    syllables.forEachIndexed { index, part ->
        val text = part.text.trim()
        if (text.isEmpty()) {
            spaceAfterPrevious = true
            return@forEachIndexed
        }
        val match = lineText.indexOf(text, cursor)
        // Some providers omit spaces from the timed fragments but include them in the line text.
        val spaceBeforePart = part.text.first().isWhitespace() ||
            (match >= 0 && lineText.substring(cursor, match).any(Char::isWhitespace))
        if (match >= 0) cursor = match + text.length
        val start = part.start ?: 0L
        val stop = lineEnd(start, part.duration) ?: syllables.getOrNull(index + 1)?.start ?: end ?: start
        val word = LyricWord(start, (stop - start).coerceAtLeast(0L), text)
        val previous = words.lastOrNull()
        when {
            previous == null -> words += word
            spaceAfterPrevious || spaceBeforePart -> {
                words[words.lastIndex] = previous.copy(text = previous.text + " ")
                words += word
            }
            else -> {
                val mergedStart = minOf(previous.startMs, word.startMs)
                val mergedEnd = maxOf(previous.endMs, word.endMs)
                words[words.lastIndex] = LyricWord(mergedStart, mergedEnd - mergedStart, previous.text + text)
            }
        }
        spaceAfterPrevious = part.text.last().isWhitespace()
    }
    return words
}

private fun List<LyricLine>.attachBackingRows(): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    for (line in this) {
        val previous = lines.lastOrNull()
        val backing = line.background
        if (line.text.isBlank() && backing != null &&
            previous != null && previous.text.isNotBlank() && previous.background == null
        ) {
            lines[lines.lastIndex] = previous.copy(background = backing)
        } else {
            lines += line
        }
    }
    return lines
}
