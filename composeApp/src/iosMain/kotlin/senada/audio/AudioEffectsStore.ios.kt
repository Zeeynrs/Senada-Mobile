package senada.audio

import coil3.PlatformContext
import platform.Foundation.NSUserDefaults

internal actual fun audioEffectsStore(context: PlatformContext): AudioEffectsStore {
    val prefs = NSUserDefaults.standardUserDefaults
    val key = "senada.audioEffects"
    return AudioEffectsStore({ prefs.stringForKey(key) }, { prefs.setObject(it, forKey = key) })
}
