package senada.lyrics.sources

import io.ktor.client.HttpClient

/**
 * The extra lyrics providers, in preference order: Apple-style word timing first, then aggregators,
 * then the Chinese services.
 * On a full tie in duration fit and metadata, the earlier provider wins.
 */
internal fun defaultLyricsSources(http: HttpClient): List<LyricsSource> = listOf(
    BetterLyricsSource(http),
    LyricsPlusSource(http),
    SimpMusicSource(http),
    // Strongest for Mandarin, Japanese and Korean catalogues; the duration and version checks keep wrong songs out.
    NetEaseSource(http),
    QqMusicSource(http),
    KuGouSource(http),
)
