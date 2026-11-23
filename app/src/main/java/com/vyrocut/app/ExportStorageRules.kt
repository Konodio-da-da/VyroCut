package com.vyrocut.app

internal const val DEFAULT_AUTOMATIC_AUDIO_BITRATE_ESTIMATE_BPS = 128_000
internal const val EXPORT_ESTIMATE_CONTAINER_OVERHEAD_PERCENT = 5
internal const val EXPORT_TEMPORARY_STORAGE_RESERVE_BYTES = 16L * 1024L * 1024L
internal const val EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES = 16L * 1024L * 1024L
internal const val EXPORT_SILENCE_CACHE_STORAGE_RESERVE_BYTES = 16L * 1024L * 1024L
internal const val EXPORT_SAME_VOLUME_STORAGE_RESERVE_BYTES =
    EXPORT_TEMPORARY_STORAGE_RESERVE_BYTES + EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES

internal data class ExportFileSizeEstimate(
    val estimatedBytes: Long,
    val videoBitrateBps: Int,
    val audioBitrateBps: Int,
    val usesAutomaticVideoBitrate: Boolean,
    val usesAutomaticAudioBitrate: Boolean,
)

/**
 * Estimates a complete MP4 from the requested stream bitrates plus a small
 * container allowance. Auto uses explicit planning assumptions; this is not
 * a promise about the device encoder's eventual output size.
 */
internal fun estimateExportFileSize(
    durationMs: Long,
    requestedVideoBitrateBps: Int?,
    requestedAudioBitrateBps: Int?,
    automaticVideoBitrateBps: Int?,
    automaticAudioBitrateBps: Int = DEFAULT_AUTOMATIC_AUDIO_BITRATE_ESTIMATE_BPS,
): ExportFileSizeEstimate? {
    if (durationMs <= 0L) return null
    val usesAutomaticVideo = requestedVideoBitrateBps == null
    val usesAutomaticAudio = requestedAudioBitrateBps == null
    val videoBitrate = requestedVideoBitrateBps ?: automaticVideoBitrateBps ?: return null
    val audioBitrate = requestedAudioBitrateBps ?: automaticAudioBitrateBps
    if (videoBitrate <= 0 || audioBitrate <= 0) return null

    val combinedBitrate = videoBitrate.toLong() + audioBitrate.toLong()
    val encodedBits = checkedMultiply(durationMs, combinedBitrate)
    val encodedBytes = encodedBits?.let { ceilDivide(it, 8_000L) } ?: Long.MAX_VALUE
    val containerAllowance = ceilPercent(encodedBytes, EXPORT_ESTIMATE_CONTAINER_OVERHEAD_PERCENT)
    val estimatedBytes = saturatedAdd(encodedBytes, containerAllowance)

    return ExportFileSizeEstimate(
        estimatedBytes = estimatedBytes,
        videoBitrateBps = videoBitrate,
        audioBitrateBps = audioBitrate,
        usesAutomaticVideoBitrate = usesAutomaticVideo,
        usesAutomaticAudioBitrate = usesAutomaticAudio,
    )
}

internal data class ExportStorageAvailability(
    val temporaryAvailableBytes: Long?,
    val moviesAvailableBytes: Long?,
    val sameVolume: Boolean?,
    val silenceCacheAvailableBytes: Long? = null,
    val silenceCacheSharesTemporaryVolume: Boolean? = null,
)

internal enum class ExportStoragePreflightStatus {
    SUFFICIENT,
    INSUFFICIENT,
    UNKNOWN,
}

internal enum class ExportStorageScope(val label: String) {
    TEMPORARY_AND_MOVIES("the temporary MP4 and Movies copy"),
    TEMPORARY_EXPORT("the temporary MP4"),
    MOVIES_PUBLICATION("the Movies publication"),
    SILENCE_CACHE("the temporary silent-audio WAV cache"),
}

internal data class ExportStoragePreflightResult(
    val status: ExportStoragePreflightStatus,
    val scope: ExportStorageScope? = null,
    val requiredBytes: Long? = null,
    val availableBytes: Long? = null,
)

