package com.vyrocut.app

import kotlin.math.ceil

/**
 * Compatibility names keep the pure timeline layout API stable while all
 * visual values are owned by VyroCutTimelineVisualMetrics in the design system.
 */
internal const val TIMELINE_RULER_HEIGHT_DP = VyroCutTimelineVisualMetrics.RulerHeightDp
internal const val TIMELINE_VIDEO_TRACK_HEIGHT_DP = VyroCutTimelineVisualMetrics.VideoTrackHeightDp
internal const val TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP = VyroCutTimelineVisualMetrics.VideoThumbnailHeightDp
internal const val TIMELINE_TEXT_ROW_HEIGHT_DP = VyroCutTimelineVisualMetrics.TextRowHeightDp
internal const val TIMELINE_STICKER_ROW_HEIGHT_DP = VyroCutTimelineVisualMetrics.StickerRowHeightDp
internal const val TIMELINE_SOURCE_TRACK_HEIGHT_DP = VyroCutTimelineVisualMetrics.SourceTrackHeightDp
internal const val TIMELINE_SOURCE_WAVEFORM_HEIGHT_DP = VyroCutTimelineVisualMetrics.SourceWaveformHeightDp
internal const val TIMELINE_AUDIO_LANE_HEIGHT_DP = VyroCutTimelineVisualMetrics.AudioLaneHeightDp
internal const val TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP = VyroCutTimelineVisualMetrics.AudioWaveformHeightDp

internal const val TIMELINE_TRACK_LABEL_FONT_SP = VyroCutTimelineVisualMetrics.TrackLabelFontSp
internal const val TIMELINE_TRACK_DETAIL_FONT_SP = VyroCutTimelineVisualMetrics.TrackDetailFontSp
internal const val TIMELINE_LANE_GUTTER_MIN_WIDTH_DP = VyroCutTimelineVisualMetrics.LaneGutterMinimumWidthDp
internal const val TIMELINE_LANE_GUTTER_BASE_WIDTH_DP = VyroCutTimelineVisualMetrics.LaneGutterBaseWidthDp
internal const val TIMELINE_LANE_GUTTER_MAX_WIDTH_DP = VyroCutTimelineVisualMetrics.LaneGutterMaximumWidthDp
internal const val TIMELINE_LANE_GUTTER_VIEWPORT_FRACTION = VyroCutTimelineVisualMetrics.LaneGutterViewportWidthFraction
internal const val TIMELINE_LANE_LABEL_FONT_SP = VyroCutTimelineVisualMetrics.LaneLabelFontSp
internal const val TIMELINE_LANE_LABEL_HORIZONTAL_PADDING_DP = VyroCutTimelineVisualMetrics.LaneLabelHorizontalPaddingDp
internal const val TIMELINE_LANE_LABEL_DOT_SIZE_DP = VyroCutTimelineVisualMetrics.LaneLabelDotSizeDp
internal const val TIMELINE_LANE_LABEL_DOT_GAP_DP = VyroCutTimelineVisualMetrics.LaneLabelDotGapDp
internal const val TIMELINE_TRACK_HEADER_BADGE_HORIZONTAL_PADDING_DP = VyroCutTimelineVisualMetrics.TrackHeaderBadgeHorizontalPaddingDp
internal const val TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.TrackSegmentCornerRadiusDp
internal const val TIMELINE_TRACK_HEADER_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.TrackHeaderCornerRadiusDp
internal const val TIMELINE_VIDEO_CLIP_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.VideoClipCornerRadiusDp
internal const val TIMELINE_VIDEO_CLIP_BOUNDARY_BORDER_WIDTH_DP = VyroCutTimelineVisualMetrics.VideoClipBoundaryBorderWidthDp
internal const val TIMELINE_PLAYHEAD_CAP_OUTER_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.PlayheadCapOuterCornerRadiusDp
internal const val TIMELINE_PLAYHEAD_CAP_INNER_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.PlayheadCapInnerCornerRadiusDp
internal const val TIMELINE_MEDIA_BADGE_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.MediaBadgeCornerRadiusDp
internal const val TIMELINE_RULER_TICK_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.RulerTickCornerRadiusDp
internal const val TIMELINE_TRACK_HEADER_DOT_SIZE_DP = VyroCutTimelineVisualMetrics.TrackHeaderDotSizeDp
internal const val TIMELINE_TRACK_HEADER_DOT_GAP_DP = VyroCutTimelineVisualMetrics.TrackHeaderDotGapDp
internal const val TIMELINE_TRACK_HEADER_DETAIL_GAP_DP = VyroCutTimelineVisualMetrics.TrackHeaderDetailGapDp
internal const val TIMELINE_TRACK_HEADER_INLINE_MINIMUM_WIDTH_DP = VyroCutTimelineVisualMetrics.TrackHeaderInlineMinimumWidthDp
internal const val TIMELINE_TRACK_HEADER_TRAILING_DETAIL_MIN_WIDTH_DP = VyroCutTimelineVisualMetrics.TrackHeaderTrailingDetailMinWidthDp
internal const val TIMED_EFFECT_TRIM_HANDLE_WIDTH_DP = VyroCutTimelineVisualMetrics.TimedEffectTrimHandleWidthDp
internal const val TIMED_EFFECT_TRIM_HANDLE_HEIGHT_FRACTION = VyroCutTimelineVisualMetrics.TimedEffectTrimHandleHeightFraction
internal const val TIMED_EFFECT_TRIM_HANDLE_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.TimedEffectTrimHandleCornerRadiusDp
internal const val TIMED_EFFECT_SELECTED_BORDER_WIDTH_DP = VyroCutTimelineVisualMetrics.TimedEffectSelectedBorderWidthDp

