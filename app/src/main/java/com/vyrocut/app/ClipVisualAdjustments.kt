package com.vyrocut.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import kotlin.math.roundToInt

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
    CINEMATIC("CINEMATIC"),
    LENS("LENS"),
    MONOCHROME("NEUTRAL & MONOCHROME"),
    VINTAGE("VINTAGE"),
}

internal enum class ClipCreativeEffectPreset(
    val id: String,
    val label: String,
    val category: ClipCreativeEffectCategory,
    /** Short, user-facing explanation shown for the selected effect in the browser. */
    val description: String,
) {
    ORIGINAL(
        "original",
        "Original",
        ClipCreativeEffectCategory.MONOCHROME,
        "Removes the creative look while keeping manual brightness, contrast, saturation and hue adjustments.",
    ),
    WARM(
        "warm",
        "Warm",
        ClipCreativeEffectCategory.COLOR,
        "Adds a subtle warm tint by increasing red and reducing blue.",
    ),
    COOL(
        "cool",
        "Cool",
        ClipCreativeEffectCategory.COLOR,
        "Adds a cool tint by reducing red and lifting blue.",
    ),
    CINEMATIC(
        "cinematic",
        "Cinematic",
        ClipCreativeEffectCategory.CINEMATIC,
        "Applies a restrained cool-matte grade with softened channel contrast and lifted shadows.",
    ),
    SOFT_FOCUS(
        "soft-focus",
        "Soft Focus",
        ClipCreativeEffectCategory.LENS,
        "Applies a Gaussian lens blur; higher strength increases the blur.",
    ),
    VIGNETTE(
        "vignette",
        "Vignette",
        ClipCreativeEffectCategory.LENS,
        "Darkens the frame edges with a soft, even falloff toward the corners.",
    ),
    NEGATIVE(
        "negative",
        "Negative",
        ClipCreativeEffectCategory.MONOCHROME,
        "Inverts the frame colors.",
    ),
    GRAYSCALE(
        "grayscale",
        "Grayscale",
        ClipCreativeEffectCategory.MONOCHROME,
        "Removes color using luminance-based grayscale conversion.",
    ),
    SEPIA(
        "sepia",
        "Sepia",
        ClipCreativeEffectCategory.VINTAGE,
        "Adds a warm, vintage sepia tone.",
    ),
    FILM_FADE(
        "film-fade",
        "Film Fade",
        ClipCreativeEffectCategory.VINTAGE,
        "Lifts shadows and softens contrast for a faded-film look.",
    ),
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

/**
 * Removes only the creative look, preserving the clip's manual brightness,
 * contrast, saturation, and hue adjustments.
 */
internal fun resetClipCreativeEffect(
    adjustments: ClipVisualAdjustments,
): ClipVisualAdjustments = adjustments.copy(
    creativeEffectPreset = ClipCreativeEffectPreset.ORIGINAL,
    creativeEffectIntensity = DEFAULT_CREATIVE_EFFECT_INTENSITY,
)

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
    CINEMATIC,
    SOFT_FOCUS,
    VIGNETTE,
    INVERTED,
    GRAYSCALE,
    SEPIA,
    FILM_FADE,
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
            ClipCreativeEffectPreset.CINEMATIC -> add(ClipVisualEffectSpec(ClipVisualEffectType.CINEMATIC, intensity))
            ClipCreativeEffectPreset.SOFT_FOCUS -> add(ClipVisualEffectSpec(ClipVisualEffectType.SOFT_FOCUS, intensity))
            ClipCreativeEffectPreset.VIGNETTE -> add(ClipVisualEffectSpec(ClipVisualEffectType.VIGNETTE, intensity))
            ClipCreativeEffectPreset.NEGATIVE -> add(ClipVisualEffectSpec(ClipVisualEffectType.INVERTED, intensity))
            ClipCreativeEffectPreset.GRAYSCALE -> add(ClipVisualEffectSpec(ClipVisualEffectType.GRAYSCALE, intensity))
            ClipCreativeEffectPreset.SEPIA -> add(ClipVisualEffectSpec(ClipVisualEffectType.SEPIA, intensity))
            ClipCreativeEffectPreset.FILM_FADE -> add(ClipVisualEffectSpec(ClipVisualEffectType.FILM_FADE, intensity))
        }
    }
}

internal const val MAX_SOFT_FOCUS_SIGMA_PX = 5f

/** Maps normalized strength to Media3 GaussianBlur sigma, expressed in output pixels. */
internal fun softFocusSigmaPx(intensity: Float): Float {
    require(intensity.isFinite() && intensity in 0f..1f) { "Effect intensity must be between 0 and 1" }
    return MAX_SOFT_FOCUS_SIGMA_PX * intensity
}

internal const val MAX_VIGNETTE_OVERLAY_BITMAP_DIMENSION_PX = 1_920
internal const val MAX_VIGNETTE_EDGE_ALPHA = 160
private const val VIGNETTE_GRADIENT_RADIUS = 1.4142135f

internal data class VignetteOverlayBitmapSize(
    val widthPx: Int,
    val heightPx: Int,
)

/** Keeps a full-frame effect overlay bounded while preserving the output aspect ratio. */
internal fun calculateVignetteOverlayBitmapSize(
    frameWidthPx: Int,
    frameHeightPx: Int,
): VignetteOverlayBitmapSize? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0) return null
    val longestEdge = maxOf(frameWidthPx, frameHeightPx)
    val scale = minOf(
        1.0,
        MAX_VIGNETTE_OVERLAY_BITMAP_DIMENSION_PX.toDouble() / longestEdge.toDouble(),
    )
    return VignetteOverlayBitmapSize(
        widthPx = (frameWidthPx * scale).roundToInt().coerceAtLeast(1),
        heightPx = (frameHeightPx * scale).roundToInt().coerceAtLeast(1),
    )
}

