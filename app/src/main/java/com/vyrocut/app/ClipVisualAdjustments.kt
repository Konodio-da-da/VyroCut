package com.vyrocut.app

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix

internal const val DEFAULT_CREATIVE_EFFECT_INTENSITY = 100
internal const val MIN_CREATIVE_EFFECT_INTENSITY = 0
internal const val MAX_CREATIVE_EFFECT_INTENSITY = 100

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
    val hueShiftDegrees: Int = 0,
    val creativeEffectPreset: ClipCreativeEffectPreset = ClipCreativeEffectPreset.ORIGINAL,
    val creativeEffectIntensity: Int = DEFAULT_CREATIVE_EFFECT_INTENSITY,
) {
    init {
        require(isValidClipVisualAdjustments(brightness, contrast, saturation, hueShiftDegrees, creativeEffectIntensity)) {
            "Clip visual adjustments must be between -100 and 100"
        }
    }
}

internal enum class ClipCreativeEffectCategory(val label: String) {
    COLOR("COLOR"),
    MONOCHROME("NEUTRAL & MONOCHROME"),
    VINTAGE("VINTAGE"),
}

internal enum class ClipCreativeEffectPreset(
    val id: String,
    val label: String,
    val category: ClipCreativeEffectCategory,
) {
    ORIGINAL("original", "Original", ClipCreativeEffectCategory.MONOCHROME),
    WARM("warm", "Warm", ClipCreativeEffectCategory.COLOR),
    COOL("cool", "Cool", ClipCreativeEffectCategory.COLOR),
    NEGATIVE("negative", "Negative", ClipCreativeEffectCategory.MONOCHROME),
    GRAYSCALE("grayscale", "Grayscale", ClipCreativeEffectCategory.MONOCHROME),
    SEPIA("sepia", "Sepia", ClipCreativeEffectCategory.VINTAGE),
}

internal fun clipCreativeEffectPresetsForCategory(
    category: ClipCreativeEffectCategory,
): List<ClipCreativeEffectPreset> =
    ClipCreativeEffectPreset.values().filter { it.category == category }

internal fun clipCreativeEffectPresetFromId(id: String?): ClipCreativeEffectPreset? =
    if (id == null) {
        ClipCreativeEffectPreset.ORIGINAL
    } else {
        ClipCreativeEffectPreset.values().firstOrNull { it.id == id }
    }

internal fun isValidClipVisualAdjustments(
    brightness: Int,
    contrast: Int,
    saturation: Int,
    hueShiftDegrees: Int = 0,
    creativeEffectIntensity: Int = DEFAULT_CREATIVE_EFFECT_INTENSITY,
): Boolean = brightness in -100..100 &&
    contrast in -100..100 &&
    saturation in -100..100 &&
    hueShiftDegrees in -180..180 &&
    creativeEffectIntensity in MIN_CREATIVE_EFFECT_INTENSITY..MAX_CREATIVE_EFFECT_INTENSITY

internal fun decodeClipVisualAdjustments(
    brightness: Int,
    contrast: Int,
    saturation: Int,
    hueShiftDegrees: Int = 0,
    creativeEffectPresetId: String? = null,
    creativeEffectIntensity: Int = DEFAULT_CREATIVE_EFFECT_INTENSITY,
): ClipVisualAdjustments? {
    if (!isValidClipVisualAdjustments(brightness, contrast, saturation, hueShiftDegrees, creativeEffectIntensity)) return null
    val creativeEffectPreset = clipCreativeEffectPresetFromId(creativeEffectPresetId) ?: return null
    return ClipVisualAdjustments(
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        hueShiftDegrees = hueShiftDegrees,
        creativeEffectPreset = creativeEffectPreset,
        creativeEffectIntensity = creativeEffectIntensity,
    )
}

