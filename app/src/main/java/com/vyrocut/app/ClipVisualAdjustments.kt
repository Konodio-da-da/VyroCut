package com.vyrocut.app

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment

/**
 * Non-destructive colour adjustments stored per video clip.
 *
 * Brightness and contrast use a signed percentage in [-100, 100].
 * Saturation uses Media3 HSL adjustment units in the same range.
 */
internal data class ClipVisualAdjustments(
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
) {
    init {
        require(isValidClipVisualAdjustments(brightness, contrast, saturation)) {
            "Clip visual adjustments must be between -100 and 100"
        }
    }
}

internal fun isValidClipVisualAdjustments(
    brightness: Int,
    contrast: Int,
    saturation: Int,
): Boolean = brightness in -100..100 &&
    contrast in -100..100 &&
    saturation in -100..100

internal fun decodeClipVisualAdjustments(
    brightness: Int,
    contrast: Int,
    saturation: Int,
): ClipVisualAdjustments? {
    if (!isValidClipVisualAdjustments(brightness, contrast, saturation)) return null
    return ClipVisualAdjustments(brightness, contrast, saturation)
}

/**
 * Builds the same ordered Media3 effect list for preview and Transformer export.
 * Zero-valued controls are omitted so untouched clips keep the no-op fast path.
 */
@OptIn(UnstableApi::class)
internal fun createClipVisualEffects(
    adjustments: ClipVisualAdjustments,
): List<Effect> = buildList {
    if (adjustments.brightness != 0) {
        add(Brightness(adjustments.brightness / 100f))
    }
    if (adjustments.contrast != 0) {
        add(Contrast(adjustments.contrast / 100f))
    }
    if (adjustments.saturation != 0) {
        add(
            HslAdjustment.Builder()
                .adjustSaturation(adjustments.saturation.toFloat())
                .build(),
        )
    }
}
