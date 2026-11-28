package com.vyrocut.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VyroCutDesignSystemTest {
    @Test
    fun typographyWeightsUseSharedSemanticRoles() {
        assertEquals(FontWeight.Normal, VyroCutTypography.RegularWeight)
        assertEquals(FontWeight.Medium, VyroCutTypography.MediumWeight)
        assertEquals(FontWeight.SemiBold, VyroCutTypography.EmphasisWeight)
        assertEquals(FontWeight.Bold, VyroCutTypography.StrongWeight)
    }

    @Test
    fun sharedPaletteMatchesTheApprovedDesignReference() {
        assertEquals(Color(0xFF131313), VyroCutPalette.Background)
        assertEquals(Color(0xFF1C1B1B), VyroCutPalette.Surface)
        assertEquals(Color(0xFF0FE8B8), VyroCutPalette.Accent)
        assertEquals(Color(0xFF00E5FF), VyroCutPalette.AccentSecondary)
        assertEquals(Color(0xFFE5EDEA), VyroCutPalette.TextPrimary)
        assertEquals(Color(0xFFA0B0A9), VyroCutPalette.TextMuted)
    }

    @Test
    fun timelineSemanticTokensReuseInitializedSourcePaletteValues() {
        assertEquals(VyroCutPalette.Accent, VyroCutPalette.TimelinePlayhead)
        assertEquals(VyroCutPalette.AccentSoft, VyroCutPalette.TimelineSelectionOutline)
        assertEquals(VyroCutPalette.Background, VyroCutPalette.TimelinePlayheadOutline)
        assertEquals(VyroCutPalette.Accent.copy(alpha = 0.28f), VyroCutPalette.TimelineFadeRamp)
    }

    @Test
    fun timedEffectTimelineColorsReuseTheSecondaryAccentWithoutChangingOpacity() {
        assertEquals(VyroCutPalette.AccentSecondary, VyroCutPalette.TimelineTimedEffectAccent)
        assertEquals(
            VyroCutPalette.TimelineTimedEffectAccent.copy(alpha = 0.22f),
            VyroCutPalette.TimelineTimedEffectFill,
        )
        assertEquals(VyroCutPalette.TimelineTimedEffectAccent, VyroCutPalette.TimelineTimedEffectTrimHandle)
    }

    @Test
    fun semanticCanvasAndMediaColorsStayCentralized() {
        assertEquals(Color.Black, VyroCutPalette.CanvasBlack)
        assertEquals(Color.White, VyroCutPalette.CanvasWhite)
        assertEquals(Color.White, VyroCutPalette.MediaOverlayText)
        assertEquals(0xFFFFFFFF.toInt(), VyroCutPalette.MediaOverlayText.toArgb())
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
    fun videoClipBoundaryHasDedicatedContrastWithoutRivalingSelection() {
        assertEquals(Color(0xFF586E65), VyroCutPalette.TimelineClipBoundary)
        assertTrue(
            contrastRatio(VyroCutPalette.TimelineClipBoundary, VyroCutPalette.SurfaceClip) >= 2.5,
        )
        assertTrue(
            contrastRatio(VyroCutPalette.TimelineSelectionOutline, VyroCutPalette.SurfaceSelected) >
                contrastRatio(VyroCutPalette.TimelineClipBoundary, VyroCutPalette.SurfaceClip),
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
        assertEquals(
            listOf(7, 7, 7, 2),
            listOf(
                VyroCutControlMetrics.ToolDockHorizontalPaddingDp,
                VyroCutControlMetrics.ToolDockVerticalPaddingDp,
                VyroCutControlMetrics.ToolItemHorizontalPaddingDp,
                VyroCutControlMetrics.ToolIndicatorCornerDp,
            ),
        )
    }

    @Test
    fun editorChromeMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 4, 28, 9, 8, 38, 6, 36, 18, 48, 24, 24, 5, 2, 8),
            listOf(
                VyroCutEditorChromeMetrics.HorizontalContentPaddingDp,
                VyroCutEditorChromeMetrics.ProjectNavigationButtonHorizontalPaddingDp,
                VyroCutEditorChromeMetrics.BrandIconSizeDp,
                VyroCutEditorChromeMetrics.BrandTitleStartPaddingDp,
                VyroCutEditorChromeMetrics.PreviewCanvasCornerDp,
                VyroCutEditorChromeMetrics.EmptyPreviewIconSizeDp,
                VyroCutEditorChromeMetrics.EmptyPreviewIconGapDp,
                VyroCutEditorChromeMetrics.CompactIconButtonSizeDp,
                VyroCutEditorChromeMetrics.TransportIconSizeDp,
                VyroCutEditorChromeMetrics.TransportSeekButtonSizeDp,
                VyroCutEditorChromeMetrics.TransportSeekIconSizeDp,
                VyroCutEditorChromeMetrics.PlaybackButtonIconSizeDp,
                VyroCutEditorChromeMetrics.PlaybackSpeedButtonHorizontalPaddingDp,
                VyroCutEditorChromeMetrics.ScrubberSectionGapDp,
                VyroCutEditorChromeMetrics.TimelineActionButtonHorizontalPaddingDp,
            ),
        )
    }

    @Test
    fun clipActionRowMetricsPreserveExistingGeometry() {
        assertEquals(6, VyroCutClipActionMetrics.ButtonGapDp)
        assertEquals(VyroCutShapes.CompactPanelCornerDp, VyroCutClipActionMetrics.ButtonCornerDp)
        assertEquals(10, VyroCutClipActionMetrics.ButtonCornerDp)
        assertEquals(6, VyroCutClipActionMetrics.CompactButtonHorizontalPaddingDp)
        assertEquals(24, VyroCutClipActionMetrics.StandardButtonHorizontalPaddingDp)
        assertEquals(10, VyroCutClipActionMetrics.ButtonVerticalPaddingDp)
        assertEquals(16, VyroCutClipActionMetrics.CompactIconSizeDp)
        assertEquals(24, VyroCutClipActionMetrics.StandardIconSizeDp)
        assertEquals(48, VyroCutControlMetrics.MinimumTouchTargetDp)
    }

    @Test
    fun sharedTypographyTokensRemainPositiveAndOrdered() {
        assertEquals(
            listOf(8, 9, 10, 11, 12, 14, 20, 22, 23),
            listOf(
                VyroCutTypography.BadgeSp,
                VyroCutTypography.ToolLabelSp,
                VyroCutTypography.CaptionSp,
                VyroCutTypography.LabelSp,
                VyroCutTypography.SmallBodySp,
                VyroCutTypography.BodySp,
                VyroCutTypography.SectionTitleSp,
                VyroCutTypography.BrandSp,
                VyroCutTypography.HeroTitleSp,
            ),
        )
    }


    @Test
    fun editorTypographyUsesSharedTokensWithoutChangingRenderedSizes() {
        assertEquals(12, VyroCutTypography.SmallBodySp)
        assertEquals(VyroCutTypography.SmallBodySp, VyroCutHomeMetrics.SearchPlaceholderSp)
        assertEquals(11, VyroCutTypography.LabelSp)
        assertEquals(10, VyroCutTypography.CaptionSp)
        assertEquals(22, VyroCutTypography.BrandSp)
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
    fun panelTypographyTokensPreserveSharedSemanticRoles() {
        assertEquals(1f, VyroCutTypography.PanelHeaderLetterSpacingSp, 0f)
        assertEquals(0.8f, VyroCutTypography.PanelCategoryLetterSpacingSp, 0f)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.HeaderFontSizeSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.HelperFontSizeSp)
        assertEquals(VyroCutTypography.ToolLabelSp, VyroCutTextPanelMetrics.CaptionFontSizeSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.ChipLabelFontSizeSp)
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
        assertEquals(VyroCutTypography.PanelHeaderLetterSpacingSp, VyroCutEffectsPanelMetrics.HeaderLetterSpacingSp)
        assertEquals(VyroCutTypography.PanelCategoryLetterSpacingSp, VyroCutEffectsPanelMetrics.CategoryLetterSpacingSp)
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
        assertEquals(VyroCutTypography.PanelHeaderLetterSpacingSp, VyroCutVisualPanelMetrics.HeaderLetterSpacingSp)
        assertEquals(VyroCutShapes.MediumCornerDp, 12)
        assertEquals(VyroCutSpacing.SmallDp, 8)
        assertEquals(VyroCutTypography.LabelSp, 11)
        assertEquals(VyroCutTypography.CaptionSp, 10)
    }

    @Test
    fun ratioAndCanvasPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 7, 8),
            listOf(
                VyroCutCanvasPanelMetrics.HorizontalPaddingDp,
                VyroCutCanvasPanelMetrics.VerticalPaddingDp,
                VyroCutCanvasPanelMetrics.SectionGapDp,
            ),
        )
        assertEquals(12, VyroCutShapes.MediumCornerDp)
        assertEquals(8, VyroCutSpacing.SmallDp)
        assertEquals(11, VyroCutTypography.LabelSp)
    }

    @Test
    fun textPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 8, 7, 6),
            listOf(
                VyroCutTextPanelMetrics.HorizontalPaddingDp,
                VyroCutTextPanelMetrics.VerticalPaddingDp,
                VyroCutTextPanelMetrics.SectionGapDp,
                VyroCutTextPanelMetrics.ChipGapDp,
            ),
        )
        assertEquals(10, VyroCutShapes.CompactPanelCornerDp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.HeaderFontSizeSp)
        assertEquals(VyroCutTypography.PanelHeaderLetterSpacingSp, VyroCutTextPanelMetrics.HeaderLetterSpacingSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.HelperFontSizeSp)
        assertEquals(VyroCutTypography.ToolLabelSp, VyroCutTextPanelMetrics.CaptionFontSizeSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTextPanelMetrics.ChipLabelFontSizeSp)
        assertEquals(8, VyroCutSpacing.SmallDp)
    }

    @Test
    fun stickerPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 9, 6, 6, 3, 6, 18, 24),
            listOf(
                VyroCutStickerPanelMetrics.HorizontalPaddingDp,
                VyroCutStickerPanelMetrics.VerticalPaddingDp,
                VyroCutStickerPanelMetrics.ChipGapDp,
                VyroCutStickerPanelMetrics.LayerVerticalPaddingDp,
                VyroCutStickerPanelMetrics.LayerItemGapDp,
                VyroCutStickerPanelMetrics.RemoveButtonHorizontalPaddingDp,
                VyroCutStickerPanelMetrics.AssetIconSizeDp,
                VyroCutStickerPanelMetrics.LayerAssetIconSizeDp,
            ),
        )
        assertEquals(19, VyroCutStickerPanelMetrics.SymbolChipFontSizeSp)
        assertEquals(20, VyroCutStickerPanelMetrics.LayerSymbolFontSizeSp)
        assertEquals(10, VyroCutShapes.CompactPanelCornerDp)
        assertEquals(8, VyroCutShapes.SmallCornerDp)
        assertEquals(8, VyroCutSpacing.SmallDp)
        assertEquals(11, VyroCutTypography.LabelSp)
        assertEquals(10, VyroCutTypography.CaptionSp)
        assertEquals(9, VyroCutTypography.ToolLabelSp)
    }

    @Test
    fun audioPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(10, 7, 6, 6, 32, 17, 7, 5, 6, 4),
            listOf(
                VyroCutAudioPanelMetrics.PanelHorizontalPaddingDp,
                VyroCutAudioPanelMetrics.LaneSectionGapDp,
                VyroCutAudioPanelMetrics.LaneChipGapDp,
                VyroCutAudioPanelMetrics.ClipCardVerticalPaddingDp,
                VyroCutAudioPanelMetrics.DeleteButtonSizeDp,
                VyroCutAudioPanelMetrics.DeleteIconSizeDp,
                VyroCutAudioPanelMetrics.ClipVolumePanelVerticalPaddingDp,
                VyroCutAudioPanelMetrics.ClipVolumeSectionGapDp,
                VyroCutAudioPanelMetrics.MuteButtonHorizontalPaddingDp,
                VyroCutAudioPanelMetrics.ActionButtonHorizontalPaddingDp,
            ),
        )
        assertEquals(6, VyroCutAudioPanelMetrics.ClipCardCornerDp)
        assertEquals(VyroCutTypography.PanelHeaderLetterSpacingSp, VyroCutAudioPanelMetrics.HeaderLetterSpacingSp)
        assertEquals(10, VyroCutShapes.CompactPanelCornerDp)
        assertEquals(8, VyroCutSpacing.SmallDp)
        assertEquals(8, VyroCutTypography.BadgeSp)
        assertEquals(9, VyroCutTypography.ToolLabelSp)
        assertEquals(10, VyroCutTypography.CaptionSp)
        assertEquals(11, VyroCutTypography.LabelSp)
    }

    @Test
    fun exportPanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(8, 2, 6, 8, 4, 10, 12),
            listOf(
                VyroCutExportPanelMetrics.SettingsRowGapDp,
                VyroCutExportPanelMetrics.SizeSummaryGapDp,
                VyroCutExportPanelMetrics.PresetChipGapDp,
                VyroCutExportPanelMetrics.ProgressCardSectionGapDp,
                VyroCutExportPanelMetrics.CompletionCardSectionGapDp,
                VyroCutExportPanelMetrics.CardCornerDp,
                VyroCutExportPanelMetrics.CardPaddingDp,
            ),
        )
    }


    @Test
    fun semanticTextColorsMeetNormalTextContrastAcrossStudioSurfaces() {
        val surfaces = listOf(
            VyroCutPalette.Background,
            VyroCutPalette.Surface,
            VyroCutPalette.SurfaceElevated,
            VyroCutPalette.SurfaceToolbar,
            VyroCutPalette.SurfaceSelected,
            VyroCutPalette.SurfaceSelectedTimeline,
            VyroCutPalette.SurfaceTimeline,
            VyroCutPalette.SurfaceClip,
            VyroCutPalette.SurfaceBadge,
            VyroCutPalette.SurfaceTrack,
        )

        surfaces.forEach { surface ->
            assertTrue(
                "Muted text contrast on $surface must meet 4.5:1",
                contrastRatio(VyroCutPalette.TextMuted, surface) >= 4.5,
            )
            assertTrue(
                "Primary text contrast on $surface must meet 4.5:1",
                contrastRatio(VyroCutPalette.TextPrimary, surface) >= 4.5,
            )
            assertTrue(
                "Danger text contrast on $surface must meet 4.5:1",
                contrastRatio(VyroCutPalette.Danger, surface) >= 4.5,
            )
        }
    }

    @Test
    fun subtleWaveformColorKeepsNonTextContrastOnUnselectedAudioClipSurface() {
        assertTrue(
            contrastRatio(VyroCutPalette.TextSubtle, VyroCutPalette.SurfaceClip) >= 3.0,
        )
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

    @Test
    fun mediaOverlayRendererColorsPreserveExistingArgbValues() {
        assertEquals(0x99000000.toInt(), VyroCutPalette.MediaOverlayTextShadow.toArgb())
        assertEquals(0xB8000000.toInt(), VyroCutPalette.MediaOverlayCaptionBackground.toArgb())
        assertEquals(0xAA000000.toInt(), VyroCutPalette.StickerFallbackTextShadow.toArgb())
    }

    @Test
    fun waveformRendererTokensPreserveExistingAppearance() {
        assertEquals(
            listOf(24f, 0.28f, 1f, 0.58f, 1f, 3f, 1f),
            listOf(
                VyroCutWaveformMetrics.StripDefaultHeightDp,
                VyroCutWaveformMetrics.StripBaselineAlpha,
                VyroCutWaveformMetrics.StripBaselineStrokeWidthDp,
                VyroCutWaveformMetrics.StripStrokeWidthRatio,
                VyroCutWaveformMetrics.StripMinimumStrokeWidthDp,
                VyroCutWaveformMetrics.StripMaximumStrokeWidthDp,
                VyroCutWaveformMetrics.StripMinimumBarHalfHeightDp,
            ),
        )
        assertEquals(
            listOf(0.45f, 1f, 1.5f, 1f, 2f, 0.06f, 0.45f, 0.55f),
            listOf(
                VyroCutWaveformMetrics.ClipBaselineAlpha,
                VyroCutWaveformMetrics.ClipBaselineStrokeWidthDp,
                VyroCutWaveformMetrics.ClipBarGapDp,
                VyroCutWaveformMetrics.ClipBarMinimumWidthDp,
                VyroCutWaveformMetrics.ClipBarMinimumHeightDp,
                VyroCutWaveformMetrics.ClipAmplitudeFloor,
                VyroCutWaveformMetrics.ClipBarBaseAlpha,
                VyroCutWaveformMetrics.ClipBarPeakAlpha,
            ),
        )
        assertEquals(1f, VyroCutWaveformMetrics.ClipBarBaseAlpha + VyroCutWaveformMetrics.ClipBarPeakAlpha, 0f)
    }

    @Test
    fun timelinePanelMetricsPreserveExistingLayoutDimensions() {
        assertEquals(
            listOf(6, 18, 6, 3, 4, 2, 6, 6, 6, 4, 5),
            listOf(
                VyroCutTimelinePanelMetrics.EmptyStateSectionGapDp,
                VyroCutTimelinePanelMetrics.EmptyStateVerticalPaddingDp,
                VyroCutTimelinePanelMetrics.TrackVerticalGapDp,
                VyroCutTimelinePanelMetrics.VideoClipContentPaddingDp,
                VyroCutTimelinePanelMetrics.ClipMetaBadgeHorizontalPaddingDp,
                VyroCutTimelinePanelMetrics.ClipMetaBadgeVerticalPaddingDp,
                VyroCutTimelinePanelMetrics.TextOverlaySegmentHorizontalPaddingDp,
                VyroCutTimelinePanelMetrics.StickerAddLabelHorizontalPaddingDp,
                VyroCutTimelinePanelMetrics.StickerAddLabelVerticalPaddingDp,
                VyroCutTimelinePanelMetrics.StickerSegmentHorizontalPaddingDp,
                VyroCutTimelinePanelMetrics.AudioSegmentHorizontalPaddingDp,
            ),
        )
    }


    @Test
    fun timelineVisualTokensPreserveTheCurrentEditorGeometry() {
        assertEquals(
            listOf(28, 72, 42, 30, 30, 32, 22, 36, 14),
            listOf(
                VyroCutTimelineVisualMetrics.RulerHeightDp,
                VyroCutTimelineVisualMetrics.VideoTrackHeightDp,
                VyroCutTimelineVisualMetrics.VideoThumbnailHeightDp,
                VyroCutTimelineVisualMetrics.TextRowHeightDp,
                VyroCutTimelineVisualMetrics.StickerRowHeightDp,
                VyroCutTimelineVisualMetrics.SourceTrackHeightDp,
                VyroCutTimelineVisualMetrics.SourceWaveformHeightDp,
                VyroCutTimelineVisualMetrics.AudioLaneHeightDp,
                VyroCutTimelineVisualMetrics.AudioWaveformHeightDp,
            ),
        )
        assertEquals(
            listOf(6, 4, 5, 3, 2, 1, 5, 4, 8, 320),
            listOf(
                VyroCutTimelineVisualMetrics.TrackHeaderBadgeHorizontalPaddingDp,
                VyroCutTimelineVisualMetrics.TrackSegmentCornerRadiusDp,
                VyroCutTimelineVisualMetrics.VideoClipCornerRadiusDp,
                VyroCutTimelineVisualMetrics.PlayheadCapOuterCornerRadiusDp,
                VyroCutTimelineVisualMetrics.PlayheadCapInnerCornerRadiusDp,
                VyroCutTimelineVisualMetrics.RulerTickCornerRadiusDp,
                VyroCutTimelineVisualMetrics.TrackHeaderDotSizeDp,
                VyroCutTimelineVisualMetrics.TrackHeaderDotGapDp,
                VyroCutTimelineVisualMetrics.TrackHeaderDetailGapDp,
                VyroCutTimelineVisualMetrics.TrackHeaderTrailingDetailMinWidthDp,
            ),
        )
        assertEquals(
            listOf(2, 4, 14, 9, 2, 2, 1, 10, 5, 3, 1, 11, 3, 2, 8),
            listOf(
                VyroCutTimelineVisualMetrics.PlayheadLineWidthDp,
                VyroCutTimelineVisualMetrics.PlayheadOutlineWidthDp,
                VyroCutTimelineVisualMetrics.PlayheadCapWidthDp,
                VyroCutTimelineVisualMetrics.PlayheadCapHeightDp,
                VyroCutTimelineVisualMetrics.SelectedClipBorderWidthDp,
                VyroCutTimelineVisualMetrics.RulerMajorTickWidthDp,
                VyroCutTimelineVisualMetrics.RulerMinorTickWidthDp,
                VyroCutTimelineVisualMetrics.RulerMajorTickHeightDp,
                VyroCutTimelineVisualMetrics.RulerMinorTickHeightDp,
                VyroCutTimelineVisualMetrics.RulerLabelHorizontalPaddingDp,
                VyroCutTimelineVisualMetrics.RulerLabelVerticalPaddingDp,
                VyroCutTimelineVisualMetrics.RulerLabelVerticalOffsetDp,
                VyroCutTimelineVisualMetrics.RulerLabelCornerRadiusDp,
                VyroCutTimelineVisualMetrics.RulerLabelInterLabelGapDp,
                VyroCutTimelineVisualMetrics.RulerMinimumTickSpacingDp,
            ),
        )
        assertEquals(VyroCutTypography.LabelSp, VyroCutTimelineVisualMetrics.TrackLabelFontSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTimelineVisualMetrics.TrackDetailFontSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTimelineVisualMetrics.RulerLabelFontSp)
        assertEquals(VyroCutTypography.LabelSp, VyroCutTimelineVisualMetrics.ClipLabelFontSp)
        assertEquals(VyroCutTypography.CaptionSp, VyroCutTimelineVisualMetrics.ClipMetaFontSp)
        assertEquals(VyroCutTypography.TimelineStickerSymbolSp, VyroCutTimelineVisualMetrics.StickerSymbolFontSp)
        assertEquals(0.62f, VyroCutTimelineVisualMetrics.RulerLabelGlyphWidthFactor, 0f)
        assertEquals(8f, VyroCutTimelineVisualMetrics.RulerLabelGapDp, 0f)
        assertEquals(32, VyroCutTimelineVisualMetrics.MaximumRulerLabelCharacters)
        assertEquals(
            VyroCutTimelineVisualMetrics.TrackSegmentCornerRadiusDp,
            VyroCutTimelineVisualMetrics.TrackHeaderCornerRadiusDp,
        )
        assertTrue(VyroCutTimelineVisualMetrics.TimedEffectTrackHeightDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
        assertTrue(VyroCutTimelineVisualMetrics.TimedEffectTrimHandleHitWidthDp >= VyroCutControlMetrics.MinimumTouchTargetDp)
    }


    @Test
    fun timedEffectTrimHandleGeometryUsesCentralDesignTokens() {
        assertEquals(4, VyroCutTimelineVisualMetrics.TimedEffectTrimHandleWidthDp)
        assertEquals(0.72f, VyroCutTimelineVisualMetrics.TimedEffectTrimHandleHeightFraction, 0f)
        assertEquals(2, VyroCutTimelineVisualMetrics.TimedEffectTrimHandleCornerRadiusDp)
        assertTrue(VyroCutTimelineVisualMetrics.TimedEffectTrimHandleWidthDp > 0)
        assertTrue(VyroCutTimelineVisualMetrics.TimedEffectTrimHandleHeightFraction in 0f..1f)
        assertTrue(VyroCutTimelineVisualMetrics.TimedEffectTrimHandleCornerRadiusDp >= 0)
    }

    @Test
    fun responsiveEditorLayoutTokensPreserveCurrentWindowAndAccessibilityBreakpoints() {
        assertEquals(
            listOf(112, 88, 100, 112, 144, 168),
            listOf(
                VyroCutEditorLayoutMetrics.MinPreviewHeightDp,
                VyroCutEditorLayoutMetrics.MinCompactPreviewHeightDp,
                VyroCutEditorLayoutMetrics.MinTightPreviewHeightDp,
                VyroCutEditorLayoutMetrics.MaxCompactPreviewHeightDp,
                VyroCutEditorLayoutMetrics.MaxTightPreviewHeightDp,
                VyroCutEditorLayoutMetrics.MaxPreviewHeightDp,
            ),
        )
        assertEquals(
            listOf(520, 600, 440, 64, 680, 336, 48, 560, 439),
            listOf(
                VyroCutEditorLayoutMetrics.TightWindowHeightDp,
                VyroCutEditorLayoutMetrics.CompactWindowHeightDp,
                VyroCutEditorLayoutMetrics.CompactWindowWidthDp,
                VyroCutEditorLayoutMetrics.RelinkActionExtraWidthDp,
                VyroCutEditorLayoutMetrics.ClipActionStandardLabelMinWidthDp,
                VyroCutEditorLayoutMetrics.ClipActionControlsMinimumContentWidthDp,
                VyroCutEditorLayoutMetrics.ClipActionControlsFontScaleWidthGrowthDp,
                VyroCutEditorLayoutMetrics.LayoutFallbackWindowHeightDp,
                VyroCutEditorLayoutMetrics.LayoutFallbackWindowWidthDp,
            ),
        )
        assertEquals(1, VyroCutEditorLayoutMetrics.PreviewInlineSubtitleMaxLines)
        assertEquals(2, VyroCutEditorLayoutMetrics.PreviewStackedSubtitleMaxLines)
        assertEquals(1.15f, VyroCutEditorLayoutMetrics.LargeFontScaleThreshold, 0f)
        assertEquals(0.30f, VyroCutEditorLayoutMetrics.PreviewReductionPerFontScale, 0f)
        assertEquals(0.12f, VyroCutEditorLayoutMetrics.MaxFontScalePreviewReduction, 0f)
        assertEquals(
            listOf(3, 2, 4, 6),
            listOf(
                VyroCutEditorLayoutMetrics.TightSectionSpacingDp,
                VyroCutEditorLayoutMetrics.TightContentPaddingVerticalDp,
                VyroCutEditorLayoutMetrics.CompactSectionSpacingDp,
                VyroCutEditorLayoutMetrics.DefaultSectionSpacingDp,
            ),
        )
    }

    @Test
    fun editorSpacingAndThumbnailCornerTokensPreserveExistingDimensions() {
        assertEquals(8, VyroCutSpacing.SmallDp)
        assertEquals(6, VyroCutEditorChromeMetrics.RelinkButtonHorizontalPaddingDp)
        assertEquals(6, VyroCutTimelineVisualMetrics.VideoThumbnailCornerRadiusDp)
    }

    @Test
    fun smallActionAndCircularIndicatorStylesUseSharedTokens() {
        assertEquals(16, VyroCutEffectsPanelMetrics.AddActionIconSizeDp)
        assertEquals(50, VyroCutShapes.CircularIndicatorCornerDp)
    }

}