internal enum class ClipVisualEffectType {
    BRIGHTNESS,
    CONTRAST,
    HUE_SHIFT,
    SATURATION,
    WARM_TINT,
    COOL_TINT,
    INVERTED,
    GRAYSCALE,
    SEPIA,
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
    if (adjustments.hueShiftDegrees != 0) {
        add(ClipVisualEffectSpec(ClipVisualEffectType.HUE_SHIFT, adjustments.hueShiftDegrees.toFloat()))
    }
    if (adjustments.saturation != 0) {
        add(ClipVisualEffectSpec(ClipVisualEffectType.SATURATION, adjustments.saturation.toFloat()))
    }
    val intensity = adjustments.creativeEffectIntensity / 100f
    if (intensity > 0f) {
        when (adjustments.creativeEffectPreset) {
            ClipCreativeEffectPreset.ORIGINAL -> Unit
            ClipCreativeEffectPreset.WARM -> add(ClipVisualEffectSpec(ClipVisualEffectType.WARM_TINT, intensity))
            ClipCreativeEffectPreset.COOL -> add(ClipVisualEffectSpec(ClipVisualEffectType.COOL_TINT, intensity))
            ClipCreativeEffectPreset.NEGATIVE -> add(ClipVisualEffectSpec(ClipVisualEffectType.INVERTED, intensity))
            ClipCreativeEffectPreset.GRAYSCALE -> add(ClipVisualEffectSpec(ClipVisualEffectType.GRAYSCALE, intensity))
            ClipCreativeEffectPreset.SEPIA -> add(ClipVisualEffectSpec(ClipVisualEffectType.SEPIA, intensity))
        }
    }
}

internal fun blendRgbMatrixWithIdentity(matrix: FloatArray, intensity: Float): FloatArray {
    require(matrix.size == 16) { "An RGB transformation matrix must contain 16 values" }
    require(intensity.isFinite() && intensity in 0f..1f) { "Effect intensity must be between 0 and 1" }
    return FloatArray(16) { index ->
        val identityValue = if (index % 5 == 0) 1f else 0f
        identityValue + (matrix[index] - identityValue) * intensity
    }
}

@OptIn(UnstableApi::class)
private class IntensityRgbMatrix(
    private val delegate: RgbMatrix,
    private val intensity: Float,
) : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray =
        blendRgbMatrixWithIdentity(delegate.getMatrix(presentationTimeUs, useHdr), intensity)
}

private val SEPIA_MATRIX = floatArrayOf(
    0.393f, 0.349f, 0.272f, 0f,
    0.769f, 0.686f, 0.534f, 0f,
    0.189f, 0.168f, 0.131f, 0f,
    0f, 0f, 0f, 1f,
)

internal fun sepiaRgbMatrix(): FloatArray = SEPIA_MATRIX.copyOf()

@OptIn(UnstableApi::class)
private object SepiaRgbMatrix : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = SEPIA_MATRIX
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
        ClipVisualEffectType.HUE_SHIFT -> HslAdjustment.Builder()
            .adjustHue(spec.amount)
            .build()
        ClipVisualEffectType.SATURATION -> HslAdjustment.Builder()
            .adjustSaturation(spec.amount)
            .build()
        ClipVisualEffectType.WARM_TINT -> RgbAdjustment.Builder()
            .setRedScale(1f + 0.08f * spec.amount)
            .setGreenScale(1f)
            .setBlueScale(1f - 0.12f * spec.amount)
            .build()
        ClipVisualEffectType.COOL_TINT -> RgbAdjustment.Builder()
            .setRedScale(1f - 0.12f * spec.amount)
            .setGreenScale(1f)
            .setBlueScale(1f + 0.08f * spec.amount)
            .build()
        ClipVisualEffectType.INVERTED -> {
            val inverted = RgbFilter.createInvertedFilter()
            if (spec.amount >= 1f) inverted else IntensityRgbMatrix(inverted, spec.amount)
        }
        ClipVisualEffectType.GRAYSCALE -> {
            val grayscale = RgbFilter.createGrayscaleFilter()
            if (spec.amount >= 1f) grayscale else IntensityRgbMatrix(grayscale, spec.amount)
        }
        ClipVisualEffectType.SEPIA ->
            if (spec.amount >= 1f) SepiaRgbMatrix else IntensityRgbMatrix(SepiaRgbMatrix, spec.amount)
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
