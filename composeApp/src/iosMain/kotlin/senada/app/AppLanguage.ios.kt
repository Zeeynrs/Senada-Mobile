@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package senada.app

import coil3.PlatformContext
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
import senada.resources.Res

internal actual fun registerPlatformContext(context: PlatformContext) = Unit

internal actual fun loadComposeCvrBytes(path: String): ByteArray? {
    val bundleRoot = NSBundle.mainBundle.resourcePath
    val bundleCandidates = listOfNotNull(
        bundleRoot?.let { "$it/compose-resources/composeResources/senada.resources/$path" },
        bundleRoot?.let { "$it/composeResources/senada.resources/$path" },
        "build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/$path",
        "composeApp/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/$path",
    )
    for (candidate in bundleCandidates) {
        if (NSFileManager.defaultManager.fileExistsAtPath(candidate)) {
            NSData.dataWithContentsOfFile(candidate)?.toByteArrayOrNull()?.let { return it }
        }
    }
    return runBlocking { runCatching { Res.readBytes(path) }.getOrNull() }
}

private fun NSData.toByteArrayOrNull(): ByteArray? {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)
    val ptr = this.bytes ?: return null
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), ptr, length)
    }
    return bytes
}
