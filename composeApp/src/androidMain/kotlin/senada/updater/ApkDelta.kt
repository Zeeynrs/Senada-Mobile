package senada.updater

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipFile

internal class ApkDeltaException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal data class ApkDeltaSummary(
    val baseSha256: String,
    val targetSha256: String,
    val patchSha256: String,
    val baseSize: Long,
    val targetSize: Long,
    val patchSize: Long,
) {
    val savedPercent: Int
        get() = if (targetSize <= 0L) 0 else (((targetSize - patchSize) * 100L) / targetSize).toInt().coerceIn(0, 99)
}

/**
 * Compact, streaming copy-insert binary delta format (`SNDL` v1) designed to reconstruct a
 * bit-for-bit identical signed APK from the installed base APK without loading whole APKs in RAM.
 *
 * Layout:
 * - Bytes 0..3   : ASCII magic `"SNDL"`
 * - Bytes 4..7   : big-endian Int format version (`1`)
 * - Bytes 8..39  : 32-byte raw SHA-256 of base APK
 * - Bytes 40..71 : 32-byte raw SHA-256 of target APK
 * - Bytes 72..79 : big-endian Long target APK size in bytes
 * - Bytes 80..EOF: GZIP-compressed instruction stream:
 *     - `0x00` : end of stream
 *     - `0x01` + varint(baseOffset) + varint(length) : copy bytes from base APK
 *     - `0x02` + varint(length) + raw literal bytes  : insert literal bytes
 */
