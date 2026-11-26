package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolDockLayoutRulesTest {
    @Test
    fun compactWindowReducesDockFootprintWithoutShrinkingTouchTargets() {
        val metrics = toolDockLayoutMetrics(479f, 1f)

        assertEquals(true, metrics.isCompact)
        assertEquals(VyroCutControlMetrics.CompactToolItemHorizontalPaddingDp, metrics.itemHorizontalPaddingDp)
        assertEquals(48, metrics.buttonWidthDp)
        assertEquals(48, metrics.buttonHeightDp)
        assertEquals(VyroCutControlMetrics.CompactToolIconSizeDp, metrics.iconSizeDp)
        assertTrue(metrics.buttonWidthDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
        assertTrue(metrics.buttonHeightDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
    }

    @Test
    fun compactDockUsesAnExactWidthBoundary() {
        assertEquals(true, toolDockLayoutMetrics(479.9f, 1f).isCompact)
        assertEquals(false, toolDockLayoutMetrics(480f, 1f).isCompact)
    }

    @Test
    fun largerFontScaleExpandsToolTargetsInsteadOfClippingLabels() {
        val metrics = toolDockLayoutMetrics(360f, 1.5f)

        assertEquals(false, metrics.isCompact)
        assertEquals(68, metrics.buttonWidthDp)
        assertEquals(56, metrics.buttonHeightDp)
        assertEquals(VyroCutControlMetrics.ToolIconSizeDp, metrics.iconSizeDp)
        assertTrue(metrics.buttonWidthDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
        assertTrue(metrics.buttonHeightDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
    }

    @Test
    fun largeFontScaleWinsOverCompactWidth() {
        assertEquals(
            false,
            toolDockLayoutMetrics(
                availableWidthDp = 360f,
                fontScale = VyroCutControlMetrics.ToolDockLargeFontScaleThreshold,
            ).isCompact,
        )
    }

    @Test
    fun fontScaleGrowthIsBoundedAtTheSharedMaximum() {
        val maximum = toolDockLayoutMetrics(360f, VyroCutControlMetrics.ToolDockMaxFontScale)

        assertEquals(84, maximum.buttonWidthDp)
        assertEquals(64, maximum.buttonHeightDp)
        assertEquals(maximum, toolDockLayoutMetrics(360f, 4f))
    }

    @Test
    fun invalidWindowWidthUsesTheSafeCompactFallback() {
        val expected = toolDockLayoutMetrics(0f, 1f)

        assertEquals(true, expected.isCompact)
        assertEquals(expected, toolDockLayoutMetrics(Float.NaN, 1f))
        assertEquals(expected, toolDockLayoutMetrics(Float.POSITIVE_INFINITY, 1f))
        assertEquals(expected, toolDockLayoutMetrics(-1f, 1f))
    }

    @Test
    fun invalidFontScaleUsesTheReadableBaseline() {
        val expected = toolDockLayoutMetrics(360f, 1f)

        assertEquals(expected, toolDockLayoutMetrics(360f, 0f))
        assertEquals(expected, toolDockLayoutMetrics(360f, Float.NaN))
        assertEquals(expected, toolDockLayoutMetrics(360f, Float.POSITIVE_INFINITY))
    }
}
