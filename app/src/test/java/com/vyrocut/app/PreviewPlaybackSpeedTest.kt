package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PreviewPlaybackSpeedTest {
    @Test
    fun cyclesThroughSupportedPlaybackRatesInOrder() {
        assertEquals(0.75f, nextPreviewPlaybackSpeed(0.5f), 0f)
        assertEquals(1f, nextPreviewPlaybackSpeed(0.75f), 0f)
        assertEquals(1.25f, nextPreviewPlaybackSpeed(1f), 0f)
        assertEquals(1.5f, nextPreviewPlaybackSpeed(1.25f), 0f)
        assertEquals(2f, nextPreviewPlaybackSpeed(1.5f), 0f)
    }

    @Test
    fun wrapsFromFastestRateBackToSlowest() {
        assertEquals(0.5f, nextPreviewPlaybackSpeed(2f), 0f)
    }

    @Test
    fun unknownOrNonFiniteRateFallsBackToNormalSpeed() {
        assertEquals(1f, nextPreviewPlaybackSpeed(1.1f), 0f)
        assertEquals(1f, nextPreviewPlaybackSpeed(Float.NaN), 0f)
        assertEquals(1f, nextPreviewPlaybackSpeed(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun speedLabelsRemainCompactAndReadable() {
        assertEquals("0.5×", previewPlaybackSpeedLabel(0.5f))
        assertEquals("1×", previewPlaybackSpeedLabel(1f))
        assertEquals("1.25×", previewPlaybackSpeedLabel(1.25f))
        assertEquals("2×", previewPlaybackSpeedLabel(2f))
    }

    @Test
    fun supportedRatesArePositiveAndUnique() {
        assertEquals(PREVIEW_PLAYBACK_SPEEDS.size, PREVIEW_PLAYBACK_SPEEDS.toSet().size)
        assertEquals(true, PREVIEW_PLAYBACK_SPEEDS.all { it.isFinite() && it > 0f })
    }
}
