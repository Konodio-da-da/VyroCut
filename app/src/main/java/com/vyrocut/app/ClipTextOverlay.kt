package com.vyrocut.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

internal const val MAX_CLIP_TEXT_OVERLAY_LENGTH = 120
internal const val MAX_CLIP_TEXT_OVERLAY_BITMAP_DIMENSION_PX = 1_920
internal const val MIN_CLIP_TEXT_OVERLAY_FONT_SCALE = 0.75f
internal const val MAX_CLIP_TEXT_OVERLAY_FONT_SCALE = 1.5f
internal const val MIN_CLIP_TEXT_OVERLAY_DURATION_MS = 100L

internal enum class ClipTextPosition { TOP, CENTER, BOTTOM }

internal data class ClipTextOverlayTimelineBounds(
    val startOffsetPx: Float,
    val widthPx: Float,
)

internal fun isValidClipTextOverlayTiming(startMs: Long, endMs: Long?): Boolean =
    startMs >= 0L && (endMs == null || endMs > startMs)

internal fun isClipTextOverlayVisibleAtMs(overlay: ClipTextOverlay, positionMs: Long): Boolean =
    positionMs >= overlay.startMs && (overlay.endMs == null || positionMs < overlay.endMs)

internal fun isClipTextOverlayVisibleAtUs(overlay: ClipTextOverlay, presentationTimeUs: Long): Boolean =
    presentationTimeUs >= 0L && isClipTextOverlayVisibleAtMs(overlay, presentationTimeUs / 1_000L)

/** Maps a clip-local caption interval onto the width of its text-lane segment. */
internal fun calculateClipTextOverlayTimelineBounds(
    trackWidthPx: Float,
    clipDurationMs: Long,
    overlay: ClipTextOverlay,
): ClipTextOverlayTimelineBounds? {
    if (!trackWidthPx.isFinite() || trackWidthPx <= 0f || clipDurationMs <= 0L) return null
    val startMs = overlay.startMs.coerceIn(0L, clipDurationMs)
    val endMs = (overlay.endMs ?: clipDurationMs).coerceIn(0L, clipDurationMs)
    if (endMs <= startMs) return null
    val start = trackWidthPx * (startMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    val end = trackWidthPx * (endMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    return ClipTextOverlayTimelineBounds(
        startOffsetPx = start.coerceIn(0f, trackWidthPx),
        widthPx = (end - start).coerceIn(0f, trackWidthPx - start).coerceAtLeast(1f),
    )
}

/** Splits a caption interval at the same clip-local playhead used to split its video. */
internal fun splitClipTextOverlayAt(
    overlay: ClipTextOverlay?,
    splitPositionMs: Long,
    clipDurationMs: Long,
): Pair<ClipTextOverlay?, ClipTextOverlay?> {
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

/** Keeps caption timing anchored to source time when a clip's in/out points change. */
internal fun rebaseClipTextOverlayForTrim(
    overlay: ClipTextOverlay?,
    oldSourceStartMs: Long,
    oldSourceEndMs: Long,
    newSourceStartMs: Long,
    newSourceEndMs: Long,
): ClipTextOverlay? {
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

/** A simple non-destructive text layer attached to one video clip. */
internal data class ClipTextOverlay(
    val text: String,
    val fontScale: Float = 1f,
    val position: ClipTextPosition = ClipTextPosition.BOTTOM,
    /** Clip-local start time in milliseconds, inclusive. */
    val startMs: Long = 0L,
    /** Clip-local end time in milliseconds, exclusive; null means clip end. */
    val endMs: Long? = null,
) {
    init {
        require(isValidClipTextOverlayText(text)) {
            "Clip text overlay must contain 1–120 printable characters"
        }
        require(isValidClipTextOverlayFontScale(fontScale)) {
            "Clip text font scale must be between 0.75 and 1.5"
        }
        require(isValidClipTextOverlayTiming(startMs, endMs)) {
            "Clip text overlay timing is invalid"
        }
    }
}

internal fun isValidClipTextOverlayText(text: String): Boolean {
    val normalized = text.trim()
    return normalized.isNotEmpty() &&
        normalized.length <= MAX_CLIP_TEXT_OVERLAY_LENGTH &&
        normalized.none { character ->
            character == '\u0000' || (character.isISOControl() && character != '\n' && character != '\t')
        }
}

internal fun isValidClipTextOverlayFontScale(fontScale: Float): Boolean =
    fontScale.isFinite() &&
        fontScale in MIN_CLIP_TEXT_OVERLAY_FONT_SCALE..MAX_CLIP_TEXT_OVERLAY_FONT_SCALE

internal fun decodeClipTextOverlay(
    raw: String?,
    fontScale: Float = 1f,
    position: String? = null,
    startMs: Long = 0L,
    endMs: Long? = null,
): ClipTextOverlay? {
    val normalized = raw?.replace("\r\n", "\n")?.trim() ?: return null
    if (!isValidClipTextOverlayText(normalized) || !isValidClipTextOverlayFontScale(fontScale)) return null
    val decodedPosition = if (position == null) {
        ClipTextPosition.BOTTOM
    } else {
        runCatching { ClipTextPosition.valueOf(position) }.getOrNull() ?: return null
    }
    if (!isValidClipTextOverlayTiming(startMs, endMs)) return null
    return ClipTextOverlay(normalized, fontScale, decodedPosition, startMs, endMs)
}

internal data class ClipTextOverlayBitmapSize(val widthPx: Int, val heightPx: Int)

/** Preserves the frame aspect ratio while bounding overlay bitmap memory use. */
internal fun calculateClipTextOverlayBitmapSize(
    frameWidthPx: Int,
    frameHeightPx: Int,
    maxDimensionPx: Int = MAX_CLIP_TEXT_OVERLAY_BITMAP_DIMENSION_PX,
): ClipTextOverlayBitmapSize? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0 || maxDimensionPx <= 0) return null
    val scale = min(
        1.0,
        maxDimensionPx.toDouble() / max(frameWidthPx, frameHeightPx).toDouble(),
    )
    return ClipTextOverlayBitmapSize(
        widthPx = (frameWidthPx * scale).roundToInt().coerceAtLeast(1),
        heightPx = (frameHeightPx * scale).roundToInt().coerceAtLeast(1),
    )
}

/** Frame-relative geometry shared by preview and export overlay rendering. */
internal data class ClipTextOverlayLayout(
    val maxTextWidthPx: Int,
    val textSizePx: Float,
    val horizontalPaddingPx: Float,
    val verticalPaddingPx: Float,
    val leftPx: Float,
    val topPx: Float,
    val boxWidthPx: Float,
    val boxHeightPx: Float,
)

internal fun calculateClipTextOverlayLayout(
    frameWidthPx: Int,
    frameHeightPx: Int,
    textHeightPx: Int,
    position: ClipTextPosition = ClipTextPosition.BOTTOM,
    fontScale: Float = 1f,
): ClipTextOverlayLayout? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0 || textHeightPx <= 0) return null
    if (!isValidClipTextOverlayFontScale(fontScale)) return null

    val maxTextWidthPx = (frameWidthPx * 0.86f).toInt().coerceAtLeast(1)
    val horizontalPaddingPx = min(
        max(frameWidthPx * 0.035f, 8f),
        ((frameWidthPx - maxTextWidthPx) / 2f).coerceAtLeast(0f),
    )
    val verticalPaddingPx = min(max(frameHeightPx * 0.012f, 6f), frameHeightPx / 2f)
    val boxWidthPx = maxTextWidthPx + horizontalPaddingPx * 2f
    val boxHeightPx = textHeightPx + verticalPaddingPx * 2f
    val leftPx = (frameWidthPx - boxWidthPx) / 2f
    val minTopPx = verticalPaddingPx
    val maxTopPx = max(minTopPx, frameHeightPx - boxHeightPx - verticalPaddingPx)
    val requestedTopPx = when (position) {
        ClipTextPosition.TOP -> frameHeightPx * 0.12f
        ClipTextPosition.CENTER -> (frameHeightPx - boxHeightPx) / 2f
        ClipTextPosition.BOTTOM -> frameHeightPx * 0.82f - boxHeightPx
    }
    val topPx = requestedTopPx.coerceIn(minTopPx, maxTopPx)

    return ClipTextOverlayLayout(
        maxTextWidthPx = maxTextWidthPx,
        textSizePx = (frameHeightPx * 0.052f * fontScale).coerceIn(18f, 96f),
        horizontalPaddingPx = horizontalPaddingPx,
        verticalPaddingPx = verticalPaddingPx,
        leftPx = leftPx,
        topPx = topPx,
        boxWidthPx = boxWidthPx,
        boxHeightPx = boxHeightPx,
    )
}

