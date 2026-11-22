package senada.platform

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.MainScope
import senada.db.SenadaDatabase
import senada.library.LibraryRepository
import senada.library.recordHistoryTo
import senada.library.recordListeningTo

/** Library hidup selama proses aplikasi; database di Application Support milik aplikasi. */
internal object IosLibrary {
    val library: LibraryRepository by lazy {
        LibraryRepository(NativeSqliteDriver(SenadaDatabase.Schema, LIBRARY_DATABASE)).also {
            val scope = MainScope()
            IosPlayback.controller.recordHistoryTo(it, scope)
            IosPlayback.controller.recordListeningTo(it, scope, IosPlayback::position)
        }
    }
}

private const val LIBRARY_DATABASE = "senada.db"
