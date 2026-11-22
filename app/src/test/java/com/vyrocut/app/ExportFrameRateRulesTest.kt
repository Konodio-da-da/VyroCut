package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportFrameRateRulesTest {
    @Test
    fun missingOrUnknownPreferencesPreserveSourceCadence() {
        assertEquals(ExportFrameRatePreset.SOURCE, ExportFrameRatePreset.fromId(null))
        assertEquals(ExportFrameRatePreset.SOURCE, ExportFrameRatePreset.fromId(""))
        assertEquals(ExportFrameRatePreset.SOURCE, ExportFrameRatePreset.fromId("120"))
    }

    @Test
    fun restoresEverySupportedMaximumFrameRate() {
        assertEquals(ExportFrameRatePreset.FPS_24, ExportFrameRatePreset.fromId("24"))
        assertEquals(ExportFrameRatePreset.FPS_30, ExportFrameRatePreset.fromId("30"))
        assertEquals(ExportFrameRatePreset.FPS_60, ExportFrameRatePreset.fromId("60"))
    }

    @Test
    fun sourcePresetDoesNotCreateAFrameDropEffect() {
        assertNull(exportFrameDropTargetRate(ExportFrameRatePreset.SOURCE))
    }

    @Test
    fun cappedPresetsExposeTheirExactTargetFrameRates() {
        assertEquals(24f, exportFrameDropTargetRate(ExportFrameRatePreset.FPS_24)!!, 0f)
        assertEquals(30f, exportFrameDropTargetRate(ExportFrameRatePreset.FPS_30)!!, 0f)
        assertEquals(60f, exportFrameDropTargetRate(ExportFrameRatePreset.FPS_60)!!, 0f)
    }

    @Test
    fun completionLabelsDescribeTargetsWithoutPromisingUpsampling() {
        assertEquals("Source FPS", ExportFrameRatePreset.SOURCE.summaryLabel)
        assertEquals("Target 24 FPS", ExportFrameRatePreset.FPS_24.summaryLabel)
        assertEquals("Target 30 FPS", ExportFrameRatePreset.FPS_30.summaryLabel)
        assertEquals("Target 60 FPS", ExportFrameRatePreset.FPS_60.summaryLabel)
    }
}