internal const val TIMELINE_RULER_LABEL_FONT_SP = VyroCutTimelineVisualMetrics.RulerLabelFontSp
internal const val TIMELINE_CLIP_LABEL_FONT_SP = VyroCutTimelineVisualMetrics.ClipLabelFontSp
internal const val TIMELINE_CLIP_META_FONT_SP = VyroCutTimelineVisualMetrics.ClipMetaFontSp
internal const val TIMELINE_STICKER_SYMBOL_FONT_SP = VyroCutTimelineVisualMetrics.StickerSymbolFontSp

internal const val TIMELINE_PLAYHEAD_LINE_WIDTH_DP = VyroCutTimelineVisualMetrics.PlayheadLineWidthDp
internal const val TIMELINE_PLAYHEAD_OUTLINE_WIDTH_DP = VyroCutTimelineVisualMetrics.PlayheadOutlineWidthDp
internal const val TIMELINE_PLAYHEAD_CAP_WIDTH_DP = VyroCutTimelineVisualMetrics.PlayheadCapWidthDp
internal const val TIMELINE_PLAYHEAD_CAP_HEIGHT_DP = VyroCutTimelineVisualMetrics.PlayheadCapHeightDp
internal const val TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP = VyroCutTimelineVisualMetrics.SelectedClipBorderWidthDp

internal const val TIMELINE_RULER_MAJOR_TICK_WIDTH_DP = VyroCutTimelineVisualMetrics.RulerMajorTickWidthDp
internal const val TIMELINE_RULER_MINOR_TICK_WIDTH_DP = VyroCutTimelineVisualMetrics.RulerMinorTickWidthDp
internal const val TIMELINE_RULER_MAJOR_TICK_HEIGHT_DP = VyroCutTimelineVisualMetrics.RulerMajorTickHeightDp
internal const val TIMELINE_RULER_MINOR_TICK_HEIGHT_DP = VyroCutTimelineVisualMetrics.RulerMinorTickHeightDp
internal const val TIMELINE_RULER_LABEL_HORIZONTAL_PADDING_DP = VyroCutTimelineVisualMetrics.RulerLabelHorizontalPaddingDp
internal const val TIMELINE_RULER_LABEL_VERTICAL_PADDING_DP = VyroCutTimelineVisualMetrics.RulerLabelVerticalPaddingDp
internal const val TIMELINE_RULER_LABEL_VERTICAL_OFFSET_DP = VyroCutTimelineVisualMetrics.RulerLabelVerticalOffsetDp
internal const val TIMELINE_RULER_LABEL_CORNER_RADIUS_DP = VyroCutTimelineVisualMetrics.RulerLabelCornerRadiusDp
internal const val TIMELINE_RULER_LABEL_INTER_LABEL_GAP_DP = VyroCutTimelineVisualMetrics.RulerLabelInterLabelGapDp
internal const val TIMELINE_RULER_MIN_TICK_SPACING_DP = VyroCutTimelineVisualMetrics.RulerMinimumTickSpacingDp

