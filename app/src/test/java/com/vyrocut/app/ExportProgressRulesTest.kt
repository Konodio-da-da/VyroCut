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
