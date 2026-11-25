package com.vyrocut.app

import kotlin.math.ceil

internal const val TIMELINE_RULER_HEIGHT_DP = 28
internal const val TIMELINE_VIDEO_TRACK_HEIGHT_DP = 72
internal const val TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP = 42
internal const val TIMELINE_TEXT_ROW_HEIGHT_DP = 30
internal const val TIMELINE_STICKER_ROW_HEIGHT_DP = 30
internal const val TIMELINE_SOURCE_TRACK_HEIGHT_DP = 32
internal const val TIMELINE_SOURCE_WAVEFORM_HEIGHT_DP = 22
internal const val TIMELINE_AUDIO_LANE_HEIGHT_DP = 36
internal const val TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP = 14

// Timeline text sizes derive from the shared design-system typography scale.
internal const val TIMELINE_TRACK_LABEL_FONT_SP = VyroCutTypography.LabelSp
internal const val TIMELINE_TRACK_DETAIL_FONT_SP = VyroCutTypography.CaptionSp

 // Compact header badges add horizontal identity without increasing track-row height.
internal const val TIMELINE_TRACK_HEADER_BADGE_HORIZONTAL_PADDING_DP = 6
internal const val TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP = 4
internal const val TIMELINE_TRACK_HEADER_CORNER_RADIUS_DP = TIMELINE_TRACK_SEGMENT_CORNER_RADIUS_DP
internal const val TIMELINE_VIDEO_CLIP_CORNER_RADIUS_DP = 5
internal const val TIMELINE_PLAYHEAD_CAP_OUTER_CORNER_RADIUS_DP = 3
internal const val TIMELINE_PLAYHEAD_CAP_INNER_CORNER_RADIUS_DP = 2
internal const val TIMELINE_MEDIA_BADGE_CORNER_RADIUS_DP = 2
internal const val TIMELINE_RULER_TICK_CORNER_RADIUS_DP = 1
internal const val TIMELINE_TRACK_HEADER_DOT_SIZE_DP = 5
internal const val TIMELINE_TRACK_HEADER_DOT_GAP_DP = 4
internal const val TIMELINE_TRACK_HEADER_DETAIL_GAP_DP = 8
internal const val TIMELINE_RULER_LABEL_FONT_SP = VyroCutTypography.CaptionSp
internal const val TIMELINE_CLIP_LABEL_FONT_SP = VyroCutTypography.LabelSp
internal const val TIMELINE_CLIP_META_FONT_SP = VyroCutTypography.CaptionSp
internal const val TIMELINE_STICKER_SYMBOL_FONT_SP = VyroCutTypography.TimelineStickerSymbolSp
internal const val TIMELINE_PLAYHEAD_LINE_WIDTH_DP = 2
internal const val TIMELINE_PLAYHEAD_OUTLINE_WIDTH_DP = 4
internal const val TIMELINE_PLAYHEAD_CAP_WIDTH_DP = 14
internal const val TIMELINE_PLAYHEAD_CAP_HEIGHT_DP = 9
internal const val TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP = 2

internal const val TIMELINE_RULER_MAJOR_TICK_WIDTH_DP = 2
internal const val TIMELINE_RULER_MINOR_TICK_WIDTH_DP = 1
internal const val TIMELINE_RULER_MAJOR_TICK_HEIGHT_DP = 10
internal const val TIMELINE_RULER_MINOR_TICK_HEIGHT_DP = 5
internal const val TIMELINE_RULER_LABEL_HORIZONTAL_PADDING_DP = 3
internal const val TIMELINE_RULER_LABEL_VERTICAL_PADDING_DP = 1
internal const val TIMELINE_RULER_LABEL_VERTICAL_OFFSET_DP = 11
internal const val TIMELINE_RULER_LABEL_CORNER_RADIUS_DP = 3
internal const val TIMELINE_RULER_LABEL_INTER_LABEL_GAP_DP = 2
internal const val TIMELINE_RULER_MIN_TICK_SPACING_DP = 8

internal data class TimelineRulerTickVisualMetrics(
    val widthDp: Int,
    val heightDp: Int,
)

internal fun timelineRulerTickVisualMetrics(isMajor: Boolean) = TimelineRulerTickVisualMetrics(
    widthDp = if (isMajor) TIMELINE_RULER_MAJOR_TICK_WIDTH_DP else TIMELINE_RULER_MINOR_TICK_WIDTH_DP,
    heightDp = if (isMajor) TIMELINE_RULER_MAJOR_TICK_HEIGHT_DP else TIMELINE_RULER_MINOR_TICK_HEIGHT_DP,
)

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
private const val TIMELINE_RULER_LABEL_GLYPH_WIDTH_FACTOR = 0.62f
private const val TIMELINE_RULER_LABEL_GAP_DP = 8f
private const val MAX_TIMELINE_RULER_LABEL_CHARACTERS = 32

/**
 * Estimates the horizontal room needed by a ruler timecode, including a small
 * gap before the next label. Compose scales the label's sp size automatically,
 * so the same bounded font scale is applied to this dp estimate.
 */
internal fun timelineRulerLabelMinimumSpacingDp(
    maxLabelCharacters: Int,
    fontScale: Float,
): Int {
    val safeScale = fontScale
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(1f, 2f)
        ?: 1f
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
    val safeScale = fontScale
        .takeIf { it.isFinite() && it > 0f }
        ?.coerceIn(1f, 2f)
        ?: 1f

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