private const val TIMELINE_RULER_LABEL_GLYPH_WIDTH_FACTOR = VyroCutTimelineVisualMetrics.RulerLabelGlyphWidthFactor
private const val TIMELINE_RULER_LABEL_GAP_DP = VyroCutTimelineVisualMetrics.RulerLabelGapDp
private const val MAX_TIMELINE_RULER_LABEL_CHARACTERS = VyroCutTimelineVisualMetrics.MaximumRulerLabelCharacters

/** Keeps timeline geometry in step with the shared accessibility text-scale policy. */
private fun safeTimelineFontScale(fontScale: Float): Float =
    fontScale
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(1f, VyroCutAccessibilityMetrics.MaxSupportedFontScale)
        ?: 1f


internal data class TimelineRulerTickVisualMetrics(
    val widthDp: Int,
    val heightDp: Int,
)

internal fun timelineRulerTickVisualMetrics(isMajor: Boolean) = TimelineRulerTickVisualMetrics(
    widthDp = if (isMajor) TIMELINE_RULER_MAJOR_TICK_WIDTH_DP else TIMELINE_RULER_MINOR_TICK_WIDTH_DP,
    heightDp = if (isMajor) TIMELINE_RULER_MAJOR_TICK_HEIGHT_DP else TIMELINE_RULER_MINOR_TICK_HEIGHT_DP,
)

/**
 * Increases ruler tick separation with accessibility text so the time axis
 * does not become visually crowded as its labels grow. Tick positions remain
 * in project-time coordinates; this only thins rendered marks.
 */
internal fun timelineRulerMinimumTickSpacingDp(fontScale: Float): Float =
    TIMELINE_RULER_MIN_TICK_SPACING_DP * safeTimelineFontScale(fontScale)

internal enum class TimelineRulerLabelAlignment {
    START,
    CENTER,
    END,
}

internal data class TimelineRulerLabelPlacement(
    val offsetDp: Float,
    val alignment: TimelineRulerLabelAlignment,
)

internal data class TimelinePlayheadVisualMetrics(
    val lineWidthDp: Int,
    val outlineWidthDp: Int,
    val capWidthDp: Int,
    val capHeightDp: Int,
)

internal fun timelinePlayheadVisualMetrics() = TimelinePlayheadVisualMetrics(
    lineWidthDp = TIMELINE_PLAYHEAD_LINE_WIDTH_DP,
    outlineWidthDp = TIMELINE_PLAYHEAD_OUTLINE_WIDTH_DP,
    capWidthDp = TIMELINE_PLAYHEAD_CAP_WIDTH_DP,
    capHeightDp = TIMELINE_PLAYHEAD_CAP_HEIGHT_DP,
)

internal data class TimelinePlayheadGeometry(
    val lineLeftDp: Float,
    val outlineLeftDp: Float,
    val capLeftDp: Float,
)

/** Keeps the dark outline and top cap centered on the accent playhead line. */
internal fun timelinePlayheadGeometry(lineLeftDp: Float): TimelinePlayheadGeometry {
    val safeLineLeft = lineLeftDp.takeIf { it.isFinite() } ?: 0f
    val metrics = timelinePlayheadVisualMetrics()
    return TimelinePlayheadGeometry(
        lineLeftDp = safeLineLeft,
        outlineLeftDp = safeLineLeft + (metrics.lineWidthDp - metrics.outlineWidthDp) / 2f,
        capLeftDp = safeLineLeft + (metrics.lineWidthDp - metrics.capWidthDp) / 2f,
    )
}

internal fun timelineClipSelectionBorderWidthDp(isSelected: Boolean): Int =
    if (isSelected) TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP else 0

/** Keeps unselected video clip edges visible without competing with the active selection. */
internal fun timelineVideoClipBorderWidthDp(isSelected: Boolean): Int =
    if (isSelected) TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP else TIMELINE_VIDEO_CLIP_BOUNDARY_BORDER_WIDTH_DP

/**
 * Returns the shared selection outline width for an audio clip only when its
 * stable ID matches the currently selected audio clip.
 */
internal fun timelineAudioClipSelectionBorderWidthDp(
    clipId: String,
    selectedAudioTrackId: String?,
): Int = timelineClipSelectionBorderWidthDp(
    isSelected = clipId.isNotBlank() && selectedAudioTrackId == clipId,
)

/**
 * Returns a selection outline only for the active text/sticker layer on the
 * selected video clip. Negative indices represent no active layer.
 */