/**
 * Media3 1.5.1 does not include CanvasOverlay. This BitmapOverlay renders at
 * the actual input-frame aspect ratio supplied by Media3, with its bitmap
 * capped at 1920px on the longest edge to avoid allocating a full 4K/8K frame.
 */
@OptIn(UnstableApi::class)
private class ClipTextBitmapOverlay(
    private val overlay: ClipTextOverlay,
) : BitmapOverlay() {
    private var bitmap: Bitmap? = null
    private var transparentBitmap: Bitmap? = null

    override fun configure(videoSize: Size) {
        val bitmapSize = checkNotNull(
            calculateClipTextOverlayBitmapSize(videoSize.width, videoSize.height),
        )
        val width = bitmapSize.widthPx
        val height = bitmapSize.heightPx
        val nextBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(nextBitmap)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(2f, 0f, 2f, 0x99000000.toInt())
            textSize = (height * 0.052f * overlay.fontScale).coerceIn(18f, 96f)
        }
        val maxTextWidth = (width * 0.86f).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder
            .obtain(overlay.text, 0, overlay.text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()
        val geometry = checkNotNull(
            calculateClipTextOverlayLayout(
                frameWidthPx = width,
                frameHeightPx = height,
                textHeightPx = layout.height,
                position = overlay.position,
                fontScale = overlay.fontScale,
            ),
        )

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8000000.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(
                geometry.leftPx,
                geometry.topPx,
                geometry.leftPx + geometry.boxWidthPx,
                geometry.topPx + geometry.boxHeightPx,
            ),
            height * 0.015f,
            height * 0.015f,
            backgroundPaint,
        )
        canvas.save()
        canvas.translate(
            geometry.leftPx + geometry.horizontalPaddingPx,
            geometry.topPx + geometry.verticalPaddingPx,
        )
        layout.draw(canvas)
        canvas.restore()

        val nextTransparentBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        transparentBitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = nextBitmap
        transparentBitmap = nextTransparentBitmap
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return if (isClipTextOverlayVisibleAtUs(overlay, presentationTimeUs)) {
            checkNotNull(bitmap) { "Text overlay must be configured with a video frame size before rendering" }
        } else {
            checkNotNull(transparentBitmap) { "Text overlay must be configured with a video frame size before rendering" }
        }
    }

    override fun release() {
        super.release()
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        transparentBitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = null
        transparentBitmap = null
    }
}

/** Applies a timed text layer; presentation timestamps are clip-local after Media3 clipping. */
@OptIn(UnstableApi::class)
internal fun createClipTextOverlayEffects(overlay: ClipTextOverlay?): List<Effect> {
    if (overlay == null) return emptyList()
    return listOf(OverlayEffect(listOf(ClipTextBitmapOverlay(overlay))))
}
