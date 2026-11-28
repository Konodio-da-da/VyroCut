package com.vyrocut.app

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Fingerprint of the exact bytes written to or read back from the Movies provider. */
internal data class ExportFileFingerprint(
    val byteCount: Long,
    val sha256Hex: String,
)

/** Reads a stream incrementally so large exports never need to be loaded into memory. */
internal fun fingerprintExportStream(
    input: InputStream,
    cancellationToken: ExportCancellationToken? = null,
): ExportFileFingerprint = consumeExportStream(input, output = null, cancellationToken)

/** Copies a stream while calculating the fingerprint of the bytes actually read from it. */
internal fun copyAndFingerprintExport(
    input: InputStream,
    output: OutputStream,
    cancellationToken: ExportCancellationToken? = null,
): ExportFileFingerprint = consumeExportStream(input, output, cancellationToken)

private fun consumeExportStream(
    input: InputStream,
    output: OutputStream?,
    cancellationToken: ExportCancellationToken?,
): ExportFileFingerprint {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var byteCount = 0L

    while (true) {
        cancellationToken?.throwIfCancelled()
        val read = input.read(buffer)
        if (read < 0) break
        if (read == 0) {
            val singleByte = input.read()
            if (singleByte < 0) break
            check(byteCount < Long.MAX_VALUE) {
                "Export byte count exceeds the supported file size"
            }
            output?.write(singleByte)
            digest.update(singleByte.toByte())
            byteCount++
            continue
        }
        check(byteCount <= Long.MAX_VALUE - read.toLong()) {
            "Export byte count exceeds the supported file size"
        }
        output?.write(buffer, 0, read)
        digest.update(buffer, 0, read)
        byteCount += read.toLong()
    }
    cancellationToken?.throwIfCancelled()
    output?.flush()

    val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    return ExportFileFingerprint(byteCount, hash)
}

/** Empty files and malformed fingerprints can never be accepted as a published MP4. */
internal fun exportFileFingerprintsMatch(
    expected: ExportFileFingerprint,
    actual: ExportFileFingerprint,
): Boolean {
    if (expected.byteCount <= 0L || expected.byteCount != actual.byteCount) return false
    if (!expected.sha256Hex.matches(EXPORT_SHA256_PATTERN) ||
        !actual.sha256Hex.matches(EXPORT_SHA256_PATTERN)
    ) return false
    return MessageDigest.isEqual(
        expected.sha256Hex.toByteArray(StandardCharsets.US_ASCII),
        actual.sha256Hex.toByteArray(StandardCharsets.US_ASCII),
    )
}

private val EXPORT_SHA256_PATTERN = Regex("[0-9a-f]{64}")
