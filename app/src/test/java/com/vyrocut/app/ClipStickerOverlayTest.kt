package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipStickerOverlayTest {
    @Test
    fun decoderTrimsSymbolsAndDefaultsToBottomRight() {
        assertEquals(
            ClipStickerOverlay("🔥"),
            decodeClipStickerOverlay("  🔥  "),
        )
        assertEquals(
            ClipStickerOverlay("✨", position = ClipStickerPosition.TOP_LEFT),
            decodeClipStickerOverlay("✨", position = "TOP_LEFT"),
        )
    }

    @Test
    fun rejectsBlankControlAndExcessivelyLongStickerSymbols() {
        assertFalse(isValidClipStickerOverlaySymbol("  "))
        assertFalse(isValidClipStickerOverlaySymbol("star\u0001"))
        assertFalse(isValidClipStickerOverlaySymbol("123456789"))
        assertNull(decodeClipStickerOverlay(""))
        assertNull(decodeClipStickerOverlay("123456789"))
        assertTrue(isValidClipStickerOverlaySymbol("👨‍👩‍👧‍👦"))
    }

    @Test
    fun stickerScaleMustStayWithinSupportedFiniteRange() {
        assertTrue(isValidClipStickerOverlayScale(0.5f))
        assertTrue(isValidClipStickerOverlayScale(2f))
        assertFalse(isValidClipStickerOverlayScale(0.49f))
        assertFalse(isValidClipStickerOverlayScale(2.01f))
        assertFalse(isValidClipStickerOverlayScale(Float.NaN))
        assertFalse(isValidClipStickerOverlayScale(Float.POSITIVE_INFINITY))
        assertNull(decodeClipStickerOverlay("🔥", scale = 2.1f))
    }

    @Test
    fun timedStickerUsesInclusiveStartAndExclusiveEnd() {
        val sticker = ClipStickerOverlay("🔥", startMs = 1_000L, endMs = 2_500L)

        assertFalse(isClipStickerOverlayVisibleAtMs(sticker, 999L))
        assertTrue(isClipStickerOverlayVisibleAtMs(sticker, 1_000L))
        assertTrue(isClipStickerOverlayVisibleAtMs(sticker, 2_499L))
        assertFalse(isClipStickerOverlayVisibleAtMs(sticker, 2_500L))
        assertFalse(isClipStickerOverlayVisibleAtUs(sticker, -1L))
        assertTrue(isClipStickerOverlayVisibleAtUs(sticker, 1_500_000L))
        assertFalse(isClipStickerOverlayVisibleAtUs(sticker, 2_500_000L))
    }

    @Test
    fun rejectsNegativeOrReversedStickerTimingAndUnknownPositions() {
        assertFalse(isValidClipStickerOverlayTiming(-1L, 1_000L))
        assertFalse(isValidClipStickerOverlayTiming(1_000L, 1_000L))
        assertFalse(isValidClipStickerOverlayTiming(2_000L, 1_000L))
        assertTrue(isValidClipStickerOverlayTiming(0L, null))
        assertNull(decodeClipStickerOverlay("🔥", startMs = 500L, endMs = 200L))
        assertNull(decodeClipStickerOverlay("🔥", position = "NOT_A_POSITION"))
    }

    @Test
    fun timingUpdatesStayInsideTheClipAndUseClipEndSentinel() {
        val sticker = ClipStickerOverlay("🔥", startMs = 500L, endMs = 2_500L)

        assertEquals(
            ClipStickerOverlay("🔥", startMs = 1_000L, endMs = 4_000L),
            updateClipStickerOverlayTiming(
                sticker,
                clipDurationMs = 5_000L,
                requestedStartMs = 1_000L,
                requestedEndMs = 4_000L,
            ),
        )
        assertEquals(
            ClipStickerOverlay("🔥", startMs = 0L),
            updateClipStickerOverlayTiming(
                sticker,
                clipDurationMs = 5_000L,
                requestedStartMs = -100L,
                requestedEndMs = 9_000L,
            ),
        )
        assertEquals(
            ClipStickerOverlay("🔥", startMs = 4_999L),
            updateClipStickerOverlayTiming(
                sticker,
                clipDurationMs = 5_000L,
                requestedStartMs = 9_000L,
                requestedEndMs = 1_000L,
            ),
        )
        assertNull(
            updateClipStickerOverlayTiming(
                sticker,
                clipDurationMs = 0L,
                requestedStartMs = 0L,
                requestedEndMs = 0L,
            ),
        )
    }

    @Test
    fun splitKeepsOnlyTheVisibleStickerIntervalOnEachVideoHalf() {
        val sticker = ClipStickerOverlay(
            "🚀",
            1.25f,
            ClipStickerPosition.TOP_RIGHT,
            startMs = 2_000L,
            endMs = 7_000L,
        )

        val (left, right) = splitClipStickerOverlayAt(sticker, 5_000L, 10_000L)

        assertEquals(
            ClipStickerOverlay("🚀", 1.25f, ClipStickerPosition.TOP_RIGHT, 2_000L, 5_000L),
            left,
        )
        assertEquals(
            ClipStickerOverlay("🚀", 1.25f, ClipStickerPosition.TOP_RIGHT, 0L, 2_000L),
            right,
        )
    }

    @Test
    fun trimRebasesStickerToRetainedSourceIntersectionAndDropsOutsideStickers() {
        val overlays = listOf(
            ClipStickerOverlay("🔥", startMs = 1_000L, endMs = 5_000L),
            ClipStickerOverlay("✨", startMs = 7_000L, endMs = 9_000L),
        )

        assertEquals(
            listOf(ClipStickerOverlay("🔥", startMs = 0L, endMs = 1_000L)),
            rebaseClipStickerOverlaysForTrim(
                overlays,
                oldSourceStartMs = 0L,
                oldSourceEndMs = 10_000L,
                newSourceStartMs = 4_000L,
                newSourceEndMs = 6_000L,
            ),
        )
    }

    @Test
    fun stickerTimelineBoundsMapClipLocalInAndOutToTrackWidth() {
        val fullClip = calculateClipStickerOverlayTimelineBounds(
            trackWidthPx = 120f,
            clipDurationMs = 6_000L,
            overlay = ClipStickerOverlay("🔥"),
        )
        assertEquals(0f, fullClip!!.startOffsetPx, 0.001f)
        assertEquals(120f, fullClip.widthPx, 0.001f)

        val timed = calculateClipStickerOverlayTimelineBounds(
            trackWidthPx = 120f,
            clipDurationMs = 6_000L,
            overlay = ClipStickerOverlay("✨", startMs = 1_500L, endMs = 4_500L),
        )
        assertEquals(30f, timed!!.startOffsetPx, 0.001f)
        assertEquals(60f, timed.widthPx, 0.001f)
    }

    @Test
    fun stickerTimelineBoundsClampToClipAndRejectInvalidGeometry() {
        val clamped = calculateClipStickerOverlayTimelineBounds(
            trackWidthPx = 80f,
            clipDurationMs = 4_000L,
            overlay = ClipStickerOverlay("🚀", startMs = 3_000L, endMs = 9_000L),
        )
        assertEquals(60f, clamped!!.startOffsetPx, 0.001f)
        assertEquals(20f, clamped.widthPx, 0.001f)

        assertNull(calculateClipStickerOverlayTimelineBounds(0f, 4_000L, ClipStickerOverlay("🔥")))
        assertNull(calculateClipStickerOverlayTimelineBounds(Float.NaN, 4_000L, ClipStickerOverlay("🔥")))
        assertNull(calculateClipStickerOverlayTimelineBounds(80f, 0L, ClipStickerOverlay("🔥")))
        assertNull(
            calculateClipStickerOverlayTimelineBounds(
                80f,
                4_000L,
                ClipStickerOverlay("🔥", startMs = 5_000L),
            ),
        )
    }

    @Test
    fun bitmapSizingPreservesAspectRatioAndCapsLongEdge() {
        assertEquals(
            ClipStickerOverlayBitmapSize(1_080, 1_920),
            calculateClipStickerOverlayBitmapSize(1_080, 1_920),
        )
        assertEquals(
            ClipStickerOverlayBitmapSize(1_920, 1_080),
            calculateClipStickerOverlayBitmapSize(3_840, 2_160),
        )
        assertNull(calculateClipStickerOverlayBitmapSize(0, 1_080))
        assertNull(calculateClipStickerOverlayBitmapSize(1_920, -1))
    }

    @Test
    fun stickerLayoutKeepsEachPresetInsidePortraitLandscapeAndSquareFrames() {
        listOf(1_920 to 1_080, 1_080 to 1_920, 1_080 to 1_080).forEach { (width, height) ->
            ClipStickerPosition.values().forEach { position ->
                val layout = calculateClipStickerOverlayLayout(width, height, 2f, position)
                assertTrue(layout != null)
                assertTrue(layout!!.centerXPx >= 0f && layout.centerXPx <= width.toFloat())
                assertTrue(layout.centerYPx >= 0f && layout.centerYPx <= height.toFloat())
                assertTrue(layout.textSizePx in 24f..360f)
            }
        }
        assertNull(calculateClipStickerOverlayLayout(0, 1_080))
        assertNull(calculateClipStickerOverlayLayout(1_920, 1_080, Float.NaN))
    }
}
