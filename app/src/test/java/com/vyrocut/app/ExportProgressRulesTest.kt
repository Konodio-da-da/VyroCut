package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportProgressRulesTest {
    @Test
    fun onlyTheCurrentNonBlankSessionMayContinue() {
        assertTrue(isExportSessionCurrent("session-1", "session-1"))
        assertFalse(isExportSessionCurrent(null, "session-1"))
        assertFalse(isExportSessionCurrent("session-2", "session-1"))
        assertFalse(isExportSessionCurrent("", ""))
        assertFalse(isExportSessionCurrent("session-1", ""))
    }

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

    @Test
    fun exportActionLabelOffersRetryOnlyAfterFailure() {
        assertEquals("Export MP4", exportActionButtonLabel(ExportUiStage.IDLE, isExporting = false))
        assertEquals("Retry export", exportActionButtonLabel(ExportUiStage.FAILED, isExporting = false))
        assertEquals("Export MP4", exportActionButtonLabel(ExportUiStage.CANCELLED, isExporting = false))
        assertEquals("Export MP4", exportActionButtonLabel(ExportUiStage.COMPLETE, isExporting = false))
    }

    @Test
    fun activeExportLabelTakesPrecedenceOverTerminalStage() {
        ExportUiStage.values().forEach { stage ->
            assertEquals("Exporting…", exportActionButtonLabel(stage, isExporting = true))
        }
    }

    @Test
    fun completionSummarySeparatesVerifiedMeasurementsFromSelectedSettings() {
        val verifiedOutput = "1920 × 1080 • 00:10 • 1.0 MiB actual • H.264 + AAC"
        val settings = listOf("1080p", "30 fps", "12 Mbps video", "256 kbps audio", "MP4")

        assertEquals(
            ExportCompletionSummary(
                verifiedOutput = verifiedOutput,
                exportSettings = settings,
            ),
            buildExportCompletionSummary(verifiedOutput, settings),
        )
    }

    @Test
    fun completionSummaryTrimsEmptyFieldsAndOmitsMissingSections() {
        assertEquals(
            ExportCompletionSummary(
                verifiedOutput = "1920 × 1080 • 00:10 • 1.0 MiB actual",
                exportSettings = listOf("MP4"),
            ),
            buildExportCompletionSummary(
                verifiedOutput = "  1920 × 1080 • 00:10 • 1.0 MiB actual  ",
                exportSettings = listOf("", " MP4 ", "  "),
            ),
        )
        assertEquals(null, buildExportCompletionSummary(null, listOf(" ", "")))
        assertEquals(
            ExportCompletionSummary(verifiedOutput = "verified", exportSettings = emptyList()),
            buildExportCompletionSummary("verified", emptyList()),
        )
    }

    @Test
    fun shareFailureMessagesOfferSpecificRecoveryWithoutClaimingSuccess() {
        assertEquals(
            "Android couldn't find an app to share this MP4. Install or enable a sharing app, then try again.",
            exportShareFailureMessage(ExportShareFailureReason.NO_COMPATIBLE_APP),
        )
        assertEquals(
            "Android denied access to the saved MP4. Export again, then retry sharing.",
            exportShareFailureMessage(ExportShareFailureReason.URI_ACCESS_DENIED),
        )
    }
}
