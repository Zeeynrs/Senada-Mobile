package senada.library

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.inMemoryDriver
import senada.db.SenadaDatabase

internal actual fun testLibraryDriver(): SqlDriver = inMemoryDriver(SenadaDatabase.Schema)
