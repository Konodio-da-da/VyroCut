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

    @Test
    fun overlayBitmapKeepsFrameAspectRatioAndCapsLargeAllocations() {
        assertEquals(
            ClipTextOverlayBitmapSize(1_080, 1_920),
            calculateClipTextOverlayBitmapSize(1_080, 1_920),
        )
        assertEquals(
            ClipTextOverlayBitmapSize(1_920, 1_080),
            calculateClipTextOverlayBitmapSize(3_840, 2_160),
        )
        assertEquals(
            ClipTextOverlayBitmapSize(540, 960),
            calculateClipTextOverlayBitmapSize(540, 960),
        )
    }

    @Test
    fun rejectsInvalidFrameSizesForOverlayBitmap() {
        assertNull(calculateClipTextOverlayBitmapSize(0, 1_080))
        assertNull(calculateClipTextOverlayBitmapSize(1_920, -1))
        assertNull(calculateClipTextOverlayBitmapSize(1_920, 1_080, maxDimensionPx = 0))
    }

    @Test
    fun overlayBackgroundGeometryStaysInsideLandscapeAndPortraitFrames() {
        listOf(1_920 to 1_080, 1_080 to 1_920, 1_080 to 1_080).forEach { (width, height) ->
            val layout = calculateClipTextOverlayLayout(width, height, textHeightPx = height / 10)
            assertTrue(layout != null)
            assertTrue(layout!!.leftPx >= 0f)
            assertTrue(layout.topPx >= 0f)
            assertTrue(layout.leftPx + layout.boxWidthPx <= width + 0.01f)
            assertTrue(layout.topPx + layout.boxHeightPx <= height + 0.01f)
            assertEquals(width / 2f, layout.leftPx + layout.boxWidthPx / 2f, 0.01f)
        }
    }

    @Test
    fun overlayLayoutRejectsInvalidFrameOrTextGeometry() {
        assertNull(calculateClipTextOverlayLayout(0, 1_080, 100))
        assertNull(calculateClipTextOverlayLayout(1_920, 0, 100))
        assertNull(calculateClipTextOverlayLayout(1_920, 1_080, 0))
    }
}
