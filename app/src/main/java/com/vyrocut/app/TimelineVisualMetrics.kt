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

internal const val TIMELINE_TRACK_LABEL_FONT_SP = 11
internal const val TIMELINE_TRACK_DETAIL_FONT_SP = 10
internal const val TIMELINE_RULER_LABEL_FONT_SP = 10
internal const val TIMELINE_CLIP_LABEL_FONT_SP = 11
internal const val TIMELINE_CLIP_META_FONT_SP = 10
internal const val TIMELINE_STICKER_SYMBOL_FONT_SP = 14
internal const val TIMELINE_PLAYHEAD_LINE_WIDTH_DP = 2
internal const val TIMELINE_PLAYHEAD_OUTLINE_WIDTH_DP = 4
internal const val TIMELINE_PLAYHEAD_CAP_WIDTH_DP = 14
internal const val TIMELINE_PLAYHEAD_CAP_HEIGHT_DP = 9
internal const val TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP = 2

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

    return TimelineReadabilityMetrics(
        rulerHeightDp = scaledHeight(
            TIMELINE_RULER_HEIGHT_DP,
            TIMELINE_RULER_LABEL_FONT_SP * 1.4f,
            12,
        ),
        videoTrackHeightDp = scaledHeight(
            TIMELINE_VIDEO_TRACK_HEIGHT_DP,
            TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP + TIMELINE_CLIP_LABEL_FONT_SP * 1.35f,
            12,
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
