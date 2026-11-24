package senada.app.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil3.compose.LocalPlatformContext

private fun isSystemAnimatorDisabled(context: Context): Boolean = try {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) <= 0f
} catch (_: Exception) {
    false
}

@Composable
internal actual fun rememberSystemReduceMotion(): Boolean {
    val context = LocalPlatformContext.current
    var reduced by remember(context) { mutableStateOf(isSystemAnimatorDisabled(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = isSystemAnimatorDisabled(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        if (uri != null) {
            try {
                resolver.registerContentObserver(uri, false, observer)
            } catch (_: SecurityException) {
                // Tetap memakai nilai awal bila sistem menolak observer.
            }
        }
        reduced = isSystemAnimatorDisabled(context)
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return reduced
}
