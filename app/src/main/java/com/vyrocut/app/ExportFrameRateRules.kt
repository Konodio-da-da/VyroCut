package com.vyrocut.app

/**
 * Export-only target frame-rate choices.
 *
 * SOURCE leaves each video's original presentation cadence untouched. Capped
 * presets use Media3 FrameDropEffect, which drops frames based on source
 * presentation timestamps; it does not synthesize frames for lower-rate clips.
 */
internal enum class ExportFrameRatePreset(
    val id: String,
    val label: String,
    val targetFramesPerSecond: Int?,
    val summaryLabel: String,
) {
    SOURCE("source", "Source", null, "Source FPS"),
    FPS_24("24", "24", 24, "Target 24 FPS"),
    FPS_30("30", "30", 30, "Target 30 FPS"),
    FPS_60("60", "60", 60, "Target 60 FPS");

    companion object {
        fun fromId(value: String?): ExportFrameRatePreset =
            values().firstOrNull { it.id == value } ?: SOURCE
    }
}

/** Returns null for source cadence, or the timestamp-based target FPS. */
internal fun exportFrameDropTargetRate(preset: ExportFrameRatePreset): Float? =
    preset.targetFramesPerSecond?.toFloat()
