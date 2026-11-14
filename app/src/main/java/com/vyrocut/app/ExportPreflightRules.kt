package com.vyrocut.app

/**
 * Returns true only when a source exposes an actual audio MIME track.
 * Document providers can return a duration for video containers selected
 * through the audio picker, so duration metadata alone is not sufficient.
 */
internal fun hasAudioTrackMimeType(mimeTypes: Iterable<String?>): Boolean =
    mimeTypes.any { mimeType ->
        mimeType?.trim()?.let {
            it.startsWith("audio/", ignoreCase = true) && it.substringAfter('/').isNotBlank()
        } == true
    }

/** Returns each source URI once, preserving first-use order for deterministic metadata checks. */
internal fun distinctExportSourceUris(sourceUris: Iterable<String>): List<String> =
    sourceUris.distinct()

internal data class ExportCanvasSize(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0)
    }
}

/** Uses a fixed landscape, square, or portrait frame so every clip exports consistently. */
internal fun exportCanvasSizeForPreset(preset: String): ExportCanvasSize = when (preset) {
    "16:9" -> ExportCanvasSize(widthPx = 1_920, heightPx = 1_080)
    "1:1" -> ExportCanvasSize(widthPx = 1_080, heightPx = 1_080)
    else -> ExportCanvasSize(widthPx = 1_080, heightPx = 1_920)
}

internal data class ExportClipRequirement(
    val id: String,
    val uri: String,
    val name: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
)

internal data class ExportSourceMetadata(
    val durationMs: Long?,
    val hasVideoTrack: Boolean,
    val videoWidthPx: Int? = null,
    val videoHeightPx: Int? = null,
    val videoRotationDegrees: Int = 0,
)

/**
 * Rejects an invalid edit decision list before Media3 Transformer starts.
 * Source metadata is collected separately on an IO dispatcher so this rule
 * remains deterministic and easy to exercise in local unit tests.
 */
internal fun validateExportPreflight(
    clips: List<ExportClipRequirement>,
    sources: Map<String, ExportSourceMetadata>,
    requireVideoDimensions: Boolean = false,
): String? {
    if (clips.isEmpty()) return "The project has no clips to export"

    val ids = HashSet<String>()
    var totalDurationMs = 0L

    clips.forEachIndexed { index, clip ->
        val label = clip.name.ifBlank { "Clip ${index + 1}" }
        if (clip.id.isBlank()) return "$label has no stable clip ID"
        if (!ids.add(clip.id)) return "The project contains duplicate clip IDs"
        if (clip.uri.isBlank()) return "$label has no source video"
        if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) {
            return "$label has an invalid source trim range"
        }

        val source = sources[clip.uri] ?: return "Could not inspect the source for $label"
        val sourceDurationMs = source.durationMs?.takeIf { it > 0L }
            ?: return "Could not read the duration of $label's source video"
        if (!source.hasVideoTrack) return "$label's source has no readable video track"
        if (requireVideoDimensions &&
            ((source.videoWidthPx ?: 0) <= 0 || (source.videoHeightPx ?: 0) <= 0)
        ) {
            return "$label's video dimensions are unavailable for the selected canvas background"
        }
        if (clip.sourceEndMs > sourceDurationMs) {
            return "$label's trim extends beyond its source video"
        }

        val clipDurationMs = clip.sourceEndMs - clip.sourceStartMs
        if (clipDurationMs > Long.MAX_VALUE - totalDurationMs) {
            return "The project duration is too long to export safely"
        }
        totalDurationMs += clipDurationMs
    }

    if (totalDurationMs <= 0L) return "The project has no playable duration"
    return null
}
