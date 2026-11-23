package senada.audio

import android.content.Context
import coil3.PlatformContext

internal actual fun audioEffectsStore(context: PlatformContext): AudioEffectsStore {
    val prefs by lazy { context.applicationContext.getSharedPreferences("audio_effects", Context.MODE_PRIVATE) }
    return AudioEffectsStore({ prefs.getString("settings", null) }, { prefs.edit().putString("settings", it).apply() })
}
