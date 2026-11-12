package com.vyrocut.app

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
)

/**
 * Rejects an invalid edit decision list before Media3 Transformer starts.
 * Source metadata is collected separately on an IO dispatcher so this rule
 * remains deterministic and easy to exercise in local unit tests.
 */
internal fun validateExportPreflight(
    clips: List<ExportClipRequirement>,
    sources: Map<String, ExportSourceMetadata>,
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
