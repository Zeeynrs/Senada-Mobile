package senada.lyrics.sources

/**
 * Pure-Kotlin QQ Music QRC decryptor (custom Triple-DES ECB + zlib inflate + QRC XML extraction).
 *
 * QQ Music's legacy QRC cipher differs from standard FIPS 46-3 DES in three documented ways:
 * 1. Each 32-bit half of an 8-byte block/key is byte-reversed on bit reads/writes.
 * 2. S-box 2 entry [1][7] is 15 (not 14) and S-box 4 entry [3][5] is 10 (not 1).
 * 3. The D-half subkey compression indexes `keyCompression[j] - 27` instead of `- 28`.
 * Because of these quirks, standard platform DES/3DES libraries cannot decrypt QRC payloads.
 */
internal object QqQrcCrypto {
    private val QRC_KEY = "!@#)(*$%123ZXC!@!@#)(NHL".encodeToByteArray()

    private val KEY_SHIFT = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    private val KEY_PERM_C = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17,
        9, 1, 58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    )

    private val KEY_PERM_D = intArrayOf(
        62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21,
        13, 5, 60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    )

    private val KEY_COMPRESSION = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9,
        22, 18, 11, 3, 25, 7, 15, 6, 26, 19, 12, 1,
        40, 51, 30, 36, 46, 54, 29, 39, 50, 44, 32, 47,
        43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    private val IP_LEFT = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
        61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
    )

    private val IP_RIGHT = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
        60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
    )

    private val INV_IP_BYTE_ORDER = intArrayOf(3, 2, 1, 0, 7, 6, 5, 4)

    private val P_BOX = intArrayOf(
        15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
    )

    private val SBOX = arrayOf(
        intArrayOf(
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
        ),
        intArrayOf(
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
        ),
        intArrayOf(
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
        ),
        intArrayOf(
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
        ),
        intArrayOf(
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
        ),
        intArrayOf(
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
        ),
        intArrayOf(
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
        ),
        intArrayOf(
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
        ),
    )

    private val decryptSchedules: Array<Array<IntArray>> by lazy {
        arrayOf(
            desKeySetup(QRC_KEY, 16, decrypt = true),
            desKeySetup(QRC_KEY, 8, decrypt = false),
            desKeySetup(QRC_KEY, 0, decrypt = true),
        )
    }

    private val encryptSchedules: Array<Array<IntArray>> by lazy {
        arrayOf(
            desKeySetup(QRC_KEY, 0, decrypt = false),
            desKeySetup(QRC_KEY, 8, decrypt = true),
            desKeySetup(QRC_KEY, 16, decrypt = false),
        )
    }

    /**
     * Decrypts a QQ Music QRC lyric field (hex-encoded 3DES+zlib blob, or plaintext XML)
     * into UTF-8 text. Returns null when the input is not a valid QRC payload.
     */
    fun decryptQrc(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (looksLikeQrcXml(trimmed)) return trimmed.removePrefix("\uFEFF")

        val encrypted = decodeHexOrNull(trimmed) ?: return null
        if (encrypted.size < 8) return null
        val decrypted = tripleDesDecrypt(encrypted)
        val inflated = runCatching { ZlibInflater.inflate(decrypted) }.getOrNull() ?: return null
        return inflated.decodeToString().removePrefix("\uFEFF")
    }

    /**
     * Extracts the `LyricContent` attribute from QQ Music's `<Lyric_1 ... LyricContent="..."/>` XML.
     *
     * QQ Music frequently emits unescaped double quotes inside `LyricContent` (such as artist
     * nicknames), so this scans from `LyricContent="` to the closing `"/>` rather than stopping
     * at the first quote character.
     */
    fun extractLyricContent(xmlOrText: String): String {
        val text = xmlOrText.removePrefix("\uFEFF")
        val attrMatch = LYRIC_CONTENT_START.find(text) ?: return text
        val valueStart = attrMatch.range.last + 1
        val closeTagIndex = text.indexOf("</LyricInfo", startIndex = valueStart, ignoreCase = true)
            .takeIf { it >= 0 } ?: text.length
        val tagEnd = text.lastIndexOf("/>", startIndex = (closeTagIndex - 1).coerceAtLeast(valueStart))
            .takeIf { it >= valueStart } ?: closeTagIndex
        val quoteEnd = text.lastIndexOf('"', startIndex = (tagEnd - 1).coerceAtLeast(valueStart))
        if (quoteEnd < valueStart) return text
        return unescapeXmlEntities(text.substring(valueStart, quoteEnd))
    }

    internal fun tripleDesDecrypt(input: ByteArray): ByteArray =
        runTripleDes(input, decryptSchedules)

    internal fun tripleDesEncrypt(input: ByteArray): ByteArray =
        runTripleDes(input, encryptSchedules)

    private fun runTripleDes(input: ByteArray, schedules: Array<Array<IntArray>>): ByteArray {
        val fullLen = input.size - (input.size % 8)
        val output = ByteArray(fullLen)
        val block = ByteArray(8)
        var offset = 0
        while (offset < fullLen) {
            input.copyInto(block, destinationOffset = 0, startIndex = offset, endIndex = offset + 8)
            desCryptInPlace(block, schedules[0])
            desCryptInPlace(block, schedules[1])
            desCryptInPlace(block, schedules[2])
            block.copyInto(output, destinationOffset = offset, startIndex = 0, endIndex = 8)
            offset += 8
        }
        return output
    }

    private fun desKeySetup(key: ByteArray, keyOffset: Int, decrypt: Boolean): Array<IntArray> {
        var c = 0
        for (i in 0 until 28) {
            c = c or bitNum(key, keyOffset, KEY_PERM_C[i], 31 - i)
        }
        var d = 0
        for (i in 0 until 28) {
            d = d or bitNum(key, keyOffset, KEY_PERM_D[i], 31 - i)
        }
        val schedule = Array(16) { IntArray(6) }
        for (i in 0 until 16) {
            val shift = KEY_SHIFT[i]
            c = ((c shl shift) or (c ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            d = ((d shl shift) or (d ushr (28 - shift))) and 0xFFFFFFF0.toInt()
            val target = schedule[if (decrypt) 15 - i else i]
            for (j in 0 until 24) {
                target[j / 8] = target[j / 8] or bitNumIntRight(c, KEY_COMPRESSION[j], 7 - (j % 8))
            }
            for (j in 24 until 48) {
                target[j / 8] = target[j / 8] or bitNumIntRight(d, KEY_COMPRESSION[j] - 27, 7 - (j % 8))
            }
        }
        return schedule
    }

    private fun desCryptInPlace(block: ByteArray, schedule: Array<IntArray>) {
        var s0 = 0
        for (i in 0 until 32) {
            s0 = s0 or bitNum(block, 0, IP_LEFT[i], 31 - i)
        }
        var s1 = 0
        for (i in 0 until 32) {
            s1 = s1 or bitNum(block, 0, IP_RIGHT[i], 31 - i)
        }
        for (round in 0 until 15) {
            val prevRight = s1
            s1 = feistel(s1, schedule[round]) xor s0
            s0 = prevRight
        }
        s0 = feistel(s1, schedule[15]) xor s0

        for (k in 0 until 8) {
            val b = 7 - k
            block[INV_IP_BYTE_ORDER[k]] = (
                bitNumIntRight(s1, b, 7) or
                    bitNumIntRight(s0, b, 6) or
                    bitNumIntRight(s1, b + 8, 5) or
                    bitNumIntRight(s0, b + 8, 4) or
                    bitNumIntRight(s1, b + 16, 3) or
                    bitNumIntRight(s0, b + 16, 2) or
                    bitNumIntRight(s1, b + 24, 1) or
                    bitNumIntRight(s0, b + 24, 0)
                ).toByte()
        }
    }

    private fun feistel(state: Int, roundKey: IntArray): Int {
        val t1 = bitNumIntLeft(state, 31, 0) or
            ((state and 0xF0000000.toInt()) ushr 1) or
            bitNumIntLeft(state, 4, 5) or
            bitNumIntLeft(state, 3, 6) or
            ((state and 0x0F000000) ushr 3) or
            bitNumIntLeft(state, 8, 11) or
            bitNumIntLeft(state, 7, 12) or
            ((state and 0x00F00000) ushr 5) or
            bitNumIntLeft(state, 12, 17) or
            bitNumIntLeft(state, 11, 18) or
            ((state and 0x000F0000) ushr 7) or
            bitNumIntLeft(state, 16, 23)

        val t2 = bitNumIntLeft(state, 15, 0) or
            ((state and 0x0000F000) shl 15) or
            bitNumIntLeft(state, 20, 5) or
            bitNumIntLeft(state, 19, 6) or
            ((state and 0x00000F00) shl 13) or
            bitNumIntLeft(state, 24, 11) or
            bitNumIntLeft(state, 23, 12) or
            ((state and 0x000000F0) shl 11) or
            bitNumIntLeft(state, 28, 17) or
            bitNumIntLeft(state, 27, 18) or
            ((state and 0x0000000F) shl 9) or
            bitNumIntLeft(state, 0, 23)

        val x0 = ((t1 ushr 24) and 0xFF) xor roundKey[0]
        val x1 = ((t1 ushr 16) and 0xFF) xor roundKey[1]
        val x2 = ((t1 ushr 8) and 0xFF) xor roundKey[2]
        val x3 = ((t2 ushr 24) and 0xFF) xor roundKey[3]
        val x4 = ((t2 ushr 16) and 0xFF) xor roundKey[4]
        val x5 = ((t2 ushr 8) and 0xFF) xor roundKey[5]

        val substituted =
            (SBOX[0][sboxIndex(x0 ushr 2)] shl 28) or
                (SBOX[1][sboxIndex(((x0 and 0x03) shl 4) or (x1 ushr 4))] shl 24) or
                (SBOX[2][sboxIndex(((x1 and 0x0F) shl 2) or (x2 ushr 6))] shl 20) or
                (SBOX[3][sboxIndex(x2 and 0x3F)] shl 16) or
                (SBOX[4][sboxIndex(x3 ushr 2)] shl 12) or
                (SBOX[5][sboxIndex(((x3 and 0x03) shl 4) or (x4 ushr 4))] shl 8) or
                (SBOX[6][sboxIndex(((x4 and 0x0F) shl 2) or (x5 ushr 6))] shl 4) or
                SBOX[7][sboxIndex(x5 and 0x3F)]

        var permuted = 0
        for (i in 0 until 32) {
            permuted = permuted or bitNumIntLeft(substituted, P_BOX[i], i)
        }
        return permuted
    }

    private fun bitNum(bytes: ByteArray, baseOffset: Int, bitIndex: Int, targetShift: Int): Int {
        val byteIndex = baseOffset + (bitIndex / 32) * 4 + 3 - (bitIndex % 32) / 8
        return (((bytes[byteIndex].toInt() and 0xFF) ushr (7 - (bitIndex % 8))) and 0x01) shl targetShift
    }

    private fun bitNumIntRight(word: Int, bitIndex: Int, targetShift: Int): Int =
        ((word ushr (31 - bitIndex)) and 0x01) shl targetShift

    private fun bitNumIntLeft(word: Int, bitIndex: Int, targetShift: Int): Int =
        ((word shl bitIndex) and 0x80000000.toInt()) ushr targetShift

    private fun sboxIndex(sixBits: Int): Int =
        (sixBits and 0x20) or ((sixBits and 0x1F) ushr 1) or ((sixBits and 0x01) shl 4)

    internal fun decodeHexOrNull(hex: String): ByteArray? {
        if (hex.length < 2 || hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = hexNibble(hex[i * 2])
            val lo = hexNibble(hex[i * 2 + 1])
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    internal fun encodeHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = HEX_DIGITS[v ushr 4]
            chars[i * 2 + 1] = HEX_DIGITS[v and 0x0F]
        }
        return chars.concatToString()
    }

    private fun hexNibble(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> -1
    }

    private fun looksLikeQrcXml(text: String): Boolean =
        text.contains("<Lyric_1", ignoreCase = true) ||
            text.contains("LyricContent", ignoreCase = true) ||
            text.contains("<QrcInfos", ignoreCase = true)

    private fun unescapeXmlEntities(text: String): String {
        if ('&' !in text) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch != '&') {
                out.append(ch)
                i++
                continue
            }
            val semi = text.indexOf(';', startIndex = i + 1)
            if (semi < 0 || semi - i > 12) {
                out.append(ch)
                i++
                continue
            }
            val entity = text.substring(i + 1, semi)
            val decoded = when (entity) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                else -> decodeNumericEntity(entity)
            }
            if (decoded != null) {
                out.append(decoded)
                i = semi + 1
            } else {
                out.append(ch)
                i++
            }
        }
        return out.toString()
    }

    private fun decodeNumericEntity(entity: String): String? {
        if (!entity.startsWith('#') || entity.length < 2) return null
        val isHex = entity[1] == 'x' || entity[1] == 'X'
        val digits = if (isHex) entity.substring(2) else entity.substring(1)
        val codePoint = digits.toIntOrNull(if (isHex) 16 else 10) ?: return null
        if (codePoint !in 0x0001..0x10FFFF) return null
        return if (codePoint <= 0xFFFF) {
            codePoint.toChar().toString()
        } else {
            val adjusted = codePoint - 0x10000
            val high = (0xD800 + (adjusted ushr 10)).toChar()
            val low = (0xDC00 + (adjusted and 0x3FF)).toChar()
            charArrayOf(high, low).concatToString()
        }
    }

    private const val HEX_DIGITS = "0123456789ABCDEF"
    private val LYRIC_CONTENT_START = Regex("""LyricContent\s*=\s*"""", RegexOption.IGNORE_CASE)
}
