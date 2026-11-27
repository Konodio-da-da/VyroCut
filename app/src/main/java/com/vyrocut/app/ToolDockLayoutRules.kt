package com.vyrocut.app

import kotlin.math.roundToInt

internal data class ToolDockLayoutMetrics(
    val isCompact: Boolean,
    val itemHorizontalPaddingDp: Int,
    val buttonWidthDp: Int,
    val buttonHeightDp: Int,
    val iconSizeDp: Int,
)

/**
 * Keeps the horizontally scrolling tool dock dense on narrow windows while
 * giving labels more room as system font scale grows. Button dimensions never
 * fall below the shared 48 dp touch-target baseline.
 */
internal fun toolDockLayoutMetrics(
    availableWidthDp: Float,
    fontScale: Float,
): ToolDockLayoutMetrics {
    val safeWidth = availableWidthDp.takeIf { it.isFinite() && it > 0f } ?: 0f
    val safeFontScale = (fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f)
        .coerceAtMost(VyroCutControlMetrics.ToolDockMaxFontScale)

    val compact = safeWidth < VyroCutControlMetrics.ToolDockCompactWidthThresholdDp &&
        safeFontScale < VyroCutControlMetrics.ToolDockLargeFontScaleThreshold

    if (compact) {
        return ToolDockLayoutMetrics(
            isCompact = true,
            itemHorizontalPaddingDp = VyroCutControlMetrics.CompactToolItemHorizontalPaddingDp,
            buttonWidthDp = VyroCutControlMetrics.CompactToolButtonWidthDp,
            buttonHeightDp = VyroCutControlMetrics.CompactToolButtonHeightDp,
            iconSizeDp = VyroCutControlMetrics.CompactToolIconSizeDp,
        )
    }

    val fontScaleGrowth = (safeFontScale - 1f).coerceAtLeast(0f)
    val widthGrowth = (fontScaleGrowth * VyroCutControlMetrics.ToolDockFontScaleWidthGrowthDp)
        .roundToInt()
        .coerceAtLeast(0)
    val heightGrowth = (fontScaleGrowth * VyroCutControlMetrics.ToolDockFontScaleHeightGrowthDp)
        .roundToInt()
        .coerceAtLeast(0)

    return ToolDockLayoutMetrics(
        isCompact = false,
        itemHorizontalPaddingDp = VyroCutControlMetrics.ToolItemHorizontalPaddingDp,
        buttonWidthDp = VyroCutControlMetrics.ToolButtonWidthDp + widthGrowth,
        buttonHeightDp = VyroCutControlMetrics.ToolButtonHeightDp + heightGrowth,
        iconSizeDp = VyroCutControlMetrics.ToolIconSizeDp,
    )
}
