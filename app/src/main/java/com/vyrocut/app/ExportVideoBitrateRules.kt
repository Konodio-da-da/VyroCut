package com.vyrocut.app

import kotlin.math.roundToInt

/**
 * Video bitrate choices are requested targets, not guarantees: device encoders
 * may clamp or ignore a requested bitrate when selecting a supported format.
 */
internal enum class ExportVideoBitratePreset(
    val id: String,
    val label: String,
    internal val bitsPerPixelPerFrame: Double?,
    val summaryLabel: String,
) {
    AUTO("auto", "Auto", null, "Auto bitrate"),
    BALANCED("balanced", "Balanced", 0.10, "Balanced bitrate target"),
    HIGH("high", "High", 0.14, "High bitrate target"),
    VERY_HIGH("very-high", "Very high", 0.18, "Very high bitrate target");

    companion object {
        fun fromId(value: String?): ExportVideoBitratePreset =
            values().firstOrNull { it.id == value } ?: AUTO
    }
}

internal const val DEFAULT_SOURCE_FPS_FOR_BITRATE_ESTIMATE = 30
internal const val MIN_TARGET_VIDEO_BITRATE_BPS = 1_000_000
internal const val MAX_TARGET_VIDEO_BITRATE_BPS = 20_000_000

/**
 * Estimates a requested video bitrate from output pixel rate. Source FPS uses
 * a 30 FPS estimate for bitrate sizing only; it does not alter source cadence.
 * The final target is bounded to keep malformed/extreme dimensions safe.
 *
 * AUTO returns null so Media3 retains its existing device-selected bitrate.
 */
internal fun exportTargetVideoBitrateBps(
    preset: ExportVideoBitratePreset,
    canvasSize: ExportCanvasSize,
    frameRatePreset: ExportFrameRatePreset,
): Int? {
    val bitsPerPixelPerFrame = preset.bitsPerPixelPerFrame ?: return null
    val fps = frameRatePreset.targetFramesPerSecond ?: DEFAULT_SOURCE_FPS_FOR_BITRATE_ESTIMATE
    val estimate = (
        canvasSize.widthPx.toDouble() *
            canvasSize.heightPx.toDouble() *
            fps.toDouble() *
            bitsPerPixelPerFrame
        ).roundToInt()
    return estimate.coerceIn(MIN_TARGET_VIDEO_BITRATE_BPS, MAX_TARGET_VIDEO_BITRATE_BPS)
}
