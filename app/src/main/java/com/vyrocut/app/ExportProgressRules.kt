package com.vyrocut.app

internal enum class ExportUiStage(val label: String) {
    IDLE("Ready"),
    PREPARING("Preparing export"),
    RENDERING("Rendering MP4"),
    VERIFYING("Verifying and saving"),
    COMPLETE("Export complete"),
    FAILED("Export failed"),
    CANCELLED("Export cancelled"),
}

/**
 * Accepts only valid Media3 percentage values and prevents a delayed callback
 * from making the visible progress move backwards.
 */
internal fun nextExportProgressPercent(current: Int?, reported: Int): Int? {
    if (reported !in 0..100) return current
    return maxOf(current ?: 0, reported)
}

/**
 * Describes what the export UI may truthfully show for the current stage.
 * Rendering is capped at 99% until verification and MediaStore publication
 * have completed; preparation and verification remain indeterminate.
 */
internal data class ExportProgressPresentation(
    val percent: Int?,
    val isIndeterminate: Boolean,
    /** Short phase text shown beside the percentage when progress is indeterminate. */
    val progressLabel: String,
    /** Explains the work currently happening without implying a later phase is complete. */
    val detailText: String,
)

internal fun exportProgressPresentation(
    stage: ExportUiStage,
    reportedPercent: Int?,
): ExportProgressPresentation = when (stage) {
    ExportUiStage.PREPARING -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = true,
        progressLabel = "Preparing…",
        detailText = "Checking source videos, audio lanes and available storage before rendering.",
    )
    ExportUiStage.VERIFYING -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = true,
        progressLabel = "Verifying…",
        detailText = "Checking the rendered video, audio and saved MP4 copy before it appears in Movies.",
    )
    ExportUiStage.RENDERING -> {
        val visiblePercent = reportedPercent?.coerceIn(0, 99)
        ExportProgressPresentation(
            percent = visiblePercent,
            isIndeterminate = visiblePercent == null,
            progressLabel = "Rendering…",
            detailText = "Rendering stays on this device. Cancel discards this temporary export.",
        )
    }
    ExportUiStage.COMPLETE -> ExportProgressPresentation(
        percent = 100,
        isIndeterminate = false,
        progressLabel = "Complete",
        detailText = "The MP4 passed verification and was saved to Movies/VyroCut.",
    )
    ExportUiStage.IDLE -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = false,
        progressLabel = "Ready",
        detailText = "Start an export to render a video on this device.",
    )
    ExportUiStage.FAILED -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = false,
        progressLabel = "Failed",
        detailText = "Review the export error above before retrying.",
    )
    ExportUiStage.CANCELLED -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = false,
        progressLabel = "Cancelled",
        detailText = "The temporary export was discarded.",
    )
}
