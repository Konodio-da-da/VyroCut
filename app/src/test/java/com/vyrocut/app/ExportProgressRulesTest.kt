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
}

    @Test
    fun preparationAndVerificationStayIndeterminateEvenIfAStalePercentExists() {
        assertEquals(
            ExportProgressPresentation(percent = null, isIndeterminate = true),
            exportProgressPresentation(ExportUiStage.PREPARING, 84),
        )
        assertEquals(
            ExportProgressPresentation(percent = null, isIndeterminate = true),
            exportProgressPresentation(ExportUiStage.VERIFYING, 100),
        )
    }

    @Test
    fun renderingUsesAvailableProgressButNeverClaimsCompletion() {
        assertEquals(
            ExportProgressPresentation(percent = 64, isIndeterminate = false),
            exportProgressPresentation(ExportUiStage.RENDERING, 64),
        )
        assertEquals(
            ExportProgressPresentation(percent = 99, isIndeterminate = false),
            exportProgressPresentation(ExportUiStage.RENDERING, 100),
        )
        assertEquals(
            ExportProgressPresentation(percent = null, isIndeterminate = true),
            exportProgressPresentation(ExportUiStage.RENDERING, null),
        )
    }

    @Test
    fun onlyCompletedStageMayDisplayOneHundredPercent() {
        assertEquals(
            ExportProgressPresentation(percent = 100, isIndeterminate = false),
            exportProgressPresentation(ExportUiStage.COMPLETE, null),
        )
    }

    @Test
    fun idleFailureAndCancellationDoNotShowAnActiveProgressIndicator() {
        listOf(
            ExportUiStage.IDLE,
            ExportUiStage.FAILED,
            ExportUiStage.CANCELLED,
        ).forEach { stage ->
            assertEquals(
                ExportProgressPresentation(percent = null, isIndeterminate = false),
                exportProgressPresentation(stage, 73),
            )
        }
    }
