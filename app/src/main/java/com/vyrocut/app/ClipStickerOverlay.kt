package com.vyrocut.app

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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal const val MAX_CLIP_STICKER_OVERLAYS = 5
internal const val MAX_CLIP_STICKER_SYMBOL_CODE_POINTS = 8
internal const val MIN_CLIP_STICKER_SCALE = 0.5f
internal const val MAX_CLIP_STICKER_SCALE = 2f
internal const val MAX_CLIP_STICKER_BITMAP_DIMENSION_PX = 1_920

internal enum class ClipStickerPosition {
    TOP_LEFT,
    TOP_RIGHT,
    CENTER,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

internal data class ClipStickerOverlay(
    val symbol: String,
    val scale: Float = 1f,
    val position: ClipStickerPosition = ClipStickerPosition.BOTTOM_RIGHT,
    /** Clip-local start time in milliseconds, inclusive. */
    val startMs: Long = 0L,
    /** Clip-local end time in milliseconds, exclusive; null means clip end. */
    val endMs: Long? = null,
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

internal fun isValidClipStickerOverlayTiming(startMs: Long, endMs: Long?): Boolean =
    startMs >= 0L && (endMs == null || endMs > startMs)

internal fun isClipStickerOverlayVisibleAtMs(overlay: ClipStickerOverlay, positionMs: Long): Boolean =
    positionMs >= overlay.startMs && (overlay.endMs == null || positionMs < overlay.endMs)

internal fun isClipStickerOverlayVisibleAtUs(overlay: ClipStickerOverlay, presentationTimeUs: Long): Boolean =
    presentationTimeUs >= 0L && isClipStickerOverlayVisibleAtMs(overlay, presentationTimeUs / 1_000L)

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
    val left = if (leftEnd > start) overlay.copy(endMs = leftEnd) else null
    val rightStart = maxOf(start, split)
    val right = if (end > rightStart) {
        overlay.copy(startMs = rightStart - split, endMs = end - split)
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

    return overlay.copy(
        startMs = intersectionStartMs - newSourceStartMs,
        endMs = intersectionEndMs - newSourceStartMs,
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
): ClipStickerOverlay? {
    val normalized = raw?.trim() ?: return null
    if (!isValidClipStickerOverlaySymbol(normalized) ||
        !isValidClipStickerOverlayScale(scale) ||
        !isValidClipStickerOverlayTiming(startMs, endMs)
    ) return null
    val decodedPosition = if (position == null) {
        ClipStickerPosition.BOTTOM_RIGHT
    } else {
        runCatching { ClipStickerPosition.valueOf(position) }.getOrNull() ?: return null
    }
    return ClipStickerOverlay(normalized, scale, decodedPosition, startMs, endMs)
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
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = geometry.textSizePx
            setShadowLayer(
                (geometry.textSizePx * 0.06f).coerceAtLeast(2f),
                0f,
                (geometry.textSizePx * 0.025f).coerceAtLeast(1f),
                0xAA000000.toInt(),
            )
        }
        val layoutWidth = (geometry.textSizePx * 2.4f).roundToInt().coerceAtLeast(1)
        val textLayout = StaticLayout.Builder
            .obtain(overlay.symbol, 0, overlay.symbol.length, textPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(1)
            .build()
        canvas.save()
        canvas.translate(
            geometry.centerXPx - textLayout.width / 2f,
            geometry.centerYPx - textLayout.height / 2f,
        )
        textLayout.draw(canvas)
        canvas.restore()

        val nextTransparentBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        transparentBitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = nextBitmap
        transparentBitmap = nextTransparentBitmap
    }

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
internal fun createClipStickerOverlayEffects(overlays: List<ClipStickerOverlay>): List<Effect> {
    if (overlays.isEmpty()) return emptyList()
    require(overlays.size <= MAX_CLIP_STICKER_OVERLAYS)
    return listOf(OverlayEffect(overlays.map(::ClipStickerBitmapOverlay)))
}
