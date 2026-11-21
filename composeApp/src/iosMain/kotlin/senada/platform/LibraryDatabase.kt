package senada.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import senada.db.SenadaDatabase

/** Database library di Application Support aplikasi; dipakai saat layar library dipasang. */
internal fun libraryDriver(): SqlDriver = NativeSqliteDriver(SenadaDatabase.Schema, LIBRARY_DATABASE)

private const val LIBRARY_DATABASE = "senada.db"
