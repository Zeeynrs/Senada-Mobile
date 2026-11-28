package senada.lyrics.sources

/**
 * Pure-Kotlin RFC 1950 (zlib) + RFC 1951 (DEFLATE) inflater for commonMain.
 *
 * Used by KuGou KRC and QQ Music QRC lyric decoders so both Android and iOS
 * share identical decompression logic without platform-specific bindings.
 */
internal object ZlibInflater {
    private const val MAX_BITS = 15
    private const val DEFAULT_MAX_OUTPUT_BYTES = 4 * 1024 * 1024

    private val CODE_LENGTH_ORDER = intArrayOf(
        16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15,
    )

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10,
        11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115,
        131, 163, 195, 227, 258,
    )

    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4,
        5, 5, 5, 5, 0,
    )

    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13,
        17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073,
        4097, 6145, 8193, 12289, 16385, 24577,
    )

    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2,
        3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10,
        11, 11, 12, 12, 13, 13,
    )

    private val FIXED_LIT_LEN_TABLE: HuffmanTable by lazy {
        val lengths = IntArray(288)
        for (i in 0..143) lengths[i] = 8
        for (i in 144..255) lengths[i] = 9
        for (i in 256..279) lengths[i] = 7
        for (i in 280..287) lengths[i] = 8
        HuffmanTable(lengths, 0, 288)
    }

    private val FIXED_DIST_TABLE: HuffmanTable by lazy {
        val lengths = IntArray(32) { 5 }
        HuffmanTable(lengths, 0, 32)
    }

    /**
     * Inflates a zlib-wrapped (RFC 1950) byte array.
     *
     * Any trailing bytes after the 4-byte Adler-32 trailer (such as 3DES 8-byte block
     * padding in QQ Music QRC payloads) are ignored.
     */
    fun inflate(data: ByteArray, maxOutputBytes: Int = DEFAULT_MAX_OUTPUT_BYTES): ByteArray {
        if (data.size < 6) {
            throw IllegalArgumentException("Zlib stream too short: ${data.size} bytes")
        }
        val cmf = data[0].toInt() and 0xFF
        val flg = data[1].toInt() and 0xFF
        val cm = cmf and 0x0F
        val cinfo = cmf ushr 4
        if (cm != 8 || cinfo > 7 || ((cmf shl 8) or flg) % 31 != 0) {
            throw IllegalArgumentException("Invalid zlib header")
        }
        if ((flg and 0x20) != 0) {
            throw IllegalArgumentException("Zlib preset dictionaries are not supported")
        }

        val reader = BitReader(data, 2)
        val out = ByteBuffer(initialCapacity = (data.size * 2).coerceIn(256, 65_536), maxBytes = maxOutputBytes)

        var finalBlock = false
        while (!finalBlock) {
            finalBlock = reader.readBits(1) != 0
            when (val blockType = reader.readBits(2)) {
                0 -> readStoredBlock(reader, out)
                1 -> decodeHuffmanBlock(reader, FIXED_LIT_LEN_TABLE, FIXED_DIST_TABLE, out)
                2 -> {
                    val (litLenTable, distTable) = readDynamicTables(reader)
                    decodeHuffmanBlock(reader, litLenTable, distTable, out)
                }
                else -> throw IllegalArgumentException("Invalid deflate block type: $blockType")
            }
        }

        reader.alignToByte()
        if (reader.remainingBytes() < 4) {
            throw IllegalArgumentException("Truncated zlib Adler-32 trailer")
        }
        val expectedAdler = (reader.readAlignedByte().toUInt() shl 24) or
            (reader.readAlignedByte().toUInt() shl 16) or
            (reader.readAlignedByte().toUInt() shl 8) or
            reader.readAlignedByte().toUInt()
        val actualAdler = adler32(out.buffer, out.size)
        if (actualAdler != expectedAdler) {
            throw IllegalArgumentException("Zlib Adler-32 checksum mismatch")
        }
        return out.toByteArray()
    }

    private fun readStoredBlock(reader: BitReader, out: ByteBuffer) {
        reader.alignToByte()
        val len = reader.readAlignedByte() or (reader.readAlignedByte() shl 8)
        val nlen = reader.readAlignedByte() or (reader.readAlignedByte() shl 8)
        if ((len xor 0xFFFF) != nlen) {
            throw IllegalArgumentException("Corrupt stored deflate block length")
        }
        repeat(len) {
            out.append(reader.readAlignedByte().toByte())
        }
    }

    private fun readDynamicTables(reader: BitReader): Pair<HuffmanTable, HuffmanTable> {
        val hlit = reader.readBits(5) + 257
        val hdist = reader.readBits(5) + 1
        val hclen = reader.readBits(4) + 4
        if (hlit > 286 || hdist > 30) {
            throw IllegalArgumentException("Invalid dynamic Huffman table sizes: hlit=$hlit, hdist=$hdist")
        }

        val codeLengths = IntArray(19)
        for (i in 0 until hclen) {
            codeLengths[CODE_LENGTH_ORDER[i]] = reader.readBits(3)
        }
        val codeLengthTable = HuffmanTable(codeLengths, 0, 19)

        val totalCodes = hlit + hdist
        val allLengths = IntArray(totalCodes)
        var i = 0
        while (i < totalCodes) {
            when (val sym = codeLengthTable.decode(reader)) {
                in 0..15 -> allLengths[i++] = sym
                16 -> {
                    if (i == 0) throw IllegalArgumentException("Invalid repeat code 16 at start")
                    val repeat = reader.readBits(2) + 3
                    val prev = allLengths[i - 1]
                    if (i + repeat > totalCodes) throw IllegalArgumentException("Code length overflow")
                    repeat(repeat) { allLengths[i++] = prev }
                }
                17 -> {
                    val repeat = reader.readBits(3) + 3
                    if (i + repeat > totalCodes) throw IllegalArgumentException("Code length overflow")
                    repeat(repeat) { allLengths[i++] = 0 }
                }
                18 -> {
                    val repeat = reader.readBits(7) + 11
                    if (i + repeat > totalCodes) throw IllegalArgumentException("Code length overflow")
                    repeat(repeat) { allLengths[i++] = 0 }
                }
                else -> throw IllegalArgumentException("Invalid code length symbol: $sym")
            }
        }
        if (allLengths[256] == 0) {
            throw IllegalArgumentException("Missing end-of-block symbol in Huffman table")
        }
        return HuffmanTable(allLengths, 0, hlit) to HuffmanTable(allLengths, hlit, hdist)
    }

    private fun decodeHuffmanBlock(
        reader: BitReader,
        litLenTable: HuffmanTable,
        distTable: HuffmanTable,
        out: ByteBuffer,
    ) {
        while (true) {
            val sym = litLenTable.decode(reader)
            when {
                sym < 256 -> out.append(sym.toByte())
                sym == 256 -> return
                sym in 257..285 -> {
                    val lenIndex = sym - 257
                    val length = LENGTH_BASE[lenIndex] + reader.readBits(LENGTH_EXTRA[lenIndex])
                    val distSym = distTable.decode(reader)
                    if (distSym !in 0..29) {
                        throw IllegalArgumentException("Invalid deflate distance symbol: $distSym")
                    }
                    val distance = DIST_BASE[distSym] + reader.readBits(DIST_EXTRA[distSym])
                    out.copyFromHistory(distance, length)
                }
                else -> throw IllegalArgumentException("Invalid deflate literal/length symbol: $sym")
            }
        }
    }

    internal fun adler32(bytes: ByteArray, length: Int = bytes.size): UInt {
        var s1 = 1u
        var s2 = 0u
        for (i in 0 until length) {
            s1 = (s1 + bytes[i].toUByte().toUInt()) % 65521u
            s2 = (s2 + s1) % 65521u
        }
        return (s2 shl 16) or s1
    }

    private class BitReader(private val data: ByteArray, startOffset: Int) {
        private var bytePos = startOffset
        private var bitBuf = 0
        private var bitCount = 0

        fun readBit(): Int {
            if (bitCount == 0) {
                if (bytePos >= data.size) throw IllegalArgumentException("Unexpected end of deflate stream")
                bitBuf = data[bytePos++].toInt() and 0xFF
                bitCount = 8
            }
            val bit = bitBuf and 1
            bitBuf = bitBuf ushr 1
            bitCount--
            return bit
        }

        fun readBits(count: Int): Int {
            if (count == 0) return 0
            while (bitCount < count) {
                if (bytePos >= data.size) throw IllegalArgumentException("Unexpected end of deflate stream")
                bitBuf = bitBuf or ((data[bytePos++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            val value = bitBuf and ((1 shl count) - 1)
            bitBuf = bitBuf ushr count
            bitCount -= count
            return value
        }

        fun alignToByte() {
            bitBuf = 0
            bitCount = 0
        }

        fun remainingBytes(): Int = data.size - bytePos

        fun readAlignedByte(): Int {
            if (bytePos >= data.size) throw IllegalArgumentException("Unexpected end of zlib stream")
            return data[bytePos++].toInt() and 0xFF
        }
    }

    private class HuffmanTable(lengths: IntArray, offset: Int, count: Int) {
        private val counts = IntArray(MAX_BITS + 1)
        private val symbols: IntArray
        private val maxBits: Int

        init {
            var maxLen = 0
            for (i in 0 until count) {
                val len = lengths[offset + i]
                if (len < 0 || len > MAX_BITS) {
                    throw IllegalArgumentException("Invalid Huffman code length: $len")
                }
                counts[len]++
                if (len > maxLen) maxLen = len
            }
            maxBits = maxLen
            if (maxLen > 0) {
                var left = 1
                for (len in 1..MAX_BITS) {
                    left = (left shl 1) - counts[len]
                    if (left < 0) throw IllegalArgumentException("Oversubscribed Huffman tree")
                }
            }
            val offsets = IntArray(MAX_BITS + 1)
            var sum = 0
            for (len in 1..MAX_BITS) {
                offsets[len] = sum
                sum += counts[len]
            }
            symbols = IntArray(sum)
            for (i in 0 until count) {
                val len = lengths[offset + i]
                if (len != 0) {
                    symbols[offsets[len]++] = i
                }
            }
        }

        fun decode(reader: BitReader): Int {
            if (maxBits == 0) throw IllegalArgumentException("Empty Huffman table")
            var code = 0
            var first = 0
            var index = 0
            for (len in 1..maxBits) {
                code = code or reader.readBit()
                val c = counts[len]
                if (code - c < first) {
                    return symbols[index + (code - first)]
                }
                index += c
                first = (first + c) shl 1
                code = code shl 1
            }
            throw IllegalArgumentException("Invalid Huffman code in deflate stream")
        }
    }

    private class ByteBuffer(initialCapacity: Int, private val maxBytes: Int) {
        var buffer = ByteArray(initialCapacity)
            private set
        var size = 0
            private set

        fun append(b: Byte) {
            ensureCapacity(size + 1)
            buffer[size++] = b
        }

        fun copyFromHistory(distance: Int, length: Int) {
            if (distance <= 0 || distance > size) {
                throw IllegalArgumentException("Invalid deflate back-reference distance $distance at size $size")
            }
            ensureCapacity(size + length)
            var src = size - distance
            repeat(length) {
                buffer[size++] = buffer[src++]
            }
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun ensureCapacity(required: Int) {
            if (required > maxBytes) {
                throw IllegalArgumentException("Inflated payload exceeds limit ($maxBytes bytes)")
            }
            if (required <= buffer.size) return
            var newSize = buffer.size
            while (newSize < required) {
                newSize = minOf(maxBytes, maxOf(newSize * 2, required))
            }
            buffer = buffer.copyOf(newSize)
        }
    }
}
