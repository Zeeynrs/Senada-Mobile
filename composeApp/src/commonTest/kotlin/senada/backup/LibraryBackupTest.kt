package senada.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.testLibraryDriver
import senada.model.Track

class LibraryBackupTest {
    private val database = SenadaDatabase(testLibraryDriver())
    private var clock = 1_000_000L
    private var ids = 0

    private fun TestScope.library() = LibraryRepository(
        database,
        dispatcher = StandardTestDispatcher(testScheduler),
        now = { clock++ },
        newPlaylistId = { "local-mobile${++ids}" },
    )

    private fun track(id: String, title: String, vararg artists: String, durationMs: Long? = 200_000, art: String? = null) =
        Track("yt:track:$id", title, artists.toList(), durationMs, art, "ytmusic")

    private val pagi = track("pagi1", "Lagu Pagi", "Penyanyi Satu", "Penyanyi Dua", art = "https://img.example.invalid/pagi=w544")
    private val senja = track("senja1", "Lagu Senja", "Band Senja")
    private val malam = track("malam1", "Lagu Malam", "Solo Malam", durationMs = null)

    /** Backup desktop karangan: satu file lokal, satu lagu Spotify yang belum dicocokkan, dan pengaturan. */
    private val desktopBackup = """
        {"app":"Senada","kind":"backup","format":1,"senadaVersion":"0.9.0","created":"2026-11-01T10:00:00+07:00",
         "library":{
          "liked":[
            {"uri":"yt:track:pagi1","id":"pagi1","name":"Lagu Pagi","artists":"Penyanyi Satu, Penyanyi Dua","artistId":"",
             "album":"Album","albumId":"","imageSmall":"","image":"https://img.example.invalid/pagi=w544","imageLarge":"",
             "durationMs":200000,"explicit":false,"addedAt":1700000200},
            {"uri":"local:/home/x/lagu.mp3","id":"","name":"Rekaman","artists":"","durationMs":1000,"addedAt":1700000100},
            {"uri":"yt:track:senja1","id":"senja1","name":"Lagu Senja","artists":"Band Senja","durationMs":200000,"addedAt":1700000000}
          ],
          "playlists":[
            {"id":"local-abc123","name":"Jalan","description":"perjalanan","source":"","created":1690000000,
             "tracks":[
               {"uri":"yt:track:senja1","id":"senja1","name":"Lagu Senja","artists":"Band Senja","durationMs":200000},
               {"uri":"spotify:track:xyz","id":"xyz","name":"Belum Cocok","artists":"Seseorang","durationMs":100000},
               {"uri":"yt:track:malam1","id":"malam1","name":"Lagu Malam","artists":"Solo Malam","durationMs":0}
             ]}
          ],
          "saved":[
            {"kind":"album","id":"MPREb_satu","uri":"","name":"Album Satu","subtitle":"Band","imageSmall":"","image":"","imageLarge":""},
            {"kind":"artist","id":"UC_dua","uri":"","name":"Artis Dua","subtitle":"","imageSmall":"","image":"","imageLarge":""}
          ],
          "history":[
            {"uri":"yt:track:malam1","id":"malam1","name":"Lagu Malam","artists":"Solo Malam","durationMs":0,"playedAt":1700000500},
            {"uri":"yt:track:pagi1","id":"pagi1","name":"Lagu Pagi","artists":"Penyanyi Satu, Penyanyi Dua","durationMs":200000,"playedAt":1700000400}
          ]},
         "settings":{"theme":"dark","volume":30}}
    """.trimIndent()

