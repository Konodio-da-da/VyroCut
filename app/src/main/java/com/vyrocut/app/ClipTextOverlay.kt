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

internal const val MAX_CLIP_TEXT_OVERLAY_LENGTH = 120

/** A simple non-destructive text layer attached to one video clip. */
internal data class ClipTextOverlay(val text: String) {
    init {
        require(isValidClipTextOverlayText(text)) {
            "Clip text overlay must contain 1–120 printable characters"
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

internal fun decodeClipTextOverlay(raw: String?): ClipTextOverlay? {
    val normalized = raw?.replace("\r\n", "\n")?.trim() ?: return null
    if (!isValidClipTextOverlayText(normalized)) return null
    return ClipTextOverlay(normalized)
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
): ClipTextOverlayLayout? {
    if (frameWidthPx <= 0 || frameHeightPx <= 0 || textHeightPx <= 0) return null

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
    val topPx = (frameHeightPx * 0.82f - boxHeightPx).coerceIn(minTopPx, maxTopPx)

    return ClipTextOverlayLayout(
        maxTextWidthPx = maxTextWidthPx,
        textSizePx = (frameHeightPx * 0.052f).coerceIn(18f, 96f),
        horizontalPaddingPx = horizontalPaddingPx,
        verticalPaddingPx = verticalPaddingPx,
        leftPx = leftPx,
        topPx = topPx,
        boxWidthPx = boxWidthPx,
        boxHeightPx = boxHeightPx,
    )
}

/**
 * Media3 1.5.1 does not contain CanvasOverlay. This BitmapOverlay renders into
 * the actual input-frame dimensions supplied by Media3, so the same relative
 * layout works for portrait, landscape and square clips in preview and export.
 */
@OptIn(UnstableApi::class)
private class ClipTextBitmapOverlay(
    private val overlay: ClipTextOverlay,
) : BitmapOverlay() {
    private var bitmap: Bitmap? = null

    override fun configure(videoSize: Size) {
        val width = videoSize.width
        val height = videoSize.height
        require(width > 0 && height > 0) { "Video frame dimensions must be positive" }

        val nextBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(nextBitmap)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setShadowLayer(2f, 0f, 2f, 0x99000000.toInt())
        }
        val maxTextWidth = (width * 0.86f).toInt().coerceAtLeast(1)
        textPaint.textSize = (height * 0.052f).coerceIn(18f, 96f)
        val layout = StaticLayout.Builder
            .obtain(overlay.text, 0, overlay.text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setMaxLines(3)
            .build()
        val geometry = checkNotNull(calculateClipTextOverlayLayout(width, height, layout.height))

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

        bitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = nextBitmap
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap =
        checkNotNull(bitmap) { "Text overlay must be configured with a video frame size before rendering" }

    override fun release() {
        super.release()
        bitmap?.takeIf { !it.isRecycled }?.recycle()
        bitmap = null
    }
}

/**
 * Creates a frame-sized overlay so the same relative placement works for
 * portrait, landscape and square sources. The overlay is static for the full clip.
 */
@OptIn(UnstableApi::class)
internal fun createClipTextOverlayEffects(overlay: ClipTextOverlay?): List<Effect> {
    if (overlay == null) return emptyList()
    return listOf(OverlayEffect(listOf(ClipTextBitmapOverlay(overlay))))
}
