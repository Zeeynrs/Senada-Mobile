package senada.app

import android.content.Context
import coil3.PlatformContext
import java.io.File
import kotlin.concurrent.Volatile

internal actual fun registerPlatformContext(context: PlatformContext) {
    AndroidResourceHolder.register(context)
}

internal object AndroidResourceHolder {
    @Volatile
    private var appContext: Context? = null

    fun register(context: Context) {
        appContext = context.applicationContext ?: context
    }

    fun readAssetOrNull(assetPath: String): ByteArray? = appContext?.let { ctx ->
        runCatching { ctx.assets.open(assetPath).use { it.readBytes() } }.getOrNull()
    }
}

internal actual fun loadComposeCvrBytes(path: String): ByteArray? {
    AndroidResourceHolder.readAssetOrNull("composeResources/senada.resources/$path")?.let { return it }
    val candidates = listOf(
        File("build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/$path"),
        File("composeApp/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/$path"),
    )
    for (file in candidates) {
        if (file.isFile) return runCatching { file.readBytes() }.getOrNull()
    }
    return AppStrings::class.java.classLoader
        ?.getResourceAsStream("composeResources/senada.resources/$path")
        ?.use { it.readBytes() }
}
