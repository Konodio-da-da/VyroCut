package com.vyrocut.app

import androidx.compose.ui.graphics.toArgb
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val MAX_CLIP_STICKER_OVERLAYS = 5
internal const val MAX_CLIP_STICKER_SYMBOL_CODE_POINTS = 8
internal const val MIN_CLIP_STICKER_SCALE = 0.5f
internal const val MAX_CLIP_STICKER_SCALE = 2f
internal const val MIN_CLIP_STICKER_ROTATION_DEGREES = -180
internal const val MAX_CLIP_STICKER_ROTATION_DEGREES = 180
internal const val MIN_CLIP_STICKER_OPACITY = 0f
internal const val MAX_CLIP_STICKER_OPACITY = 1f
internal const val MIN_CLIP_STICKER_FADE_DURATION_MS = 0L
internal const val MAX_CLIP_STICKER_FADE_DURATION_MS = 3_000L
internal const val MAX_CLIP_STICKER_BITMAP_DIMENSION_PX = 1_920

internal enum class ClipStickerPosition {
    TOP_LEFT,
    TOP_RIGHT,
    CENTER,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/** Small, bundled vector assets that work without network access. */
internal enum class ClipStickerAsset(
    val id: String,
    val label: String,
    val fallbackSymbol: String,
    val drawableResId: Int,
) {
    STAR("star", "Star", "⭐", R.drawable.vyro_sticker_star),
    HEART("heart", "Heart", "❤️", R.drawable.vyro_sticker_heart),
    SPARKLE("sparkle", "Sparkle", "✨", R.drawable.vyro_sticker_sparkle),
    BOLT("bolt", "Bolt", "⚡", R.drawable.vyro_sticker_bolt),
    CROWN("crown", "Crown", "👑", R.drawable.vyro_sticker_crown),
    FLOWER("flower", "Flower", "🌼", R.drawable.vyro_sticker_flower),
    CLAPPER("clapper", "Clapper", "🎬", R.drawable.vyro_sticker_clapper),
    CAMERA("camera", "Camera", "📷", R.drawable.vyro_sticker_camera),
    FIRE("fire", "Fire", "🔥", R.drawable.vyro_sticker_fire),
    LIKE("like", "Like", "👍", R.drawable.vyro_sticker_like);

    companion object {
        fun fromId(id: String?): ClipStickerAsset? =
            id?.let { candidate -> values().firstOrNull { it.id == candidate } }
    }
}

private val CLIP_STICKER_ASSET_ID_PATTERN = Regex("[a-z][a-z0-9-]{0,47}")

internal fun isValidClipStickerAssetId(assetId: String?): Boolean =
    assetId == null || CLIP_STICKER_ASSET_ID_PATTERN.matches(assetId)

internal data class ClipStickerOverlay(
    val symbol: String,
    val scale: Float = 1f,
    val position: ClipStickerPosition = ClipStickerPosition.BOTTOM_RIGHT,
    /** Clip-local start time in milliseconds, inclusive. */
    val startMs: Long = 0L,
    /** Clip-local end time in milliseconds, exclusive; null means clip end. */
    val endMs: Long? = null,
    /** Stable optional ID for a bundled vector asset; null keeps legacy Unicode stickers. */
    val assetId: String? = null,
    /** Clockwise rotation applied around the sticker's canvas position. */
    val rotationDegrees: Int = 0,
    /** Opacity from fully transparent (0) to fully opaque (1). */
    val opacity: Float = 1f,
    /** Clip-local linear fade-in duration in milliseconds. */
    val fadeInMs: Long = 0L,
    /** Clip-local linear fade-out duration in milliseconds. */
    val fadeOutMs: Long = 0L,
) {
    init {
        require(isValidClipStickerOverlaySymbol(symbol)) {
            "Sticker must contain 1–8 printable Unicode code points"
        }
        require(isValidClipStickerOverlayScale(scale)) {
            "Sticker scale must be between 0.5 and 2"
        }
        require(isValidClipStickerOverlayTiming(startMs, endMs)) {
            "Sticker timing is invalid"
        }
        require(isValidClipStickerAssetId(assetId)) {
            "Sticker asset ID must be a lowercase stable identifier"
        }
        require(isValidClipStickerOverlayRotation(rotationDegrees)) {
            "Sticker rotation must be between -180 and 180 degrees"
        }
        require(isValidClipStickerOverlayOpacity(opacity)) {
            "Sticker opacity must be between 0 and 1"
        }
        require(isValidClipStickerOverlayFadeDurations(fadeInMs, fadeOutMs)) {
            "Sticker fade durations must be between 0 and $MAX_CLIP_STICKER_FADE_DURATION_MS milliseconds"
        }
    }
}

internal fun isValidClipStickerOverlaySymbol(symbol: String): Boolean {
    val normalized = symbol.trim()
    return normalized.isNotEmpty() &&
        normalized.codePointCount(0, normalized.length) in 1..MAX_CLIP_STICKER_SYMBOL_CODE_POINTS &&
        normalized.none { it.isISOControl() }
}

internal fun isValidClipStickerOverlayScale(scale: Float): Boolean =
    scale.isFinite() && scale in MIN_CLIP_STICKER_SCALE..MAX_CLIP_STICKER_SCALE

internal fun isValidClipStickerOverlayRotation(rotationDegrees: Int): Boolean =
    rotationDegrees in MIN_CLIP_STICKER_ROTATION_DEGREES..MAX_CLIP_STICKER_ROTATION_DEGREES

internal fun isValidClipStickerOverlayOpacity(opacity: Float): Boolean =
    opacity.isFinite() && opacity in MIN_CLIP_STICKER_OPACITY..MAX_CLIP_STICKER_OPACITY

internal fun isValidClipStickerOverlayFadeDuration(durationMs: Long): Boolean =
    durationMs in MIN_CLIP_STICKER_FADE_DURATION_MS..MAX_CLIP_STICKER_FADE_DURATION_MS

internal fun isValidClipStickerOverlayFadeDurations(fadeInMs: Long, fadeOutMs: Long): Boolean =
    isValidClipStickerOverlayFadeDuration(fadeInMs) &&
        isValidClipStickerOverlayFadeDuration(fadeOutMs)

internal fun clipStickerOpacityAlpha(opacity: Float): Int? =
    if (isValidClipStickerOverlayOpacity(opacity)) (opacity * 255f).roundToInt().coerceIn(0, 255) else null

private fun multiplyStickerArgbAlpha(color: Int, opacityAlpha: Int): Int {
    val originalAlpha = (color ushr 24) and 0xFF
    val nextAlpha = (originalAlpha * opacityAlpha + 127) / 255
    return (color and 0x00FFFFFF) or (nextAlpha shl 24)
}

/** Rejects stale layer taps after a sticker is removed or the clip changes. */
internal fun resolveClipStickerLayerSelection(
    requestedIndex: Int,
    overlayCount: Int,
): Int? =
    requestedIndex.takeIf {
        overlayCount in 1..MAX_CLIP_STICKER_OVERLAYS && it in 0 until overlayCount
    }

internal data class ClipStickerOverlayDuplicateResult(
    val overlays: List<ClipStickerOverlay>,
    val selectedIndex: Int,
)

/**
 * Duplicates a layer without changing its source-time interval or visual settings.
 * The copy is placed at the first unused preset position so it remains visible
 * instead of being hidden exactly underneath the original layer.
 */
internal fun duplicateClipStickerOverlay(
    overlays: List<ClipStickerOverlay>,
    selectedIndex: Int,
): ClipStickerOverlayDuplicateResult? {
    if (overlays.size !in 1 until MAX_CLIP_STICKER_OVERLAYS ||
        selectedIndex !in overlays.indices
    ) return null

    val availablePosition = ClipStickerPosition.values().firstOrNull { candidate ->
        overlays.none { it.position == candidate }
    } ?: return null
    val duplicate = overlays[selectedIndex].copy(position = availablePosition)
    return ClipStickerOverlayDuplicateResult(
        overlays = overlays + duplicate,
        selectedIndex = overlays.size,
    )
}

/**
 * Updates one sticker's size without changing its timing, position, or symbol.
 * Finite values are clamped to the supported editor range; non-finite values
 * are rejected so malformed UI state cannot enter project history.
 */
internal fun updateClipStickerOverlayScale(
    overlay: ClipStickerOverlay,
    requestedScale: Float,
): ClipStickerOverlay? {
    if (!requestedScale.isFinite()) return null
    return overlay.copy(
        scale = requestedScale.coerceIn(MIN_CLIP_STICKER_SCALE, MAX_CLIP_STICKER_SCALE),
    )
}

/**
 * Updates one sticker's angle without changing its asset, size, position, or timing.
 * Non-finite UI values are rejected; finite values are rounded and clamped to a half turn.
 */
internal fun updateClipStickerOverlayRotation(
    overlay: ClipStickerOverlay,
    requestedDegrees: Float,
): ClipStickerOverlay? {
    if (!requestedDegrees.isFinite()) return null
    return overlay.copy(
        rotationDegrees = requestedDegrees.roundToInt().coerceIn(
            MIN_CLIP_STICKER_ROTATION_DEGREES,
            MAX_CLIP_STICKER_ROTATION_DEGREES,
        ),
    )
}

internal fun updateClipStickerOverlayOpacity(
    overlay: ClipStickerOverlay,
    requestedOpacity: Float,
): ClipStickerOverlay? {
    if (!requestedOpacity.isFinite()) return null
    return overlay.copy(
        opacity = requestedOpacity.coerceIn(MIN_CLIP_STICKER_OPACITY, MAX_CLIP_STICKER_OPACITY),
    )
}

private fun clipStickerOverlayDurationMs(
    overlay: ClipStickerOverlay,
    clipDurationMs: Long,
): Long? {
    if (clipDurationMs <= 0L) return null
    val effectiveEndMs = (overlay.endMs ?: clipDurationMs).coerceAtMost(clipDurationMs)
    return (effectiveEndMs - overlay.startMs).takeIf { it > 0L }
}

internal fun updateClipStickerOverlayFadeIn(
    overlay: ClipStickerOverlay,
    clipDurationMs: Long,
    requestedFadeInMs: Long,
): ClipStickerOverlay? {
    val durationMs = clipStickerOverlayDurationMs(overlay, clipDurationMs) ?: return null
    return overlay.copy(
        fadeInMs = requestedFadeInMs.coerceIn(
            MIN_CLIP_STICKER_FADE_DURATION_MS,
            min(MAX_CLIP_STICKER_FADE_DURATION_MS, durationMs),
        ),
    )
}

internal fun updateClipStickerOverlayFadeOut(
    overlay: ClipStickerOverlay,
    clipDurationMs: Long,
    requestedFadeOutMs: Long,
): ClipStickerOverlay? {
    val durationMs = clipStickerOverlayDurationMs(overlay, clipDurationMs) ?: return null
    return overlay.copy(
        fadeOutMs = requestedFadeOutMs.coerceIn(
            MIN_CLIP_STICKER_FADE_DURATION_MS,
            min(MAX_CLIP_STICKER_FADE_DURATION_MS, durationMs),
        ),
    )
}

internal fun isValidClipStickerOverlayTiming(startMs: Long, endMs: Long?): Boolean =
    startMs >= 0L && (endMs == null || endMs > startMs)

/**
 * Normalized clip-local timing and the non-crossing ranges for the IN/OUT controls.
 * The IN handle can move only up to one millisecond before OUT; OUT can move
 * only from one millisecond after IN through the clip end.
 */
internal data class ClipStickerTimingSliderBounds(
    val startMs: Long,
    val endMs: Long,
    val startSliderMaxMs: Long,
    val endSliderMinMs: Long,
)

internal fun calculateClipStickerTimingSliderBounds(
    clipDurationMs: Long,
    requestedStartMs: Long,
    requestedEndMs: Long,
): ClipStickerTimingSliderBounds? {
    if (clipDurationMs <= 0L) return null
    val startMs = requestedStartMs.coerceIn(0L, clipDurationMs - 1L)
    val endMs = requestedEndMs.coerceIn(startMs + 1L, clipDurationMs)
    return ClipStickerTimingSliderBounds(
        startMs = startMs,
        endMs = endMs,
        startSliderMaxMs = endMs - 1L,
        endSliderMinMs = startMs + 1L,
    )
}

/**
 * Updates a sticker's clip-local interval without allowing it to escape the owning clip.
 * An end equal to the clip duration is stored as null, meaning "through clip end".
 */
internal fun updateClipStickerOverlayTiming(
    overlay: ClipStickerOverlay,
    clipDurationMs: Long,
    requestedStartMs: Long,
    requestedEndMs: Long,
): ClipStickerOverlay? {
    val bounds = calculateClipStickerTimingSliderBounds(
        clipDurationMs = clipDurationMs,
        requestedStartMs = requestedStartMs,
        requestedEndMs = requestedEndMs,
    ) ?: return null
    val intervalDurationMs = bounds.endMs - bounds.startMs
    return overlay.copy(
        startMs = bounds.startMs,
        endMs = bounds.endMs.takeIf { it < clipDurationMs },
        fadeInMs = overlay.fadeInMs.coerceAtMost(intervalDurationMs),
        fadeOutMs = overlay.fadeOutMs.coerceAtMost(intervalDurationMs),
    )
}

internal fun isClipStickerOverlayVisibleAtMs(overlay: ClipStickerOverlay, positionMs: Long): Boolean =
    positionMs >= overlay.startMs && (overlay.endMs == null || positionMs < overlay.endMs)

internal fun isClipStickerOverlayVisibleAtUs(overlay: ClipStickerOverlay, presentationTimeUs: Long): Boolean =
    presentationTimeUs >= 0L && isClipStickerOverlayVisibleAtMs(overlay, presentationTimeUs / 1_000L)

/**
 * Returns the clip-local alpha multiplier for a sticker's fade envelope.
 * Overlapping fades use the lower of their two linear ramps; a zero-duration
 * fade is fully opaque on that side. The clip duration resolves an open-ended
 * sticker OUT point without changing the persisted clip-local interval.
 */
internal fun clipStickerFadeAlphaScale(
    overlay: ClipStickerOverlay,
    positionMs: Long,
    clipDurationMs: Long,
): Float {
    if (clipDurationMs <= 0L || positionMs < 0L) return 0f
    val endMs = (overlay.endMs ?: clipDurationMs).coerceAtMost(clipDurationMs)
    if (endMs <= overlay.startMs || positionMs < overlay.startMs || positionMs >= endMs) return 0f

    val intervalDurationMs = endMs - overlay.startMs
    val fadeInMs = overlay.fadeInMs.coerceAtMost(intervalDurationMs)
    val fadeOutMs = overlay.fadeOutMs.coerceAtMost(intervalDurationMs)
    val fadeInScale = if (fadeInMs == 0L) {
        1f
    } else {
        ((positionMs - overlay.startMs).toDouble() / fadeInMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    val fadeOutScale = if (fadeOutMs == 0L) {
        1f
    } else {
        ((endMs - positionMs).toDouble() / fadeOutMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    return min(fadeInScale, fadeOutScale)
}

/** Splits one sticker interval at the same clip-local playhead used to split its video. */
internal fun splitClipStickerOverlayAt(
    overlay: ClipStickerOverlay?,
    splitPositionMs: Long,
    clipDurationMs: Long,
): Pair<ClipStickerOverlay?, ClipStickerOverlay?> {
    if (overlay == null || clipDurationMs <= 0L) return null to null
    val split = splitPositionMs.coerceIn(0L, clipDurationMs)
    val start = overlay.startMs
    val end = (overlay.endMs ?: clipDurationMs).coerceAtMost(clipDurationMs)
    if (end <= start) return null to null

    val leftEnd = minOf(end, split)
    val leftDurationMs = (leftEnd - start).coerceAtLeast(0L)
    val left = if (leftDurationMs > 0L) {
        overlay.copy(
            endMs = leftEnd,
            fadeInMs = overlay.fadeInMs.coerceAtMost(leftDurationMs),
            fadeOutMs = overlay.fadeOutMs.coerceAtMost(leftDurationMs),
        )
    } else {
        null
    }
    val rightStart = maxOf(start, split)
    val rightDurationMs = (end - rightStart).coerceAtLeast(0L)
    val right = if (rightDurationMs > 0L) {
        overlay.copy(
            startMs = rightStart - split,
            endMs = end - split,
            fadeInMs = overlay.fadeInMs.coerceAtMost(rightDurationMs),
            fadeOutMs = overlay.fadeOutMs.coerceAtMost(rightDurationMs),
        )
    } else {
        null
    }
    return left to right
}

/** Splits every sticker independently while preserving each layer's clip-local timing. */
internal fun splitClipStickerOverlaysAt(
    overlays: List<ClipStickerOverlay>,
    splitPositionMs: Long,
    clipDurationMs: Long,
): Pair<List<ClipStickerOverlay>, List<ClipStickerOverlay>> {
    if (overlays.isEmpty() || clipDurationMs <= 0L) {
        return emptyList<ClipStickerOverlay>() to emptyList<ClipStickerOverlay>()
    }
    require(overlays.size <= MAX_CLIP_STICKER_OVERLAYS)
    val left = mutableListOf<ClipStickerOverlay>()
    val right = mutableListOf<ClipStickerOverlay>()
    overlays.forEach { overlay ->
        val (leftOverlay, rightOverlay) = splitClipStickerOverlayAt(overlay, splitPositionMs, clipDurationMs)
        leftOverlay?.let(left::add)
        rightOverlay?.let(right::add)
    }
    return left to right
}

/** Keeps sticker timing anchored to source time when a clip's in/out points change. */
internal fun rebaseClipStickerOverlayForTrim(
    overlay: ClipStickerOverlay?,
    oldSourceStartMs: Long,
    oldSourceEndMs: Long,
    newSourceStartMs: Long,
    newSourceEndMs: Long,
): ClipStickerOverlay? {
    if (overlay == null || oldSourceStartMs < 0L || newSourceStartMs < 0L ||
        oldSourceEndMs <= oldSourceStartMs || newSourceEndMs <= newSourceStartMs
    ) return null
    val oldDurationMs = oldSourceEndMs - oldSourceStartMs
    val overlayEndRelativeMs = (overlay.endMs ?: oldDurationMs).coerceAtMost(oldDurationMs)
    if (overlay.startMs >= overlayEndRelativeMs ||
        overlay.startMs > Long.MAX_VALUE - oldSourceStartMs ||
        overlayEndRelativeMs > Long.MAX_VALUE - oldSourceStartMs
    ) return null

    val absoluteStartMs = oldSourceStartMs + overlay.startMs
    val absoluteEndMs = oldSourceStartMs + overlayEndRelativeMs
    val intersectionStartMs = maxOf(absoluteStartMs, newSourceStartMs)
    val intersectionEndMs = minOf(absoluteEndMs, newSourceEndMs)
    if (intersectionEndMs <= intersectionStartMs) return null

    val retainedDurationMs = intersectionEndMs - intersectionStartMs
    return overlay.copy(
        startMs = intersectionStartMs - newSourceStartMs,
        endMs = intersectionEndMs - newSourceStartMs,
        fadeInMs = overlay.fadeInMs.coerceAtMost(retainedDurationMs),
        fadeOutMs = overlay.fadeOutMs.coerceAtMost(retainedDurationMs),
    )
}

/** Rebases every sticker independently after a non-destructive source trim. */
internal fun rebaseClipStickerOverlaysForTrim(
    overlays: List<ClipStickerOverlay>,
    oldSourceStartMs: Long,
    oldSourceEndMs: Long,
    newSourceStartMs: Long,
    newSourceEndMs: Long,
): List<ClipStickerOverlay> = overlays.mapNotNull { overlay ->
    rebaseClipStickerOverlayForTrim(
        overlay = overlay,
        oldSourceStartMs = oldSourceStartMs,
        oldSourceEndMs = oldSourceEndMs,
        newSourceStartMs = newSourceStartMs,
        newSourceEndMs = newSourceEndMs,
    )
}

internal fun decodeClipStickerOverlay(
    raw: String?,
    scale: Float = 1f,
    position: String? = null,
    startMs: Long = 0L,
    endMs: Long? = null,
    assetId: String? = null,
    rotationDegrees: Int = 0,
    opacity: Float = 1f,
    fadeInMs: Long = 0L,
    fadeOutMs: Long = 0L,
): ClipStickerOverlay? {
    val normalized = raw?.trim() ?: return null
    val normalizedAssetId = assetId?.trim()?.takeIf { it.isNotEmpty() }
    if (!isValidClipStickerOverlaySymbol(normalized) ||
        !isValidClipStickerOverlayScale(scale) ||
        !isValidClipStickerOverlayTiming(startMs, endMs) ||
        !isValidClipStickerAssetId(normalizedAssetId) ||
        !isValidClipStickerOverlayRotation(rotationDegrees) ||
        !isValidClipStickerOverlayOpacity(opacity) ||
        !isValidClipStickerOverlayFadeDurations(fadeInMs, fadeOutMs)
    ) return null
    val decodedPosition = if (position == null) {
        ClipStickerPosition.BOTTOM_RIGHT
    } else {
        runCatching { ClipStickerPosition.valueOf(position) }.getOrNull() ?: return null
    }
    return ClipStickerOverlay(
        symbol = normalized,
        scale = scale,
        position = decodedPosition,
        startMs = startMs,
        endMs = endMs,
        assetId = normalizedAssetId,
        rotationDegrees = rotationDegrees,
        opacity = opacity,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs,
    )
}

internal data class ClipStickerOverlayTimelineBounds(
    val startOffsetPx: Float,
    val widthPx: Float,
)

/** Maps a clip-local sticker interval onto its row inside the owning video clip. */
internal fun calculateClipStickerOverlayTimelineBounds(
    trackWidthPx: Float,
    clipDurationMs: Long,
    overlay: ClipStickerOverlay,
): ClipStickerOverlayTimelineBounds? {
    if (!trackWidthPx.isFinite() || trackWidthPx <= 0f || clipDurationMs <= 0L) return null
    val startMs = overlay.startMs.coerceIn(0L, clipDurationMs)
    val endMs = (overlay.endMs ?: clipDurationMs).coerceIn(0L, clipDurationMs)
    if (endMs <= startMs) return null
    val start = trackWidthPx * (startMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    val end = trackWidthPx * (endMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    return ClipStickerOverlayTimelineBounds(
        startOffsetPx = start.coerceIn(0f, trackWidthPx),
        widthPx = (end - start).coerceIn(0f, trackWidthPx - start).coerceAtLeast(1f),
    )
}

internal data class ClipStickerOverlayBitmapSize(val widthPx: Int, val heightPx: Int)

internal fun calculateClipStickerOverlayBitmapSize(
    frameWidthPx: Int,
    frameHeightPx: Int,
    maxDimensionPx: Int = MAX_CLIP_STICKER_BITMAP_DIMENSION_PX,
): ClipStickerOverlayBitmapSize? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0 || maxDimensionPx <= 0) return null
    val scale = min(
        1.0,
        maxDimensionPx.toDouble() / max(frameWidthPx, frameHeightPx).toDouble(),
    )
    return ClipStickerOverlayBitmapSize(
        widthPx = (frameWidthPx * scale).roundToInt().coerceAtLeast(1),
        heightPx = (frameHeightPx * scale).roundToInt().coerceAtLeast(1),
    )
}

internal data class ClipStickerOverlayLayout(
    val centerXPx: Float,
    val centerYPx: Float,
    val textSizePx: Float,
)

internal fun calculateClipStickerOverlayLayout(
    frameWidthPx: Int,
    frameHeightPx: Int,
    scale: Float = 1f,
    position: ClipStickerPosition = ClipStickerPosition.BOTTOM_RIGHT,
): ClipStickerOverlayLayout? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0 || !isValidClipStickerOverlayScale(scale)) return null
    val horizontalFraction = when (position) {
        ClipStickerPosition.TOP_LEFT, ClipStickerPosition.BOTTOM_LEFT -> 0.18f
        ClipStickerPosition.TOP_RIGHT, ClipStickerPosition.BOTTOM_RIGHT -> 0.82f
        ClipStickerPosition.CENTER -> 0.5f
    }
    val verticalFraction = when (position) {
        ClipStickerPosition.TOP_LEFT, ClipStickerPosition.TOP_RIGHT -> 0.18f
        ClipStickerPosition.BOTTOM_LEFT, ClipStickerPosition.BOTTOM_RIGHT -> 0.82f
        ClipStickerPosition.CENTER -> 0.5f
    }
    return ClipStickerOverlayLayout(
        centerXPx = frameWidthPx * horizontalFraction,
        centerYPx = frameHeightPx * verticalFraction,
        textSizePx = (min(frameWidthPx, frameHeightPx) * 0.17f * scale).coerceIn(24f, 360f),
    )
}

/** Renders Unicode sticker glyphs as timed Media3 bitmap overlays. */
@OptIn(UnstableApi::class)
private class ClipStickerBitmapOverlay(
    private val overlay: ClipStickerOverlay,
    private val context: Context,
    private val clipDurationMs: Long,
) : BitmapOverlay() {
    private var bitmap: Bitmap? = null
    private var transparentBitmap: Bitmap? = null

    override fun configure(videoSize: Size) {
        val bitmapSize = checkNotNull(
            calculateClipStickerOverlayBitmapSize(videoSize.width, videoSize.height),
        )
        val width = bitmapSize.widthPx
        val height = bitmapSize.heightPx
        val nextBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(nextBitmap)
        val geometry = checkNotNull(
            calculateClipStickerOverlayLayout(width, height, overlay.scale, overlay.position),
        )
        val stickerAsset = ClipStickerAsset.fromId(overlay.assetId)
        val drawable = stickerAsset?.let { context.getDrawable(it.drawableResId)?.mutate() }
        val opacityAlpha = checkNotNull(clipStickerOpacityAlpha(overlay.opacity))
        canvas.save()
        canvas.rotate(overlay.rotationDegrees.toFloat(), geometry.centerXPx, geometry.centerYPx)
        if (drawable != null) {
            val assetSizePx = geometry.textSizePx.roundToInt().coerceAtLeast(1)
            drawable.alpha = opacityAlpha
            drawable.setBounds(0, 0, assetSizePx, assetSizePx)
            canvas.translate(
                geometry.centerXPx - assetSizePx / 2f,
                geometry.centerYPx - assetSizePx / 2f,
            )
            drawable.draw(canvas)
        } else {
            // Unknown future asset IDs intentionally fall back to the persisted Unicode symbol.
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = VyroCutPalette.MediaOverlayText.toArgb()
                alpha = opacityAlpha
                textSize = geometry.textSizePx
                setShadowLayer(
                    (geometry.textSizePx * 0.06f).coerceAtLeast(2f),
                    0f,
                    (geometry.textSizePx * 0.025f).coerceAtLeast(1f),
                    multiplyStickerArgbAlpha(
                        VyroCutPalette.StickerFallbackTextShadow.toArgb(),
                        opacityAlpha,
                    ),
                )
            }
            val layoutWidth = (geometry.textSizePx * 2.4f).roundToInt().coerceAtLeast(1)
            val textLayout = StaticLayout.Builder
                .obtain(overlay.symbol, 0, overlay.symbol.length, textPaint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
            canvas.translate(
                geometry.centerXPx - textLayout.width / 2f,
                geometry.centerYPx - textLayout.height / 2f,
            )
            textLayout.draw(canvas)
        }
        canvas.restore()

        val nextTransparentBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        transparentBitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = nextBitmap
        transparentBitmap = nextTransparentBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings =
        OverlaySettings.Builder()
            .setAlphaScale(
                clipStickerFadeAlphaScale(
                    overlay = overlay,
                    positionMs = presentationTimeUs / 1_000L,
                    clipDurationMs = clipDurationMs,
                ),
            )
            .build()

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        if (isClipStickerOverlayVisibleAtUs(overlay, presentationTimeUs)) {
            checkNotNull(bitmap) { "Sticker overlay must be configured with a video frame size before rendering" }
        } else {
            checkNotNull(transparentBitmap) { "Sticker overlay must be configured with a video frame size before rendering" }
        }

    override fun release() {
        super.release()
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        transparentBitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = null
        transparentBitmap = null
    }
}

@OptIn(UnstableApi::class)
internal fun createClipStickerOverlayEffects(
    overlays: List<ClipStickerOverlay>,
    context: Context,
    clipDurationMs: Long,
): List<Effect> {
    if (overlays.isEmpty()) return emptyList()
    require(overlays.size <= MAX_CLIP_STICKER_OVERLAYS)
    val applicationContext = context.applicationContext
    return listOf(
        OverlayEffect(overlays.map { overlay ->
            ClipStickerBitmapOverlay(overlay, applicationContext, clipDurationMs)
        }),
    )
}
