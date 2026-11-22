package com.vyrocut.app

internal const val TIMELINE_RULER_HEIGHT_DP = 26
internal const val TIMELINE_VIDEO_TRACK_HEIGHT_DP = 70
internal const val TIMELINE_VIDEO_THUMBNAIL_HEIGHT_DP = 42
internal const val TIMELINE_TEXT_ROW_HEIGHT_DP = 28
internal const val TIMELINE_SOURCE_TRACK_HEIGHT_DP = 30
internal const val TIMELINE_AUDIO_LANE_HEIGHT_DP = 32
internal const val TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP = 12

internal const val TIMELINE_TRACK_LABEL_FONT_SP = 10
internal const val TIMELINE_RULER_LABEL_FONT_SP = 9
internal const val TIMELINE_CLIP_LABEL_FONT_SP = 10
internal const val TIMELINE_CLIP_META_FONT_SP = 9

/**
 * Returns the vertical space needed by the visible text-overlay rows.
 * Keep the calculation bounded by the same overlay limit used by editing.
 */
internal fun timelineTextTrackHeightDp(layerCount: Int): Int =
    layerCount.coerceIn(1, MAX_CLIP_TEXT_OVERLAYS) * TIMELINE_TEXT_ROW_HEIGHT_DP
