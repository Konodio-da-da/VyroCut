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
    fun everyPresetCanBeAppliedThroughTheExistingValidatedAdjustmentModel() {
        ClipVisualPreset.values().forEach { preset ->
            val adjustments = preset.adjustments
            assertEquals(
                true,
                isValidClipVisualAdjustments(
                    adjustments.brightness,
                    adjustments.contrast,
                    adjustments.saturation,
                ),
            )
        }
    }
}