internal fun timelineOverlaySelectionBorderWidthDp(
    clipIndex: Int,
    layerIndex: Int,
    selectedClipIndex: Int,
    selectedLayerIndex: Int,
): Int = timelineClipSelectionBorderWidthDp(
    isSelected = clipIndex >= 0 &&
        layerIndex >= 0 &&
        selectedClipIndex >= 0 &&
        selectedLayerIndex >= 0 &&
        clipIndex == selectedClipIndex &&
        layerIndex == selectedLayerIndex,
)

/** Uses the centralized one-dp outline for a selected timed-effect segment. */
internal fun timelineTimedEffectSelectionBorderWidthDp(isSelected: Boolean): Int =
    if (isSelected) TIMED_EFFECT_SELECTED_BORDER_WIDTH_DP else 0

/**
 * Trim handles cover the label's edge area only while the effect is selected.
 * Unselected segments have no handles, so their labels can use the full width.
 */
internal fun timelineTimedEffectLabelHorizontalInsetDp(
    isSelected: Boolean,
    handleHitWidthDp: Float,
): Float {
    if (!isSelected) return 0f
    return handleHitWidthDp.takeIf { it.isFinite() && it > 0f } ?: 0f
}


/**
 * Estimates the horizontal room needed by a ruler timecode, including a small
 * gap before the next label. Compose scales the label's sp size automatically,
 * so the same bounded font scale is applied to this dp estimate.
 */
internal fun timelineRulerLabelMinimumSpacingDp(
    maxLabelCharacters: Int,
    fontScale: Float,
): Int {
    val safeScale = safeTimelineFontScale(fontScale)
    val safeCharacters = maxLabelCharacters.coerceIn(1, MAX_TIMELINE_RULER_LABEL_CHARACTERS)
    return ceil(
        safeCharacters * TIMELINE_RULER_LABEL_FONT_SP *
            TIMELINE_RULER_LABEL_GLYPH_WIDTH_FACTOR * safeScale +
            TIMELINE_RULER_LABEL_GAP_DP,
    ).toInt().coerceAtLeast(1)
}

/**
 * Fixed timeline rows grow with the user's system font scale so labels and
 * waveforms do not get clipped inside dp-sized tracks.
 *
 * Compose scales sp text automatically; these values scale the surrounding dp
 * geometry to preserve enough vertical room. The scale is bounded to avoid an
 * accidental malformed configuration producing unbounded timeline height.
 */
/**
 * Includes the label's horizontal pill padding while leaving a small gap
 * between adjacent labels at the minimum spacing selected for the ruler.
 */
internal fun timelineRulerLabelWidthDp(
    maxLabelCharacters: Int,
    fontScale: Float,
): Int = (
    timelineRulerLabelMinimumSpacingDp(maxLabelCharacters, fontScale) -
        TIMELINE_RULER_LABEL_INTER_LABEL_GAP_DP
    ).coerceAtLeast(1)

/**
 * Centers a ruler label on its tick unless that would push the label outside
 * the scroll-content bounds. Edge labels are aligned inward instead.
 */
internal fun timelineRulerLabelPlacement(
    tickOffsetDp: Float,
    labelWidthDp: Int,
    contentWidthDp: Float,
): TimelineRulerLabelPlacement {
    val safeWidth = labelWidthDp.coerceAtLeast(1).toFloat()
    val safeContentWidth = contentWidthDp
        .takeIf { it.isFinite() && it > 0f }
        ?: safeWidth
    if (safeContentWidth <= safeWidth) {
        return TimelineRulerLabelPlacement(0f, TimelineRulerLabelAlignment.START)
    }

    val safeTickOffset = tickOffsetDp.takeIf { it.isFinite() } ?: 0f
    val centeredLeft = safeTickOffset - safeWidth / 2f
    return when {
        centeredLeft < 0f ->
            TimelineRulerLabelPlacement(0f, TimelineRulerLabelAlignment.START)
        centeredLeft + safeWidth > safeContentWidth ->
            TimelineRulerLabelPlacement(
                offsetDp = safeContentWidth - safeWidth,
                alignment = TimelineRulerLabelAlignment.END,
            )
        else ->
            TimelineRulerLabelPlacement(centeredLeft, TimelineRulerLabelAlignment.CENTER)
    }
}

/**
 * The optional trailing track hint is hidden on short timelines so it cannot
 * consume the width needed by the track's descriptive label. The budget grows
 * with system font scale because Compose scales the hint text in sp.
 */
/**
 * Narrow timeline headers give the track name and its description separate
 * lines instead of squeezing the description into an unusably small inline
 * slot. The threshold scales with accessibility text and is bounded by the
 * same font-scale policy used by the other timeline metrics.
 */
