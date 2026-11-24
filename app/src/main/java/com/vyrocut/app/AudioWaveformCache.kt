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
 * Stable, best-effort source revision for waveform cache invalidation.
 * Unknown/negative provider metadata is omitted rather than treated as a
 * meaningful revision. Null means the provider exposed no usable metadata.
 */
internal fun mediaSourceRevision(
    sizeBytes: Long?,
    lastModifiedMs: Long?,
): String? {
    val size = sizeBytes?.takeIf { it >= 0L }
    val modified = lastModifiedMs?.takeIf { it >= 0L }
    if (size == null && modified == null) return null
    return "size=${size ?: "?"};modified=${modified ?: "?"}"
}

/** Backward-compatible name retained for waveform callers and tests. */
internal fun audioWaveformSourceRevision(
    sizeBytes: Long?,
    lastModifiedMs: Long?,
): String? = mediaSourceRevision(sizeBytes, lastModifiedMs)


/**
 * Waveform UI entries are keyed by source URI and trim only. The provider
 * revision is tracked separately so the visible lookup stays consistent while
 * the disposable disk cache can still invalidate replaced content URIs.
 */
internal fun audioWaveformMemoryCacheKey(clip: AudioTrackClip): String =
    audioWaveformCacheKey(clip.uri, clip.sourceStartMs, clip.sourceEndMs)

internal fun audioWaveformSourceRevisionKey(sourceRevision: String?): String =
    sourceRevision?.takeIf { it.isNotBlank() } ?: "unversioned"

internal enum class AudioWaveformLoadState {
    LOADING,
    READY,
    UNAVAILABLE,
}

internal fun shouldDecodeAudioWaveform(
    hasCachedWaveform: Boolean,
    cachedSourceRevisionKey: String?,
    currentSourceRevisionKey: String,
    loadState: AudioWaveformLoadState?,
): Boolean =
    !hasCachedWaveform ||
        cachedSourceRevisionKey != currentSourceRevisionKey ||
        loadState != AudioWaveformLoadState.READY

internal fun audioWaveformStatusLabel(
    loadState: AudioWaveformLoadState?,
    amplitudes: List<Float>?,
): String? = when (loadState) {
    AudioWaveformLoadState.LOADING -> "Loading waveform…"
    AudioWaveformLoadState.UNAVAILABLE -> "Waveform unavailable"
    AudioWaveformLoadState.READY -> if (
        !amplitudes.isNullOrEmpty() && amplitudes.all { it <= 0.000001f }
    ) "Silent audio" else null
    null -> null
}

/** A disposable cache entry ordered by its most recent successful use. */
internal data class AudioWaveformCacheEntry(
    val fileName: String,
    val lastAccessedMs: Long,
)

/**
 * Returns the least-recently-used filenames needed to bring the cache within
 * [maxFiles]. Filename ordering makes equal-timestamp eviction deterministic.
 */
internal fun audioWaveformCacheFilesToEvict(
    entries: List<AudioWaveformCacheEntry>,
    maxFiles: Int,
): List<String> {
    require(maxFiles >= 0)
    val excessCount = (entries.size - maxFiles).coerceAtLeast(0)
    if (excessCount == 0) return emptyList()

    return entries
        .sortedWith(
            compareBy<AudioWaveformCacheEntry> { it.lastAccessedMs }
                .thenBy { it.fileName },
        )
        .take(excessCount)
        .map { it.fileName }
}

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
