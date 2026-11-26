package com.vyrocut.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Shared visual tokens for the home screen, editor, and Material components.
 *
 * Keep UI colors here so the app can evolve without each screen drifting into
 * a separate palette. Track and canvas colors remain semantic and distinct.
 */
internal object VyroCutPalette {
    val Background = Color(0xFF131313)
    val Surface = Color(0xFF1C1B1B)
    val SurfaceElevated = Color(0xFF292929)
    val SurfaceToolbar = Color(0xFF1B201F)
    val SurfaceSelected = Color(0xFF16483D)
    val SurfaceSelectedTimeline = Color(0xFF1B4239)
    val SurfaceTimeline = Color(0xFF172C28)
    val SurfaceClip = Color(0xFF252A28)
    val SurfaceBadge = Color(0xFF29312E)
    val SurfaceTrack = Color(0xFF293330)

    // Declare source accent tokens before derived timeline tokens. Kotlin
    // initializes object properties in declaration order.
    val Accent = Color(0xFF0FE8B8)
    val AccentSecondary = Color(0xFF00E5FF)
    val AccentSoft = Color(0xFF8AFFD8)
    val AccentInk = Color(0xFF00382A)

    val TimelinePlayhead = Accent
    val TimelinePlayheadOutline = Background
    val TimelineSelectionOutline = AccentSoft
    val HeroGradientStart = Color(0xFF202725)
    val HeroGradientEnd = Color(0xFF171918)
    val ThumbnailGradientStart = Color(0xFF34443E)
    val CanvasDark = Color(0xFF202322)

    val TextPrimary = Color(0xFFE5EDEA)
    val TextMuted = Color(0xFFA0B0A9)
    val TextSubtle = Color(0xFF71827B)
    val Danger = Color(0xFFFF8A80)
    val OnDanger = Color.Black
    val Outline = Color(0xFF39423F)

    // Semantic media/canvas colors: keep true black/white distinct from UI text.
    val CanvasBlack = Color.Black
    val CanvasWhite = Color.White
    val MediaOverlayText = Color.White
    val MediaOverlayTextShadow = Color(0x99000000)
    val MediaOverlayCaptionBackground = Color(0xB8000000)
    val StickerFallbackTextShadow = Color(0xAA000000)
    val MediaOverlayScrim = Color.Black.copy(alpha = 0.68f)
    val WaveformForeground = Color.White.copy(alpha = 0.9f)

    val MusicTrack = Color(0xFF185C48)
    val SfxTrack = Color(0xFF57452A)
    val VoiceoverTrack = Color(0xFF344A67)
}


/** Shared spacing scale. Values are dp units to keep token files platform-light. */
internal object VyroCutSpacing {
    const val XXSmallDp = 2
    const val XSmallDp = 4
    const val SmallDp = 8
    const val MediumDp = 12
    const val LargeDp = 16
    const val XLargeDp = 24
}

/** Shared corner radii; the tool item keeps its existing 9 dp shape. */
internal object VyroCutShapes {
    const val SmallCornerDp = 8
    const val CompactPanelCornerDp = 10
    const val ToolItemCornerDp = 9
    const val MediumCornerDp = 12
    const val LargeCornerDp = 16
    const val CardCornerDp = 20
    const val PillCornerDp = 24
}

/** Small, explicit type scale shared by the home, editor, and timeline. */
internal object VyroCutTypography {
    const val BadgeSp = 8
    const val ToolLabelSp = 9
    const val CaptionSp = 10
    const val LabelSp = 11
    const val SmallBodySp = 12
    const val BodySp = 14
    const val SectionTitleSp = 20
    const val BrandSp = 22
    const val HeroTitleSp = 23
    const val TimelineStickerSymbolSp = 14
    const val TimelineSectionLetterSpacingSp = 1.3f
}

