package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TimelineMathTest {
    @Test
    fun mapsMedia3ControllerPositionIntoProjectTimeline() {
        assertEquals(
            PlayerTimelinePosition(2, 1_250L, 4_250L),
            resolvePlayerTimelinePosition(listOf(1_000L, 2_000L, 3_000L), 2, 1_250L),
        )
    }

    @Test
    fun clampsMedia3ControllerPositionToClipWindow() {
        assertEquals(
            PlayerTimelinePosition(1, 2_000L, 3_000L),
            resolvePlayerTimelinePosition(listOf(1_000L, 2_000L), 1, Long.MAX_VALUE),
        )
        assertEquals(
            PlayerTimelinePosition(0, 0L, 0L),
            resolvePlayerTimelinePosition(listOf(1_000L), 0, -50L),
        )
    }

    @Test
    fun invalidMedia3PositionHasNoTimelineMapping() {
        assertNull(resolvePlayerTimelinePosition(emptyList(), 0, 0L))
        assertNull(resolvePlayerTimelinePosition(listOf(1_000L), -1, 0L))
        assertNull(resolvePlayerTimelinePosition(listOf(0L), 0, 0L))
    }

    @Test
    fun restoredFinalFrameCountsAsProjectEndForPlayRestart() {
        assertTrue(isTimelinePositionAtEnd(999L, 1_000L))
        assertTrue(isTimelinePositionAtEnd(1_000L, 1_000L))
        assertFalse(isTimelinePositionAtEnd(998L, 1_000L))
        assertFalse(isTimelinePositionAtEnd(0L, 0L))
    }

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
    fun playheadAtClipBoundaryStartsAtTheNextClipAfterItsGap() {
        assertEquals(
            106f,
            calculateTimelinePlayheadOffsetPx(
                clipDurationsMs = listOf(1_000L, 2_000L),
                clipWidthsPx = listOf(100f, 200f),
                playheadPositionMs = 1_000L,
                gapPx = 6f,
            ),
            0.001f,
        )
    }

    @Test
    fun playheadInsideAClipUsesItsProportionalPixelPosition() {
        assertEquals(
            50f,
            calculateTimelinePlayheadOffsetPx(
                listOf(1_000L, 2_000L),
                listOf(100f, 200f),
                500L,
                6f,
            ),
            0.001f,
        )
    }

    @Test
    fun playheadAtProjectEndStopsAtTheLastPlayableClipEnd() {
        assertEquals(
            306f,
            calculateTimelinePlayheadOffsetPx(
                listOf(1_000L, 2_000L),
                listOf(100f, 200f),
                3_000L,
                6f,
            ),
            0.001f,
        )
    }

    @Test
    fun zeroDurationItemsKeepTheirVisualSlotWithoutConsumingTimelineTime() {
        assertEquals(
            85f,
            calculateTimelinePlayheadOffsetPx(
                listOf(0L, 1_000L, 0L),
                listOf(80f, 120f, 90f),
                0L,
                5f,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            calculateTimelinePlayheadOffsetPx(listOf(0L, 0L), listOf(50f, 60f), 0L, 5f),
            0.001f,
        )
    }

    @Test
    fun duplicateInsertsASeparateItemAfterTheSelectedClipWithoutMutatingOriginal() {
        val original = listOf("A", "B", "C")
        val duplicate = duplicateTimelineItemAfter(original, 1) { it + " copy" }!!

        assertEquals(listOf("A", "B", "B copy", "C"), duplicate.items)
        assertEquals(2, duplicate.selectedIndex)
        assertEquals(listOf("A", "B", "C"), original)
        assertNull(duplicateTimelineItemAfter(original, -1) { it })
    }

    @Test
    fun timelinePixelOffsetMapsProportionallyInsideAClip() {
        assertEquals(
            TimelineSeekTarget(0, 500L),
            resolveTimelineSeekTargetAtOffsetPx(
                clipDurationsMs = listOf(1_000L, 2_000L),
                clipWidthsPx = listOf(100f, 200f),
                offsetPx = 50f,
                gapPx = 10f,
            ),
        )
    }

    @Test
    fun scrolledTimelineChildOffsetIsMappedOnceInContentCoordinates() {
        // A pointer 60 px into the viewport after a 100 px scroll reports
        // content x=160 px. Adding the scroll state again would incorrectly
        // seek to x=260 px (1,500 ms into the second clip instead of 500 ms).
        assertEquals(
            TimelineSeekTarget(1, 500L),
            resolveTimelineSeekTargetAtOffsetPx(
                clipDurationsMs = listOf(1_000L, 2_000L),
                clipWidthsPx = listOf(100f, 200f),
                offsetPx = 160f,
                gapPx = 10f,
            ),
        )
    }

    @Test
    fun timelinePixelOffsetInGapSnapsToNextClipStart() {
        assertEquals(
            TimelineSeekTarget(1, 0L),
            resolveTimelineSeekTargetAtOffsetPx(
                listOf(1_000L, 2_000L),
                listOf(100f, 200f),
                offsetPx = 105f,
                gapPx = 10f,
            ),
        )
    }

    @Test
    fun timelinePixelOffsetPastEndTargetsLastPlayableFrame() {
        assertEquals(
            TimelineSeekTarget(2, 1_999L),
            resolveTimelineSeekTargetAtOffsetPx(
                listOf(1_000L, 0L, 2_000L),
                listOf(100f, 80f, 200f),
                offsetPx = 500f,
                gapPx = 10f,
            ),
        )
    }

    @Test
    fun zeroDurationVisualSlotDoesNotConsumePlaybackTime() {
        assertEquals(
            TimelineSeekTarget(1, 0L),
            resolveTimelineSeekTargetAtOffsetPx(
                listOf(0L, 1_000L),
                listOf(80f, 120f),
                offsetPx = 40f,
                gapPx = 6f,
            ),
        )
    }

    @Test
    fun invalidTimelinePixelOffsetStillResolvesSafely() {
        assertEquals(
            TimelineSeekTarget(0, 0L),
            resolveTimelineSeekTargetAtOffsetPx(
                listOf(1_000L),
                listOf(Float.NaN),
                offsetPx = Float.NaN,
                gapPx = Float.POSITIVE_INFINITY,
            ),
        )
        assertNull(resolveTimelineSeekTargetAtOffsetPx(listOf(0L), listOf(100f), 50f, 6f))
    }

    @Test
    fun skipForwardCrossesIntoTheNextClip() {
        assertEquals(
            TimelineSeekTarget(1, 3_000L),
            resolveTimelineSeekTargetAfterDelta(
                listOf(4_000L, 5_000L),
                currentTimelinePositionMs = 2_000L,
                deltaMs = 5_000L,
            ),
        )
    }

    @Test
    fun skipBackwardCrossesIntoThePreviousClip() {
        assertEquals(
            TimelineSeekTarget(0, 1_000L),
            resolveTimelineSeekTargetAfterDelta(
                listOf(4_000L, 5_000L),
                currentTimelinePositionMs = 6_000L,
                deltaMs = -5_000L,
            ),
        )
    }

    @Test
    fun skipControlsClampAtBothProjectEnds() {
        assertEquals(
            TimelineSeekTarget(0, 0L),
            resolveTimelineSeekTargetAfterDelta(listOf(2_000L, 3_000L), 500L, Long.MIN_VALUE),
        )
        assertEquals(
            TimelineSeekTarget(1, 2_999L),
            resolveTimelineSeekTargetAfterDelta(listOf(2_000L, 3_000L), 4_500L, Long.MAX_VALUE),
        )
        assertNull(resolveTimelineSeekTargetAfterDelta(listOf(0L, -1L), 0L, 5_000L))
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
    fun splitAtPlaybackPositionConvertsClipRelativeTimeToSourceTime() {
        assertEquals(
            ClipRange(5_000L, 6_750L) to ClipRange(6_750L, 9_000L),
            splitClipRangeAtPlaybackPosition(5_000L, 9_000L, 1_750L),
        )
    }

    @Test
    fun splitAtPlaybackPositionRejectsTheClipEdges() {
        assertNull(splitClipRangeAtPlaybackPosition(5_000L, 9_000L, 0L))
        assertNull(splitClipRangeAtPlaybackPosition(5_000L, 9_000L, 4_000L))
        assertNull(splitClipRangeAtPlaybackPosition(5_000L, 9_000L, -1L))
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
    fun trimRequiresReadableSourceThatStillContainsSavedRange() {
        assertTrue(isClipRangeWithinSourceDuration(10_000L, 1_000L, 9_000L))
        assertTrue(isClipRangeWithinSourceDuration(9_000L, 1_000L, 9_000L))
        assertFalse(isClipRangeWithinSourceDuration(null, 1_000L, 9_000L))
        assertFalse(isClipRangeWithinSourceDuration(8_999L, 1_000L, 9_000L))
        assertFalse(isClipRangeWithinSourceDuration(10_000L, -1L, 9_000L))
        assertFalse(isClipRangeWithinSourceDuration(10_000L, 9_000L, 9_000L))
    }

    @Test
    fun trimRangeClampsBothHandlesToTheOriginalSourceDuration() {
        assertEquals(
            ClipRange(0L, 9_000L),
            clampTrimRange(
                sourceDurationMs = 9_000L,
                requestedStartMs = -500L,
                requestedEndMs = 12_000L,
            ),
        )
    }

    @Test
    fun trimRangePreservesIndependentInAndOutPoints() {
        assertEquals(
            ClipRange(1_250L, 7_750L),
            clampTrimRange(10_000L, 1_250L, 7_750L),
        )
    }

    @Test
    fun trimRangeRejectsSegmentsShorterThanOneHundredMilliseconds() {
        assertNull(clampTrimRange(10_000L, 2_000L, 2_099L))
        assertEquals(
            ClipRange(2_000L, 2_100L),
            clampTrimRange(10_000L, 2_000L, 2_100L),
        )
        assertNull(clampTrimRange(0L, 0L, 500L))
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

    @Test
    fun createsPortablePreviewCheckpointUsingAbsoluteSourceTime() {
        val clip = ClipPlaybackRange("clip", 2_000L, 7_000L)

        assertEquals(
            SavedPlaybackPosition("clip", 4_500L),
            createSavedPlaybackPosition(clip, 2_500L),
        )
        assertEquals(
            SavedPlaybackPosition("clip", 6_999L),
            createSavedPlaybackPosition(clip, 99_000L),
        )
        assertEquals(
            SavedPlaybackPosition("clip", 2_000L),
            createSavedPlaybackPosition(clip, -500L),
        )
    }

    @Test
    fun invalidClipCannotProducePortablePreviewCheckpoint() {
        assertNull(createSavedPlaybackPosition(null, 500L))
        assertNull(createSavedPlaybackPosition(ClipPlaybackRange("", 0L, 1_000L), 500L))
        assertNull(createSavedPlaybackPosition(ClipPlaybackRange("empty", 500L, 500L), 0L))
        assertNull(createSavedPlaybackPosition(ClipPlaybackRange("negative", -1L, 500L), 0L))
    }

    @Test
    fun portablePreviewCheckpointMustMatchOneClipAndItsSourceRange() {
        val clips = listOf(
            ClipPlaybackRange("first", 0L, 1_000L),
            ClipPlaybackRange("second", 2_000L, 5_000L),
        )
        val valid = SavedPlaybackPosition("second", 3_500L)

        assertEquals(valid, validateSavedPlaybackPosition(clips, valid))
        assertNull(validateSavedPlaybackPosition(clips, SavedPlaybackPosition("missing", 3_500L)))
        assertNull(validateSavedPlaybackPosition(clips, SavedPlaybackPosition("second", 5_000L)))
        assertNull(validateSavedPlaybackPosition(clips, SavedPlaybackPosition("second", -1L)))
        assertNull(
            validateSavedPlaybackPosition(
                clips + ClipPlaybackRange("second", 6_000L, 7_000L),
                valid,
            ),
        )
    }


    @Test
    fun timelineAutoFollowDoesNotScrollWhenPausedOrNotScrollable() {
        assertNull(calculateTimelineAutoFollowScrollTargetPx(0, 900f, 500, 1_000, false))
        assertNull(calculateTimelineAutoFollowScrollTargetPx(0, 900f, 500, 0, true))
        assertNull(calculateTimelineAutoFollowScrollTargetPx(0, Float.NaN, 500, 1_000, true))
    }

    @Test
    fun timelineAutoFollowLeavesManualScrollAloneWhilePlayheadIsInsideSafeBand() {
        assertNull(
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 100,
                markerPositionPx = 350f,
                viewportWidthPx = 500,
                maxScrollPx = 1_000,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun timelineAutoFollowMovesForwardWhenPlayheadLeavesRightSafeBoundary() {
        assertEquals(
            725,
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 100,
                markerPositionPx = 900f,
                viewportWidthPx = 500,
                maxScrollPx = 1_200,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun timelineAutoFollowMovesBackWhenPlayheadFallsLeftOfSafeBoundary() {
        assertEquals(
            425,
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 700,
                markerPositionPx = 600f,
                viewportWidthPx = 500,
                maxScrollPx = 1_200,
                isPlaying = true,
            ),
        )
    }

    @Test
    fun timelineAutoFollowClampsToScrollableContentBounds() {
        assertEquals(
            800,
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 0,
                markerPositionPx = 2_000f,
                viewportWidthPx = 500,
                maxScrollPx = 800,
                isPlaying = true,
            ),
        )
        assertEquals(
            0,
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 700,
                markerPositionPx = 0f,
                viewportWidthPx = 500,
                maxScrollPx = 800,
                isPlaying = true,
            ),
        )
    }


    @Test
    fun rulerTicksUseAbsoluteProjectTimeAcrossFractionalClipBoundaries() {
        val ticks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(1_500L, 2_500L),
            clipWidthsPx = listOf(150f, 250f),
            gapPx = 10f,
            majorIntervalMs = 2_000L,
        )

        assertEquals(listOf(0L, 1_000L, 2_000L, 3_000L, 4_000L), ticks.map { it.timeMs })
        assertEquals(listOf(0f, 100f, 210f, 310f, 410f), ticks.map { it.offsetPx })
        assertEquals(listOf(true, false, true, false, true), ticks.map { it.isMajor })
    }

    @Test
    fun rulerTicksKeepZeroDurationVisualSlotsWithoutAdvancingProjectTime() {
        val ticks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(0L, 1_000L),
            clipWidthsPx = listOf(40f, 100f),
            gapPx = 10f,
        )

        assertEquals(listOf(0L, 1_000L), ticks.map { it.timeMs })
        assertEquals(listOf(50f, 150f), ticks.map { it.offsetPx })
    }

    @Test
    fun rulerTicksIncludeProjectEndAndNeverExceedMaximum() {
        val cappedTicks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(2_000_000L),
            clipWidthsPx = listOf(320f),
            gapPx = 6f,
        )
        assertEquals(2_000, cappedTicks.size)
        assertEquals(2_000_000L, cappedTicks.last().timeMs)
        assertEquals(320f, cappedTicks.last().offsetPx, 0.001f)
        assertTrue(cappedTicks.last().isMajor)

        val shortProjectTicks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(500L),
            clipWidthsPx = listOf(100f),
            gapPx = 6f,
        )
        assertEquals(listOf(0L, 500L), shortProjectTicks.map { it.timeMs })
    }

    @Test
    fun rulerTicksRejectInvalidIntervalsAndBoundVeryLongProjects() {
        assertTrue(calculateTimelineRulerTicks(listOf(1_000L), listOf(100f), 6f, 0L).isEmpty())
        assertTrue(calculateTimelineRulerTicks(listOf(1_000L), listOf(100f), 6f, majorIntervalMs = 0L).isEmpty())

        val ticks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(Long.MAX_VALUE),
            clipWidthsPx = listOf(320f),
            gapPx = 6f,
        )
        assertTrue(ticks.size <= 2_000)
        assertEquals(0L, ticks.first().timeMs)
        assertEquals(Long.MAX_VALUE, ticks.last().timeMs)
    }

}
