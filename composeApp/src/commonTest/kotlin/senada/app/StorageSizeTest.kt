package senada.app

import kotlin.test.Test
import kotlin.test.assertEquals

class StorageSizeTest {
    @Test
    fun formatsBytesKilobytesAndMegabytesWithDecimalComma() {
        val examples = mapOf(
            0L to "0 B",
            999L to "999 B",
            1_000L to "1 KB",
            1_250L to "1,3 KB",
            999_950L to "1 MB",
            12_400_000L to "12,4 MB",
            4_400_000L to "4,4 MB",
            999_960_000L to "1 GB",
            2_500_000_000L to "2,5 GB",
        )

        for ((bytes, expected) in examples) {
            assertEquals(expected, formatSize(bytes))
        }
    }
}
