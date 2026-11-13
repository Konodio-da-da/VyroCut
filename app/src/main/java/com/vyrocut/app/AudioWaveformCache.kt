package com.vyrocut.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32

internal const val MAX_AUDIO_WAVEFORM_BUCKETS = 512

internal const val AUDIO_WAVEFORM_TEMP_FILE_PREFIX = "waveform-"
internal const val AUDIO_WAVEFORM_TEMP_FILE_SUFFIX = ".tmp"
internal const val AUDIO_WAVEFORM_TEMP_FILE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L

/**
 * Identifies abandoned cache-write temp files without touching active writers,
 * recent files, completed cache entries, or files with future timestamps.
 */
internal fun isStaleAudioWaveformTempFile(
    fileName: String,
    lastModifiedMs: Long,
    nowMs: Long,
    maxAgeMs: Long = AUDIO_WAVEFORM_TEMP_FILE_MAX_AGE_MS,
): Boolean {
    if (!fileName.startsWith(AUDIO_WAVEFORM_TEMP_FILE_PREFIX) ||
        !fileName.endsWith(AUDIO_WAVEFORM_TEMP_FILE_SUFFIX) ||
        lastModifiedMs < 0L ||
        nowMs < 0L ||
        nowMs < lastModifiedMs ||
        maxAgeMs <= 0L
    ) return false

    return nowMs - lastModifiedMs >= maxAgeMs
}

private const val AUDIO_WAVEFORM_CACHE_MAGIC = 0x56594357
private const val AUDIO_WAVEFORM_CACHE_VERSION = 2
private const val AUDIO_WAVEFORM_CACHE_HEADER_BYTES = 16

/**
 * Encodes a disposable waveform cache entry. CRC32 protects against silent
 * corruption where a damaged float would otherwise still look like a valid peak.
 */
internal fun encodeAudioWaveformCache(values: List<Float>): ByteArray {
    require(values.size in 1..MAX_AUDIO_WAVEFORM_BUCKETS)
    require(values.all { it.isFinite() && it in 0f..1f })

    val payloadBuffer = ByteArrayOutputStream(values.size * 4)
    DataOutputStream(payloadBuffer).use { payload ->
        values.forEach(payload::writeFloat)
    }
    val samplePayload = payloadBuffer.toByteArray()
    val checksum = CRC32().apply { update(samplePayload) }.value

    val cacheBuffer = ByteArrayOutputStream(AUDIO_WAVEFORM_CACHE_HEADER_BYTES + samplePayload.size)
    DataOutputStream(cacheBuffer).use { output ->
        output.writeInt(AUDIO_WAVEFORM_CACHE_MAGIC)
        output.writeInt(AUDIO_WAVEFORM_CACHE_VERSION)
        output.writeInt(values.size)
        output.writeInt(checksum.toInt())
        output.write(samplePayload)
    }
    return cacheBuffer.toByteArray()
}

/** Returns null for an unsupported version, wrong resolution, corruption or truncation. */
internal fun decodeAudioWaveformCache(
    encoded: ByteArray,
    expectedBucketCount: Int,
): List<Float>? {
    if (expectedBucketCount !in 1..MAX_AUDIO_WAVEFORM_BUCKETS ||
        encoded.size != AUDIO_WAVEFORM_CACHE_HEADER_BYTES + expectedBucketCount * 4
    ) return null

    return runCatching {
        DataInputStream(ByteArrayInputStream(encoded)).use { input ->
            require(input.readInt() == AUDIO_WAVEFORM_CACHE_MAGIC)
            require(input.readInt() == AUDIO_WAVEFORM_CACHE_VERSION)
            require(input.readInt() == expectedBucketCount)
            val expectedChecksum = input.readInt().toLong() and 0xffff_ffffL
            val samplePayload = ByteArray(expectedBucketCount * 4)
            input.readFully(samplePayload)
            require(input.read() == -1)
            require(CRC32().apply { update(samplePayload) }.value == expectedChecksum)

            DataInputStream(ByteArrayInputStream(samplePayload)).use { samples ->
                List(expectedBucketCount) {
                    samples.readFloat().also { value ->
                        require(value.isFinite() && value in 0f..1f)
                    }
                }
            }
        }
    }.getOrNull()
}
