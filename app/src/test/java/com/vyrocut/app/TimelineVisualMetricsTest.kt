package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineVisualMetricsTest {
    @Test
    fun defaultTimelineMetricsUseClearerTypographyAndBaselineHeights() {
        val metrics = timelineReadabilityMetrics(1f)

        assertEquals(11, TIMELINE_TRACK_LABEL_FONT_SP)
        assertEquals(10, TIMELINE_TRACK_DETAIL_FONT_SP)
        assertEquals(10, TIMELINE_RULER_LABEL_FONT_SP)
        assertEquals(11, TIMELINE_CLIP_LABEL_FONT_SP)
        assertEquals(10, TIMELINE_CLIP_META_FONT_SP)
        assertEquals(TIMELINE_RULER_HEIGHT_DP, metrics.rulerHeightDp)
        assertEquals(TIMELINE_VIDEO_TRACK_HEIGHT_DP, metrics.videoTrackHeightDp)
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, metrics.textRowHeightDp)
        assertEquals(TIMELINE_STICKER_ROW_HEIGHT_DP, metrics.stickerRowHeightDp)
        assertEquals(TIMELINE_SOURCE_TRACK_HEIGHT_DP, metrics.sourceTrackHeightDp)
        assertEquals(TIMELINE_SOURCE_WAVEFORM_HEIGHT_DP, metrics.sourceWaveformHeightDp)
        assertEquals(TIMELINE_AUDIO_LANE_HEIGHT_DP, metrics.audioLaneHeightDp)
        assertEquals(TIMELINE_AUDIO_WAVEFORM_HEIGHT_DP, metrics.audioWaveformHeightDp)
    }

    @Test
    fun rulerLabelSpacingAccountsForTimecodeWidthAndSystemFontScale() {
        assertEquals(39, timelineRulerLabelMinimumSpacingDp(5, 1f))
        assertEquals(55, timelineRulerLabelMinimumSpacingDp(5, 1.5f))
        assertEquals(
            timelineRulerLabelMinimumSpacingDp(5, 2f),
            timelineRulerLabelMinimumSpacingDp(5, 4f),
        )
    }

    @Test
    fun rulerLabelSpacingBoundsMalformedCharacterCountsAndFontScales() {
        assertEquals(
            timelineRulerLabelMinimumSpacingDp(1, 1f),
            timelineRulerLabelMinimumSpacingDp(0, Float.NaN),
        )
        assertEquals(405, timelineRulerLabelMinimumSpacingDp(100, 2f))
        assertEquals(
            timelineRulerLabelMinimumSpacingDp(5, 1f),
            timelineRulerLabelMinimumSpacingDp(5, Float.NEGATIVE_INFINITY),
        )
    }

    @Test
    fun trackHeaderBadgesUseCompactMetricsWithoutExtraVerticalPadding() {
        assertEquals(6, TIMELINE_TRACK_HEADER_BADGE_HORIZONTAL_PADDING_DP)
        assertEquals(4, TIMELINE_TRACK_HEADER_CORNER_RADIUS_DP)
        assertEquals(5, TIMELINE_TRACK_HEADER_DOT_SIZE_DP)
        assertEquals(4, TIMELINE_TRACK_HEADER_DOT_GAP_DP)
        assertEquals(8, TIMELINE_TRACK_HEADER_DETAIL_GAP_DP)
        assertEquals(11, TIMELINE_TRACK_LABEL_FONT_SP)
        assertTrue(TIMELINE_TRACK_HEADER_DOT_SIZE_DP < TIMELINE_TRACK_LABEL_FONT_SP)
    }

    @Test
    fun majorAndMinorRulerTicksHaveAVisibleVisualHierarchy() {
        val major = timelineRulerTickVisualMetrics(isMajor = true)
        val minor = timelineRulerTickVisualMetrics(isMajor = false)

        assertEquals(2, major.widthDp)
        assertEquals(10, major.heightDp)
        assertEquals(1, minor.widthDp)
        assertEquals(5, minor.heightDp)
        assertTrue(major.widthDp > minor.widthDp)
        assertTrue(major.heightDp > minor.heightDp)
    }

    @Test
    fun rulerLabelWidthIncludesPillPaddingAndRespectsFontScale() {
        assertEquals(37, timelineRulerLabelWidthDp(5, 1f))
        assertEquals(53, timelineRulerLabelWidthDp(5, 1.5f))
        assertEquals(
            timelineRulerLabelWidthDp(5, 2f),
            timelineRulerLabelWidthDp(5, 4f),
        )
    }

    @Test
    fun rulerLabelsStayInsideTimelineContentAtBothEdges() {
        val first = timelineRulerLabelPlacement(0f, 40, 200f)
        val middle = timelineRulerLabelPlacement(100f, 40, 200f)
        val last = timelineRulerLabelPlacement(200f, 40, 200f)

        assertEquals(0f, first.offsetDp, 0f)
        assertEquals(TimelineRulerLabelAlignment.START, first.alignment)
        assertEquals(80f, middle.offsetDp, 0f)
        assertEquals(TimelineRulerLabelAlignment.CENTER, middle.alignment)
        assertEquals(160f, last.offsetDp, 0f)
        assertEquals(TimelineRulerLabelAlignment.END, last.alignment)
    }

    @Test
    fun rulerLabelPlacementUsesSafeBoundsForMalformedGeometry() {
        val placement = timelineRulerLabelPlacement(Float.NaN, 0, Float.NaN)

        assertEquals(0f, placement.offsetDp, 0f)
        assertEquals(TimelineRulerLabelAlignment.START, placement.alignment)
    }

    @Test
    fun playheadOutlineAndCapStayCenteredOnTheNeedle() {
        val metrics = timelinePlayheadVisualMetrics()
        val geometry = timelinePlayheadGeometry(48f)
        val lineCenter = geometry.lineLeftDp + metrics.lineWidthDp / 2f

        assertEquals(2, metrics.lineWidthDp)
        assertEquals(4, metrics.outlineWidthDp)
        assertEquals(14, metrics.capWidthDp)
        assertEquals(9, metrics.capHeightDp)
        assertEquals(lineCenter, geometry.outlineLeftDp + metrics.outlineWidthDp / 2f, 0.001f)
        assertEquals(lineCenter, geometry.capLeftDp + metrics.capWidthDp / 2f, 0.001f)
    }

    @Test
    fun malformedPlayheadOffsetsFallBackToTheTimelineOrigin() {
        assertEquals(0f, timelinePlayheadGeometry(Float.NaN).lineLeftDp, 0f)
        assertEquals(0f, timelinePlayheadGeometry(Float.POSITIVE_INFINITY).lineLeftDp, 0f)
        assertEquals(-1f, timelinePlayheadGeometry(Float.NEGATIVE_INFINITY).outlineLeftDp, 0f)
    }

    @Test
    fun selectedClipGetsAnOutlineWhileUnselectedClipsDoNot() {
        assertEquals(TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP, timelineClipSelectionBorderWidthDp(true))
        assertEquals(0, timelineClipSelectionBorderWidthDp(false))
    }

    @Test
    fun audioClipSelectionOutlineFollowsTheSelectedStableId() {
        assertEquals(
            TIMELINE_SELECTED_CLIP_BORDER_WIDTH_DP,
            timelineAudioClipSelectionBorderWidthDp("audio-1", "audio-1"),
        )
        assertEquals(0, timelineAudioClipSelectionBorderWidthDp("audio-1", "audio-2"))
        assertEquals(0, timelineAudioClipSelectionBorderWidthDp("audio-1", null))
        assertEquals(0, timelineAudioClipSelectionBorderWidthDp("", ""))
    }

    @Test
    fun largerSystemFontScaleExpandsRowsAndWaveformsTogether() {
        val baseline = timelineReadabilityMetrics(1f)
        val large = timelineReadabilityMetrics(1.5f)

        assertTrue(large.rulerHeightDp > baseline.rulerHeightDp)
        assertTrue(large.videoTrackHeightDp > baseline.videoTrackHeightDp)
        assertTrue(large.textRowHeightDp > baseline.textRowHeightDp)
        assertTrue(large.stickerRowHeightDp > baseline.stickerRowHeightDp)
        assertTrue(large.sourceTrackHeightDp > baseline.sourceTrackHeightDp)
        assertTrue(large.sourceWaveformHeightDp > baseline.sourceWaveformHeightDp)
        assertTrue(large.audioLaneHeightDp > baseline.audioLaneHeightDp)
        assertTrue(large.audioWaveformHeightDp > baseline.audioWaveformHeightDp)
    }

    @Test
    fun videoTrackKeepsThumbnailFixedWhileScalingOnlyItsLabel() {
        assertEquals(TIMELINE_VIDEO_TRACK_HEIGHT_DP, timelineReadabilityMetrics(1f).videoTrackHeightDp)
        assertEquals(77, timelineReadabilityMetrics(1.5f).videoTrackHeightDp)
        assertEquals(84, timelineReadabilityMetrics(2f).videoTrackHeightDp)
    }

    @Test
    fun invalidOrSmallerThanDefaultFontScaleUsesReadableBaseline() {
        val baseline = timelineReadabilityMetrics(1f)

        assertEquals(baseline, timelineReadabilityMetrics(Float.NaN))
        assertEquals(baseline, timelineReadabilityMetrics(Float.POSITIVE_INFINITY))
        assertEquals(baseline, timelineReadabilityMetrics(0f))
        assertEquals(baseline, timelineReadabilityMetrics(0.85f))
    }

    @Test
    fun extremeFontScaleIsBoundedAtTwoTimes() {
        assertEquals(timelineReadabilityMetrics(2f), timelineReadabilityMetrics(4f))
    }

    @Test
    fun textTrackHeightUsesOneRowForEmptyOrInvalidLayerCounts() {
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(0))
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(-2))
    }

    @Test
    fun stickerTrackHeightUsesOneRowForEmptyOrInvalidLayerCounts() {
        assertEquals(TIMELINE_STICKER_ROW_HEIGHT_DP, timelineStickerTrackHeightDp(0))
        assertEquals(TIMELINE_STICKER_ROW_HEIGHT_DP, timelineStickerTrackHeightDp(-2))
    }

    @Test
    fun stickerTrackHeightScalesAndRespectsOverlayLimit() {
        assertEquals(TIMELINE_STICKER_ROW_HEIGHT_DP, timelineStickerTrackHeightDp(1))
        assertEquals(2 * TIMELINE_STICKER_ROW_HEIGHT_DP, timelineStickerTrackHeightDp(2))
        assertEquals(
            MAX_CLIP_STICKER_OVERLAYS * TIMELINE_STICKER_ROW_HEIGHT_DP,
            timelineStickerTrackHeightDp(MAX_CLIP_STICKER_OVERLAYS + 2),
        )
        assertEquals(3 * 36, timelineStickerTrackHeightDp(3, 36))
    }

    @Test
    fun textTrackHeightScalesAndRespectsOverlayLimit() {
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(1))
        assertEquals(2 * TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(2))
        assertEquals(
            MAX_CLIP_TEXT_OVERLAYS * TIMELINE_TEXT_ROW_HEIGHT_DP,
            timelineTextTrackHeightDp(MAX_CLIP_TEXT_OVERLAYS + 2),
        )
        assertEquals(3 * 34, timelineTextTrackHeightDp(3, 34))
    }

    @Test
    fun customTrackRowHeightCannotOverflowIntegerBounds() {
        assertEquals(Int.MAX_VALUE, timelineTextTrackHeightDp(MAX_CLIP_TEXT_OVERLAYS, Int.MAX_VALUE))
    }
    @Test
    fun timelineTypographyUsesSharedDesignSystemTokens() {
        assertEquals(VyroCutTypography.LabelSp, TIMELINE_TRACK_LABEL_FONT_SP)
        assertEquals(VyroCutTypography.CaptionSp, TIMELINE_TRACK_DETAIL_FONT_SP)
        assertEquals(VyroCutTypography.CaptionSp, TIMELINE_RULER_LABEL_FONT_SP)
        assertEquals(VyroCutTypography.LabelSp, TIMELINE_CLIP_LABEL_FONT_SP)
        assertEquals(VyroCutTypography.CaptionSp, TIMELINE_CLIP_META_FONT_SP)
        assertEquals(VyroCutTypography.TimelineStickerSymbolSp, TIMELINE_STICKER_SYMBOL_FONT_SP)
        assertEquals(1.3f, VyroCutTypography.TimelineSectionLetterSpacingSp, 0f)
    }

}
