package senada.library

import app.cash.sqldelight.db.SqlDriver

/** Database kosong di memori dengan skema library, satu per tes. */
internal expect fun testLibraryDriver(): SqlDriver