/** Control geometry tokens preserve the current dock's minimum hit dimensions. */
internal object VyroCutControlMetrics {
    const val MinimumTouchTargetDp = 48
    const val ToolButtonWidthDp = 52
    const val ToolButtonHeightDp = 48
    const val ToolIconSizeDp = 20
    const val ToolIndicatorWidthDp = 18
    const val ToolIndicatorHeightDp = 2
    const val ToolDockHorizontalPaddingDp = 7
    const val ToolDockVerticalPaddingDp = 7
    const val ToolItemHorizontalPaddingDp = 7
    const val ToolIndicatorCornerDp = 2
    const val CompactToolButtonWidthDp = MinimumTouchTargetDp
    const val CompactToolButtonHeightDp = ToolButtonHeightDp
    const val CompactToolIconSizeDp = 18
    const val CompactToolItemHorizontalPaddingDp = 4
    const val ToolDockCompactWidthThresholdDp = 480
    const val ToolDockLargeFontScaleThreshold = 1.3f
    const val ToolDockMaxFontScale = 2f
    const val ToolDockFontScaleWidthGrowthDp = 32
    const val ToolDockFontScaleHeightGrowthDp = 16
}


/**
 * Editor header, preview, transport and timeline-control geometry.
 * Values intentionally preserve the current Compose layout while centralizing
 * repeated styling literals for safe future refinement.
 */
internal object VyroCutEditorChromeMetrics {
    const val HorizontalContentPaddingDp = 10
    const val ProjectNavigationButtonHorizontalPaddingDp = 4
    const val BrandIconSizeDp = 28
    const val BrandTitleStartPaddingDp = 9
    const val PreviewCanvasCornerDp = 8
    const val EmptyPreviewIconSizeDp = 38
    const val EmptyPreviewIconGapDp = 6
    const val CompactIconButtonSizeDp = 36
    const val TransportIconSizeDp = 18
    const val TransportSeekButtonSizeDp = 48
    const val TransportSeekIconSizeDp = 24
    const val PlaybackButtonIconSizeDp = 24
    const val PlaybackSpeedButtonHorizontalPaddingDp = 5
    const val ScrubberSectionGapDp = 2
    const val TimelineActionButtonHorizontalPaddingDp = 8
    const val RelinkButtonHorizontalPaddingDp = 6
}

/**
 * Responsive editor layout thresholds and sizing rules. Keep these values in
 * the shared design system so compact-window and accessibility behaviour can
 * be tuned without scattering breakpoints across screen logic.
 */
internal object VyroCutEditorLayoutMetrics {
    const val MinPreviewHeightDp = 112
    const val MinCompactPreviewHeightDp = 88
    const val MinTightPreviewHeightDp = 100
    const val MaxCompactPreviewHeightDp = 112
    const val MaxTightPreviewHeightDp = 144
    const val MaxPreviewHeightDp = 168

    const val TightWindowHeightDp = 520
    const val CompactWindowHeightDp = 600
    const val CompactWindowWidthDp = 440
    // The transport row contains four frame/seek controls, speed, and play.
    // Below this window width the row scrolls instead of clipping its actions.
    const val TransportControlsMinimumWindowWidthDp = 340
    const val TransportControlsFontScaleWidthGrowthDp = 40
    const val RelinkActionExtraWidthDp = 64
    const val ClipActionStandardLabelMinWidthDp = 680
    const val ClipActionControlsMinimumContentWidthDp = 336
    const val ClipActionControlsFontScaleWidthGrowthDp = 48
    const val PreviewInlineSubtitleMaxLines = 1
    const val PreviewStackedSubtitleMaxLines = 2
    const val LargeFontScaleThreshold = 1.15f
    const val PreviewReductionPerFontScale = 0.30f
    const val MaxFontScalePreviewReduction = 0.12f
    const val LayoutFallbackWindowHeightDp = 560
    const val LayoutFallbackWindowWidthDp = CompactWindowWidthDp - 1

    const val TightSectionSpacingDp = 3
    const val TightContentPaddingVerticalDp = 2
    const val CompactSectionSpacingDp = 4
    const val DefaultSectionSpacingDp = 6
}

/**
 * Geometry for the video import/split/trim action row.
 * Values preserve the current button spacing and corner shape.
 */
