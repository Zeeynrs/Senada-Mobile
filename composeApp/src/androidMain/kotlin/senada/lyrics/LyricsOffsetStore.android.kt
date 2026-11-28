package senada.lyrics

import android.content.Context
import coil3.PlatformContext

internal actual fun lyricsOffsetStore(context: PlatformContext): LyricsOffsetStore {
    val prefs by lazy { context.applicationContext.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE) }
    return LyricsOffsetStore({ prefs.getString("offsets", null) }, { prefs.edit().putString("offsets", it).apply() })
}
