package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipVisualAdjustmentsTest {
    @Test
    fun tonalPresetsResolveToBoundedNonDestructiveAdjustments() {
        assertEquals(ClipVisualAdjustments(), clipVisualPresetAdjustments("Original"))
        assertEquals(
            ClipVisualAdjustments(brightness = -8, contrast = 20, saturation = -12),
            clipVisualPresetAdjustments("Cinema"),
        )
        assertEquals(
            ClipVisualAdjustments(brightness = 5, contrast = 12, saturation = 35),
            clipVisualPresetAdjustments("Vivid"),
        )
        assertEquals(
            ClipVisualAdjustments(brightness = 2, contrast = -8, saturation = -40),
            clipVisualPresetAdjustments("Muted"),
        )
        assertEquals(
            ClipVisualAdjustments(brightness = 0, contrast = 12, saturation = -100),
            clipVisualPresetAdjustments("Mono"),
        )
    }

    @Test
    fun unknownPresetDoesNotChangeAClip() {
        assertNull(clipVisualPresetAdjustments("Warm"))
        assertNull(clipVisualPresetAdjustments("Not a preset"))
    }

    @Test
    fun hueShiftIsBoundedToAHalfTurnInEitherDirection() {
        assertEquals(
            ClipVisualAdjustments(hueShiftDegrees = -180),
            decodeClipVisualAdjustments(0, 0, 0, -180),
        )
        assertEquals(
            ClipVisualAdjustments(hueShiftDegrees = 180),
            decodeClipVisualAdjustments(0, 0, 0, 180),
        )
        assertNull(decodeClipVisualAdjustments(0, 0, 0, -181))
        assertNull(decodeClipVisualAdjustments(0, 0, 0, 181))
    }

    @Test
    fun olderAdjustmentPayloadsKeepTheirZeroHueDefault() {
        assertEquals(
            ClipVisualAdjustments(brightness = 10, contrast = -5, saturation = 20),
            decodeClipVisualAdjustments(10, -5, 20),
        )
    }

    @Test
    fun effectSpecsIncludeHueRotationAndKeepUntouchedClipsAsNoOps() {
        assertEquals(emptyList<ClipVisualEffectSpec>(), createClipVisualEffectSpecs(ClipVisualAdjustments()))
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.HUE_SHIFT, 90f)),
            createClipVisualEffectSpecs(ClipVisualAdjustments(hueShiftDegrees = 90)),
        )
        assertEquals(
            listOf(
                ClipVisualEffectSpec(ClipVisualEffectType.BRIGHTNESS, 0.25f),
                ClipVisualEffectSpec(ClipVisualEffectType.CONTRAST, -0.1f),
                ClipVisualEffectSpec(ClipVisualEffectType.HUE_SHIFT, 90f),
                ClipVisualEffectSpec(ClipVisualEffectType.SATURATION, 20f),
            ),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(
                    brightness = 25,
                    contrast = -10,
                    saturation = 20,
                    hueShiftDegrees = 90,
                ),
            ),
        )
    }

    @Test
    fun everyPresetCanBeAppliedThroughTheExistingValidatedAdjustmentModel() {
        ClipVisualPreset.values().forEach { preset ->
            val adjustments = preset.adjustments
            assertEquals(
                true,
                isValidClipVisualAdjustments(
                    adjustments.brightness,
                    adjustments.contrast,
                    adjustments.saturation,
                    adjustments.hueShiftDegrees,
                ),
            )
        }
    }
}