internal object VyroCutClipActionMetrics {
    const val ButtonGapDp = 6
    const val ButtonCornerDp = VyroCutShapes.CompactPanelCornerDp
    const val CompactButtonHorizontalPaddingDp = 6
    const val StandardButtonHorizontalPaddingDp = 24
    const val ButtonVerticalPaddingDp = 10
    const val CompactIconSizeDp = 16
    const val StandardIconSizeDp = 24
}

/**
 * Home-screen-specific geometry retained as semantic tokens during styling
 * consolidation. Values intentionally match the existing Compose layout.
 */
internal object VyroCutHomeMetrics {
    const val ProjectListItemGapDp = 14
    const val SearchFieldHeightDp = 50
    const val SearchPlaceholderSp = VyroCutTypography.SmallBodySp
    const val SearchIconSizeDp = 18
    const val ImportButtonSizeDp = 42
    const val ImportIconSizeDp = 27
    const val OfflineBannerHorizontalPaddingDp = 13
    const val OfflineBannerVerticalPaddingDp = 9
    const val OfflineIndicatorSizeDp = 7
    const val HeroCardHeightDp = 176
    const val HeroCardHorizontalPaddingDp = 18
    const val HeroIconCornerDp = 14
    const val HeroIconPaddingDp = 13
    const val HeroIconSizeDp = 34
    const val HeroTitleGapDp = 10
    const val SortIconSizeDp = 17
    const val SortLabelGapDp = 5
    const val EmptyStateVerticalPaddingDp = 30
    const val EmptyStateIconSizeDp = 38
    const val ProjectCardCornerDp = 13
    const val ProjectCardPaddingDp = 11
    const val ProjectThumbnailWidthDp = 76
    const val ProjectThumbnailHeightDp = 68
    const val ProjectThumbnailIconSizeDp = 27
    const val ProjectBadgeCornerDp = 3
    const val ProjectBadgeHorizontalPaddingDp = 5
    const val ProjectBadgeVerticalPaddingDp = 2
}

/**
 * Effects-panel layout and tracking metrics. These values intentionally match
 * the existing editor panel so token adoption does not alter its geometry.
 */
internal object VyroCutEffectsPanelMetrics {
    const val HorizontalPaddingDp = 10
    const val VerticalPaddingDp = 9
    const val SectionGapDp = 6
    const val ResetButtonHorizontalPaddingDp = 6
    const val ResetButtonVerticalPaddingDp = 0
    const val HeaderLetterSpacingSp = 1f
    const val CategoryLetterSpacingSp = 0.8f
}

/**
 * Filters and Adjust panel geometry. Values preserve the current editor layout
 * while moving repeated styling literals into the shared design system.
 */
internal object VyroCutVisualPanelMetrics {
    const val HorizontalPaddingDp = 10
    const val VerticalPaddingDp = 7
    const val SectionGapDp = 10
    const val HeaderLetterSpacingSp = 1f
    const val ResetButtonHorizontalPaddingDp = 6
    const val ResetButtonVerticalPaddingDp = 0
}

/**
 * Aspect-ratio and canvas-background panel geometry. These tokens preserve the
 * existing panel dimensions while keeping its spacing in the shared system.
 */
internal object VyroCutCanvasPanelMetrics {
    const val HorizontalPaddingDp = 10
    const val VerticalPaddingDp = 7
    const val SectionGapDp = 8
}

/**
 * Text-overlay panel geometry and typography. Values preserve the current
 * editor panel while moving repeated styling literals into shared tokens.
 */
internal object VyroCutTextPanelMetrics {
    const val HorizontalPaddingDp = 10
    const val VerticalPaddingDp = 8
    const val SectionGapDp = 7
    const val HeaderFontSizeSp = 10
    const val HeaderLetterSpacingSp = 1f
    const val HelperFontSizeSp = 10
    const val CaptionFontSizeSp = 9
    const val ChipGapDp = 6
    const val ChipLabelFontSizeSp = 10
}

/**
 * Sticker-overlay panel metrics. Generic spacing, shapes, and typography reuse
 * the shared scales; these tokens cover only the panel's existing unique values.
 */
