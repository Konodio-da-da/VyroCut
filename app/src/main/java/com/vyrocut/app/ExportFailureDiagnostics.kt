package com.vyrocut.app

import java.util.Locale

internal enum class ExportFailureStage {
    PREPARATION,
    RENDERING,
    VERIFYING_OR_PUBLISHING,
}

internal enum class ExportFailureCategory {
    STORAGE,
    SOURCE_PERMISSION,
    SOURCE_UNAVAILABLE,
    UNSUPPORTED_ENCODER,
    INVALID_OUTPUT,
    UNKNOWN,
}

internal data class ExportFailureDiagnostic(
    val category: ExportFailureCategory,
    val userMessage: String,
)

/**
 * Converts low-level export failures into actionable messages without exposing
 * private file paths, content URIs, or provider-specific exception text.
 *
 * The classifier examines a short cause chain because Media3 often wraps the
 * platform exception that contains the useful storage, permission, or codec clue.
 */
internal fun diagnoseExportFailure(
    stage: ExportFailureStage,
    error: Throwable,
): ExportFailureDiagnostic {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = error
    while (current != null && chain.size < 8 && chain.none { it === current }) {
        chain += current
        current = current.cause
    }

    val classNames = chain.map { it.javaClass.name.lowercase(Locale.ROOT) }
    val details = chain.joinToString(" ") { throwable ->
        "${throwable.javaClass.name} ${throwable.message.orEmpty()}"
    }.lowercase(Locale.ROOT)

    val category = when {
        details.containsAny(
            "enospc",
            "no space left on device",
            "not enough free space",
            "insufficient storage",
            "disk full",
            "quota exceeded",
        ) -> ExportFailureCategory.STORAGE

        classNames.any { it.contains("securityexception") || it.contains("recoverablesecurityexception") } ||
            details.containsAny("permission denied", "eacces", "not permitted", "uri permission") ->
            ExportFailureCategory.SOURCE_PERMISSION

        stage == ExportFailureStage.VERIFYING_OR_PUBLISHING && details.containsAny(
            "no video track",
            "no readable video",
            "no audio samples",
            "duration mismatch",
            "failed verification",
            "does not match the verified",
            "fingerprint is invalid",
            "truncated",
            "corrupt mp4",
            "undecodable",
        ) -> ExportFailureCategory.INVALID_OUTPUT

        details.containsAny(
            "encoder",
            "mediacodec",
            "codec",
            "unsupported format",
            "no suitable encoder",
        ) -> ExportFailureCategory.UNSUPPORTED_ENCODER

        stage != ExportFailureStage.VERIFYING_OR_PUBLISHING &&
            (classNames.any { it.contains("filenotfoundexception") || it.contains("nosuchfileexception") } ||
                details.containsAny("enoent", "source file not found", "failed to open source")) ->
            ExportFailureCategory.SOURCE_UNAVAILABLE

        else -> ExportFailureCategory.UNKNOWN
    }

    val userMessage = when (category) {
        ExportFailureCategory.STORAGE ->
            "Not enough free storage is available. Free space and try exporting again."

        ExportFailureCategory.SOURCE_PERMISSION ->
            if (stage == ExportFailureStage.VERIFYING_OR_PUBLISHING) {
                "Android denied access while verifying or saving the export. Check storage access and try again."
            } else {
                "VyroCut could not read a source file. Re-import or relink the clip and grant access."
            }

        ExportFailureCategory.SOURCE_UNAVAILABLE ->
            "A source file is missing or unreadable. Relink the affected clip and try again."

        ExportFailureCategory.UNSUPPORTED_ENCODER ->
            "This device could not encode the selected media or settings. Try a lower resolution or bitrate."

        ExportFailureCategory.INVALID_OUTPUT ->
            "The rendered MP4 failed verification and was not published. Try exporting again."

        ExportFailureCategory.UNKNOWN -> when (stage) {
            ExportFailureStage.PREPARATION ->
                "VyroCut could not prepare the export. Check the source clips and export settings."

            ExportFailureStage.RENDERING ->
                "The device encoder could not complete the render. Try again or reduce export quality."

            ExportFailureStage.VERIFYING_OR_PUBLISHING ->
                "The rendered MP4 could not be verified or saved to Movies. Nothing was published; check storage and try again."
        }
    }

    return ExportFailureDiagnostic(category, userMessage)
}

private fun String.containsAny(vararg candidates: String): Boolean =
    candidates.any(::contains)
