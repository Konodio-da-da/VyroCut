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

internal enum class ClipVisualEffectType {
    BRIGHTNESS,
    CONTRAST,
    SATURATION,
}

/** Pure description of an effect, safe to exercise in local JVM unit tests. */
internal data class ClipVisualEffectSpec(
    val type: ClipVisualEffectType,
    val amount: Float,
)

/**
 * Builds a deterministic effect description without constructing Android/Media3
 * effect objects. This keeps local JVM tests independent of Android framework stubs.
 */
internal fun createClipVisualEffectSpecs(
    adjustments: ClipVisualAdjustments,
): List<ClipVisualEffectSpec> = buildList {
    if (adjustments.brightness != 0) {
        add(ClipVisualEffectSpec(ClipVisualEffectType.BRIGHTNESS, adjustments.brightness / 100f))
    }
    if (adjustments.contrast != 0) {
        add(ClipVisualEffectSpec(ClipVisualEffectType.CONTRAST, adjustments.contrast / 100f))
    }
    if (adjustments.saturation != 0) {
        add(ClipVisualEffectSpec(ClipVisualEffectType.SATURATION, adjustments.saturation.toFloat()))
    }
}

/**
 * Builds the same ordered Media3 effect list for preview and Transformer export.
 * Zero-valued controls are omitted so untouched clips keep the no-op fast path.
 * Keep Android-backed Media3 object construction out of local JVM tests.
 */
@OptIn(UnstableApi::class)
internal fun createClipVisualEffects(
    adjustments: ClipVisualAdjustments,
): List<Effect> = createClipVisualEffectSpecs(adjustments).map { spec ->
    when (spec.type) {
        ClipVisualEffectType.BRIGHTNESS -> Brightness(spec.amount)
        ClipVisualEffectType.CONTRAST -> Contrast(spec.amount)
        ClipVisualEffectType.SATURATION -> HslAdjustment.Builder()
            .adjustSaturation(spec.amount)
            .build()
    }
}

/**
 * Quick tonal looks built from the same non-destructive controls used by Adjust.
 * These are not LUTs or colour-temperature filters; they are portable presets
 * for brightness, contrast and saturation only.
 */
internal enum class ClipVisualPreset(
    val label: String,
    val adjustments: ClipVisualAdjustments,
) {
    ORIGINAL("Original", ClipVisualAdjustments()),
    CINEMA("Cinema", ClipVisualAdjustments(brightness = -8, contrast = 20, saturation = -12)),
    VIVID("Vivid", ClipVisualAdjustments(brightness = 5, contrast = 12, saturation = 35)),
    MUTED("Muted", ClipVisualAdjustments(brightness = 2, contrast = -8, saturation = -40)),
    MONO("Mono", ClipVisualAdjustments(brightness = 0, contrast = 12, saturation = -100)),
}

internal fun clipVisualPresetAdjustments(label: String): ClipVisualAdjustments? =
    ClipVisualPreset.values().firstOrNull { it.label == label }?.adjustments
