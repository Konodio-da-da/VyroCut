package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMathTest {
    @Test
    fun mapsPlayerPositionAfterEarlierClips() {
        assertEquals(
            4_250L,
            calculateTimelinePositionMs(listOf(1_000L, 2_000L, 3_000L), 2, 1_250L),
        )
    }

    @Test
    fun clampsPlayerPositionToCurrentClipDuration() {
        assertEquals(
            3_000L,
            calculateTimelinePositionMs(listOf(1_000L, 2_000L), 1, 9_000L),
        )
    }

    @Test
    fun invalidPlayerIndexMapsToTimelineStart() {
        assertEquals(0L, calculateTimelinePositionMs(listOf(1_000L), -1, 500L))
        assertEquals(0L, calculateTimelinePositionMs(listOf(1_000L), 1, 500L))
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
            calculateTimelinePositionMs(listOf(Long.MAX_VALUE - 5L, 100L), 1, 5L),
        )
    }
    @Test
    fun splitKeepsSourceRangesInLeftToRightPlaybackOrder() {
        assertEquals(
            ClipRange(1_000L, 2_500L) to ClipRange(2_500L, 4_000L),
            splitClipRange(1_000L, 4_000L, 2_500L),
        )
    }

    @Test
    fun splitRejectsPositionsAtOrOutsideClipBoundaries() {
        assertNull(splitClipRange(1_000L, 4_000L, 1_000L))
        assertNull(splitClipRange(1_000L, 4_000L, 4_000L))
        assertNull(splitClipRange(4_000L, 1_000L, 2_500L))
    }

    @Test
    fun trimStartMovesOnlyTheSourceInPoint() {
        assertEquals(
            ClipRange(2_000L, 4_000L),
            trimClipRange(1_000L, 4_000L, 1_000L, fromStart = true),
        )
    }

    @Test
    fun trimEndMovesOnlyTheSourceOutPoint() {
        assertEquals(
            ClipRange(1_000L, 3_000L),
            trimClipRange(1_000L, 4_000L, 1_000L, fromStart = false),
        )
    }

    @Test
    fun trimCannotCreateAnEmptyOrNegativeLengthClip() {
        assertNull(trimClipRange(1_000L, 2_000L, 1_000L, fromStart = true))
        assertNull(trimClipRange(1_000L, 2_000L, 1_000L, fromStart = false))
        assertNull(trimClipRange(1_000L, 2_000L, 0L, fromStart = true))
    }

    @Test
    fun timelineDurationSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, calculateTimelineDurationMs(listOf(Long.MAX_VALUE - 10L, 50L)))
        assertEquals(0L, calculateTimelineDurationMs(emptyList()))
    }

    @Test
    fun movesTimelineItemsInBothDirections() {
        val original = listOf("A", "B", "C", "D")
        assertEquals(listOf("B", "A", "C", "D"), moveTimelineItem(original, 1, 0))
        assertEquals(listOf("A", "C", "B", "D"), moveTimelineItem(original, 1, 2))
    }

    @Test
    fun invalidTimelineMoveLeavesOrderUnchanged() {
        val original = listOf("A", "B", "C")
        assertEquals(original, moveTimelineItem(original, -1, 1))
        assertEquals(original, moveTimelineItem(original, 1, 3))
        assertEquals(original, moveTimelineItem(original, 1, 1))
    }

    @Test
    fun movingTimelineItemDoesNotMutateOriginalList() {
        val original = listOf("A", "B", "C")
        moveTimelineItem(original, 0, 2)
        assertEquals(listOf("A", "B", "C"), original)
    }

    @Test
    fun restoresSameClipAndSourcePositionAfterReordering() {
        val previous = listOf(
            ClipPlaybackRange("a", 0L, 5_000L),
            ClipPlaybackRange("b", 2_000L, 8_000L),
        )
        val next = listOf(previous[1], previous[0])

        assertEquals(
            PlaybackRestoreTarget(0, 1_250L),
            resolvePlaybackRestoreTarget(previous, next, "b", 1_250L, 1),
        )
    }

    @Test
    fun trimStartClampsPlayheadToNewSourceInPoint() {
        val previous = listOf(ClipPlaybackRange("a", 1_000L, 6_000L))
        val next = listOf(ClipPlaybackRange("a", 3_000L, 6_000L))

        assertEquals(
            PlaybackRestoreTarget(0, 0L),
            resolvePlaybackRestoreTarget(previous, next, "a", 500L, 0),
        )
    }

    @Test
    fun trimEndClampsPlayheadToLastPlayableMillisecond() {
        val previous = listOf(ClipPlaybackRange("a", 1_000L, 8_000L))
        val next = listOf(ClipPlaybackRange("a", 1_000L, 4_000L))

        assertEquals(
            PlaybackRestoreTarget(0, 2_999L),
            resolvePlaybackRestoreTarget(previous, next, "a", 6_000L, 0),
        )
    }

    @Test
    fun removedCurrentClipFallsBackToSelectedClipStart() {
        val previous = listOf(
            ClipPlaybackRange("a", 0L, 5_000L),
            ClipPlaybackRange("b", 0L, 5_000L),
        )
        val next = listOf(ClipPlaybackRange("b", 0L, 5_000L))

        assertEquals(
            PlaybackRestoreTarget(0, 0L),
            resolvePlaybackRestoreTarget(previous, next, "a", 2_000L, 0),
        )
    }

    @Test
    fun newlySelectedDuplicateOrImportedClipTakesPriorityOverOldPlayhead() {
        val previous = listOf(ClipPlaybackRange("a", 0L, 5_000L))
        val next = listOf(
            ClipPlaybackRange("a", 0L, 5_000L),
            ClipPlaybackRange("new-copy", 0L, 5_000L),
        )

        assertEquals(
            PlaybackRestoreTarget(1, 0L),
            resolvePlaybackRestoreTarget(
                previousClips = previous,
                nextClips = next,
                currentClipId = "a",
                currentClipPositionMs = 2_000L,
                fallbackClipIndex = 1,
                preferredClipId = "new-copy",
            ),
        )
    }

    @Test
    fun emptyNextPlaylistHasNoPlaybackRestoreTarget() {
        assertNull(
            resolvePlaybackRestoreTarget(
                previousClips = listOf(ClipPlaybackRange("a", 0L, 1_000L)),
                nextClips = emptyList(),
                currentClipId = "a",
                currentClipPositionMs = 500L,
                fallbackClipIndex = 0,
            ),
        )
    }


    @Test
    fun appendingToEmptyTimelineSelectsTheFirstClip() {
        assertEquals(
            TimelineAppendResult(listOf("first"), 0),
            appendTimelineItemAndSelect(emptyList(), "first"),
        )
    }

    @Test
    fun appendingToPopulatedTimelinePreservesOrderAndSelectsNewClip() {
        assertEquals(
            TimelineAppendResult(listOf("a", "b", "c"), 2),
            appendTimelineItemAndSelect(listOf("a", "b"), "c"),
        )
    }


    @Test
    fun restoresSavedPreviewByStableClipIdAfterReordering() {
        val clips = listOf(
            ClipPlaybackRange("second", 500L, 4_500L),
            ClipPlaybackRange("first", 0L, 3_000L),
        )
        assertEquals(
            PlaybackRestoreTarget(0, 1_250L),
            resolveSavedPlaybackTarget(clips, SavedPlaybackPosition("second", 1_750L)),
        )
    }

    @Test
    fun savedSourceTimeBeforeTrimmedStartClampsToClipStart() {
        assertEquals(
            PlaybackRestoreTarget(0, 0L),
            resolveSavedPlaybackTarget(
                listOf(ClipPlaybackRange("clip", 2_000L, 6_000L)),
                SavedPlaybackPosition("clip", 1_500L),
            ),
        )
    }

    @Test
    fun savedSourceTimeAfterTrimmedEndClampsToLastPlayableMillisecond() {
        assertEquals(
            PlaybackRestoreTarget(0, 1_999L),
            resolveSavedPlaybackTarget(
                listOf(ClipPlaybackRange("clip", 2_000L, 4_000L)),
                SavedPlaybackPosition("clip", 9_000L),
            ),
        )
    }

    @Test
    fun missingSavedClipFallsBackToRequestedSelection() {
        assertEquals(
            PlaybackRestoreTarget(1, 0L),
            resolveSavedPlaybackTarget(
                listOf(
                    ClipPlaybackRange("a", 0L, 1_000L),
                    ClipPlaybackRange("b", 0L, 2_000L),
                ),
                SavedPlaybackPosition("deleted", 500L),
                fallbackClipIndex = 1,
            ),
        )
    }

    @Test
    fun emptyProjectHasNoSavedPlaybackTarget() {
        assertNull(resolveSavedPlaybackTarget(emptyList(), SavedPlaybackPosition("clip", 500L)))
    }

}