internal object VyroCutStickerPanelMetrics {
    const val HorizontalPaddingDp = 10
    const val VerticalPaddingDp = 9
    const val ChipGapDp = 6
    const val LayerVerticalPaddingDp = 6
    const val LayerItemGapDp = 3
    const val SymbolChipFontSizeSp = 19
    const val LayerSymbolFontSizeSp = 20
    const val AssetIconSizeDp = 18
    const val LayerAssetIconSizeDp = 24
    const val RemoveButtonHorizontalPaddingDp = 6
}

/**
 * Audio tool panel metrics. Shared shape, spacing, and typography scales are
 * reused where possible; these values preserve the current Audio panel geometry.
 */
internal object VyroCutAudioPanelMetrics {
    const val PanelHorizontalPaddingDp = 10
    const val LaneSectionGapDp = 7
    const val LaneChipGapDp = 6
    const val ClipCardCornerDp = 6
    const val ClipCardVerticalPaddingDp = 6
    const val DeleteButtonSizeDp = 32
    const val DeleteIconSizeDp = 17
    const val ClipVolumePanelVerticalPaddingDp = 7
    const val ClipVolumeSectionGapDp = 5
    const val MuteButtonHorizontalPaddingDp = 6
    const val ActionButtonHorizontalPaddingDp = 4
    const val HeaderLetterSpacingSp = 1f
}

/**
 * Export settings and progress-card geometry. Values preserve the current
 * export controls while keeping their repeated spacing and card dimensions
 * in the shared design system.
 */
internal object VyroCutExportPanelMetrics {
    const val SettingsRowGapDp = 8
    const val SizeSummaryGapDp = 2
    const val PresetChipGapDp = 6
    const val ProgressCardSectionGapDp = 8
    const val CompletionCardSectionGapDp = 4
    const val CardCornerDp = 10
    const val CardPaddingDp = 12
}


/**
 * Timeline panel spacing and insets. These values preserve the existing
 * timeline composition while keeping its repeated geometry centrally editable.
 */
internal object VyroCutTimelinePanelMetrics {
    const val EmptyStateSectionGapDp = 6
    const val EmptyStateVerticalPaddingDp = 18
    const val TrackVerticalGapDp = 6
    const val VideoClipContentPaddingDp = 3
    const val ClipMetaBadgeHorizontalPaddingDp = 4
    const val ClipMetaBadgeVerticalPaddingDp = 2
    const val TextOverlaySegmentHorizontalPaddingDp = 6
    const val StickerAddLabelHorizontalPaddingDp = 6
    const val StickerAddLabelVerticalPaddingDp = 6
    const val StickerSegmentHorizontalPaddingDp = 4
    const val AudioSegmentHorizontalPaddingDp = 5
}

/**
 * Shared waveform renderer metrics. The strip is used by audio lanes and the
 * Audio tool; the compact clip waveform keeps its own proportions. Values
 * intentionally preserve the existing renderers' visual output.
 */

/**
 * Timeline visual tokens shared by the ruler, track rows, clip segments, and
 * playhead. The adjacent TimelineVisualMetrics file keeps the pure layout
 * calculations; this object is the single source of truth for their geometry.
 * Values intentionally match the existing rendered dimensions.
 */
internal object VyroCutTimelineVisualMetrics {
    const val RulerHeightDp = 28
    const val VideoTrackHeightDp = 72
    const val VideoThumbnailHeightDp = 42
    const val TextRowHeightDp = 30
    const val StickerRowHeightDp = 30
    const val SourceTrackHeightDp = 32
    const val SourceWaveformHeightDp = 22
    const val AudioLaneHeightDp = 36
    const val AudioWaveformHeightDp = 14

