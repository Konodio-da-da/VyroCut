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

    @Test
    fun overlayFontScaleAndPositionAreValidatedAndDecoded() {
        assertEquals(
            ClipTextOverlay("Styled title", 1.25f, ClipTextPosition.TOP),
            decodeClipTextOverlay("Styled title", fontScale = 1.25f, position = "TOP"),
        )
        assertEquals(
            ClipTextOverlay("Default title"),
            decodeClipTextOverlay("Default title"),
        )
        assertFalse(isValidClipTextOverlayFontScale(Float.NaN))
        assertFalse(isValidClipTextOverlayFontScale(0.5f))
        assertFalse(isValidClipTextOverlayFontScale(1.6f))
        assertNull(decodeClipTextOverlay("Styled title", fontScale = 2f))
        assertNull(decodeClipTextOverlay("Styled title", position = "LEFT"))
    }

    @Test
    fun overlayLayoutHonoursTopCenterAndBottomPositionsAndFontScale() {
        val top = calculateClipTextOverlayLayout(
            frameWidthPx = 1_920,
            frameHeightPx = 1_080,
            textHeightPx = 120,
            position = ClipTextPosition.TOP,
        )!!
        val center = calculateClipTextOverlayLayout(
            frameWidthPx = 1_920,
            frameHeightPx = 1_080,
            textHeightPx = 120,
            position = ClipTextPosition.CENTER,
        )!!
        val bottom = calculateClipTextOverlayLayout(
            frameWidthPx = 1_920,
            frameHeightPx = 1_080,
            textHeightPx = 120,
            position = ClipTextPosition.BOTTOM,
        )!!
        val large = calculateClipTextOverlayLayout(
            frameWidthPx = 1_920,
            frameHeightPx = 1_080,
            textHeightPx = 160,
            fontScale = 1.25f,
        )!!

        assertTrue(top.topPx < center.topPx)
        assertTrue(center.topPx < bottom.topPx)
        assertEquals(1.25f, large.textSizePx / bottom.textSizePx, 0.001f)
    }
}
