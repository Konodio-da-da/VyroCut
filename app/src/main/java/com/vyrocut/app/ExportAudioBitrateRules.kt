package com.vyrocut.app

/**
 * Requested AAC audio bitrate presets for Media3 Transformer export.
 *
 * These are encoder requests, not guaranteed output bitrates. Device encoders
 * may clamp or ignore a requested value to retain a supported configuration.
 */
internal enum class ExportAudioBitratePreset(
    val id: String,
    val label: String,
    val bitrateBps: Int?,
    val summaryLabel: String,
) {
    AUTO("auto", "Auto", null, "Auto audio bitrate"),
    SPEECH("96", "96 kbps", 96_000, "96 kbps audio target"),
    STANDARD("128", "128 kbps", 128_000, "128 kbps audio target"),
    HIGH("192", "192 kbps", 192_000, "192 kbps audio target"),
    VERY_HIGH("256", "256 kbps", 256_000, "256 kbps audio target");

    companion object {
        fun fromId(value: String?): ExportAudioBitratePreset =
            values().firstOrNull { it.id == value } ?: AUTO
    }
}

/** Auto returns null so Media3 retains its existing audio encoder defaults. */
internal fun exportRequestedAudioBitrateBps(preset: ExportAudioBitratePreset): Int? =
    preset.bitrateBps

internal fun exportAudioBitrateSummary(preset: ExportAudioBitratePreset): String =
    preset.bitrateBps?.let { bitrate ->
        "Requested target: ${bitrate / 1_000} kbps • device encoder may adjust to a supported value."
    } ?: "Auto keeps Media3's default audio encoder bitrate."