/**
 * Transformer first writes a private temporary MP4. Publication then creates
 * and verifies a second MediaStore copy before the temporary file is removed.
 * Same-volume checks therefore budget for both copies at once.
 */
internal fun evaluateExportStoragePreflight(
    estimate: ExportFileSizeEstimate,
    availability: ExportStorageAvailability,
    silenceCacheRequiredBytes: Long = 0L,
): ExportStoragePreflightResult {
    val temporaryAvailable = availability.temporaryAvailableBytes?.takeIf { it >= 0L }
    val moviesAvailable = availability.moviesAvailableBytes?.takeIf { it >= 0L }
    val silenceCacheAvailable = availability.silenceCacheAvailableBytes?.takeIf { it >= 0L }
    if (silenceCacheRequiredBytes < 0L) {
        return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
    }
    val needsSilenceCache = silenceCacheRequiredBytes > 0L
    val sameVolume = availability.sameVolume
        ?: return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)

    if (sameVolume) {
        var required = saturatedAdd(
            saturatedMultiply(estimate.estimatedBytes, 2L),
            EXPORT_SAME_VOLUME_STORAGE_RESERVE_BYTES,
        )
        var separateSilenceCacheRequired: Long? = null
        val unknownSilenceCacheVolume = if (!needsSilenceCache) {
            false
        } else {
            when (availability.silenceCacheSharesTemporaryVolume) {
                true -> {
                    required = saturatedAdd(required, silenceCacheRequiredBytes)
                    false
                }
                false -> {
                    separateSilenceCacheRequired = saturatedAdd(
                        silenceCacheRequiredBytes,
                        EXPORT_SILENCE_CACHE_STORAGE_RESERVE_BYTES,
                    )
                    false
                }
                null -> true
            }
        }

        val knownAvailable = listOfNotNull(temporaryAvailable, moviesAvailable).minOrNull()
        if (knownAvailable != null && knownAvailable < required) {
            return ExportStoragePreflightResult(
                status = ExportStoragePreflightStatus.INSUFFICIENT,
                scope = ExportStorageScope.TEMPORARY_AND_MOVIES,
                requiredBytes = required,
                availableBytes = knownAvailable,
            )
        }
        if (temporaryAvailable == null || moviesAvailable == null) {
            return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
        }
        if (separateSilenceCacheRequired != null) {
            if (silenceCacheAvailable != null && silenceCacheAvailable < separateSilenceCacheRequired) {
                return ExportStoragePreflightResult(
                    status = ExportStoragePreflightStatus.INSUFFICIENT,
                    scope = ExportStorageScope.SILENCE_CACHE,
                    requiredBytes = separateSilenceCacheRequired,
                    availableBytes = silenceCacheAvailable,
                )
            }
            if (silenceCacheAvailable == null) {
                return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
            }
        }
        if (unknownSilenceCacheVolume) {
            return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
        }
        return ExportStoragePreflightResult(
            status = ExportStoragePreflightStatus.SUFFICIENT,
            scope = ExportStorageScope.TEMPORARY_AND_MOVIES,
            requiredBytes = required,
            availableBytes = minOf(temporaryAvailable, moviesAvailable),
        )
    }

    var temporaryRequired = saturatedAdd(
        estimate.estimatedBytes,
        EXPORT_TEMPORARY_STORAGE_RESERVE_BYTES,
    )
    val moviesRequired = saturatedAdd(
        estimate.estimatedBytes,
        EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES,
    )
    var separateSilenceCacheRequired: Long? = null
    val unknownSilenceCacheVolume = if (!needsSilenceCache) {
        false
    } else {
        when (availability.silenceCacheSharesTemporaryVolume) {
            true -> {
                temporaryRequired = saturatedAdd(temporaryRequired, silenceCacheRequiredBytes)
                false
            }
            false -> {
                separateSilenceCacheRequired = saturatedAdd(
                    silenceCacheRequiredBytes,
                    EXPORT_SILENCE_CACHE_STORAGE_RESERVE_BYTES,
                )
                false
            }
            null -> true
        }
    }

    if (temporaryAvailable != null && temporaryAvailable < temporaryRequired) {
        return ExportStoragePreflightResult(
            status = ExportStoragePreflightStatus.INSUFFICIENT,
            scope = ExportStorageScope.TEMPORARY_EXPORT,
            requiredBytes = temporaryRequired,
            availableBytes = temporaryAvailable,
        )
    }
    if (moviesAvailable != null && moviesAvailable < moviesRequired) {
        return ExportStoragePreflightResult(
            status = ExportStoragePreflightStatus.INSUFFICIENT,
            scope = ExportStorageScope.MOVIES_PUBLICATION,
            requiredBytes = moviesRequired,
            availableBytes = moviesAvailable,
        )
    }
    if (separateSilenceCacheRequired != null) {
        if (silenceCacheAvailable != null && silenceCacheAvailable < separateSilenceCacheRequired) {
            return ExportStoragePreflightResult(
                status = ExportStoragePreflightStatus.INSUFFICIENT,
                scope = ExportStorageScope.SILENCE_CACHE,
                requiredBytes = separateSilenceCacheRequired,
                availableBytes = silenceCacheAvailable,
            )
        }
        if (silenceCacheAvailable == null) {
            return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
        }
    }
    if (temporaryAvailable == null || moviesAvailable == null || unknownSilenceCacheVolume) {
        return ExportStoragePreflightResult(ExportStoragePreflightStatus.UNKNOWN)
    }
    return ExportStoragePreflightResult(
        status = ExportStoragePreflightStatus.SUFFICIENT,
        scope = null,
        requiredBytes = null,
        availableBytes = null,
    )
}

