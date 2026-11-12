package com.vyrocut.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LetterboxContentRect(
    val leftPx: Float,
    val topPx: Float,
    val rightPx: Float,
    val bottomPx: Float,
)

internal data class LetterboxPixelProbe(val x: Int, val y: Int)

/** Selects one interior pixel from each letterbox/pillarbox region, away from edges. */
internal fun letterboxBackgroundProbePoints(
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    sourceRotationDegrees: Int = 0,
): List<LetterboxPixelProbe> {
    if (canvasWidthPx <= 0 || canvasHeightPx <= 0) return emptyList()
    val content = calculateLetterboxContentRect(
        canvasWidthPx,
        canvasHeightPx,
        sourceWidthPx,
        sourceHeightPx,
        sourceRotationDegrees,
    ) ?: return emptyList()
    val probes = buildList {
        if (content.topPx > 0f) {
            add(LetterboxPixelProbe(canvasWidthPx / 2, (content.topPx / 2f).roundToInt()))
        }
        if (content.bottomPx < canvasHeightPx.toFloat()) {
            add(
                LetterboxPixelProbe(
                    canvasWidthPx / 2,
                    ((content.bottomPx + canvasHeightPx) / 2f).roundToInt(),
                ),
            )
        }
        if (content.leftPx > 0f) {
            add(LetterboxPixelProbe((content.leftPx / 2f).roundToInt(), canvasHeightPx / 2))
        }
        if (content.rightPx < canvasWidthPx.toFloat()) {
            add(
                LetterboxPixelProbe(
                    ((content.rightPx + canvasWidthPx) / 2f).roundToInt(),
                    canvasHeightPx / 2,
                ),
            )
        }
    }
    return probes.map { probe ->
        LetterboxPixelProbe(
            x = probe.x.coerceIn(0, canvasWidthPx - 1),
            y = probe.y.coerceIn(0, canvasHeightPx - 1),
        )
    }.distinct()
}

/**
 * Allows small H.264 colour shifts while rejecting a missing or wrong canvas
 * background. Empty samples mean the source fills the canvas and has no bars.
 */
internal fun validateLetterboxBackgroundPixels(
    actualPixelsArgb: List<Int>,
    expectedArgb: Int,
    maxChannelDifference: Int = 32,
): String? {
    if (actualPixelsArgb.isEmpty()) return null
    if (maxChannelDifference !in 0..255) return "The canvas background pixel tolerance is invalid"
    val expectedRed = (expectedArgb shr 16) and 0xFF
    val expectedGreen = (expectedArgb shr 8) and 0xFF
    val expectedBlue = expectedArgb and 0xFF
    val mismatch = actualPixelsArgb.any { actual ->
        val red = (actual shr 16) and 0xFF
        val green = (actual shr 8) and 0xFF
        val blue = actual and 0xFF
        abs(red - expectedRed) > maxChannelDifference ||
            abs(green - expectedGreen) > maxChannelDifference ||
            abs(blue - expectedBlue) > maxChannelDifference
    }
    return if (mismatch) {
        "The exported MP4 canvas background pixels do not match the selected background"
    } else {
        null
    }
}

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
    canvasWidthPx: Int,
    canvasHeightPx: Int,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    sourceRotationDegrees: Int,
    backgroundColorArgb: Int,
): Effect? {
    require(canvasWidthPx > 0 && canvasHeightPx > 0) {
        "Canvas dimensions must be positive before creating a background"
    }
    require(sourceWidthPx > 0 && sourceHeightPx > 0) {
        "Source dimensions must be positive before creating a canvas background"
    }
    val content = calculateLetterboxContentRect(
        canvasWidthPx = canvasWidthPx,
        canvasHeightPx = canvasHeightPx,
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        sourceRotationDegrees = sourceRotationDegrees,
    ) ?: return null
    val hasLetterboxBars = content.leftPx > 0f || content.topPx > 0f ||
        content.rightPx < canvasWidthPx.toFloat() || content.bottomPx < canvasHeightPx.toFloat()
    if (!hasLetterboxBars) return null

    // BitmapOverlay is available in the project's pinned Media3 1.5.1 API.
    // The transparent center lets the fitted source video remain visible.
    val bitmap = Bitmap.createBitmap(canvasWidthPx, canvasHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = backgroundColorArgb
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    val width = canvasWidthPx.toFloat()
    val height = canvasHeightPx.toFloat()
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
    return OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bitmap)))
}
