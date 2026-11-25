package senada.offline

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import senada.catalog.ResolvedAudio
import senada.lyrics.LyricLine
import senada.lyrics.LyricWord
import senada.lyrics.LyricsDocument
import senada.model.Track

class OfflineStoreTest {
    private val track = Track("yt:track:test-id", "Lagu uji", listOf("Senada"), source = "ytmusic")
    private val audio = ResolvedAudio("https://example.invalid/audio", durationMs = 8_000L, loudnessDb = -2f)
    private val files = FileSystem.SYSTEM

    @Test
    fun truncatedHttpAudioIsNeverPublishedAsDownloaded() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "senada-transfer-${Random.nextLong()}"
        val http = HttpClient(MockEngine {
            assertEquals("client-token", it.headers["X-Test-Stream"])
            respond(
                "part", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("audio/mp4"), HttpHeaders.ContentLength to listOf("8")),
            )
        }) { install(HttpTimeout) }
        try {
            val store = OfflineStore(root, files) { "file://$it" }
            assertFailsWith<IllegalStateException> {
                store.save(track, audio, writeAudio = { dest ->
                    fetchOfflineFile(http, audio.location, dest, headers = mapOf("X-Test-Stream" to "client-token"))
                })
            }
            assertNull(store.find(track))
            assertEquals(emptyList(), files.listOrNull(root).orEmpty())
        } finally {
            http.close()
            files.deleteRecursively(root, mustExist = false)
        }
    }

    @Test
    fun incompleteAudioIsHiddenAndACompletedCopySurvivesRestart() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "senada-offline-${Random.nextLong()}"
        try {
            val store = OfflineStore(root, files) { "file://$it" }
            assertFailsWith<IllegalStateException> {
                store.save(track, audio, writeAudio = {
                    files.write(it) { writeUtf8("partial") }
                    error("interrupted")
                })
            }
            assertNull(store.find(track))
            assertEquals(emptyList(), store.list())

            val lyrics = LyricsDocument.Plain(listOf("Baris lirik"))
            store.save(
                track, audio, writeAudio = { files.write(it) { writeUtf8("complete") } },
                writeCover = { files.write(it) { writeUtf8("cover") } }, readLyrics = { lyrics },
            )
            val reopened = OfflineStore(root, files) { "file://$it" }
            val song = assertNotNull(reopened.find(track))
            assertEquals(track, song.track)
            assertEquals(8_000L, song.durationMs)
            assertEquals(-2f, song.loudnessDb)
            assertEquals(lyrics, reopened.lyrics(track))
            assertNotNull(song.coverUri)
            assertEquals(listOf(song), reopened.list())

            reopened.remove(track)
            assertNull(reopened.find(track))
            assertEquals(emptyList(), reopened.list())
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }

    @Test
    fun wordTimedSyncedLyricsSurviveOfflineSaveAndReopen() = runTest {
        val root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "senada-offline-words-${Random.nextLong()}"
        try {
            val store = OfflineStore(root, files) { "file://$it" }
            val lyrics = LyricsDocument.Synced(
                listOf(
                    LyricLine(
                        startMs = 1_000L,
                        text = "Halo dunia",
                        words = listOf(
                            LyricWord(1_000L, 400L, "Halo "),
                            LyricWord(1_400L, 600L, "dunia"),
                        ),
                        endMs = 2_000L,
                    ),
                ),
            )
            store.save(
                track, audio, writeAudio = { files.write(it) { writeUtf8("complete") } },
                readLyrics = { lyrics },
            )
            val reopened = OfflineStore(root, files) { "file://$it" }
            assertEquals(lyrics, reopened.lyrics(track))
        } finally {
            files.deleteRecursively(root, mustExist = false)
        }
    }
}
