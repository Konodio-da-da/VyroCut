package com.vyrocut.app

import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect

internal data class LetterboxContentRect(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
)

/**
 * Calculates the content bounds produced by Media3's scale-to-fit presentation.
 * Rotation metadata is applied before calculating the source aspect ratio.
 */
internal fun calculateLetterboxContentRect(
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    sourceRotationDegrees: Int = 0,
): LetterboxContentRect? {
    if (canvasWidthPx <= 0 || canvasHeightPx <= 0 || sourceWidthPx <= 0 || sourceHeightPx <= 0) {
        return null
    }

    val normalizedRotation = ((sourceRotationDegrees % 360) + 360) % 360
    val swapsDimensions = normalizedRotation == 90 || normalizedRotation == 270
    val orientedWidth = if (swapsDimensions) sourceHeightPx else sourceWidthPx
    val orientedHeight = if (swapsDimensions) sourceWidthPx else sourceHeightPx
    val sourceAspectRatio = orientedWidth.toDouble() / orientedHeight.toDouble()
    val canvasAspectRatio = canvasWidthPx.toDouble() / canvasHeightPx.toDouble()

    return if (sourceAspectRatio > canvasAspectRatio) {
        val contentHeight = canvasWidthPx.toDouble() / sourceAspectRatio
        val top = (canvasHeightPx - contentHeight) / 2.0
        LetterboxContentRect(
            leftPx = 0f,
            topPx = top.toFloat(),
            rightPx = canvasWidthPx.toFloat(),
            bottomPx = (top + contentHeight).toFloat(),
        )
    } else {
        val contentWidth = canvasHeightPx.toDouble() * sourceAspectRatio
        val left = (canvasWidthPx - contentWidth) / 2.0
        LetterboxContentRect(
            leftPx = left.toFloat(),
            topPx = 0f,
            rightPx = (left + contentWidth).toFloat(),
            bottomPx = canvasHeightPx.toFloat(),
        )
    }
}

internal fun exportCanvasBackgroundArgbForPreset(preset: String): Int = when (preset) {
    "DARK" -> 0xFF202322.toInt()
    "WHITE" -> AndroidColor.WHITE
    else -> AndroidColor.BLACK
}

/**
 * Paints only the letterbox/pillarbox areas after Presentation has fitted the
 * source into the fixed export canvas. The transparent center preserves video.
 */
@OptIn(UnstableApi::class)
internal fun createLetterboxBackgroundEffect(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    sourceRotationDegrees: Int,
    backgroundColorArgb: Int,
): Effect {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) {
        "Source dimensions must be positive before creating a canvas background"
    }
    val paint = Paint().apply {
        color = backgroundColorArgb
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    val overlay = object : CanvasOverlay(true) {
        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            canvas.drawColor(AndroidColor.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val content = calculateLetterboxContentRect(
                canvasWidthPx = canvas.width,
                canvasHeightPx = canvas.height,
                sourceWidthPx = sourceWidthPx,
                sourceHeightPx = sourceHeightPx,
                sourceRotationDegrees = sourceRotationDegrees,
            ) ?: return
            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            if (content.topPx > 0f) {
                canvas.drawRect(0f, 0f, width, content.topPx, paint)
            }
            if (content.bottomPx < height) {
                canvas.drawRect(0f, content.bottomPx, width, height, paint)
            }
            if (content.leftPx > 0f) {
                canvas.drawRect(0f, content.topPx, content.leftPx, content.bottomPx, paint)
            }
            if (content.rightPx < width) {
                canvas.drawRect(content.rightPx, content.topPx, width, content.bottomPx, paint)
            }
        }
    }
    return OverlayEffect(listOf(overlay))
}