    const val TrackLabelFontSp = VyroCutTypography.LabelSp
    const val TrackDetailFontSp = VyroCutTypography.CaptionSp
    const val TrackHeaderBadgeHorizontalPaddingDp = 6
    const val TrackSegmentCornerRadiusDp = 4
    const val TrackHeaderCornerRadiusDp = TrackSegmentCornerRadiusDp
    const val VideoClipCornerRadiusDp = 5
    const val VideoThumbnailCornerRadiusDp = 6
    const val PlayheadCapOuterCornerRadiusDp = 3
    const val PlayheadCapInnerCornerRadiusDp = 2
    const val MediaBadgeCornerRadiusDp = 2
    const val RulerTickCornerRadiusDp = 1
    const val TrackHeaderDotSizeDp = 5
    const val TrackHeaderDotGapDp = 4
    const val TrackHeaderDetailGapDp = 8
    const val TrackHeaderInlineMinimumWidthDp = 168
    const val TrackHeaderTrailingDetailMinWidthDp = 320
    const val TimedEffectTrackHeightDp = 48
    const val TimedEffectTrimHandleHitWidthDp = 48

    const val RulerLabelFontSp = VyroCutTypography.CaptionSp
    const val ClipLabelFontSp = VyroCutTypography.LabelSp
    const val ClipMetaFontSp = VyroCutTypography.CaptionSp
    const val StickerSymbolFontSp = VyroCutTypography.TimelineStickerSymbolSp

    const val PlayheadLineWidthDp = 2
    const val PlayheadOutlineWidthDp = 4
    const val PlayheadCapWidthDp = 14
    const val PlayheadCapHeightDp = 9
    const val SelectedClipBorderWidthDp = 2

    const val RulerMajorTickWidthDp = 2
    const val RulerMinorTickWidthDp = 1
    const val RulerMajorTickHeightDp = 10
    const val RulerMinorTickHeightDp = 5
    const val RulerLabelHorizontalPaddingDp = 3
    const val RulerLabelVerticalPaddingDp = 1
    const val RulerLabelVerticalOffsetDp = 11
    const val RulerLabelCornerRadiusDp = 3
    const val RulerLabelInterLabelGapDp = 2
    const val RulerMinimumTickSpacingDp = 8

    const val RulerLabelGlyphWidthFactor = 0.62f
    const val RulerLabelGapDp = 8f
    const val MaximumRulerLabelCharacters = 32
}

internal object VyroCutWaveformMetrics {
    const val StripDefaultHeightDp = 24f
    const val StripBaselineAlpha = 0.28f
    const val StripBaselineStrokeWidthDp = 1f
    const val StripStrokeWidthRatio = 0.58f
    const val StripMinimumStrokeWidthDp = 1f
    const val StripMaximumStrokeWidthDp = 3f
    const val StripMinimumBarHalfHeightDp = 1f
    const val StripAmplitudeExponent = 0.8f

    const val ClipBaselineAlpha = 0.45f
    const val ClipBaselineStrokeWidthDp = 1f
    const val ClipBarGapDp = 1.5f
    const val ClipBarMinimumWidthDp = 1f
    const val ClipBarMinimumHeightDp = 2f
    const val ClipAmplitudeFloor = 0.06f
    const val ClipBarBaseAlpha = 0.45f
    const val ClipBarPeakAlpha = 0.55f
}

internal fun vyroCutColorScheme(): ColorScheme = darkColorScheme(
    primary = VyroCutPalette.Accent,
    onPrimary = VyroCutPalette.AccentInk,
    primaryContainer = VyroCutPalette.SurfaceSelected,
    onPrimaryContainer = VyroCutPalette.AccentSoft,
    secondary = VyroCutPalette.AccentSecondary,
    onSecondary = VyroCutPalette.AccentInk,
    background = VyroCutPalette.Background,
    onBackground = VyroCutPalette.TextPrimary,
    surface = VyroCutPalette.Surface,
    onSurface = VyroCutPalette.TextPrimary,
    surfaceVariant = VyroCutPalette.SurfaceElevated,
    onSurfaceVariant = VyroCutPalette.TextMuted,
    error = VyroCutPalette.Danger,
    onError = VyroCutPalette.OnDanger,
    outline = VyroCutPalette.Outline,
)

@Composable
internal fun VyroCutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = vyroCutColorScheme(),
        content = content,
    )
}
