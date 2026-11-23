package com.vyrocut.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VyroCutDesignSystemTest {
    @Test
    fun sharedPaletteMatchesTheApprovedDesignReference() {
        assertEquals(Color(0xFF131313), VyroCutPalette.Background)
        assertEquals(Color(0xFF1C1B1B), VyroCutPalette.Surface)
        assertEquals(Color(0xFF0FE8B8), VyroCutPalette.Accent)
        assertEquals(Color(0xFF00E5FF), VyroCutPalette.AccentSecondary)
        assertEquals(Color(0xFFE5EDEA), VyroCutPalette.TextPrimary)
        assertEquals(Color(0xFF9BAAA5), VyroCutPalette.TextMuted)
    }

    @Test
    fun timelineSemanticTokensReuseInitializedSourcePaletteValues() {
        assertEquals(VyroCutPalette.Accent, VyroCutPalette.TimelinePlayhead)
        assertEquals(VyroCutPalette.AccentSoft, VyroCutPalette.TimelineSelectionOutline)
        assertEquals(VyroCutPalette.Background, VyroCutPalette.TimelinePlayheadOutline)
    }

    @Test
    fun semanticCanvasAndMediaColorsStayCentralized() {
        assertEquals(Color.Black, VyroCutPalette.CanvasBlack)
        assertEquals(Color.White, VyroCutPalette.CanvasWhite)
        assertEquals(Color.White, VyroCutPalette.MediaOverlayText)
        assertEquals(Color.Black.copy(alpha = 0.68f), VyroCutPalette.MediaOverlayScrim)
        assertEquals(Color.White.copy(alpha = 0.9f), VyroCutPalette.WaveformForeground)
        assertEquals(Color.Black, VyroCutPalette.OnDanger)
        assertEquals(Color(0xFF39423F), VyroCutPalette.Outline)
    }

    @Test
    fun timelineEmphasisColorsHaveStrongContrastAgainstTrackSurfaces() {
        val trackSurfaces = listOf(
            VyroCutPalette.SurfaceClip,
            VyroCutPalette.SurfaceTimeline,
            VyroCutPalette.SurfaceSelected,
            VyroCutPalette.SurfaceSelectedTimeline,
            VyroCutPalette.HeroGradientStart,
        )
        trackSurfaces.forEach { surface ->
            assertTrue(
                "Playhead must remain distinguishable from $surface",
                contrastRatio(VyroCutPalette.TimelinePlayhead, surface) >= 3.0,
            )
        }
        assertTrue(
            contrastRatio(VyroCutPalette.TimelineSelectionOutline, VyroCutPalette.SurfaceSelected) >= 3.0,
        )
        assertTrue(
            contrastRatio(VyroCutPalette.TimelinePlayheadOutline, VyroCutPalette.TimelinePlayhead) >= 3.0,
        )
    }

    @Test
    fun materialColorSchemeUsesTheSharedPalette() {
        val scheme = vyroCutColorScheme()

        assertEquals(VyroCutPalette.Accent, scheme.primary)
        assertEquals(VyroCutPalette.AccentSecondary, scheme.secondary)
        assertEquals(VyroCutPalette.Background, scheme.background)
        assertEquals(VyroCutPalette.Surface, scheme.surface)
        assertEquals(VyroCutPalette.TextPrimary, scheme.onSurface)
        assertEquals(VyroCutPalette.Danger, scheme.error)
        assertEquals(VyroCutPalette.OnDanger, scheme.onError)
        assertEquals(VyroCutPalette.Outline, scheme.outline)
    }


    @Test
    fun sharedLayoutTokensKeepTheExistingSpacingAndShapeScaleOrdered() {
        assertEquals(
            listOf(2, 4, 8, 12, 16, 24),
            listOf(
                VyroCutSpacing.XXSmallDp,
                VyroCutSpacing.XSmallDp,
                VyroCutSpacing.SmallDp,
                VyroCutSpacing.MediumDp,
                VyroCutSpacing.LargeDp,
                VyroCutSpacing.XLargeDp,
            ),
        )
        assertEquals(
            listOf(8, 9, 12, 16, 20, 24),
            listOf(
                VyroCutShapes.SmallCornerDp,
                VyroCutShapes.ToolItemCornerDp,
                VyroCutShapes.MediumCornerDp,
                VyroCutShapes.LargeCornerDp,
                VyroCutShapes.CardCornerDp,
                VyroCutShapes.PillCornerDp,
            ),
        )
    }

    @Test
    fun toolDockControlsPreserveAccessibleMinimumDimensions() {
        assertTrue(VyroCutControlMetrics.MinimumTouchTargetDp >= 48)
        assertTrue(VyroCutControlMetrics.ToolButtonWidthDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
        assertTrue(VyroCutControlMetrics.ToolButtonHeightDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
        assertTrue(VyroCutControlMetrics.ToolIconSizeDp > 0)
        assertTrue(VyroCutControlMetrics.ToolIndicatorWidthDp > 0)
        assertTrue(VyroCutControlMetrics.ToolIndicatorHeightDp > 0)
    }

    @Test
    fun sharedTypographyTokensRemainPositiveAndOrdered() {
        assertEquals(
            listOf(8, 9, 10, 11, 14, 20, 22, 23),
            listOf(
                VyroCutTypography.BadgeSp,
                VyroCutTypography.ToolLabelSp,
                VyroCutTypography.CaptionSp,
                VyroCutTypography.LabelSp,
                VyroCutTypography.BodySp,
                VyroCutTypography.SectionTitleSp,
                VyroCutTypography.BrandSp,
                VyroCutTypography.HeroTitleSp,
            ),
        )
    }


    @Test
    fun projectHomeMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(
                14, 50, 12, 18, 42, 27, 13, 9, 7, 176, 18, 14, 13, 34, 10,
                17, 5, 30, 38, 13, 11, 76, 68, 27, 3, 5, 2,
            ),
            listOf(
                VyroCutHomeMetrics.ProjectListItemGapDp,
                VyroCutHomeMetrics.SearchFieldHeightDp,
                VyroCutHomeMetrics.SearchPlaceholderSp,
                VyroCutHomeMetrics.SearchIconSizeDp,
                VyroCutHomeMetrics.ImportButtonSizeDp,
                VyroCutHomeMetrics.ImportIconSizeDp,
                VyroCutHomeMetrics.OfflineBannerHorizontalPaddingDp,
                VyroCutHomeMetrics.OfflineBannerVerticalPaddingDp,
                VyroCutHomeMetrics.OfflineIndicatorSizeDp,
                VyroCutHomeMetrics.HeroCardHeightDp,
                VyroCutHomeMetrics.HeroCardHorizontalPaddingDp,
                VyroCutHomeMetrics.HeroIconCornerDp,
                VyroCutHomeMetrics.HeroIconPaddingDp,
                VyroCutHomeMetrics.HeroIconSizeDp,
                VyroCutHomeMetrics.HeroTitleGapDp,
                VyroCutHomeMetrics.SortIconSizeDp,
                VyroCutHomeMetrics.SortLabelGapDp,
                VyroCutHomeMetrics.EmptyStateVerticalPaddingDp,
                VyroCutHomeMetrics.EmptyStateIconSizeDp,
                VyroCutHomeMetrics.ProjectCardCornerDp,
                VyroCutHomeMetrics.ProjectCardPaddingDp,
                VyroCutHomeMetrics.ProjectThumbnailWidthDp,
                VyroCutHomeMetrics.ProjectThumbnailHeightDp,
                VyroCutHomeMetrics.ProjectThumbnailIconSizeDp,
                VyroCutHomeMetrics.ProjectBadgeCornerDp,
                VyroCutHomeMetrics.ProjectBadgeHorizontalPaddingDp,
                VyroCutHomeMetrics.ProjectBadgeVerticalPaddingDp,
            ),
        )
    }

    @Test
    fun effectsPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 9, 6, 6, 0),
            listOf(
                VyroCutEffectsPanelMetrics.HorizontalPaddingDp,
                VyroCutEffectsPanelMetrics.VerticalPaddingDp,
                VyroCutEffectsPanelMetrics.SectionGapDp,
                VyroCutEffectsPanelMetrics.ResetButtonHorizontalPaddingDp,
                VyroCutEffectsPanelMetrics.ResetButtonVerticalPaddingDp,
            ),
        )
        assertEquals(1f, VyroCutEffectsPanelMetrics.HeaderLetterSpacingSp, 0f)
        assertEquals(0.8f, VyroCutEffectsPanelMetrics.CategoryLetterSpacingSp, 0f)
        assertEquals(12, VyroCutShapes.MediumCornerDp)
        assertEquals(8, VyroCutSpacing.SmallDp)
        assertEquals(10, VyroCutTypography.CaptionSp)
        assertEquals(11, VyroCutTypography.LabelSp)
    }

    @Test
    fun filtersAndAdjustPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 7, 10, 6, 0),
            listOf(
                VyroCutVisualPanelMetrics.HorizontalPaddingDp,
                VyroCutVisualPanelMetrics.VerticalPaddingDp,
                VyroCutVisualPanelMetrics.SectionGapDp,
                VyroCutVisualPanelMetrics.ResetButtonHorizontalPaddingDp,
                VyroCutVisualPanelMetrics.ResetButtonVerticalPaddingDp,
            ),
        )
        assertEquals(1f, VyroCutVisualPanelMetrics.HeaderLetterSpacingSp, 0f)
        assertEquals(VyroCutShapes.MediumCornerDp, 12)
        assertEquals(VyroCutSpacing.SmallDp, 8)
        assertEquals(VyroCutTypography.LabelSp, 11)
        assertEquals(VyroCutTypography.CaptionSp, 10)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92
            else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) +
            0.7152 * linear(color.green) +
            0.0722 * linear(color.blue)
    }
}
