package com.vyrocut.app

import kotlin.math.roundToInt

internal const val MIN_EDITOR_PREVIEW_HEIGHT_DP = 112
internal const val MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = 88
internal const val MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = 100
internal const val MAX_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = 112
internal const val MAX_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = 144
internal const val MAX_EDITOR_PREVIEW_HEIGHT_DP = 168

internal const val EDITOR_TIGHT_WINDOW_HEIGHT_DP = 520
internal const val EDITOR_COMPACT_WINDOW_HEIGHT_DP = 600
// Keep preview metadata beside transport only when the row has room for the
// transport controls and a readable title/subtitle column.
internal const val EDITOR_COMPACT_WINDOW_WIDTH_DP = 440
// Reserve extra width when a missing-source Relink/Reading action is present.
internal const val EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP = 64
internal const val EDITOR_PREVIEW_INLINE_SUBTITLE_MAX_LINES = 1
internal const val EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES = 2
internal const val EDITOR_LARGE_FONT_SCALE_THRESHOLD = 1.15f
internal const val EDITOR_PREVIEW_REDUCTION_PER_FONT_SCALE = 0.30f
internal const val MAX_EDITOR_FONT_SCALE_PREVIEW_REDUCTION = 0.12f
internal const val EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP = 560
internal const val EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP = EDITOR_COMPACT_WINDOW_WIDTH_DP - 1

internal const val TIGHT_EDITOR_SECTION_SPACING_DP = 3
internal const val TIGHT_EDITOR_CONTENT_PADDING_VERTICAL_DP = 2
internal const val COMPACT_EDITOR_SECTION_SPACING_DP = 4
internal const val DEFAULT_EDITOR_SECTION_SPACING_DP = 6

/**
 * Small, pure layout values shared by the editor screen.
 *
 * The whole editor remains vertically scrollable. Tight windows reclaim a
 * little more space from outer padding and section gaps without hiding tools,
 * reducing touch targets, or changing timeline/playback geometry.
 */
internal data class EditorLayoutMetrics(
    val previewHeightDp: Int,
    val sectionSpacingDp: Int,
    val contentPaddingVerticalDp: Int,
    val stackPreviewMetadataAboveTransportControls: Boolean,
    val previewMetadataSubtitleMaxLines: Int,
)

/**
 * Uses the app window bounds and current font scale (not physical-device
 * dimensions) so split-screen, resized windows, and larger accessibility text
 * receive the same compact treatment as short displays.
 */
internal fun editorLayoutMetrics(
    windowHeightDp: Int,
    windowWidthDp: Int = EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP,
    fontScale: Float = 1f,
    hasPreviewRelinkAction: Boolean = false,
): EditorLayoutMetrics {
    val safeHeight = windowHeightDp.takeIf { it > 0 }
        ?: EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP
    val safeWidth = windowWidthDp.takeIf { it > 0 }
        ?: EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP
    val safeFontScale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val tight = safeHeight < EDITOR_TIGHT_WINDOW_HEIGHT_DP
    val compact = safeHeight < EDITOR_COMPACT_WINDOW_HEIGHT_DP

    val (basePreviewHeight, minimumPreviewHeight) = when {
        tight -> (safeHeight / 5).coerceIn(
            MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
            MAX_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
        ) to MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP
        compact -> (safeHeight / 5).coerceIn(
            MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP,
            MAX_TIGHT_EDITOR_PREVIEW_HEIGHT_DP,
        ) to MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP
        else -> (safeHeight / 5).coerceIn(
            MIN_EDITOR_PREVIEW_HEIGHT_DP,
            MAX_EDITOR_PREVIEW_HEIGHT_DP,
        ) to MIN_EDITOR_PREVIEW_HEIGHT_DP
    }

    // Larger text already stacks preview metadata above transport controls.
    // Reclaim a small amount of preview height as font scale grows, but never
    // cross the existing minimum for the current window-height class.
    val fontScalePreviewReduction = (
        (safeFontScale - EDITOR_LARGE_FONT_SCALE_THRESHOLD).coerceAtLeast(0f) *
            EDITOR_PREVIEW_REDUCTION_PER_FONT_SCALE
        ).coerceAtMost(MAX_EDITOR_FONT_SCALE_PREVIEW_REDUCTION)
    val previewHeight = (basePreviewHeight * (1f - fontScalePreviewReduction))
        .roundToInt()
        .coerceAtLeast(minimumPreviewHeight)

    val sectionSpacing = when {
        tight -> TIGHT_EDITOR_SECTION_SPACING_DP
        compact -> COMPACT_EDITOR_SECTION_SPACING_DP
        else -> DEFAULT_EDITOR_SECTION_SPACING_DP
    }
    val contentPadding = if (tight) {
        TIGHT_EDITOR_CONTENT_PADDING_VERTICAL_DP
    } else {
        sectionSpacing
    }

    val inlineMetadataMinimumWidth = EDITOR_COMPACT_WINDOW_WIDTH_DP +
        if (hasPreviewRelinkAction) EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP else 0

    val stackPreviewMetadata = safeWidth < inlineMetadataMinimumWidth ||
        safeFontScale >= EDITOR_LARGE_FONT_SCALE_THRESHOLD

    return EditorLayoutMetrics(
        previewHeightDp = previewHeight,
        sectionSpacingDp = sectionSpacing,
        contentPaddingVerticalDp = contentPadding,
        stackPreviewMetadataAboveTransportControls = stackPreviewMetadata,
        previewMetadataSubtitleMaxLines = if (stackPreviewMetadata) {
            EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES
        } else {
            EDITOR_PREVIEW_INLINE_SUBTITLE_MAX_LINES
        },
    )
}

/** Kept as a small compatibility helper for callers that only need the preview height. */
internal fun editorPreviewHeightDp(windowHeightDp: Int): Int =
    editorLayoutMetrics(windowHeightDp).previewHeightDp