/** Maps normalized effect strength to the vignette's maximum corner opacity. */
internal fun vignetteOverlayEdgeAlpha(intensity: Float): Int {
    require(intensity.isFinite() && intensity in 0f..1f) {
        "Effect intensity must be between 0 and 1"
    }
    return (MAX_VIGNETTE_EDGE_ALPHA * intensity).roundToInt()
}

@OptIn(UnstableApi::class)
private class VignetteBitmapOverlay(
    private val intensity: Float,
    private val alphaScaleAtPresentationTimeUs: ((Long) -> Float)? = null,
) : BitmapOverlay() {
    private var bitmap: Bitmap? = null

    override fun configure(videoSize: Size) {
        val bitmapSize = checkNotNull(
            calculateVignetteOverlayBitmapSize(videoSize.width, videoSize.height),
        )
        val nextBitmap = Bitmap.createBitmap(
            bitmapSize.widthPx,
            bitmapSize.heightPx,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(nextBitmap)
        val edgeAlpha = vignetteOverlayEdgeAlpha(intensity)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                0f,
                0f,
                VIGNETTE_GRADIENT_RADIUS,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.argb(edgeAlpha, 0, 0, 0),
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        canvas.save()
        try {
            // Normalize both axes so the vignette falloff reaches every frame edge evenly.
            canvas.translate(bitmapSize.widthPx / 2f, bitmapSize.heightPx / 2f)
            canvas.scale(bitmapSize.widthPx / 2f, bitmapSize.heightPx / 2f)
            canvas.drawCircle(0f, 0f, VIGNETTE_GRADIENT_RADIUS, paint)
        } finally {
            canvas.restore()
        }

        bitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = nextBitmap
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        checkNotNull(bitmap) { "Vignette overlay must be configured with a video frame size before rendering" }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val alphaScale = if (alphaScaleAtPresentationTimeUs == null) {
            1f
        } else {
            alphaScaleAtPresentationTimeUs.invoke(presentationTimeUs)
                .takeIf { it.isFinite() }
                ?.coerceIn(0f, 1f)
                ?: 0f
        }
        return OverlaySettings.Builder().setAlphaScale(alphaScale).build()
    }

    override fun release() {
        super.release()
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = null
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

// Reduces channel contrast and lifts the toe with a subtle warm bias.
// The matrix is column-major, matching Android's 4x4 color-matrix convention.
private val FILM_FADE_MATRIX = floatArrayOf(
    0.88f, 0f, 0f, 0f,
    0f, 0.88f, 0f, 0f,
    0f, 0f, 0.88f, 0f,
    0.055f, 0.05f, 0.045f, 1f,
)

internal fun filmFadeRgbMatrix(): FloatArray = FILM_FADE_MATRIX.copyOf()

// A restrained cool-matte grade: slightly softens RGB channel gain and lifts
// the blue channel more than red to keep the shadow bias cool without clipping alpha.
private val CINEMATIC_MATRIX = floatArrayOf(
    0.94f, 0.01f, 0.02f, 0f,
    0f, 0.96f, 0.02f, 0f,
    -0.01f, 0.02f, 0.94f, 0f,
    0.015f, 0.025f, 0.045f, 1f,
)

internal fun cinematicRgbMatrix(): FloatArray = CINEMATIC_MATRIX.copyOf()

@OptIn(UnstableApi::class)
private object SepiaRgbMatrix : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = SEPIA_MATRIX
}

@OptIn(UnstableApi::class)
private object FilmFadeRgbMatrix : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = FILM_FADE_MATRIX
}

@OptIn(UnstableApi::class)
private object CinematicRgbMatrix : RgbMatrix {
    override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray = CINEMATIC_MATRIX
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
        ClipVisualEffectType.FILM_FADE ->
            if (spec.amount >= 1f) FilmFadeRgbMatrix else IntensityRgbMatrix(FilmFadeRgbMatrix, spec.amount)
        ClipVisualEffectType.CINEMATIC ->
            if (spec.amount >= 1f) CinematicRgbMatrix else IntensityRgbMatrix(CinematicRgbMatrix, spec.amount)
        ClipVisualEffectType.SOFT_FOCUS -> GaussianBlur(softFocusSigmaPx(spec.amount))
        ClipVisualEffectType.VIGNETTE -> OverlayEffect(listOf(VignetteBitmapOverlay(spec.amount)))
    }
}

/**
 * Creates the first time-ranged creative effect renderer. The bitmap is
 * generated once at maximum Vignette opacity; Media3 applies a per-frame alpha
 * scale, avoiding per-frame bitmap allocation and preserving clip-local timing.
 */
@OptIn(UnstableApi::class)
internal fun createClipTimedVignetteEffects(
    segments: List<ClipTimedEffectSegment>,
    clipDurationMs: Long,
): List<Effect> {
    if (segments.none { it.preset == ClipCreativeEffectPreset.VIGNETTE }) return emptyList()
    require(validateClipTimedEffectSegments(segments, clipDurationMs)) {
        "Timed Vignette segments must be valid for their owning clip"
    }
    return listOf(
        OverlayEffect(
            listOf(
                VignetteBitmapOverlay(intensity = 1f) { presentationTimeUs ->
                    activeClipTimedVignetteIntensityAtUs(
                        segments = segments,
                        presentationTimeUs = presentationTimeUs,
                        clipDurationMs = clipDurationMs,
                    )
                },
            ),
        ),
    )
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