    @Test
    fun desktop_backup_imports_supported_fields() = runTest {
        val library = library()

        val summary = library.importBackup(desktopBackup)

        assertEquals(ImportSummary(favoritesAdded = 2, playlistsAdded = 1, savedAdded = 2, historyAdded = 2, skipped = 2), summary)
        assertEquals(listOf(pagi, senja), library.favorites.first())
        val playlist = library.playlist("local-abc123").first()!!
        assertEquals("Jalan", playlist.name)
        assertEquals("perjalanan", playlist.description)
        assertEquals(1_690_000_000_000L, playlist.createdAtMs)
        assertEquals(listOf(senja, malam), playlist.entries.map { it.track })
        assertEquals(listOf(malam, pagi), library.history().first())
        assertEquals(listOf("album" to "MPREb_satu", "artist" to "UC_dua"), library.savedItems.first().map { it.kind to it.id })
        assertTrue("\"subtitle\":\"Band\"" in library.savedItems.first().first().metadata)
    }

    @Test
    fun mobile_export_is_readable_by_desktop() = runTest {
        val library = library()
        library.setFavorite(pagi, true)
        val id = library.createPlaylist("Mobile", listOf(senja, senja))
        library.recordPlay(malam)
        library.setSaved("album", "MPREb_x", """{"kind":"album","id":"MPREb_x","name":"X","subtitle":"Y"}""", saved = true)

        val text = library.exportBackup(createdIso = "2026-11-21T00:00:00Z")

        val root = Json.parseToJsonElement(text).jsonObject
        assertEquals("Senada", root.string("app"))
        assertEquals("backup", root.string("kind"))
        assertEquals(1L, root["format"]!!.jsonPrimitive.long)
        assertFalse("settings" in root)
        val lib = root["library"]!!.jsonObject
        val liked = lib["liked"]!!.jsonArray.single().jsonObject
        assertEquals("yt:track:pagi1", liked.string("uri"))
        assertEquals("pagi1", liked.string("id"))
        assertEquals("Lagu Pagi", liked.string("name"))
        assertEquals("Penyanyi Satu, Penyanyi Dua", liked.string("artists"))
        assertEquals(200_000L, liked["durationMs"]!!.jsonPrimitive.long)
        assertEquals(1_000L, liked["addedAt"]!!.jsonPrimitive.long)
        // Semua kolom lagu desktop ada, supaya desktop bisa langsung memakainya.
        val desktopKeys = setOf("uri", "id", "name", "artists", "artistId", "album", "albumId", "imageSmall", "image", "imageLarge", "durationMs", "explicit")
        assertTrue(liked.keys.containsAll(desktopKeys))
        val playlist = lib["playlists"]!!.jsonArray.single().jsonObject
        assertEquals(id, playlist.string("id"))
        assertEquals(2, (playlist["tracks"] as JsonArray).size)
        assertEquals("Y", lib["saved"]!!.jsonArray.single().jsonObject.string("subtitle"))
        assertEquals("yt:track:malam1", lib["history"]!!.jsonArray.single().jsonObject.string("uri"))

        // Dibaca lagi oleh Senada sendiri tanpa kehilangan isi.
        val reread = BackupCodec.decode(text)
        assertEquals(listOf(pagi), reread.liked.map { it.track })
        assertEquals(listOf(senja, senja), reread.playlists.single().tracks)
        assertEquals(listOf(malam), reread.history.map { it.track })
        assertEquals(0, reread.skipped)
    }

    @Test
    fun repeat_import_is_idempotent() = runTest {
        val library = library()
        library.importBackup(desktopBackup)

        val again = library.importBackup(desktopBackup)

        assertTrue(again.changesNothing)
        assertEquals(2, again.skipped)
        assertEquals(2, library.favorites.first().size)
        assertEquals(2, library.playlist("local-abc123").first()!!.entries.size)
        assertEquals(2, library.savedItems.first().size)
        assertEquals(2, library.history().first().size)
    }

