package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLayoutRulesTest {
    @Test
    fun tightWindowReducesPreviewAndSectionSpacing() {
        val metrics = editorLayoutMetrics(480)

        assertEquals(96, metrics.previewHeightDp)
        assertEquals(TIGHT_EDITOR_SECTION_SPACING_DP, metrics.sectionSpacingDp)
        assertEquals(TIGHT_EDITOR_CONTENT_PADDING_VERTICAL_DP, metrics.contentPaddingVerticalDp)
    }

    @Test
    fun compactWindowOutsideTightRangeKeepsCompactSpacing() {
        val metrics = editorLayoutMetrics(EDITOR_TIGHT_WINDOW_HEIGHT_DP)

        assertEquals(COMPACT_EDITOR_SECTION_SPACING_DP, metrics.sectionSpacingDp)
        assertEquals(COMPACT_EDITOR_SECTION_SPACING_DP, metrics.contentPaddingVerticalDp)
    }

    @Test
    fun veryShortWindowKeepsPreviewAboveUsableMinimum() {
        assertEquals(
            MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
            editorLayoutMetrics(320).previewHeightDp,
        )
    }

    @Test
    fun mediumAndTallWindowsKeepExistingPreviewScaling() {
        assertEquals(112, editorLayoutMetrics(560).previewHeightDp)
        assertEquals(148, editorLayoutMetrics(740).previewHeightDp)
        assertEquals(MAX_EDITOR_PREVIEW_HEIGHT_DP, editorLayoutMetrics(1_000).previewHeightDp)
    }

    @Test
    fun regularWindowKeepsStandardEditorSpacing() {
        val metrics = editorLayoutMetrics(600)

        assertEquals(DEFAULT_EDITOR_SECTION_SPACING_DP, metrics.sectionSpacingDp)
        assertEquals(DEFAULT_EDITOR_SECTION_SPACING_DP, metrics.contentPaddingVerticalDp)
    }

    @Test
    fun compactWidthStacksPreviewMetadataAboveTransportControls() {
        assertEquals(
            true,
            editorLayoutMetrics(640, EDITOR_COMPACT_WINDOW_WIDTH_DP - 1)
                .stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            false,
            editorLayoutMetrics(640, EDITOR_COMPACT_WINDOW_WIDTH_DP)
                .stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            false,
            editorLayoutMetrics(640, 600)
                .stackPreviewMetadataAboveTransportControls,
        )
    }

    @Test
    fun missingSourceRelinkActionReservesAdditionalInlineWidth() {
        val relinkThreshold = EDITOR_COMPACT_WINDOW_WIDTH_DP + EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP

        assertEquals(
            true,
            editorLayoutMetrics(
                windowHeightDp = 640,
                windowWidthDp = EDITOR_COMPACT_WINDOW_WIDTH_DP,
                hasPreviewRelinkAction = true,
            ).stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            true,
            editorLayoutMetrics(640, relinkThreshold - 1, hasPreviewRelinkAction = true)
                .stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            false,
            editorLayoutMetrics(640, relinkThreshold, hasPreviewRelinkAction = true)
                .stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            false,
            editorLayoutMetrics(640, EDITOR_COMPACT_WINDOW_WIDTH_DP)
                .stackPreviewMetadataAboveTransportControls,
        )
    }

    @Test
    fun previewSubtitleLineBudgetMatchesStackedMetadataLayout() {
        val inline = editorLayoutMetrics(640, 600)
        assertEquals(false, inline.stackPreviewMetadataAboveTransportControls)
        assertEquals(EDITOR_PREVIEW_INLINE_SUBTITLE_MAX_LINES, inline.previewMetadataSubtitleMaxLines)

        val compact = editorLayoutMetrics(640, EDITOR_COMPACT_WINDOW_WIDTH_DP - 1)
        assertEquals(true, compact.stackPreviewMetadataAboveTransportControls)
        assertEquals(EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES, compact.previewMetadataSubtitleMaxLines)

        val relinkCompact = editorLayoutMetrics(
            640,
            EDITOR_COMPACT_WINDOW_WIDTH_DP + EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP - 1,
            hasPreviewRelinkAction = true,
        )
        assertEquals(true, relinkCompact.stackPreviewMetadataAboveTransportControls)
        assertEquals(EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES, relinkCompact.previewMetadataSubtitleMaxLines)

        val relinkInline = editorLayoutMetrics(
            640,
            EDITOR_COMPACT_WINDOW_WIDTH_DP + EDITOR_RELINK_ACTION_EXTRA_WIDTH_DP,
            hasPreviewRelinkAction = true,
        )
        assertEquals(false, relinkInline.stackPreviewMetadataAboveTransportControls)
        assertEquals(EDITOR_PREVIEW_INLINE_SUBTITLE_MAX_LINES, relinkInline.previewMetadataSubtitleMaxLines)

        val accessible = editorLayoutMetrics(640, 600, fontScale = 1.3f)
        assertEquals(true, accessible.stackPreviewMetadataAboveTransportControls)
        assertEquals(EDITOR_PREVIEW_STACKED_SUBTITLE_MAX_LINES, accessible.previewMetadataSubtitleMaxLines)
    }

    @Test
    fun clipActionLabelsCompactBeforeTheSplitControlLosesReadableWidth() {
        assertEquals(true, editorLayoutMetrics(640, EDITOR_CLIP_ACTION_STANDARD_LABEL_MIN_WIDTH_DP - 1).useCompactClipActionLabels)
        assertEquals(false, editorLayoutMetrics(640, EDITOR_CLIP_ACTION_STANDARD_LABEL_MIN_WIDTH_DP).useCompactClipActionLabels)
        assertEquals(true, editorLayoutMetrics(640, 900, fontScale = EDITOR_LARGE_FONT_SCALE_THRESHOLD).useCompactClipActionLabels)
        assertEquals(false, editorLayoutMetrics(640, 900, fontScale = EDITOR_LARGE_FONT_SCALE_THRESHOLD - 0.001f).useCompactClipActionLabels)
        assertEquals(true, editorLayoutMetrics(640, 0).useCompactClipActionLabels)
    }

    @Test
    fun clipActionRowScrollsBelowTheMinimumContentWidth() {
        val minimumWindowWidth =
            VyroCutEditorLayoutMetrics.ClipActionControlsMinimumContentWidthDp +
                2 * VyroCutEditorChromeMetrics.HorizontalContentPaddingDp

        val atMinimum = editorLayoutMetrics(640, minimumWindowWidth)
        val belowMinimum = editorLayoutMetrics(640, minimumWindowWidth - 1)

        assertEquals(
            VyroCutEditorLayoutMetrics.ClipActionControlsMinimumContentWidthDp,
            atMinimum.clipActionControlsMinimumContentWidthDp,
        )
        assertFalse(atMinimum.scrollClipActionControlsHorizontally)
        assertTrue(belowMinimum.scrollClipActionControlsHorizontally)
    }

    @Test
    fun clipActionMinimumWidthGrowsWithFontScaleAndCapsAtTwoTimes() {
        val base = VyroCutEditorLayoutMetrics.ClipActionControlsMinimumContentWidthDp
        val insets = 2 * VyroCutEditorChromeMetrics.HorizontalContentPaddingDp

        assertEquals(base, editorLayoutMetrics(640, 640, fontScale = 1f).clipActionControlsMinimumContentWidthDp)
        assertEquals(base + 24, editorLayoutMetrics(640, 640, fontScale = 1.5f).clipActionControlsMinimumContentWidthDp)
        assertEquals(base + 48, editorLayoutMetrics(640, 640, fontScale = 2f).clipActionControlsMinimumContentWidthDp)
        assertEquals(
            editorLayoutMetrics(640, 2 * VyroCutEditorChromeMetrics.HorizontalContentPaddingDp + base + 48, fontScale = 2f)
                .clipActionControlsMinimumContentWidthDp,
            editorLayoutMetrics(640, 640, fontScale = 2.5f).clipActionControlsMinimumContentWidthDp,
        )
        assertTrue(editorLayoutMetrics(640, base + insets + 23, fontScale = 1.5f).scrollClipActionControlsHorizontally)
        assertFalse(editorLayoutMetrics(640, base + insets + 24, fontScale = 1.5f).scrollClipActionControlsHorizontally)
        assertEquals(
            editorLayoutMetrics(640, 640, fontScale = 1f).clipActionControlsMinimumContentWidthDp,
            editorLayoutMetrics(640, 640, fontScale = Float.NaN).clipActionControlsMinimumContentWidthDp,
        )
    }

    @Test
    fun narrowWindowsScrollTransportControlsInsteadOfClippingThem() {
        assertEquals(true, editorLayoutMetrics(640, 339).scrollTransportControlsHorizontally)
        assertEquals(false, editorLayoutMetrics(640, 340).scrollTransportControlsHorizontally)
        assertEquals(false, editorLayoutMetrics(640, 360).scrollTransportControlsHorizontally)
    }

    @Test
    fun transportControlScrollThresholdGrowsWithAccessibilityFontScale() {
        assertEquals(true, editorLayoutMetrics(640, 359, fontScale = 1.5f).scrollTransportControlsHorizontally)
        assertEquals(false, editorLayoutMetrics(640, 360, fontScale = 1.5f).scrollTransportControlsHorizontally)
        assertEquals(true, editorLayoutMetrics(640, 379, fontScale = 2f).scrollTransportControlsHorizontally)
        assertEquals(false, editorLayoutMetrics(640, 380, fontScale = 2f).scrollTransportControlsHorizontally)
        assertEquals(
            editorLayoutMetrics(640, 380, fontScale = 2f),
            editorLayoutMetrics(640, 380, fontScale = 2.5f),
        )
    }

    @Test
    fun invalidWindowWidthUsesSafeCompactFallback() {
        val expected = editorLayoutMetrics(640, EDITOR_LAYOUT_FALLBACK_WINDOW_WIDTH_DP)

        assertEquals(true, expected.stackPreviewMetadataAboveTransportControls)
        assertEquals(expected, editorLayoutMetrics(640, 0))
        assertEquals(expected, editorLayoutMetrics(640, -1))
    }

    @Test
    fun missingWindowWidthDefaultsToCompactMetadataLayout() {
        assertEquals(
            true,
            editorLayoutMetrics(640).stackPreviewMetadataAboveTransportControls,
        )
    }

    @Test
    fun availableWindowHeightUsesExactTightAndCompactBoundaries() {
        val justBelowTight = editorLayoutMetrics(519)
        val atTightBoundary = editorLayoutMetrics(520)
        val justBelowCompact = editorLayoutMetrics(599)
        val atCompactBoundary = editorLayoutMetrics(600)

        assertEquals(103, justBelowTight.previewHeightDp)
        assertEquals(TIGHT_EDITOR_SECTION_SPACING_DP, justBelowTight.sectionSpacingDp)

        assertEquals(104, atTightBoundary.previewHeightDp)
        assertEquals(COMPACT_EDITOR_SECTION_SPACING_DP, atTightBoundary.sectionSpacingDp)

        assertEquals(119, justBelowCompact.previewHeightDp)
        assertEquals(COMPACT_EDITOR_SECTION_SPACING_DP, justBelowCompact.sectionSpacingDp)

        assertEquals(120, atCompactBoundary.previewHeightDp)
        assertEquals(DEFAULT_EDITOR_SECTION_SPACING_DP, atCompactBoundary.sectionSpacingDp)
    }

    @Test
    fun largeFontScaleStacksPreviewMetadataEvenOnWideWindows() {
        assertEquals(
            true,
            editorLayoutMetrics(
                windowHeightDp = 640,
                windowWidthDp = 600,
                fontScale = EDITOR_LARGE_FONT_SCALE_THRESHOLD,
            ).stackPreviewMetadataAboveTransportControls,
        )
        assertEquals(
            true,
            editorLayoutMetrics(640, 600, fontScale = 1.3f)
                .stackPreviewMetadataAboveTransportControls,
        )
    }

    @Test
    fun fontScaleBelowThresholdKeepsWidePreviewMetadataInline() {
        assertEquals(
            false,
            editorLayoutMetrics(
                windowHeightDp = 640,
                windowWidthDp = 600,
                fontScale = EDITOR_LARGE_FONT_SCALE_THRESHOLD - 0.001f,
            ).stackPreviewMetadataAboveTransportControls,
        )
    }

    @Test
    fun largeFontScaleReducesPreviewGraduallyAndCapsTheReduction() {
        assertEquals(128, editorLayoutMetrics(640, 600, fontScale = 1.15f).previewHeightDp)
        assertEquals(122, editorLayoutMetrics(640, 600, fontScale = 1.3f).previewHeightDp)
        assertEquals(115, editorLayoutMetrics(640, 600, fontScale = 1.5f).previewHeightDp)
        assertEquals(113, editorLayoutMetrics(640, 600, fontScale = 1.8f).previewHeightDp)
        assertEquals(113, editorLayoutMetrics(640, 600, fontScale = 2.5f).previewHeightDp)
    }

    @Test
    fun fontScalePreviewReductionNeverCrossesWindowClassMinimums() {
        assertEquals(
            MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
            editorLayoutMetrics(480, 600, fontScale = 2f).previewHeightDp,
        )
        assertEquals(
            MIN_TIGHT_EDITOR_PREVIEW_HEIGHT_DP,
            editorLayoutMetrics(560, 600, fontScale = 2f).previewHeightDp,
        )
        assertEquals(
            MIN_EDITOR_PREVIEW_HEIGHT_DP,
            editorLayoutMetrics(600, 600, fontScale = 2f).previewHeightDp,
        )
        assertEquals(
            MIN_COMPACT_EDITOR_PREVIEW_HEIGHT_DP,
            editorLayoutMetrics(320, 600, fontScale = 2f).previewHeightDp,
        )
    }

    @Test
    fun fontScaleBelowThresholdKeepsExistingPreviewHeight() {
        assertEquals(128, editorLayoutMetrics(640, 600, fontScale = 1.149f).previewHeightDp)
    }

    @Test
    fun invalidFontScaleFallsBackWithoutChangingWidthRules() {
        val expected = editorLayoutMetrics(640, 600, fontScale = 1f)

        assertEquals(expected, editorLayoutMetrics(640, 600, fontScale = 0f))
        assertEquals(expected, editorLayoutMetrics(640, 600, fontScale = -1f))
        assertEquals(expected, editorLayoutMetrics(640, 600, fontScale = Float.NaN))
        assertEquals(expected, editorLayoutMetrics(640, 600, fontScale = Float.POSITIVE_INFINITY))
    }

    @Test
    fun invalidWindowHeightUsesSafeFallback() {
        val expected = editorLayoutMetrics(EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP)

        assertEquals(expected, editorLayoutMetrics(0))
        assertEquals(expected, editorLayoutMetrics(-1))
        assertEquals(expected.previewHeightDp, editorPreviewHeightDp(-1))
    }
}
