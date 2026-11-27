@file:OptIn(ExperimentalForeignApi::class)

package senada.lyrics

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.rangeOfComposedCharacterSequenceAtIndex

internal actual fun graphemeOffsets(text: String): IntArray {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nativeText = text as NSString
    return buildList {
        var offset = 0
        add(offset)
        while (offset < text.length) {
            offset += nativeText.rangeOfComposedCharacterSequenceAtIndex(offset.toULong()).useContents { length.toInt() }
            add(offset)
        }
    }.toIntArray()
}
