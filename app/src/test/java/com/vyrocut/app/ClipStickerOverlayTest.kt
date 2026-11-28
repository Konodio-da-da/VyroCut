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
    fun stickerScaleUpdatesClampAndPreserveOtherOverlayProperties() {
        val sticker = ClipStickerOverlay(
            "🚀",
            scale = 1.25f,
            position = ClipStickerPosition.TOP_LEFT,
            startMs = 1_000L,
            endMs = 3_500L,
        )

        assertEquals(
            sticker.copy(scale = 1.5f),
            updateClipStickerOverlayScale(sticker, 1.5f),
        )
        assertEquals(
            sticker.copy(scale = MIN_CLIP_STICKER_SCALE),
            updateClipStickerOverlayScale(sticker, 0.1f),
        )
        assertEquals(
            sticker.copy(scale = MAX_CLIP_STICKER_SCALE),
            updateClipStickerOverlayScale(sticker, 3f),
        )
    }

    @Test
    fun stickerScaleUpdatesRejectNonFiniteValues() {
        val sticker = ClipStickerOverlay("🔥")

        assertNull(updateClipStickerOverlayScale(sticker, Float.NaN))
        assertNull(updateClipStickerOverlayScale(sticker, Float.POSITIVE_INFINITY))
        assertNull(updateClipStickerOverlayScale(sticker, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun rotationDefaultsToZeroAndRejectsValuesOutsideHalfTurn() {
        assertEquals(0, decodeClipStickerOverlay("🔥")?.rotationDegrees)
        assertTrue(isValidClipStickerOverlayRotation(-180))
        assertTrue(isValidClipStickerOverlayRotation(180))
        assertFalse(isValidClipStickerOverlayRotation(-181))
        assertFalse(isValidClipStickerOverlayRotation(181))
        assertNull(decodeClipStickerOverlay("🔥", rotationDegrees = 181))
    }

    @Test
    fun rotationUpdatesClampFiniteValuesAndRejectNonFiniteValues() {
        val sticker = ClipStickerOverlay("⭐", scale = 1.25f, rotationDegrees = 45)

        assertEquals(sticker.copy(rotationDegrees = 90), updateClipStickerOverlayRotation(sticker, 90f))
        assertEquals(sticker.copy(rotationDegrees = -180), updateClipStickerOverlayRotation(sticker, -240f))
        assertEquals(sticker.copy(rotationDegrees = 180), updateClipStickerOverlayRotation(sticker, 240f))
        assertNull(updateClipStickerOverlayRotation(sticker, Float.NaN))
        assertNull(updateClipStickerOverlayRotation(sticker, Float.POSITIVE_INFINITY))
        assertNull(updateClipStickerOverlayRotation(sticker, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun stickerOpacityDefaultsToOpaqueAndRejectsInvalidValues() {
        assertEquals(1f, decodeClipStickerOverlay("🔥")?.opacity ?: -1f, 0f)
        assertTrue(isValidClipStickerOverlayOpacity(0f))
        assertTrue(isValidClipStickerOverlayOpacity(0.5f))
        assertTrue(isValidClipStickerOverlayOpacity(1f))
        assertFalse(isValidClipStickerOverlayOpacity(-0.01f))
        assertFalse(isValidClipStickerOverlayOpacity(1.01f))
        assertFalse(isValidClipStickerOverlayOpacity(Float.NaN))
        assertFalse(isValidClipStickerOverlayOpacity(Float.POSITIVE_INFINITY))
        assertNull(decodeClipStickerOverlay("🔥", opacity = Float.NaN))
        assertNull(decodeClipStickerOverlay("🔥", opacity = 1.1f))
    }

    @Test
    fun stickerOpacityUpdatesClampFiniteValuesAndRejectNonFiniteValues() {
        val sticker = ClipStickerOverlay(
            symbol = "✨",
            scale = 1.4f,
            position = ClipStickerPosition.TOP_LEFT,
            startMs = 250L,
            endMs = 2_000L,
            assetId = "sparkle",
            rotationDegrees = 35,
        )
        assertEquals(sticker.copy(opacity = 0.35f), updateClipStickerOverlayOpacity(sticker, 0.35f))
        assertEquals(sticker.copy(opacity = 0f), updateClipStickerOverlayOpacity(sticker, -1f))
        assertEquals(sticker.copy(opacity = 1f), updateClipStickerOverlayOpacity(sticker, 2f))
        assertNull(updateClipStickerOverlayOpacity(sticker, Float.NaN))
        assertNull(updateClipStickerOverlayOpacity(sticker, Float.POSITIVE_INFINITY))
        assertNull(updateClipStickerOverlayOpacity(sticker, Float.NEGATIVE_INFINITY))
    }

    @Test
    fun stickerOpacityMapsToEightBitAlphaWithSafeBoundaries() {
        assertEquals(0, clipStickerOpacityAlpha(0f))
        assertEquals(128, clipStickerOpacityAlpha(0.5f))
        assertEquals(255, clipStickerOpacityAlpha(1f))
        assertNull(clipStickerOpacityAlpha(Float.NaN))
        assertNull(clipStickerOpacityAlpha(-0.1f))
    }

    @Test
    fun stickerOpacitySurvivesSplitAndSourceTrimRebasing() {
        val sticker = ClipStickerOverlay(
            symbol = "🔥",
            startMs = 500L,
            endMs = 2_500L,
            assetId = "fire",
            opacity = 0.4f,
        )
        val (left, right) = splitClipStickerOverlayAt(sticker, 1_500L, 3_000L)
        assertEquals(0.4f, left?.opacity ?: -1f, 0f)
        assertEquals(0.4f, right?.opacity ?: -1f, 0f)

        val rebased = rebaseClipStickerOverlayForTrim(
            overlay = sticker,
            oldSourceStartMs = 0L,
            oldSourceEndMs = 3_000L,
            newSourceStartMs = 250L,
            newSourceEndMs = 3_000L,
        )
        assertEquals(0.4f, rebased?.opacity ?: -1f, 0f)
    }

    @Test
    fun stickerFadeDurationsDefaultToZeroAndRejectInvalidValues() {
        val legacy = decodeClipStickerOverlay("✨")
        assertEquals(0L, legacy?.fadeInMs)
        assertEquals(0L, legacy?.fadeOutMs)
        assertTrue(isValidClipStickerOverlayFadeDurations(0L, 0L))
        assertTrue(isValidClipStickerOverlayFadeDurations(1_500L, 3_000L))
        assertFalse(isValidClipStickerOverlayFadeDurations(-1L, 0L))
        assertFalse(isValidClipStickerOverlayFadeDurations(0L, 3_001L))
        assertNull(decodeClipStickerOverlay("✨", fadeInMs = -1L))
        assertNull(decodeClipStickerOverlay("✨", fadeOutMs = 3_001L))
    }

    @Test
    fun stickerFadeUpdatesClampToTheVisibleClipLocalInterval() {
        val sticker = ClipStickerOverlay("✨", startMs = 500L, endMs = 2_500L)

        assertEquals(
            sticker.copy(fadeInMs = 2_000L),
            updateClipStickerOverlayFadeIn(sticker, 5_000L, 5_000L),
        )
        assertEquals(
            sticker.copy(fadeOutMs = 1_250L),
            updateClipStickerOverlayFadeOut(sticker, 5_000L, 1_250L),
        )
        assertEquals(sticker, updateClipStickerOverlayFadeIn(sticker, 5_000L, -1L))
        assertEquals(null, updateClipStickerOverlayFadeOut(sticker, 0L, 500L))
    }

    @Test
    fun stickerFadeAlphaUsesClipLocalTimeAndCombinesBothRamps() {
        val sticker = ClipStickerOverlay(
            symbol = "✨",
            startMs = 1_000L,
            endMs = 5_000L,
            fadeInMs = 1_000L,
            fadeOutMs = 1_000L,
        )

        assertEquals(0f, clipStickerFadeAlphaScale(sticker, 999L, 6_000L), 0f)
        assertEquals(0f, clipStickerFadeAlphaScale(sticker, 1_000L, 6_000L), 0f)
        assertEquals(0.5f, clipStickerFadeAlphaScale(sticker, 1_500L, 6_000L), 0.0001f)
        assertEquals(1f, clipStickerFadeAlphaScale(sticker, 2_000L, 6_000L), 0.0001f)
        assertEquals(1f, clipStickerFadeAlphaScale(sticker, 4_000L, 6_000L), 0.0001f)
        assertEquals(0.5f, clipStickerFadeAlphaScale(sticker, 4_500L, 6_000L), 0.0001f)
        assertEquals(0f, clipStickerFadeAlphaScale(sticker, 5_000L, 6_000L), 0f)
        assertEquals(0f, clipStickerFadeAlphaScale(sticker, 4_000L, 0L), 0f)

        val overlapping = sticker.copy(startMs = 0L, endMs = 1_000L, fadeInMs = 800L, fadeOutMs = 800L)
        assertEquals(0.625f, clipStickerFadeAlphaScale(overlapping, 500L, 1_000L), 0.0001f)
    }

    @Test
    fun openEndedStickerFadeOutUsesTheOwningClipDuration() {
        val sticker = ClipStickerOverlay("🔥", fadeOutMs = 1_000L)

        assertEquals(1f, clipStickerFadeAlphaScale(sticker, 1_000L, 2_000L), 0.0001f)
        assertEquals(0.5f, clipStickerFadeAlphaScale(sticker, 1_500L, 2_000L), 0.0001f)
        assertEquals(0f, clipStickerFadeAlphaScale(sticker, 2_000L, 2_000L), 0f)
    }

    @Test
    fun stickerFadeSettingsSurviveSplitAndSourceTrimRebasing() {
        val sticker = ClipStickerOverlay(
            symbol = "🔥",
            startMs = 500L,
            endMs = 2_500L,
            fadeInMs = 400L,
            fadeOutMs = 600L,
        )
        val (left, right) = splitClipStickerOverlayAt(sticker, 1_500L, 3_000L)
        assertEquals(400L, left?.fadeInMs)
        assertEquals(600L, left?.fadeOutMs)
        assertEquals(400L, right?.fadeInMs)
        assertEquals(600L, right?.fadeOutMs)

        val rebased = rebaseClipStickerOverlayForTrim(
            overlay = sticker,
            oldSourceStartMs = 0L,
            oldSourceEndMs = 3_000L,
            newSourceStartMs = 250L,
            newSourceEndMs = 3_000L,
        )
        assertEquals(400L, rebased?.fadeInMs)
        assertEquals(600L, rebased?.fadeOutMs)
    }

    @Test
    fun stickerRotationSurvivesSplitAndSourceTrimRebasing() {
        val sticker = ClipStickerOverlay(
            symbol = "⭐",
            position = ClipStickerPosition.TOP_RIGHT,
            startMs = 500L,
            endMs = 2_500L,
            assetId = "star",
            rotationDegrees = -37,
        )

        val (left, right) = splitClipStickerOverlayAt(sticker, 1_500L, 3_000L)
        assertEquals(-37, left?.rotationDegrees)
        assertEquals(-37, right?.rotationDegrees)
        assertEquals("star", right?.assetId)

        val rebased = rebaseClipStickerOverlayForTrim(
            sticker,
            oldSourceStartMs = 0L,
            oldSourceEndMs = 3_000L,
            newSourceStartMs = 250L,
            newSourceEndMs = 3_000L,
        )
        assertEquals(-37, rebased?.rotationDegrees)
        assertEquals("star", rebased?.assetId)
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
    fun stickerTimingSliderBoundsKeepInAndOutSeparatedAndInsideTheClip() {
        val bounds = calculateClipStickerTimingSliderBounds(
            clipDurationMs = 5_000L,
            requestedStartMs = 1_000L,
            requestedEndMs = 4_000L,
        )

        assertEquals(1_000L, bounds?.startMs)
        assertEquals(4_000L, bounds?.endMs)
        assertEquals(3_999L, bounds?.startSliderMaxMs)
        assertEquals(1_001L, bounds?.endSliderMinMs)
    }

    @Test
    fun stickerTimingSliderBoundsClampRequestsAndHandleOneMillisecondIntervals() {
        val clamped = calculateClipStickerTimingSliderBounds(
            clipDurationMs = 5_000L,
            requestedStartMs = -100L,
            requestedEndMs = 9_000L,
        )
        assertEquals(0L, clamped?.startMs)
        assertEquals(5_000L, clamped?.endMs)
        assertEquals(4_999L, clamped?.startSliderMaxMs)
        assertEquals(1L, clamped?.endSliderMinMs)

        val oneMillisecond = calculateClipStickerTimingSliderBounds(
            clipDurationMs = 1L,
            requestedStartMs = 0L,
            requestedEndMs = 1L,
        )
        assertEquals(0L, oneMillisecond?.startSliderMaxMs)
        assertEquals(1L, oneMillisecond?.endSliderMinMs)
        assertNull(
            calculateClipStickerTimingSliderBounds(
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
    @Test
    fun builtInVectorStickerPackHasUniqueStableIdsAndDrawableResources() {
        val assets = ClipStickerAsset.values().toList()

        assertEquals(10, assets.size)
        assertEquals(
            listOf("star", "heart", "sparkle", "bolt", "crown", "flower", "clapper", "camera", "fire", "like"),
            assets.map { it.id },
        )
        assertEquals(assets.size, assets.map { it.id }.toSet().size)
        assertEquals(assets.size, assets.map { it.drawableResId }.toSet().size)
        assets.forEach { asset ->
            assertTrue(isValidClipStickerAssetId(asset.id))
            assertTrue(asset.label.isNotBlank())
            assertTrue(asset.drawableResId > 0)
            assertTrue(isValidClipStickerOverlaySymbol(asset.fallbackSymbol))
            assertEquals(asset, ClipStickerAsset.fromId(asset.id))
        }
    }

    @Test
    fun stickerLayerSelectionAcceptsOnlyAnExistingLayerIndex() {
        assertEquals(0, resolveClipStickerLayerSelection(0, 1))
        assertEquals(4, resolveClipStickerLayerSelection(4, MAX_CLIP_STICKER_OVERLAYS))
        assertNull(resolveClipStickerLayerSelection(-1, 3))
        assertNull(resolveClipStickerLayerSelection(3, 3))
        assertNull(resolveClipStickerLayerSelection(0, 0))
        assertNull(resolveClipStickerLayerSelection(0, MAX_CLIP_STICKER_OVERLAYS + 1))
    }

    @Test
    fun duplicatingStickerPreservesItsSettingsAndUsesAnUnusedVisiblePosition() {
        val original = ClipStickerOverlay(
            symbol = "⭐",
            scale = 1.4f,
            position = ClipStickerPosition.BOTTOM_RIGHT,
            startMs = 600L,
            endMs = 3_200L,
            assetId = "star",
            rotationDegrees = 45,
        )
        val other = ClipStickerOverlay(
            symbol = "❤️",
            position = ClipStickerPosition.TOP_LEFT,
        )
        val originalLayers = listOf(original, other)

        val result = duplicateClipStickerOverlay(originalLayers, selectedIndex = 0)

        assertEquals(
            listOf(original, other, original.copy(position = ClipStickerPosition.TOP_RIGHT)),
            result?.overlays,
        )
        assertEquals(2, result?.selectedIndex)
        assertEquals(listOf(original, other), originalLayers)
    }

    @Test
    fun stickerDuplicationRejectsInvalidSelectionAndFullLayerCapacity() {
        val sticker = ClipStickerOverlay(symbol = "⭐")
        assertNull(duplicateClipStickerOverlay(emptyList(), 0))
        assertNull(duplicateClipStickerOverlay(listOf(sticker), -1))
        assertNull(duplicateClipStickerOverlay(listOf(sticker), 1))

        val fullLayers = ClipStickerPosition.values().map { position ->
            ClipStickerOverlay(symbol = "⭐", position = position)
        }
        assertNull(duplicateClipStickerOverlay(fullLayers, 0))
        assertNull(duplicateClipStickerOverlay(fullLayers + sticker, 0))
    }

    @Test
    fun assetStickerIdsDecodeAndUnknownFutureAssetsKeepTheirUnicodeFallback() {
        val builtIn = decodeClipStickerOverlay("⭐", assetId = "star")
        assertEquals("star", builtIn?.assetId)
        assertEquals(ClipStickerAsset.STAR, ClipStickerAsset.fromId(builtIn?.assetId))

        val future = decodeClipStickerOverlay("✨", assetId = "future-shape")
        assertEquals("future-shape", future?.assetId)
        assertNull(ClipStickerAsset.fromId(future?.assetId))

        assertNull(decodeClipStickerOverlay("⭐", assetId = "invalid/id"))
        assertFalse(isValidClipStickerAssetId("UpperCase"))
        assertTrue(isValidClipStickerAssetId(null))
    }

    @Test
    fun builtInAssetIdentitySurvivesStickerSplitAndSourceTrimRebasing() {
        val sticker = ClipStickerOverlay(
            symbol = "⭐",
            startMs = 500L,
            endMs = 2_500L,
            assetId = "star",
        )

        val (left, right) = splitClipStickerOverlayAt(
            overlay = sticker,
            splitPositionMs = 1_500L,
            clipDurationMs = 3_000L,
        )
        assertEquals("star", left?.assetId)
        assertEquals("star", right?.assetId)

        val rebased = rebaseClipStickerOverlayForTrim(
            overlay = sticker,
            oldSourceStartMs = 0L,
            oldSourceEndMs = 3_000L,
            newSourceStartMs = 250L,
            newSourceEndMs = 3_000L,
        )
        assertEquals("star", rebased?.assetId)
    }

}
