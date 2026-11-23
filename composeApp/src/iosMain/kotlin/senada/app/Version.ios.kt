package senada.app

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
internal actual fun versionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""
