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
 * Returns false once the user cancels an export or a newer export supersedes it.
 * Call after every suspending preparation step before continuing or mutating UI state.
 */
internal fun isExportSessionCurrent(activeSessionId: String?, expectedSessionId: String): Boolean =
    expectedSessionId.isNotBlank() && activeSessionId == expectedSessionId

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
        detailText = "The export stopped before a verified MP4 was saved. Review the export message below before retrying.",
    )
    ExportUiStage.CANCELLED -> ExportProgressPresentation(
        percent = null,
        isIndeterminate = false,
        progressLabel = "Cancelled",
        detailText = "The active export was cancelled; its temporary file was discarded.",
    )
}

/**
 * Keeps terminal failure and cancellation feedback visible after the active
 * progress indicator has stopped. IDLE and COMPLETE use their own UI sections.
 */
internal fun shouldShowExportStatusCard(stage: ExportUiStage, isExporting: Boolean): Boolean =
    isExporting || stage == ExportUiStage.FAILED || stage == ExportUiStage.CANCELLED

/**
 * Makes a failed export's existing, fully revalidated action explicit.
 * The retry uses the same export callback and therefore reruns preflight.
 */
internal fun exportActionButtonLabel(stage: ExportUiStage, isExporting: Boolean): String = when {
    isExporting -> "Exporting…"
    stage == ExportUiStage.FAILED -> "Retry export"
    else -> "Export MP4"
}

/**
 * Keeps facts measured from the published MP4 separate from the settings
 * selected before rendering, so the completion card cannot imply they are
 * the same kind of metadata.
 */
internal data class ExportCompletionSummary(
    val verifiedOutput: String?,
    val exportSettings: List<String>,
)

internal fun buildExportCompletionSummary(
    verifiedOutput: String?,
    exportSettings: List<String>,
): ExportCompletionSummary? {
    val cleanOutput = verifiedOutput?.trim()?.takeIf { it.isNotEmpty() }
    val cleanSettings = exportSettings.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleanOutput == null && cleanSettings.isEmpty()) return null
    return ExportCompletionSummary(
        verifiedOutput = cleanOutput,
        exportSettings = cleanSettings,
    )
}

/** Reasons the Android Sharesheet could not be opened for a verified export. */
internal enum class ExportShareFailureReason {
    NO_COMPATIBLE_APP,
    URI_ACCESS_DENIED,
}

/** Gives the user a recovery action without claiming that a share was completed. */
internal fun exportShareFailureMessage(reason: ExportShareFailureReason): String = when (reason) {
    ExportShareFailureReason.NO_COMPATIBLE_APP ->
        "Android couldn't find an app to share this MP4. Install or enable a sharing app, then try again."
    ExportShareFailureReason.URI_ACCESS_DENIED ->
        "Android denied access to the saved MP4. Export again, then retry sharing."
}


/**
 * Formats monotonic elapsed export time without implying a completion estimate.
 * Negative values are treated as zero; hours are shown only after one hour.
 */
internal fun formatExportElapsedTime(elapsedMs: Long): String {
    val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1_000L
    val seconds = totalSeconds % 60L
    val totalMinutes = totalSeconds / 60L
    fun twoDigits(value: Long): String = value.toString().padStart(2, '0')

    return if (totalMinutes >= 60L) {
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}"
    } else {
        "${twoDigits(totalMinutes)}:${twoDigits(seconds)}"
    }
}
