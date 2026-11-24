package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportProgressRulesTest {
    @Test
    fun acceptsAvailableProgressAtBothBoundaries() {
        assertEquals(0, nextExportProgressPercent(null, 0))
        assertEquals(100, nextExportProgressPercent(99, 100))
    }

    @Test
    fun ignoresOutOfRangeProgressWithoutLosingTheLastValidValue() {
        assertNull(nextExportProgressPercent(null, -1))
        assertEquals(42, nextExportProgressPercent(42, 101))
    }

    @Test
    fun progressNeverMovesBackwards() {
        assertEquals(64, nextExportProgressPercent(64, 31))
        assertEquals(64, nextExportProgressPercent(64, 64))
        assertEquals(72, nextExportProgressPercent(64, 72))
    }

    @Test
    fun preparationAndVerificationStayIndeterminateWithAccuratePhaseCopy() {
        assertEquals(
            ExportProgressPresentation(
                percent = null,
                isIndeterminate = true,
                progressLabel = "Preparing…",
                detailText = "Checking source videos, audio lanes and available storage before rendering.",
            ),
            exportProgressPresentation(ExportUiStage.PREPARING, 84),
        )
        assertEquals(
            ExportProgressPresentation(
                percent = null,
                isIndeterminate = true,
                progressLabel = "Verifying…",
                detailText = "Checking the rendered video, audio and saved MP4 copy before it appears in Movies.",
            ),
            exportProgressPresentation(ExportUiStage.VERIFYING, 100),
        )
    }

    @Test
    fun renderingUsesAvailableProgressButNeverClaimsCompletion() {
        val expectedDetail =
            "Rendering stays on this device. Cancel discards this temporary export."
        assertEquals(
            ExportProgressPresentation(
                percent = 64,
                isIndeterminate = false,
                progressLabel = "Rendering…",
                detailText = expectedDetail,
            ),
            exportProgressPresentation(ExportUiStage.RENDERING, 64),
        )
        assertEquals(
            ExportProgressPresentation(
                percent = 99,
                isIndeterminate = false,
                progressLabel = "Rendering…",
                detailText = expectedDetail,
            ),
            exportProgressPresentation(ExportUiStage.RENDERING, 100),
        )
        assertEquals(
            ExportProgressPresentation(
                percent = null,
                isIndeterminate = true,
                progressLabel = "Rendering…",
                detailText = expectedDetail,
            ),
            exportProgressPresentation(ExportUiStage.RENDERING, null),
        )
    }

    @Test
    fun onlyCompletedStageMayDisplayOneHundredPercentAndVerifiedCopy() {
        assertEquals(
            ExportProgressPresentation(
                percent = 100,
                isIndeterminate = false,
                progressLabel = "Complete",
                detailText = "The MP4 passed verification and was saved to Movies/VyroCut.",
            ),
            exportProgressPresentation(ExportUiStage.COMPLETE, null),
        )
    }

    @Test
    fun idleFailureAndCancellationDoNotShowAnActiveProgressIndicator() {
        val expected = mapOf(
            ExportUiStage.IDLE to ("Ready" to "Start an export to render a video on this device."),
            ExportUiStage.FAILED to ("Failed" to "Review the export error above before retrying."),
            ExportUiStage.CANCELLED to ("Cancelled" to "The temporary export was discarded."),
        )
        expected.forEach { (stage, copy) ->
            assertEquals(
                ExportProgressPresentation(
                    percent = null,
                    isIndeterminate = false,
                    progressLabel = copy.first,
                    detailText = copy.second,
                ),
                exportProgressPresentation(stage, 73),
            )
        }
    }
}