internal fun timelineTrackHeaderStacksDetail(
    availableWidthDp: Float,
    fontScale: Float,
): Boolean {
    val safeWidth = availableWidthDp
        .takeIf { it.isFinite() && it > 0f }
        ?: 0f
    val safeFontScale = safeTimelineFontScale(fontScale)
    val requiredInlineWidth = ceil(
        TIMELINE_TRACK_HEADER_INLINE_MINIMUM_WIDTH_DP * safeFontScale,
    )
    return safeWidth < requiredInlineWidth
}

internal fun timelineTrackHeaderShowsTrailingDetail(
    availableWidthDp: Float,
    fontScale: Float,
): Boolean {
    val safeWidth = availableWidthDp
        .takeIf { it.isFinite() && it > 0f }
        ?: 0f
    val safeFontScale = safeTimelineFontScale(fontScale)
    val requiredWidth = ceil(
        TIMELINE_TRACK_HEADER_TRAILING_DETAIL_MIN_WIDTH_DP * safeFontScale,
    )
    return safeWidth >= requiredWidth
}

internal data class TimelineReadabilityMetrics(
    val rulerHeightDp: Int,
    val videoTrackHeightDp: Int,
    val textRowHeightDp: Int,
    val stickerRowHeightDp: Int,
    val sourceTrackHeightDp: Int,
    val sourceWaveformHeightDp: Int,
    val audioLaneHeightDp: Int,
    val audioWaveformHeightDp: Int,
)

internal fun timelineReadabilityMetrics(fontScale: Float): TimelineReadabilityMetrics {
    val safeScale = safeTimelineFontScale(fontScale)

    fun scaledHeight(baseHeightDp: Int, contentHeightDp: Float, extraPaddingDp: Int): Int =
        maxOf(baseHeightDp, ceil(contentHeightDp * safeScale).toInt() + extraPaddingDp)

    fun scaledHeightWithFixedContent(
        baseHeightDp: Int,
        fixedContentHeightDp: Int,
        scalableContentHeightDp: Float,
        extraPaddingDp: Int,
    ): Int = maxOf(
        baseHeightDp,
        fixedContentHeightDp + ceil(scalableContentHeightDp * safeScale).toInt() + extraPaddingDp,
    )

    return TimelineReadabilityMetrics(
        rulerHeightDp = scaledHeight(
            TIMELINE_RULER_HEIGHT_DP,
            TIMELINE_RULER_LABEL_FONT_SP * 1.4f,
            12,
        ),
        // The thumbnail stays at its fixed dp height; only the sp label grows.
        videoTrackHeightDp = scaledHeightWithFixedContent(
            baseHeightDp = TIMELINE_VIDEO_TRACK_HEIGHT_DP,
            fixedContentHeightDp = TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP,
            scalableContentHeightDp = TIMELINE_CLIP_LABEL_FONT_SP * 1.35f,
            extraPaddingDp = 12,
        ),
        textRowHeightDp = scaledHeight(
            TIMELINE_TEXT_ROW_HEIGHT_DP,
            TIMELINE_CLIP_LABEL_FONT_SP * 1.35f,
            8,
        ),
        stickerRowHeightDp = scaledHeight(
            TIMELINE_STICKER_ROW_HEIGHT_DP,
            TIMELINE_STICKER_SYMBOL_FONT_SP * 1.35f,
            8,
        ),
        sourceTrackHeightDp = scaledHeight(
            TIMELINE_SOURCE_TRACK_HEIGHT_DP,
            TIMELINE_SOURCE_WAVEFORM_HEIGHT_DP.toFloat(),
            8,
        ),
        sourceWaveformHeightDp = ceil(TIMELINE_SOURCE_WAVEFORM_HEIGHT_DP * safeScale).toInt(),
        audioLaneHeightDp = scaledHeight(
            TIMELINE_AUDIO_LANE_HEIGHT_DP,
            TIMELINE_CLIP_LABEL_FONT_SP * 1.35f + TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP,
            6,
        ),
        audioWaveformHeightDp = ceil(TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP * safeScale).toInt(),
    )
}

