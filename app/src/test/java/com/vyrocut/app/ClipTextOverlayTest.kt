package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTextOverlayTest {
    @Test
    fun trimsTextAndNormalizesWindowsLineEndings() {
        assertEquals(
            ClipTextOverlay("First line\nSecond line"),
            decodeClipTextOverlay("  First line\r\nSecond line  "),
        )
    }

    @Test
    fun blankTextMeansNoOverlay() {
        assertNull(decodeClipTextOverlay(null))
        assertNull(decodeClipTextOverlay(""))
        assertNull(decodeClipTextOverlay("   \n  "))
    }

    @Test
    fun rejectsTextLongerThanThePersistedLimit() {
        assertFalse(isValidClipTextOverlayText("x".repeat(MAX_CLIP_TEXT_OVERLAY_LENGTH + 1)))
        assertNull(decodeClipTextOverlay("x".repeat(MAX_CLIP_TEXT_OVERLAY_LENGTH + 1)))
        assertTrue(isValidClipTextOverlayText("x".repeat(MAX_CLIP_TEXT_OVERLAY_LENGTH)))
    }

    @Test
    fun rejectsControlCharactersButAllowsLineBreaksAndTabs() {
        assertFalse(isValidClipTextOverlayText("Title\u0001"))
        assertTrue(isValidClipTextOverlayText("Title\nSubtitle"))
        assertTrue(isValidClipTextOverlayText("Title\tSubtitle"))
    }
}
