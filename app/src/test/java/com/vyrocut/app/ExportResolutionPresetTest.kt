package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportResolutionPresetTest {
    @Test
    fun defaultsToFullHdForMissingOrUnknownPreferenceValues() {
        assertEquals(
            ExportResolutionPreset.FULL_HD_1080,
            ExportResolutionPreset.fromId(null),
        )
        assertEquals(
            ExportResolutionPreset.FULL_HD_1080,
            ExportResolutionPreset.fromId("4k"),
        )
    }

    @Test
    fun restoresSupportedResolutionPreferenceValues() {
        assertEquals(
            ExportResolutionPreset.HD_720,
            ExportResolutionPreset.fromId("720p"),
        )
        assertEquals(
            ExportResolutionPreset.FULL_HD_1080,
            ExportResolutionPreset.fromId("1080p"),
        )
    }

    @Test
    fun createsLandscapeCanvasSizesAtBothResolutions() {
        assertEquals(
            ExportCanvasSize(1_280, 720),
            exportCanvasSizeForPreset("16:9", ExportResolutionPreset.HD_720),
        )
        assertEquals(
            ExportCanvasSize(1_920, 1_080),
            exportCanvasSizeForPreset("16:9", ExportResolutionPreset.FULL_HD_1080),
        )
    }

    @Test
    fun createsSquareCanvasSizesAtBothResolutions() {
        assertEquals(
            ExportCanvasSize(720, 720),
            exportCanvasSizeForPreset("1:1", ExportResolutionPreset.HD_720),
        )
        assertEquals(
            ExportCanvasSize(1_080, 1_080),
            exportCanvasSizeForPreset("1:1", ExportResolutionPreset.FULL_HD_1080),
        )
    }

    @Test
    fun createsPortraitCanvasSizesAtBothResolutions() {
        assertEquals(
            ExportCanvasSize(720, 1_280),
            exportCanvasSizeForPreset("9:16", ExportResolutionPreset.HD_720),
        )
        assertEquals(
            ExportCanvasSize(1_080, 1_920),
            exportCanvasSizeForPreset("9:16", ExportResolutionPreset.FULL_HD_1080),
        )
    }

    @Test
    fun preservesTheExistingFullHdDefault() {
        assertEquals(
            ExportCanvasSize(1_920, 1_080),
            exportCanvasSizeForPreset("16:9"),
        )
        assertEquals(
            ExportCanvasSize(1_080, 1_080),
            exportCanvasSizeForPreset("1:1"),
        )
        assertEquals(
            ExportCanvasSize(1_080, 1_920),
            exportCanvasSizeForPreset("9:16"),
        )
    }
}
