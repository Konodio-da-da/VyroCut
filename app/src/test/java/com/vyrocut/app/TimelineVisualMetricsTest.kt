package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineVisualMetricsTest {
    @Test
    fun textTrackHeightUsesOneRowForEmptyOrInvalidLayerCounts() {
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(0))
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(-2))
    }

    @Test
    fun textTrackHeightScalesAndRespectsOverlayLimit() {
        assertEquals(TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(1))
        assertEquals(2 * TIMELINE_TEXT_ROW_HEIGHT_DP, timelineTextTrackHeightDp(2))
        assertEquals(
            MAX_CLIP_TEXT_OVERLAYS * TIMELINE_TEXT_ROW_HEIGHT_DP,
            timelineTextTrackHeightDp(MAX_CLIP_TEXT_OVERLAYS + 2),
        )
    }
}
