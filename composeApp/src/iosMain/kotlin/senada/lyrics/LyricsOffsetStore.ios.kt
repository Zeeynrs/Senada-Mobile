package senada.lyrics

import coil3.PlatformContext
import platform.Foundation.NSUserDefaults

internal actual fun lyricsOffsetStore(context: PlatformContext): LyricsOffsetStore {
    val prefs = NSUserDefaults.standardUserDefaults
    val key = "senada.lyricsOffsets"
    return LyricsOffsetStore({ prefs.stringForKey(key) }, { prefs.setObject(it, forKey = key) })
}
