package senada.app

import coil3.PlatformContext
import platform.Foundation.NSUserDefaults

internal actual fun userProfileStore(context: PlatformContext): UserProfileStore {
    val prefs = NSUserDefaults.standardUserDefaults
    val key = "senada.profile"
    return UserProfileStore(
        read = { prefs.stringForKey(key) },
        write = { prefs.setObject(it, forKey = key) },
    )
}