    @Test
    fun an_existing_playlist_keeps_its_name_order_and_duplicates() = runTest {
        val library = library()
        library.importBackup(desktopBackup)
        library.renamePlaylist("local-abc123", "Nama Mobile")
        library.addToPlaylist("local-abc123", listOf(senja))
        library.movePlaylistItem("local-abc123", from = 2, to = 0)
        val before = library.playlist("local-abc123").first()!!.entries.map { it.track }
        val withNewSong = desktopBackup.replace(
            """{"uri":"yt:track:malam1","id":"malam1","name":"Lagu Malam","artists":"Solo Malam","durationMs":0}""",
            """{"uri":"yt:track:malam1","id":"malam1","name":"Lagu Malam","artists":"Solo Malam","durationMs":0},
               {"uri":"yt:track:pagi1","id":"pagi1","name":"Lagu Pagi","artists":"Penyanyi Satu, Penyanyi Dua","durationMs":200000}""",
        )

        val summary = library.importBackup(withNewSong)

        assertEquals(1, summary.playlistTracksAdded)
        val after = library.playlist("local-abc123").first()!!
        assertEquals("Nama Mobile", after.name)
        assertEquals(before + pagi, after.entries.map { it.track })
    }

    @Test
    fun history_keeps_the_newest_play_and_the_limit() = runTest {
        val library = library()
        // Lagu malam diputar di ponsel setelah waktu di backup; lagu pagi belum pernah.
        clock = 1_800_000_000_000L
        library.recordPlay(malam)

        library.importBackup(desktopBackup)

        assertEquals(listOf(malam, pagi), library.history().first())
        val exported = Json.parseToJsonElement(library.exportBackup("x")).jsonObject["library"]!!.jsonObject
        assertEquals(1_800_000_000L, exported["history"]!!.jsonArray.first().jsonObject["playedAt"]!!.jsonPrimitive.long)

        val many = (1..210).joinToString(",") { n ->
            """{"uri":"yt:track:v$n","id":"v$n","name":"Lagu $n","artists":"A","durationMs":1000,"playedAt":${1_600_000_000 + n}}"""
        }
        library.importBackup("""{"history":[$many]}""")
        assertEquals(LibraryRepository.HISTORY_LIMIT, library.history().first().size)
    }

    @Test
    fun preview_counts_without_writing() = runTest {
        val library = library()

        val preview = library.importBackup(desktopBackup, apply = false)

        assertEquals(2, preview.favoritesAdded)
        assertEquals(emptyList(), library.favorites.first())
        assertEquals(emptyList(), library.playlists.first())
        assertEquals(emptyList(), library.history().first())
        assertEquals(emptyList(), library.savedItems.first())
    }

    @Test
    fun malformed_backup_writes_nothing() = runTest {
        val library = library()
        library.setFavorite(senja, true)
        val broken = listOf(
            "bukan json",
            "[1,2,3]",
            """{"app":"Lain","kind":"backup"}""",
            """{"app":"Senada","kind":"backup","format":1,"library":{"liked":"rusak"}}""",
            desktopBackup.replace(""""history":[""", """"history":{"rusak":true},"x":["""),
        )
        for (text in broken) {
            assertFailsWith<BackupException>(text) { library.importBackup(text) }
        }

        assertEquals(listOf(senja), library.favorites.first())
        assertEquals(emptyList(), library.playlists.first())
    }

    @Test
    fun unsupported_future_format_is_rejected() = runTest {
        val error = assertFailsWith<BackupException> {
            library().importBackup(desktopBackup.replace("\"format\":1", "\"format\":2"))
        }
        assertTrue("lebih baru" in error.userMessage)
    }

    @Test
    fun desktop_settings_are_not_applied() = runTest {
        val library = library()
        library.importBackup(desktopBackup)

        val exported = Json.parseToJsonElement(library.exportBackup("x")).jsonObject

        // Pengaturan desktop tidak dibaca dan tidak ikut keluar lagi dari ponsel.
        assertFalse("settings" in exported)
        assertFalse("theme" in exported.toString())
    }

    @Test
    fun oversized_backups_are_rejected_before_parsing() {
        val error = assertFailsWith<BackupException> { BackupCodec.decode(" ".repeat(BackupCodec.MAX_BYTES + 1)) }
        assertTrue("10 MB" in error.userMessage)
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content
}
