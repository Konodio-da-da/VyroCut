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
    fun resettingCreativeEffectRestoresOriginalAndPreservesManualAdjustments() {
        val adjustments = ClipVisualAdjustments(
            brightness = 18,
            contrast = -12,
            saturation = 27,
            hueShiftDegrees = 45,
            creativeEffectPreset = ClipCreativeEffectPreset.SEPIA,
            creativeEffectIntensity = 38,
        )

        assertEquals(
            ClipVisualAdjustments(
                brightness = 18,
                contrast = -12,
                saturation = 27,
                hueShiftDegrees = 45,
                creativeEffectPreset = ClipCreativeEffectPreset.ORIGINAL,
                creativeEffectIntensity = DEFAULT_CREATIVE_EFFECT_INTENSITY,
            ),
            resetClipCreativeEffect(adjustments),
        )
    }

    @Test
    fun resettingAnOriginalClipIsIdempotent() {
        val original = ClipVisualAdjustments(
            brightness = -8,
            contrast = 11,
            saturation = -20,
            hueShiftDegrees = -30,
        )

        assertEquals(original, resetClipCreativeEffect(original))
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
        assertEquals(ClipCreativeEffectPreset.CINEMATIC, clipCreativeEffectPresetFromId("cinematic"))
        assertEquals(ClipCreativeEffectPreset.SOFT_FOCUS, clipCreativeEffectPresetFromId("soft-focus"))
        assertEquals(ClipCreativeEffectPreset.NEGATIVE, clipCreativeEffectPresetFromId("negative"))
        assertEquals(ClipCreativeEffectPreset.GRAYSCALE, clipCreativeEffectPresetFromId("grayscale"))
        assertEquals(ClipCreativeEffectPreset.SEPIA, clipCreativeEffectPresetFromId("sepia"))
        assertEquals(ClipCreativeEffectPreset.FILM_FADE, clipCreativeEffectPresetFromId("film-fade"))
        assertEquals(ClipCreativeEffectPreset.VIGNETTE, clipCreativeEffectPresetFromId("vignette"))
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
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.CINEMATIC, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.CINEMATIC),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.SOFT_FOCUS, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.SOFT_FOCUS),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.INVERTED, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.NEGATIVE),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.GRAYSCALE, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.GRAYSCALE),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.SEPIA, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.SEPIA),
            ),
        )
        assertEquals(
            listOf(ClipVisualEffectSpec(ClipVisualEffectType.FILM_FADE, 1f)),
            createClipVisualEffectSpecs(
                ClipVisualAdjustments(creativeEffectPreset = ClipCreativeEffectPreset.FILM_FADE),
            ),
        )
    }

    @Test
    fun softFocusSigmaScalesWithStrengthAndRejectsInvalidInputs() {
        assertEquals(0f, softFocusSigmaPx(0f), 0.0001f)
        assertEquals(1.25f, softFocusSigmaPx(0.25f), 0.0001f)
        assertEquals(MAX_SOFT_FOCUS_SIGMA_PX, softFocusSigmaPx(1f), 0.0001f)
        assertEquals(false, runCatching { softFocusSigmaPx(-0.01f) }.isSuccess)
        assertEquals(false, runCatching { softFocusSigmaPx(1.01f) }.isSuccess)
        assertEquals(false, runCatching { softFocusSigmaPx(Float.NaN) }.isSuccess)
    }

    @Test
    fun vignetteOverlayBitmapSizePreservesAspectRatioAndCapsTheLongestEdge() {
        assertEquals(
            VignetteOverlayBitmapSize(1_920, 1_080),
            calculateVignetteOverlayBitmapSize(3_840, 2_160),
        )
        assertEquals(
            VignetteOverlayBitmapSize(1_080, 1_920),
            calculateVignetteOverlayBitmapSize(1_080, 1_920),
        )
        assertEquals(
            VignetteOverlayBitmapSize(640, 640),
            calculateVignetteOverlayBitmapSize(640, 640),
        )
        assertNull(calculateVignetteOverlayBitmapSize(0, 1_080))
        assertNull(calculateVignetteOverlayBitmapSize(1_920, -1))
    }

    @Test
    fun vignetteOverlayEdgeAlphaScalesWithIntensityAndRejectsInvalidValues() {
        assertEquals(0, vignetteOverlayEdgeAlpha(0f))
        assertEquals(MAX_VIGNETTE_EDGE_ALPHA / 2, vignetteOverlayEdgeAlpha(0.5f))
        assertEquals(MAX_VIGNETTE_EDGE_ALPHA, vignetteOverlayEdgeAlpha(1f))
        assertEquals(false, runCatching { vignetteOverlayEdgeAlpha(-0.01f) }.isSuccess)
        assertEquals(false, runCatching { vignetteOverlayEdgeAlpha(1.01f) }.isSuccess)
        assertEquals(false, runCatching { vignetteOverlayEdgeAlpha(Float.NaN) }.isSuccess)
        assertEquals(false, runCatching { vignetteOverlayEdgeAlpha(Float.POSITIVE_INFINITY) }.isSuccess)
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
            ClipCreativeEffectPreset.CINEMATIC to ClipVisualEffectType.CINEMATIC,
            ClipCreativeEffectPreset.SOFT_FOCUS to ClipVisualEffectType.SOFT_FOCUS,
            ClipCreativeEffectPreset.NEGATIVE to ClipVisualEffectType.INVERTED,
            ClipCreativeEffectPreset.GRAYSCALE to ClipVisualEffectType.GRAYSCALE,
            ClipCreativeEffectPreset.SEPIA to ClipVisualEffectType.SEPIA,
            ClipCreativeEffectPreset.FILM_FADE to ClipVisualEffectType.FILM_FADE,
            ClipCreativeEffectPreset.VIGNETTE to ClipVisualEffectType.VIGNETTE,
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
    fun effectBrowserGroupsPreserveEveryPresetInStableOrder() {
        assertEquals(
            listOf(ClipCreativeEffectPreset.WARM, ClipCreativeEffectPreset.COOL),
            clipCreativeEffectPresetsForCategory(ClipCreativeEffectCategory.COLOR),
        )
        assertEquals(
            listOf(ClipCreativeEffectPreset.CINEMATIC),
            clipCreativeEffectPresetsForCategory(ClipCreativeEffectCategory.CINEMATIC),
        )
        assertEquals(
            listOf(ClipCreativeEffectPreset.SOFT_FOCUS, ClipCreativeEffectPreset.VIGNETTE),
            clipCreativeEffectPresetsForCategory(ClipCreativeEffectCategory.LENS),
        )
        assertEquals(
            listOf(
                ClipCreativeEffectPreset.ORIGINAL,
                ClipCreativeEffectPreset.NEGATIVE,
                ClipCreativeEffectPreset.GRAYSCALE,
            ),
            clipCreativeEffectPresetsForCategory(ClipCreativeEffectCategory.MONOCHROME),
        )
        assertEquals(
            listOf(ClipCreativeEffectPreset.SEPIA, ClipCreativeEffectPreset.FILM_FADE),
            clipCreativeEffectPresetsForCategory(ClipCreativeEffectCategory.VINTAGE),
        )

        // Browser order is category-first (Color, Cinematic, Lens, Monochrome, Vintage), not
        // enum declaration order. Verify that every preset appears exactly once
        // while keeping the category ordering asserted above.
        val browserOrder = ClipCreativeEffectCategory.values()
            .flatMap(::clipCreativeEffectPresetsForCategory)
        assertEquals(
            listOf(
                ClipCreativeEffectPreset.WARM,
                ClipCreativeEffectPreset.COOL,
                ClipCreativeEffectPreset.CINEMATIC,
                ClipCreativeEffectPreset.SOFT_FOCUS,
                ClipCreativeEffectPreset.VIGNETTE,
                ClipCreativeEffectPreset.ORIGINAL,
                ClipCreativeEffectPreset.NEGATIVE,
                ClipCreativeEffectPreset.GRAYSCALE,
                ClipCreativeEffectPreset.SEPIA,
                ClipCreativeEffectPreset.FILM_FADE,
            ),
            browserOrder,
        )
        assertEquals(ClipCreativeEffectPreset.values().toSet(), browserOrder.toSet())
        assertEquals(ClipCreativeEffectPreset.values().size, browserOrder.size)
    }

    @Test
    fun everyCreativeEffectPresetHasAConciseDescriptionForTheEffectsBrowser() {
        val presets = ClipCreativeEffectPreset.values().toList()

        assertEquals(presets.size, presets.map { it.id }.toSet().size)
        presets.forEach { preset ->
            assertEquals(true, preset.description.isNotBlank())
            assertEquals(true, preset.description.length <= 120)
        }
        assertEquals(
            true,
            ClipCreativeEffectPreset.ORIGINAL.description.contains("manual", ignoreCase = true),
        )
    }

    @Test
    fun sepiaMatrixUsesColumnMajorRgbTransformAndPreservesAlpha() {
        assertArrayEquals(
            floatArrayOf(
                0.393f, 0.349f, 0.272f, 0f,
                0.769f, 0.686f, 0.534f, 0f,
                0.189f, 0.168f, 0.131f, 0f,
                0f, 0f, 0f, 1f,
            ),
            sepiaRgbMatrix(),
            0.0001f,
        )
    }

    @Test
    fun filmFadeMatrixLiftsShadowsSoftensContrastAndPreservesAlpha() {
        val matrix = filmFadeRgbMatrix()
        assertArrayEquals(
            floatArrayOf(
                0.88f, 0f, 0f, 0f,
                0f, 0.88f, 0f, 0f,
                0f, 0f, 0.88f, 0f,
                0.055f, 0.05f, 0.045f, 1f,
            ),
            matrix,
            0.0001f,
        )
        assertEquals(1f, matrix[15], 0.0001f)
        assertEquals(0f, matrix[3], 0.0001f)
        assertEquals(0f, matrix[7], 0.0001f)
        assertEquals(0f, matrix[11], 0.0001f)
        assertEquals(true, matrix[0] < 1f && matrix[5] < 1f && matrix[10] < 1f)
        assertEquals(true, matrix[12] > 0f && matrix[13] > 0f && matrix[14] > 0f)
        matrix[12] = 99f
        assertEquals(0.055f, filmFadeRgbMatrix()[12], 0.0001f)
    }

    @Test
    fun cinematicMatrixUsesCoolMatteGradeAndPreservesAlpha() {
        val matrix = cinematicRgbMatrix()
        assertArrayEquals(
            floatArrayOf(
                0.94f, 0.01f, 0.02f, 0f,
                0f, 0.96f, 0.02f, 0f,
                -0.01f, 0.02f, 0.94f, 0f,
                0.015f, 0.025f, 0.045f, 1f,
            ),
            matrix,
            0.0001f,
        )
        assertEquals(1f, matrix[15], 0.0001f)
        assertEquals(0f, matrix[3], 0.0001f)
        assertEquals(0f, matrix[7], 0.0001f)
        assertEquals(0f, matrix[11], 0.0001f)
        assertEquals(true, matrix[0] < 1f && matrix[5] < 1f && matrix[10] < 1f)
        assertEquals(true, matrix[14] > matrix[12])
        matrix[14] = 99f
        assertEquals(0.045f, cinematicRgbMatrix()[14], 0.0001f)
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
