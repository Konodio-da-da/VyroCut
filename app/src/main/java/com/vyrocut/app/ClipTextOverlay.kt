package com.vyrocut.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect

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

/**
 * Creates a frame-sized overlay so the same relative placement works for
 * portrait, landscape and square sources. The overlay is intentionally static
 * for the full clip and is applied after canvas fitting during export.
 */
@OptIn(UnstableApi::class)
internal fun createClipTextOverlayEffects(overlay: ClipTextOverlay?): List<Effect> {
    if (overlay == null) return emptyList()

    val textLayer = object : CanvasOverlay(true) {
        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 0f, 2f, 0x99000000.toInt())
        }
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8000000.toInt()
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            if (canvas.width <= 0 || canvas.height <= 0) return
            val maxTextWidth = (canvas.width * 0.86f).toInt().coerceAtLeast(1)
            textPaint.textSize = (canvas.height * 0.052f).coerceIn(18f, 96f)
            val layout = StaticLayout.Builder
                .obtain(overlay.text, 0, overlay.text.length, textPaint, maxTextWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(3)
                .build()

            val horizontalPadding = (canvas.width * 0.035f).coerceAtLeast(8f)
            val verticalPadding = (canvas.height * 0.012f).coerceAtLeast(6f)
            val boxWidth = maxTextWidth + horizontalPadding * 2f
            val boxHeight = layout.height + verticalPadding * 2f
            val left = (canvas.width - boxWidth) / 2f
            val top = (canvas.height * 0.82f - boxHeight)
                .coerceIn(verticalPadding, (canvas.height - boxHeight - verticalPadding).coerceAtLeast(verticalPadding))
            canvas.drawRoundRect(
                RectF(left, top, left + boxWidth, top + boxHeight),
                canvas.height * 0.015f,
                canvas.height * 0.015f,
                backgroundPaint,
            )
            canvas.save()
            canvas.translate(left + horizontalPadding, top + verticalPadding)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    return listOf(OverlayEffect(listOf(textLayer)))
}
