package com.vyrocut.app

internal data class ExportTrackSummary(
    val videoSampleCount: Long,
    val audioSampleCount: Long,
    val videoDurationMs: Long,
    val videoWidthPx: Int = 0,
    val videoHeightPx: Int = 0,
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
