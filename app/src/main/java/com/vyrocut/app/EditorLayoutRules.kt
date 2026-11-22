package com.vyrocut.app

internal const val MIN_EDITOR_PREVIEW_HEIGHT_DP = 112
internal const val MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = 88
internal const val MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = 100
internal const val MAX_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = 112
internal const val MAX_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = 144
internal const val MAX_EDITOR_PREVIEW_HEIGHT_DP = 168

internal const val EDITOR_TIGHT_WINDOW_HEIGHT_DP = 520
internal const val EDITOR_COMPACT_WINDOW_HEIGHT_DP = 600
internal const val EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP = 560

internal const val COMPACT_EDITOR_SECTION_SPACING_DP = 4
internal const val DEFAULT_EDITOR_SECTION_SPACING_DP = 6

/**
 * Small, pure layout values shared by the editor screen.
 *
 * The whole editor remains vertically scrollable. Compact windows reclaim a
 * little space from the preview and inter-section gaps without hiding tools
 * or changing timeline/playback geometry.
 */
internal data class EditorLayoutMetrics(
    val previewHeightDp: Int,
    val sectionSpacingDp: Int,
    val contentPaddingVerticalDp: Int,
)

/**
 * Uses the app window height (not physical-device dimensions) so split-screen
 * and resized windows receive the same compact treatment as short displays.
 */
internal fun editorLayoutMetrics(windowHeightDp: Int): EditorLayoutMetrics {
    val safeHeight = windowHeightDp.takeIf { it > 0 }
        ?: EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP
    val compact = safeHeight < EDITOR_COMPACT_WINDOW_HEIGHT_DP

    val previewHeight = when {
        safeHeight < EDITOR_TIGHT_WINDOW_HEIGHT_DP ->
            (safeHeight / 5).coerceIn(
                MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
                MAX_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
            )
        compact ->
            (safeHeight / 5).coerceIn(
                MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP,
                MAX_TIGHT_EDITOR_PREVIEW_HEIGHT_DP,
            )
        else ->
            (safeHeight / 5).coerceIn(
                MIN_EDITOR_PREVIEW_HEIGHT_DP,
                MAX_EDITOR_PREVIEW_HEIGHT_DP,
            )
    }

    val sectionSpacing = if (compact) {
        COMPACT_EDITOR_SECTION_SPACING_DP
    } else {
        DEFAULT_EDITOR_SECTION_SPACING_DP
    }

    return EditorLayoutMetrics(
        previewHeightDp = previewHeight,
        sectionSpacingDp = sectionSpacing,
        contentPaddingVerticalDp = sectionSpacing,
    )
}

/** Kept as a small compatibility helper for callers that only need the preview height. */
internal fun editorPreviewHeightDp(windowHeightDp: Int): Int =
    editorLayoutMetrics(windowHeightDp).previewHeightDp