internal object ApkDelta {
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte())
    private const val FORMAT_VERSION = 1
    private const val OP_EOF = 0
    private const val OP_COPY = 1
    private const val OP_INSERT = 2
    private const val MAX_TARGET_BYTES = 512L * 1024L * 1024L
    private const val IO_BUFFER_BYTES = 64 * 1024
    private const val BLOCK_SIZE = 1024
    private const val MIN_MATCH_BYTES = 32

    fun sha256Hex(file: File): String = sha256Bytes(file).toHex()

    fun sha256Bytes(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(IO_BUFFER_BYTES)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    /**
     * Reconstructs [outputFile] from [baseFile] and [patchFile], verifying both base and target SHA-256 digests.
     * Deletes [outputFile] if any check fails.
     */
    fun apply(baseFile: File, patchFile: File, outputFile: File): String {
        if (!baseFile.isFile || !baseFile.canRead()) {
            throw ApkDeltaException("APK dasar yang terpasang tidak dapat dibaca.")
        }
        if (!patchFile.isFile || !patchFile.canRead()) {
            throw ApkDeltaException("File patch delta tidak ditemukan.")
        }
        val baseLength = baseFile.length()
        val actualBaseHash = sha256Bytes(baseFile)
        var completed = false

        try {
            FileInputStream(patchFile).use { rawIn ->
                val bufferedIn = BufferedInputStream(rawIn, IO_BUFFER_BYTES)
                val header = DataInputStream(bufferedIn)
                val magic = ByteArray(4)
                header.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    throw ApkDeltaException("Format patch delta tidak dikenali.")
                }
                val version = header.readInt()
                if (version != FORMAT_VERSION) {
                    throw ApkDeltaException("Versi format patch delta ($version) tidak didukung.")
                }
                val expectedBaseHash = ByteArray(32).also(header::readFully)
                val expectedTargetHash = ByteArray(32).also(header::readFully)
                val expectedTargetSize = header.readLong()
                if (expectedTargetSize <= 0L || expectedTargetSize > MAX_TARGET_BYTES) {
                    throw ApkDeltaException("Ukuran target patch delta di luar batas aman.")
                }
                if (!actualBaseHash.contentEquals(expectedBaseHash)) {
                    throw ApkDeltaException("APK terpasang tidak cocok dengan paket dasar delta.")
                }

                outputFile.parentFile?.mkdirs()
                val outDigest = MessageDigest.getInstance("SHA-256")
                var totalWritten = 0L
                val buffer = ByteArray(IO_BUFFER_BYTES)

                RandomAccessFile(baseFile, "r").use { baseRaf ->
                    BufferedOutputStream(FileOutputStream(outputFile), IO_BUFFER_BYTES).use { out ->
                        GZIPInputStream(bufferedIn, IO_BUFFER_BYTES).use { ops ->
                            var sawEof = false
                            while (true) {
                                val op = ops.read()
                                if (op == -1) break
                                when (op) {
                                    OP_EOF -> {
                                        sawEof = true
                                        // Drain to stream EOF so GZIPInputStream verifies the GZIP CRC32/ISIZE trailer.
                                        if (ops.read() != -1) {
                                            throw ApkDeltaException("Data tambahan tidak sah setelah akhir patch delta.")
                                        }
                                        break
                                    }
                                    OP_COPY -> {
                                        val offset = readVarLong(ops)
                                        val length = readVarLong(ops)
                                        if (offset < 0L || length <= 0L || offset > baseLength || length > baseLength - offset) {
                                            throw ApkDeltaException("Rentang salinan patch delta tidak valid.")
                                        }
                                        if (length > expectedTargetSize - totalWritten) {
                                            throw ApkDeltaException("Patch delta melebihi ukuran APK target.")
                                        }
                                        baseRaf.seek(offset)
                                        var remaining = length
                                        while (remaining > 0L) {
                                            val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                                            baseRaf.readFully(buffer, 0, chunk)
                                            out.write(buffer, 0, chunk)
                                            outDigest.update(buffer, 0, chunk)
                                            remaining -= chunk
                                            totalWritten += chunk
                                        }
                                    }
                                    OP_INSERT -> {
                                        val length = readVarLong(ops)
                                        if (length <= 0L || length > expectedTargetSize - totalWritten) {
                                            throw ApkDeltaException("Data sisipan patch delta tidak valid.")
                                        }
                                        var remaining = length
                                        while (remaining > 0L) {
                                            val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                                            val read = ops.read(buffer, 0, chunk)
                                            if (read < 0) throw EOFException("Patch delta terpotong.")
                                            out.write(buffer, 0, read)
                                            outDigest.update(buffer, 0, read)
                                            remaining -= read
                                            totalWritten += read
                                        }
                                    }
                                    else -> throw ApkDeltaException("Instruksi patch delta tidak dikenali ($op).")
                                }
                            }
                            if (!sawEof) {
                                throw ApkDeltaException("Patch delta tidak memiliki penanda akhir yang sah.")
                            }
                        }
                    }
                }

                if (totalWritten != expectedTargetSize) {
                    throw ApkDeltaException("Ukuran hasil rekonstruksi delta tidak sesuai target.")
                }
                val actualTargetHash = outDigest.digest()
                if (!actualTargetHash.contentEquals(expectedTargetHash)) {
                    throw ApkDeltaException("Checksum SHA-256 APK hasil delta tidak cocok.")
                }
                completed = true
                return actualTargetHash.toHex()
            }
        } catch (e: ApkDeltaException) {
            throw e
        } catch (e: Exception) {
            throw ApkDeltaException("Gagal menerapkan patch delta APK.", e)
        } finally {
            if (!completed) {
                runCatching { outputFile.delete() }
            }
        }
    }

    /**
     * Generates an `SNDL` v1 patch from [baseFile] to [targetFile] using ZIP-entry-aligned and
     * block-indexed exact byte matching so unchanged compressed APK entries copy in one operation.
     */
    fun create(baseFile: File, targetFile: File, patchFile: File): ApkDeltaSummary {
        val baseBytes = baseFile.readBytes()
        val targetBytes = targetFile.readBytes()
        val baseHash = MessageDigest.getInstance("SHA-256").digest(baseBytes)
        val targetHash = MessageDigest.getInstance("SHA-256").digest(targetBytes)

        val zipEntryOffsets = indexZipLocalEntries(baseBytes)
        val blockIndex = indexBlocks(baseBytes)

        patchFile.parentFile?.mkdirs()
        FileOutputStream(patchFile).use { rawOut ->
            val buffered = BufferedOutputStream(rawOut, IO_BUFFER_BYTES)
            val header = DataOutputStream(buffered)
            header.write(MAGIC)
            header.writeInt(FORMAT_VERSION)
            header.write(baseHash)
            header.write(targetHash)
            header.writeLong(targetBytes.size.toLong())
            header.flush()

            GZIPOutputStream(buffered, IO_BUFFER_BYTES).use { ops ->
                val pendingLiterals = ByteArrayOutputStream()
                fun flushLiterals() {
                    if (pendingLiterals.size() > 0) {
                        val bytes = pendingLiterals.toByteArray()
                        ops.write(OP_INSERT)
                        writeVarLong(ops, bytes.size.toLong())
                        ops.write(bytes)
                        pendingLiterals.reset()
                    }
                }

                var pos = 0
                val targetLen = targetBytes.size
                while (pos < targetLen) {
                    val match = findBestMatch(baseBytes, targetBytes, pos, zipEntryOffsets, blockIndex)
                    if (match != null && match.length >= MIN_MATCH_BYTES) {
                        flushLiterals()
                        ops.write(OP_COPY)
                        writeVarLong(ops, match.baseOffset.toLong())
                        writeVarLong(ops, match.length.toLong())
                        pos += match.length
                    } else {
                        pendingLiterals.write(targetBytes[pos].toInt() and 0xFF)
                        pos++
                    }
                }
                flushLiterals()
                ops.write(OP_EOF)
            }
        }

        val patchHash = sha256Hex(patchFile)
        return ApkDeltaSummary(
            baseSha256 = baseHash.toHex(),
            targetSha256 = targetHash.toHex(),
            patchSha256 = patchHash,
            baseSize = baseBytes.size.toLong(),
            targetSize = targetBytes.size.toLong(),
            patchSize = patchFile.length(),
        )
    }

    private data class Match(val baseOffset: Int, val length: Int)

    private fun findBestMatch(
        base: ByteArray,
        target: ByteArray,
        pos: Int,
        zipEntries: Map<Long, List<Int>>,
        blockIndex: Map<Long, List<Int>>,
    ): Match? {
        if (pos + MIN_MATCH_BYTES > target.size) return null

        // Fast path: ZIP Local File Header (`PK\x03\x04`) exact entry copy.
        if (
            pos + 30 <= target.size &&
            target[pos] == 0x50.toByte() &&
            target[pos + 1] == 0x4B.toByte() &&
            target[pos + 2] == 0x03.toByte() &&
            target[pos + 3] == 0x04.toByte()
        ) {
            val sig = fnv1a64(target, pos, minOf(64, target.size - pos))
            zipEntries[sig]?.let { candidates ->
                var best: Match? = null
                for (baseStart in candidates) {
                    val len = commonPrefixLength(base, baseStart, target, pos)
                    if (len >= MIN_MATCH_BYTES && (best == null || len > best.length)) {
                        best = Match(baseStart, len)
                    }
                }
                if (best != null) return best
            }
        }

        if (pos + BLOCK_SIZE <= target.size) {
            val sig = fnv1a64(target, pos, BLOCK_SIZE)
            blockIndex[sig]?.let { candidates ->
                var best: Match? = null
                for (baseStart in candidates) {
                    val len = commonPrefixLength(base, baseStart, target, pos)
                    if (len >= BLOCK_SIZE && (best == null || len > best.length)) {
                        best = Match(baseStart, len)
                    }
                }
                if (best != null) return best
            }
        }
        return null
    }

    private fun indexZipLocalEntries(base: ByteArray): Map<Long, List<Int>> {
        val map = HashMap<Long, MutableList<Int>>()
        var i = 0
        val limit = base.size - 30
        while (i <= limit) {
            if (
                base[i] == 0x50.toByte() &&
                base[i + 1] == 0x4B.toByte() &&
                base[i + 2] == 0x03.toByte() &&
                base[i + 3] == 0x04.toByte()
            ) {
                val sig = fnv1a64(base, i, minOf(64, base.size - i))
                val list = map.getOrPut(sig) { ArrayList(2) }
                if (list.size < 8) list.add(i)
                i += 30
            } else {
                i++
            }
        }
        return map
    }

    private fun indexBlocks(base: ByteArray): Map<Long, List<Int>> {
        val map = HashMap<Long, MutableList<Int>>(base.size / BLOCK_SIZE + 1)
        var offset = 0
        while (offset + BLOCK_SIZE <= base.size) {
            val sig = fnv1a64(base, offset, BLOCK_SIZE)
            val list = map.getOrPut(sig) { ArrayList(2) }
            if (list.size < 4) list.add(offset)
            offset += BLOCK_SIZE
        }
        return map
    }

    private fun commonPrefixLength(base: ByteArray, baseOffset: Int, target: ByteArray, targetOffset: Int): Int {
        val max = minOf(base.size - baseOffset, target.size - targetOffset)
        var i = 0
        while (i < max && base[baseOffset + i] == target[targetOffset + i]) {
            i++
        }
        return i
    }

    private fun fnv1a64(bytes: ByteArray, offset: Int, length: Int): Long {
        var hash = -3750763034362895579L // 0xcbf29ce484222325
        for (i in offset until offset + length) {
            hash = hash xor (bytes[i].toLong() and 0xFFL)
            hash *= 1099511628211L
        }
        return hash
    }

    private fun writeVarLong(out: OutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v.toInt() and 0x7F) or 0x80))
            v = v ushr 7
        }
    }

    private fun readVarLong(input: InputStream): Long {
        var result = 0L
        var shift = 0
        while (shift < 64) {
            val b = input.read()
            if (b < 0) throw EOFException("Unexpected EOF reading varint")
            result = result or ((b.toLong() and 0x7FL) shl shift)
            if ((b and 0x80) == 0) return result
            shift += 7
        }
        throw ApkDeltaException("Varint korup pada patch delta.")
    }
}

internal fun ByteArray.toHex(): String = buildString(size * 2) {
    for (byte in this@toHex) {
        val value = byte.toInt() and 0xFF
        append(HEX_CHARS[value ushr 4])
        append(HEX_CHARS[value and 0x0F])
    }
}

private val HEX_CHARS = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')
