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
    val TextMuted = Color(0xFF9BAAA5)
    val TextSubtle = Color(0xFF71827B)
    val Danger = Color(0xFFFF8A80)
    val OnDanger = Color.Black
    val Outline = Color(0xFF39423F)

    // Semantic media/canvas colors: keep true black/white distinct from UI text.
    val CanvasBlack = Color.Black
    val CanvasWhite = Color.White
    val MediaOverlayText = Color.White
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

/** Small, explicit type scale for the existing home and tool-dock hierarchy. */
internal object VyroCutTypography {
    const val BadgeSp = 8
    const val ToolLabelSp = 9
    const val CaptionSp = 10
    const val LabelSp = 11
    const val BodySp = 14
    const val SectionTitleSp = 20
    const val BrandSp = 22
    const val HeroTitleSp = 23
}

/** Control geometry tokens preserve the current dock's minimum hit dimensions. */
internal object VyroCutControlMetrics {
    const val MinimumTouchTargetDp = 48
    const val ToolButtonWidthDp = 52
    const val ToolButtonHeightDp = 48
    const val ToolIconSizeDp = 20
    const val ToolIndicatorWidthDp = 18
    const val ToolIndicatorHeightDp = 2
}


/**
 * Home-screen-specific geometry retained as semantic tokens during styling
 * consolidation. Values intentionally match the existing Compose layout.
 */
internal object VyroCutHomeMetrics {
    const val ProjectListItemGapDp = 14
    const val SearchFieldHeightDp = 50
    const val SearchPlaceholderSp = 12
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
