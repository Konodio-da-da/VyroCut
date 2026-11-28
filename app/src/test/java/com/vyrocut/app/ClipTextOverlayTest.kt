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
    fun timedOverlayUsesInclusiveStartAndExclusiveEndBoundaries() {
        val overlay = ClipTextOverlay("Timed caption", startMs = 1_000L, endMs = 2_500L)

        assertFalse(isClipTextOverlayVisibleAtMs(overlay, 999L))
        assertTrue(isClipTextOverlayVisibleAtMs(overlay, 1_000L))
        assertTrue(isClipTextOverlayVisibleAtMs(overlay, 2_499L))
        assertFalse(isClipTextOverlayVisibleAtMs(overlay, 2_500L))
        assertFalse(isClipTextOverlayVisibleAtUs(overlay, -1L))
        assertTrue(isClipTextOverlayVisibleAtUs(overlay, 1_500_000L))
        assertFalse(isClipTextOverlayVisibleAtUs(overlay, 2_500_000L))
    }

    @Test
    fun fullClipOverlayRemainsVisibleWithoutAnExplicitEndTime() {
        val overlay = ClipTextOverlay("Full clip")

        assertTrue(isClipTextOverlayVisibleAtMs(overlay, 0L))
        assertTrue(isClipTextOverlayVisibleAtMs(overlay, Long.MAX_VALUE))
    }

    @Test
    fun rejectsNegativeOrReversedTextTiming() {
        assertFalse(isValidClipTextOverlayTiming(-1L, 1_000L))
        assertFalse(isValidClipTextOverlayTiming(1_000L, 1_000L))
        assertFalse(isValidClipTextOverlayTiming(2_000L, 1_000L))
        assertTrue(isValidClipTextOverlayTiming(0L, null))
        assertNull(decodeClipTextOverlay("Bad range", startMs = 500L, endMs = 200L))
    }

    @Test
    fun timelineBoundsMapClipLocalCaptionTimeToSegmentPixels() {
        val overlay = ClipTextOverlay("Segment", startMs = 2_000L, endMs = 7_000L)

        assertEquals(
            ClipTextOverlayTimelineBounds(startOffsetPx = 20f, widthPx = 50f),
            calculateClipTextOverlayTimelineBounds(100f, 10_000L, overlay),
        )
        assertEquals(
            ClipTextOverlayTimelineBounds(startOffsetPx = 0f, widthPx = 100f),
            calculateClipTextOverlayTimelineBounds(100f, 10_000L, ClipTextOverlay("Full clip")),
        )
        assertNull(calculateClipTextOverlayTimelineBounds(100f, 0L, overlay))
    }

    @Test
    fun splittingCaptionKeepsOnlyTheVisibleIntersectionOnEachVideoHalf() {
        val overlay = ClipTextOverlay("Middle caption", 1.25f, ClipTextPosition.TOP, 2_000L, 7_000L)

        val (left, right) = splitClipTextOverlayAt(overlay, splitPositionMs = 5_000L, clipDurationMs = 10_000L)

        assertEquals(ClipTextOverlay("Middle caption", 1.25f, ClipTextPosition.TOP, 2_000L, 5_000L), left)
        assertEquals(ClipTextOverlay("Middle caption", 1.25f, ClipTextPosition.TOP, 0L, 2_000L), right)
    }

    @Test
    fun splitPreservesEveryTextLayerOnItsVisibleVideoHalf() {
        val overlays = listOf(
            ClipTextOverlay("Early", startMs = 500L, endMs = 2_000L),
            ClipTextOverlay("Late", 1.25f, ClipTextPosition.TOP, 6_000L, 9_000L),
        )

        val (left, right) = splitClipTextOverlaysAt(overlays, 5_000L, 10_000L)

        assertEquals(listOf(overlays[0]), left)
        assertEquals(
            listOf(ClipTextOverlay("Late", 1.25f, ClipTextPosition.TOP, 1_000L, 4_000L)),
            right,
        )
    }

    @Test
    fun trimRebasesAndDropsEachTextLayerIndependently() {
        val overlays = listOf(
            ClipTextOverlay("Keep", startMs = 1_000L, endMs = 5_000L),
            ClipTextOverlay("Drop", startMs = 7_000L, endMs = 9_000L),
        )

        assertEquals(
            listOf(ClipTextOverlay("Keep", startMs = 0L, endMs = 1_000L)),
            rebaseClipTextOverlaysForTrim(
                overlays,
                oldSourceStartMs = 0L,
                oldSourceEndMs = 10_000L,
                newSourceStartMs = 4_000L,
                newSourceEndMs = 6_000L,
            ),
        )
    }

    @Test
    fun selectingTextLayerSeeksToItsInclusiveInPointWithinTheClip() {
        assertEquals(1_250L, clipTextOverlaySeekPositionMs(1_250L, 5_000L))
        assertEquals(0L, clipTextOverlaySeekPositionMs(null, 5_000L))
        assertEquals(0L, clipTextOverlaySeekPositionMs(-50L, 5_000L))
        assertEquals(4_999L, clipTextOverlaySeekPositionMs(5_000L, 5_000L))
        assertEquals(0L, clipTextOverlaySeekPositionMs(1_000L, 0L))
    }

    @Test
    fun trimmingClipRebasesCaptionAgainstTheNewSourceInPoint() {
        val overlay = ClipTextOverlay("Trimmed caption", startMs = 2_000L, endMs = 7_000L)

        assertEquals(
            ClipTextOverlay("Trimmed caption", startMs = 0L, endMs = 4_000L),
            rebaseClipTextOverlayForTrim(
                overlay,
                oldSourceStartMs = 0L,
                oldSourceEndMs = 10_000L,
                newSourceStartMs = 3_000L,
                newSourceEndMs = 8_000L,
            ),
        )
        assertNull(
            rebaseClipTextOverlayForTrim(
                overlay,
                oldSourceStartMs = 0L,
                oldSourceEndMs = 10_000L,
                newSourceStartMs = 8_000L,
                newSourceEndMs = 9_000L,
            ),
        )
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
