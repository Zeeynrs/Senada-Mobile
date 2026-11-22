package senada.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.MainScope
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.recordHistoryTo
import senada.library.recordListeningTo

/** Library hidup selama proses aplikasi, sama seperti pemutar. */
internal object AndroidLibrary {
    private var library: LibraryRepository? = null

    fun get(context: Context): LibraryRepository = library ?: create(context.applicationContext).also { library = it }

    private fun create(context: Context): LibraryRepository {
        val library = LibraryRepository(libraryDriver(context))
        val playback = AndroidPlayback.get(context)
        val scope = MainScope()
        playback.controller.recordHistoryTo(library, scope)
        playback.controller.recordListeningTo(library, scope, playback.position)
        return library
    }
}

/** Database library di penyimpanan privat aplikasi. */
private fun libraryDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(SenadaDatabase.Schema, context, LIBRARY_DATABASE)

private const val LIBRARY_DATABASE = "senada.db"
