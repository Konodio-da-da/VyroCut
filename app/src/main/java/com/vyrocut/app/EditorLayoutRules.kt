package com.vyrocut.app

import kotlin.math.roundToInt

// Compatibility aliases keep the pure layout API stable while the shared
// design system owns the source values and responsive breakpoints.
internal const val MIN_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MinPreviewHeightDp
internal const val MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MinCompactPreviewHeightDp
internal const val MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MinTightPreviewHeightDp
internal const val MAX_COMPACT_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MaxCompactPreviewHeightDp
internal const val MAX_TIGHT_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MaxTightPreviewHeightDp
internal const val MAX_EDITOR_PREVIEW_HEIGHT_DP = VyroCutEditorLayoutMetrics.MaxPreviewHeightDp

internal const val EDITOR_TIGHT_WINDOW_HEIGHT_DP = VyroCutEditorLayoutMetrics.TightWindowHeightDp
internal const val EDITOR_COMPACT_WINDOW_HEIGHT_DP = VyroCutEditorLayoutMetrics.CompactWindowHeightDp
internal const val EDITOR_COMPACT_WINDOW_WIDTH_DP = VyroCutEditorLayoutMetrics.CompactWindowWidthDp
internal const val EDITOR_HEADER_BRAND_TITLE_MINIMUM_WINDOW_WIDTH_DP = VyroCutEditorLayoutMetrics.HeaderBrandTitleMinimumWindowWidthDp
internal const val EDITOR_HEADER_BRAND_TITLE_FONT_SCALE_WIDTH_GROWTH_DP = VyroCutEditorLayoutMetrics.HeaderBrandTitleFontScaleWidthGrowthDp
internal const val EDITOR_TRANSPORT_CONTROLS_MINIMUM_WINDOW_WIDTH_DP = VyroCutEditorLayoutMetrics.TransportControlsMinimumWindowWidthDp
internal const val EDITOR_TRANSPORT_CONTROLS_FONT_SCALE_WIDTH_GROWTH_DP = VyroCutEditorLayoutMetrics.TransportControlsFontScaleWidthGrowthDp
internal const val EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP = VyroCutEditorLayoutMetrics.RelinkActionExtraWidthDp
internal const val EDITOR_CLIP_ACTION_STANDARD_LABEL_MIN_WIDTH_DP = VyroCutEditorLayoutMetrics.ClipActionStandardLabelMinWidthDp
internal const val EDITOR_PREVIEW_INLINE_SUBTITLE_MAX_LINES = VyroCutEditorLayoutMetrics.PreviewInlineSubtitleMaxLines
internal const val EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES = VyroCutEditorLayoutMetrics.PreviewStackedSubtitleMaxLines
internal const val EDITOR_LARGE_FONT_SCALE_THRESHOLD = VyroCutEditorLayoutMetrics.LargeFontScaleThreshold
internal const val EDITOR_PREVIEW_REDUCTION_PER_FONT_SCALE = VyroCutEditorLayoutMetrics.PreviewReductionPerFontScale
internal const val MAX_EDITOR_FONT_SCALE_PREVIEW_REDUCTION = VyroCutEditorLayoutMetrics.MaxFontScalePreviewReduction
internal const val EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP = VyroCutEditorLayoutMetrics.LayoutFallbackWindowHeightDp
internal const val EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP = VyroCutEditorLayoutMetrics.LayoutFallbackWindowWidthDp

internal const val TIGHT_EDITOR_SECTION_SPACING_DP = VyroCutEditorLayoutMetrics.TightSectionSpacingDp
internal const val TIGHT_EDITOR_CONTENT_PADDING_VERTICAL_DP = VyroCutEditorLayoutMetrics.TightContentPaddingVerticalDp
internal const val COMPACT_EDITOR_SECTION_SPACING_DP = VyroCutEditorLayoutMetrics.CompactSectionSpacingDp
internal const val DEFAULT_EDITOR_SECTION_SPACING_DP = VyroCutEditorLayoutMetrics.DefaultSectionSpacingDp

/** Keeps editor width calculations aligned with the shared bounded accessibility policy. */
private fun safeEditorFontScale(fontScale: Float): Float =
    fontScale
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(1f, VyroCutAccessibilityMetrics.MaxSupportedFontScale)
        ?: 1f

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
    val useCompactClipActionLabels: Boolean,
    val clipActionControlsMinimumContentWidthDp: Int,
    val scrollClipActionControlsHorizontally: Boolean,
    val scrollTransportControlsHorizontally: Boolean,
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
    val safeFontScale = safeEditorFontScale(fontScale)
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

    val transportControlsFontScaleGrowthDp = (
        (safeFontScale - 1f).coerceAtLeast(0f) *
            EDITOR_TRANSPORT_CONTROLS_FONT_SCALE_WIDTH_GROWTH_DP
        ).roundToInt().coerceAtLeast(0)
    val transportControlsMinimumWindowWidthDp =
        EDITOR_TRANSPORT_CONTROLS_MINIMUM_WINDOW_WIDTH_DP + transportControlsFontScaleGrowthDp

    // Keep the three weighted clip actions and the fixed delete target usable.
    // The window threshold includes the editor's horizontal content insets.
    val clipActionFontScaleGrowthDp = (
        (safeFontScale - 1f).coerceAtLeast(0f) *
            VyroCutEditorLayoutMetrics.ClipActionControlsFontScaleWidthGrowthDp
        ).roundToInt().coerceAtLeast(0)
    val clipActionMinimumContentWidthDp =
        VyroCutEditorLayoutMetrics.ClipActionControlsMinimumContentWidthDp +
            clipActionFontScaleGrowthDp
    val clipActionMinimumWindowWidthDp =
        clipActionMinimumContentWidthDp + 2 * VyroCutEditorChromeMetrics.HorizontalContentPaddingDp

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
        useCompactClipActionLabels = safeWidth < EDITOR_CLIP_ACTION_STANDARD_LABEL_MIN_WIDTH_DP ||
            safeFontScale >= EDITOR_LARGE_FONT_SCALE_THRESHOLD,
        clipActionControlsMinimumContentWidthDp = clipActionMinimumContentWidthDp,
        scrollClipActionControlsHorizontally = safeWidth < clipActionMinimumWindowWidthDp,
        scrollTransportControlsHorizontally = safeWidth < transportControlsMinimumWindowWidthDp,
    )
}

/**
 * Keeps the project title action readable in compact windows. The brand
 * wordmark yields space before the project action is forced below its
 * minimum useful width. Font-scale growth follows the shared 4x
 * accessibility range.
 */
internal fun editorHeaderShowsBrandTitle(
    windowWidthDp: Int,
    fontScale: Float = 1f,
): Boolean {
    val safeWidth = windowWidthDp.takeIf { it > 0 }
        ?: EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP
    val safeFontScale = safeEditorFontScale(fontScale)
    val fontScaleGrowth = (
        (safeFontScale - 1f) * EDITOR_HEADER_BRAND_TITLE_FONT_SCALE_WIDTH_GROWTH_DP
    ).roundToInt().coerceAtLeast(0)
    val requiredWidth = EDITOR_HEADER_BRAND_TITLE_MINIMUM_WINDOW_WIDTH_DP + fontScaleGrowth
    return safeWidth >= requiredWidth
}

/** Kept as a small compatibility helper for callers that only need the preview height. */
internal fun editorPreviewHeightDp(windowHeightDp: Int): Int =
    editorLayoutMetrics(windowHeightDp).previewHeightDp
