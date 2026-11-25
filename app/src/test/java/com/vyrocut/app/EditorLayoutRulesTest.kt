package com.vyrocut.app

import org.junit.Assert.assertEquals
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
