package senada.app

import android.content.Context
import coil3.PlatformContext

internal actual fun userProfileStore(context: PlatformContext): UserProfileStore {
    val prefs = context.applicationContext.getSharedPreferences("profile", Context.MODE_PRIVATE)
    return UserProfileStore(
        read = { prefs.getString("settings", null) },
        write = { check(prefs.edit().putString("settings", it).commit()) },
    )
}
