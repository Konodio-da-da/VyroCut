package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportVideoBitrateRulesTest {
    private val portrait1080p = ExportCanvasSize(widthPx = 1080, heightPx = 1920)

    @Test
    fun missingOrUnknownPreferencesPreserveAutomaticEncoderSelection() {
        assertEquals(ExportVideoBitratePreset.AUTO, ExportVideoBitratePreset.fromId(null))
        assertEquals(ExportVideoBitratePreset.AUTO, ExportVideoBitratePreset.fromId(""))
        assertEquals(ExportVideoBitratePreset.AUTO, ExportVideoBitratePreset.fromId("lossless"))
        assertNull(
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.AUTO,
                portrait1080p,
                ExportFrameRatePreset.FPS_30,
            ),
        )
    }

    @Test
    fun restoresEverySupportedManualBitratePreset() {
        assertEquals(ExportVideoBitratePreset.BALANCED, ExportVideoBitratePreset.fromId("balanced"))
        assertEquals(ExportVideoBitratePreset.HIGH, ExportVideoBitratePreset.fromId("high"))
        assertEquals(ExportVideoBitratePreset.VERY_HIGH, ExportVideoBitratePreset.fromId("very-high"))
    }

    @Test
    fun 1080pThirtyFpsTargetsUseDocumentedBitsPerPixelPerFrameValues() {
        assertEquals(
            6_220_800,
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.BALANCED,
                portrait1080p,
                ExportFrameRatePreset.FPS_30,
            ),
        )
        assertEquals(
            8_709_120,
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.HIGH,
                portrait1080p,
                ExportFrameRatePreset.FPS_30,
            ),
        )
        assertEquals(
            11_197_440,
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.VERY_HIGH,
                portrait1080p,
                ExportFrameRatePreset.FPS_30,
            ),
        )
    }

    @Test
    fun bitrateScalesWithTargetFrameRateAndOutputPixelCount() {
        val highAt30 = exportTargetVideoBitrateBps(
            ExportVideoBitratePreset.HIGH,
            portrait1080p,
            ExportFrameRatePreset.FPS_30,
        )
        val highAt60 = exportTargetVideoBitrateBps(
            ExportVideoBitratePreset.HIGH,
            portrait1080p,
            ExportFrameRatePreset.FPS_60,
        )
        val balanced720p = exportTargetVideoBitrateBps(
            ExportVideoBitratePreset.BALANCED,
            ExportCanvasSize(widthPx = 1280, heightPx = 720),
            ExportFrameRatePreset.FPS_30,
        )

        assertEquals(17_418_240, highAt60)
        assertEquals(2_764_800, balanced720p)
        assertEquals(highAt30!! * 2, highAt60)
    }

    @Test
    fun sourceFrameRateUsesThirtyFpsOnlyForTheBitrateEstimate() {
        assertEquals(
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.BALANCED,
                portrait1080p,
                ExportFrameRatePreset.FPS_30,
            ),
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.BALANCED,
                portrait1080p,
                ExportFrameRatePreset.SOURCE,
            ),
        )
    }

    @Test
    fun verySmallAndVeryLargeOutputsAreSafelyBounded() {
        assertEquals(
            MIN_TARGET_VIDEO_BITRATE_BPS,
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.BALANCED,
                ExportCanvasSize(widthPx = 360, heightPx = 640),
                ExportFrameRatePreset.FPS_24,
            ),
        )
        assertEquals(
            MAX_TARGET_VIDEO_BITRATE_BPS,
            exportTargetVideoBitrateBps(
                ExportVideoBitratePreset.VERY_HIGH,
                ExportCanvasSize(widthPx = 7680, heightPx = 4320),
                ExportFrameRatePreset.FPS_60,
            ),
        )
    }

    @Test
    fun completionLabelsDescribeRequestedQualityRatherThanActualOutputBitrate() {
        assertEquals("Auto bitrate", ExportVideoBitratePreset.AUTO.summaryLabel)
        assertEquals("Balanced bitrate target", ExportVideoBitratePreset.BALANCED.summaryLabel)
        assertEquals("High bitrate target", ExportVideoBitratePreset.HIGH.summaryLabel)
        assertEquals("Very high bitrate target", ExportVideoBitratePreset.VERY_HIGH.summaryLabel)
    }
}
