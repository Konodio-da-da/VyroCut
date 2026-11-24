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
)

internal fun exportProgressPresentation(
    stage: ExportUiStage,
    reportedPercent: Int?,
): ExportProgressPresentation = when (stage) {
    ExportUiStage.PREPARING,
    ExportUiStage.VERIFYING -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = true,
    )
    ExportUiStage.RENDERING -> {
        val visiblePercent = reportedPercent?.coerceIn(0, 99)
        ExportProgressPresentation(
            percent = visiblePercent,
            isIndeterminate = visiblePercent == null,
        )
    }
    ExportUiStage.COMPLETE -> ExportProgressPresentation(
        percent = 100,
        isIndeterminate = false,
    )
    ExportUiStage.IDLE,
    ExportUiStage.FAILED,
    ExportUiStage.CANCELLED -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = false,
    )
}
