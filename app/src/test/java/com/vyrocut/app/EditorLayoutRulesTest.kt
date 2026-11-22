package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLayoutRulesTest {
    @Test
    fun previewHeightScalesWithWindowAndRespectsBounds() {
        assertEquals(MIN_EDITOR_PREVIEW_HEIGHT_DP, editorPreviewHeightDp(560))
        assertEquals(148, editorPreviewHeightDp(740))
        assertEquals(MAX_EDITOR_PREVIEW_HEIGHT_DP, editorPreviewHeightDp(1_000))
    }

    @Test
    fun invalidWindowHeightFallsBackToMinimumPreview() {
        assertEquals(MIN_EDITOR_PREVIEW_HEIGHT_DP, editorPreviewHeightDp(0))
        assertEquals(MIN_EDITOR_PREVIEW_HEIGHT_DP, editorPreviewHeightDp(-1))
    }
}
