package senada.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun versionName(): String {
    val context = LocalContext.current
    return remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}