private fun boundedTimelineTrackHeight(layerCount: Int, rowHeightDp: Int, maximumLayers: Int): Int {
    val rows = layerCount.coerceIn(1, maximumLayers)
    val safeRowHeight = rowHeightDp.coerceAtLeast(1)
    return (rows.toLong() * safeRowHeight.toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

/**
 * Returns the vertical space needed by the visible text-overlay rows.
 * Keep the calculation bounded by the same overlay limit used by editing.
 */
internal fun timelineTextTrackHeightDp(
    layerCount: Int,
    rowHeightDp: Int = TIMELINE_TEXT_ROW_HEIGHT_DP,
): Int = boundedTimelineTrackHeight(layerCount, rowHeightDp, MAX_CLIP_TEXT_OVERLAYS)

/**
 * Returns the vertical space needed by the visible sticker-overlay rows.
 * Keep the calculation bounded by the same overlay limit used by editing.
 */
internal fun timelineStickerTrackHeightDp(
    layerCount: Int,
    rowHeightDp: Int = TIMELINE_STICKER_ROW_HEIGHT_DP,
): Int = boundedTimelineTrackHeight(layerCount, rowHeightDp, MAX_CLIP_STICKER_OVERLAYS)

/**
 * Counter-translates non-interactive track metadata against the shared
 * horizontal scroll offset. Negative offsets are treated as the origin.
 */
internal fun timelineTrackHeaderPinnedTranslationPx(scrollOffsetPx: Int): Float =
    scrollOffsetPx.coerceAtLeast(0).toFloat()

/**
 * Resolves a compact fixed lane-label gutter that grows modestly with system
 * font scale while preserving at least three quarters of the viewport for
 * the time axis. Invalid geometry falls back to the default 360 dp viewport.
 */
internal fun timelineLaneGutterWidthDp(viewportWidthDp: Float, fontScale: Float): Int {
    val safeViewportWidth = viewportWidthDp
        .takeIf { it.isFinite() && it > 0f }
        ?: 360f
    val safeFontScale = safeTimelineFontScale(fontScale)
    val scaledWidth = ceil(
        TIMELINE_LANE_GUTTER_BASE_WIDTH_DP * safeFontScale.coerceAtMost(1.25f),
    ).toInt()
    val viewportMaximum = (safeViewportWidth * TIMELINE_LANE_GUTTER_VIEWPORT_FRACTION)
        .toInt()
        .coerceAtLeast(TIMELINE_LANE_GUTTER_MIN_WIDTH_DP)
    val maximumWidth = minOf(TIMELINE_LANE_GUTTER_MAX_WIDTH_DP, viewportMaximum)
        .coerceAtLeast(TIMELINE_LANE_GUTTER_MIN_WIDTH_DP)
    return scaledWidth.coerceIn(TIMELINE_LANE_GUTTER_MIN_WIDTH_DP, maximumWidth)
}

/**
 * Shortens only lane labels that exceed the fixed gutter's estimated text
 * budget. The caller keeps the original label in accessibility semantics.
 */
internal fun timelineLaneDisplayLabel(
    label: String,
    gutterWidthDp: Int,
    fontScale: Float,
): String {
    val safeLabel = label.trim()
    if (safeLabel.isEmpty()) return ""

    val safeGutterWidth = gutterWidthDp.coerceAtLeast(TIMELINE_LANE_GUTTER_MIN_WIDTH_DP)
    val safeFontScale = safeTimelineFontScale(fontScale)
    val availableTextWidthDp = (
        safeGutterWidth -
            2 * TIMELINE_LANE_LABEL_HORIZONTAL_PADDING_DP -
            TIMELINE_LANE_LABEL_DOT_SIZE_DP -
            TIMELINE_LANE_LABEL_DOT_GAP_DP
        ).coerceAtLeast(0)

    fun estimatedTextWidthDp(value: String): Double =
        value.length.toDouble() *
            TIMELINE_LANE_LABEL_FONT_SP *
            VyroCutTimelineVisualMetrics.LaneLabelGlyphWidthFactor *
            safeFontScale.toDouble()

    if (estimatedTextWidthDp(safeLabel) <= availableTextWidthDp) return safeLabel

    val compactLabel = when (safeLabel.uppercase()) {
        "STICKER" -> "STKR"
        "EFFECT" -> "FX"
        "SOURCE" -> "SRC"
        "VOICE" -> "VO"
        "VIDEO" -> "VID"
        "MUSIC" -> "MUS"
        "TEXT" -> "TXT"
        "SFX" -> "SFX"
        else -> safeLabel.take(3)
    }
    return if (estimatedTextWidthDp(compactLabel) <= availableTextWidthDp) {
        compactLabel
    } else {
        compactLabel.take(2)
    }
}
