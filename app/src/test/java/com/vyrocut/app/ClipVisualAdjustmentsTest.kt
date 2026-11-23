package com.vyrocut.app

import org.junit.Assert.assertArrayEquals
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
    @Test
    fun creativeEffectIdsResolveToStablePresetsAndRejectUnknownValues() {
        assertEquals(ClipCreativeEffectPreset.ORIGINAL, clipCreativeEffectPresetFromId(null))
        assertEquals(ClipCreativeEffectPreset.ORIGINAL, clipCreativeEffectPresetFromId("original"))
        assertEquals(ClipCreativeEffectPreset.WARM, clipCreativeEffectPresetFromId("warm"))
        assertEquals(ClipCreativeEffectPreset.COOL, clipCreativeEffectPresetFromId("cool"))
        assertEquals(ClipCreativeEffectPreset.NEGATIVE, clipCreativeEffectPresetFromId("negative"))
        assertNull(clipCreativeEffectPresetFromId("future-effect"))
        assertNull(decodeClipVisualAdjustments(0, 0, 0, creativeEffectPresetId = "future-effect"))
    }

    @Test
    fun creativeLooksProduceDeterministicRealMedia3EffectSpecs() {
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.WARM_TINT, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.WARM),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.COOL_TINT, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.COOL),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.INVERTED, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.NEGATIVE),
            ),
        )
    }

    @Test
    fun creativeLookIsComposedAfterExistingPerClipAdjustments() {
        assertEquals(
            listOf(
                ClipVisualEffectSpec(ClipVisualEffectType.BRIGHTNESS, 0.2f),
                ClipVisualEffectSpec(ClipVisualEffectType.HUE_SHIFT, 45f),
                ClipVisualEffectSpec(ClipVisualEffectType.COOL_TINT, 1f),
            ),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(
                    brightness = 20,
                    hueShiftDegrees = 45,
                    creativeEffectPreset = ClipCreativeEffectPreset.COOL,
                ),
            ),
        )
    }

    @Test
    fun creativeEffectIntensityDefaultsToFullStrengthAndRejectsOutOfRangeValues() {
        assertEquals(DEFAULT_CREATIVE_EFFECT_INTENSITY, ClipVisualAdjustments().creativeEffectIntensity)
        assertEquals(
            35,
            decodeClipVisualAdjustments(0, 0, 0, creativeEffectIntensity = 35)?.creativeEffectIntensity,
        )
        assertNull(decodeClipVisualAdjustments(0, 0, 0, creativeEffectIntensity = -1))
        assertNull(decodeClipVisualAdjustments(0, 0, 0, creativeEffectIntensity = 101))
        assertEquals(
            false,
            isValidClipVisualAdjustments(0, 0, 0, creativeEffectIntensity = 101),
        )
    }

    @Test
    fun creativeEffectIntensityScalesEveryLookAndZeroIsANoOp() {
        val cases = listOf(
            ClipCreativeEffectPreset.WARM to ClipVisualEffectType.WARM_TINT,
            ClipCreativeEffectPreset.COOL to ClipVisualEffectType.COOL_TINT,
            ClipCreativeEffectPreset.NEGATIVE to ClipVisualEffectType.INVERTED,
        )
        cases.forEach { (preset, type) ->
            assertEquals(
                listOf(ClipVisualEffectSpec(type, 0.35f)),
                createClipVisualEffectSpecs(
                    ClipVisualAdjustments(
                        creativeEffectPreset = preset,
                        creativeEffectIntensity = 35,
                    ),
                ),
            )
            assertEquals(
                emptyList<ClipVisualEffectSpec>(),
                createClipVisualEffectSpecs(
                    ClipVisualAdjustments(
                        creativeEffectPreset = preset,
                        creativeEffectIntensity = 0,
                    ),
                ),
            )
        }
    }

    @Test
    fun negativeMatrixIntensityBlendsIdentityWithFullInversion() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val inverted = floatArrayOf(
            -1f, 0f, 0f, 0f,
            0f, -1f, 0f, 0f,
            0f, 0f, -1f, 0f,
            1f, 1f, 1f, 1f,
        )
        val halfStrength = floatArrayOf(
            0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f,
            0.5f, 0.5f, 0.5f, 1f,
        )
        assertArrayEquals(identity, blendRgbMatrixWithIdentity(inverted, 0f), 0.0001f)
        assertArrayEquals(halfStrength, blendRgbMatrixWithIdentity(inverted, 0.5f), 0.0001f)
        assertArrayEquals(inverted, blendRgbMatrixWithIdentity(inverted, 1f), 0.0001f)
    }

}
