package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {
    @Test
    fun mapsPlayerPositionAfterEarlierClips() {
        assertEquals(
            4_250L,
            timelinePositionMs(listOf(1_000L, 2_000L, 3_000L), 2, 1_250L),
        )
    }

    @Test
    fun clampsPlayerPositionToCurrentClipDuration() {
        assertEquals(
            3_000L,
            timelinePositionMs(listOf(1_000L, 2_000L), 1, 9_000L),
        )
    }

    @Test
    fun invalidPlayerIndexMapsToTimelineStart() {
        assertEquals(0L, timelinePositionMs(listOf(1_000L), -1, 500L))
        assertEquals(0L, timelinePositionMs(listOf(1_000L), 1, 500L))
    }

    @Test
    fun mapsInsideClip() {
        assertEquals(
            TimelineSeekTarget(1, 350L),
            resolveTimelineSeekTarget(listOf(1_000L, 2_000L), 1_350L),
        )
    }

    @Test
    fun exactBoundarySelectsNextClip() {
        assertEquals(
            TimelineSeekTarget(1, 0L),
            resolveTimelineSeekTarget(listOf(1_000L, 2_000L), 1_000L),
        )
    }

    @Test
    fun timelineEndSeeksToLastMillisecondOfLastPlayableClip() {
        assertEquals(
            TimelineSeekTarget(2, 1_999L),
            resolveTimelineSeekTarget(listOf(1_000L, 0L, 2_000L), 3_000L),
        )
        assertEquals(
            TimelineSeekTarget(2, 1_999L),
            resolveTimelineSeekTarget(listOf(1_000L, 0L, 2_000L), Long.MAX_VALUE),
        )
    }

    @Test
    fun negativeTimelinePositionClampsToStart() {
        assertEquals(
            TimelineSeekTarget(0, 0L),
            resolveTimelineSeekTarget(listOf(1_000L, 2_000L), -50L),
        )
    }

    @Test
    fun emptyOrZeroLengthTimelineHasNoSeekTarget() {
        assertNull(resolveTimelineSeekTarget(emptyList(), 0L))
        assertNull(resolveTimelineSeekTarget(listOf(0L, -1L), 0L))
    }

    @Test
    fun durationSumSaturatesInsteadOfOverflowing() {
        assertEquals(
            Long.MAX_VALUE,
            timelinePositionMs(listOf(Long.MAX_VALUE - 5L, 100L), 1, 5L),
        )
    }
}
