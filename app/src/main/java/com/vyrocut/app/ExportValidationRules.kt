package com.vyrocut.app

import java.util.Locale

internal data class ExportTrackSummary(
    val videoSampleCount: Long,
    val audioSampleCount: Long,
    val videoDurationMs: Long,
    val videoWidthPx: Int = 0,
    val videoHeightPx: Int = 0,
    val videoMimeType: String? = null,
    val audioMimeType: String? = null,
)

/**
 * Returns a user-readable validation failure, or null when the demuxed export
 * contains the expected playable tracks and is close to the requested timeline length.
 */
internal fun validateExportTrackSummary(
    summary: ExportTrackSummary,
    expectedDurationMs: Long,
    sourceHasAudio: Boolean,
    expectedCanvasSize: ExportCanvasSize? = null,
): String? {
    if (expectedDurationMs <= 0L) return "The project has no playable duration"
    if (summary.videoSampleCount <= 0L) return "The exported MP4 contains no readable video samples"
    if (summary.videoDurationMs <= 0L) return "The exported video has no measurable duration"
    if (summary.videoWidthPx <= 0 || summary.videoHeightPx <= 0) {
        return "The exported MP4 has invalid video dimensions"
    }
    if (expectedCanvasSize != null &&
        (summary.videoWidthPx != expectedCanvasSize.widthPx || summary.videoHeightPx != expectedCanvasSize.heightPx)
    ) {
        return "Export canvas ${summary.videoWidthPx}x${summary.videoHeightPx} does not match selected ${expectedCanvasSize.widthPx}x${expectedCanvasSize.heightPx} canvas"
    }

    // Allow codec/frame-boundary rounding without accepting a materially truncated render.
    val durationToleranceMs = maxOf(250L, expectedDurationMs / 100L)
    val durationDifferenceMs = if (summary.videoDurationMs >= expectedDurationMs) {
        summary.videoDurationMs - expectedDurationMs
    } else {
        expectedDurationMs - summary.videoDurationMs
    }
    if (durationDifferenceMs > durationToleranceMs) {
        return "Export duration ${summary.videoDurationMs}ms does not match the ${expectedDurationMs}ms timeline"
    }
    if (sourceHasAudio && summary.audioSampleCount <= 0L) {
        return "The source clips contain audio, but the exported MP4 has no readable audio samples"
    }
    return null
}


/**
 * Formats metadata read back from the completed MP4. File size is the exact
 * byte count fingerprinted before MediaStore publication, not the preflight
 * estimate or a requested encoder bitrate.
 */
internal fun formatVerifiedExportOutputSummary(
    summary: ExportTrackSummary,
    fileSizeBytes: Long,
): String? {
    if (summary.videoWidthPx <= 0 || summary.videoHeightPx <= 0) return null
    val durationLabel = formatVerifiedExportDuration(summary.videoDurationMs) ?: return null
    val sizeLabel = formatActualExportFileSize(fileSizeBytes) ?: return null

    val codecs = buildList {
        summary.videoMimeType?.let { mime -> formatExportCodecLabel(mime)?.let(::add) }
        if (summary.audioSampleCount > 0L) {
            summary.audioMimeType?.let { mime -> formatExportCodecLabel(mime)?.let(::add) }
        }
    }

    return buildList {
        add("${summary.videoWidthPx} × ${summary.videoHeightPx}")
        add(durationLabel)
        add("$sizeLabel actual")
        if (codecs.isNotEmpty()) add(codecs.joinToString(" + "))
    }.joinToString(" • ")
}

internal fun formatActualExportFileSize(bytes: Long): String? {
    if (bytes <= 0L) return null
    val mebibytes = bytes.toDouble() / 1_048_576.0
    return String.format(Locale.ROOT, "%.1f MiB", mebibytes)
}

internal fun formatVerifiedExportDuration(durationMs: Long): String? {
    if (durationMs <= 0L) return null
    val totalSeconds = durationMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60L, seconds)
    }
}

private fun formatExportCodecLabel(mimeType: String): String? {
    val normalized = mimeType.trim().lowercase(Locale.ROOT)
    if (normalized.substringBefore('/') !in setOf("audio", "video")) return null
    return when (normalized) {
        "video/avc" -> "H.264"
        "video/hevc" -> "HEVC"
        "video/av01" -> "AV1"
        "video/mp4v-es" -> "MPEG-4"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/3gpp" -> "AMR"
        "audio/amr-wb" -> "AMR-WB"
        else -> normalized.substringAfter('/').uppercase(Locale.ROOT)
    }
}
