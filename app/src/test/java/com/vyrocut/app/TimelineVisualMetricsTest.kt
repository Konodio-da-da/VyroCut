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
