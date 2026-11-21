package senada.platform

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import senada.db.SenadaDatabase

/** Database library di penyimpanan privat aplikasi; dipakai saat layar library dipasang. */
internal fun libraryDriver(context: Context): SqlDriver =
    AndroidSqliteDriver(SenadaDatabase.Schema, context.applicationContext, LIBRARY_DATABASE)

private const val LIBRARY_DATABASE = "senada.db"
