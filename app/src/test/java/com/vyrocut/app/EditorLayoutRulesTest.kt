package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutRulesTest {
    @Test
    fun compactWindowReducesPreviewAndSectionSpacing() {
        val metrics = editorLayoutMetrics(480)

        assertEquals(96, metrics.previewHeightDp)
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
    fun invalidWindowHeightUsesSafeFallback() {
        val expected = editorLayoutMetrics(EDITOR_LAYOUT_FALLBACK_WINDOW_HEIGHT_DP)

        assertEquals(expected, editorLayoutMetrics(0))
        assertEquals(expected, editorLayoutMetrics(-1))
        assertEquals(expected.previewHeightDp, editorPreviewHeightDp(-1))
    }
}