internal fun describeExportStoragePreflight(result: ExportStoragePreflightResult): String =
    when (result.status) {
        ExportStoragePreflightStatus.SUFFICIENT ->
            "Available storage passed the export preflight."
        ExportStoragePreflightStatus.UNKNOWN ->
            "Free space could not be verified. Review available storage before exporting."
        ExportStoragePreflightStatus.INSUFFICIENT -> {
            val scope = result.scope?.label ?: "the export"
            val required = result.requiredBytes?.let(::formatBytesAsMiB) ?: "an unknown amount"
            val available = result.availableBytes?.let(::formatBytesAsMiB) ?: "an unknown amount"
            "Not enough free space for $scope: need about $required, but only $available is available."
        }
    }

internal fun formatExportSizeMiB(bytes: Long): String =
    "~${ceilDivide(bytes.coerceAtLeast(0L), MEBIBYTE_BYTES)} MiB"

private const val MEBIBYTE_BYTES = 1_048_576L

private fun formatBytesAsMiB(bytes: Long): String =
    "${ceilDivide(bytes.coerceAtLeast(0L), MEBIBYTE_BYTES)} MiB"

internal fun isPathWithinStorageRoot(path: String, root: String): Boolean {
    // Android filesystem paths use forward slashes; normalize trailing separators only.
    val normalizedPath = path.trimEnd('/')
    val normalizedRoot = root.trimEnd('/').ifEmpty { "/" }
    if (normalizedPath == normalizedRoot) return true
    val prefix = if (normalizedRoot.endsWith('/')) normalizedRoot else "$normalizedRoot/"
    return normalizedPath.startsWith(prefix)
}

private fun checkedMultiply(first: Long, second: Long): Long? {
    if (first < 0L || second < 0L) return null
    if (first == 0L || second == 0L) return 0L
    if (first > Long.MAX_VALUE / second) return null
    return first * second
}

private fun saturatedMultiply(first: Long, second: Long): Long =
    checkedMultiply(first, second) ?: Long.MAX_VALUE

private fun saturatedAdd(first: Long, second: Long): Long =
    if (first > Long.MAX_VALUE - second) Long.MAX_VALUE else first + second

private fun ceilDivide(value: Long, divisor: Long): Long {
    if (value <= 0L) return 0L
    val quotient = value / divisor
    return if (value % divisor == 0L) quotient else quotient + 1L
}

private fun ceilPercent(value: Long, percent: Int): Long {
    val whole = (value / 100L) * percent.toLong()
    val remainder = ceilDivide((value % 100L) * percent.toLong(), 100L)
    return saturatedAdd(whole, remainder)
}
