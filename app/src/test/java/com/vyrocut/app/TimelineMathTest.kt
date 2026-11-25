package com.vyrocut.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TimelineMathTest {
    @Test
    fun userTimelineSeeksUseExactPrecisionWhilePlaylistRebuildsUseClosestSync() {
        assertEquals(PreviewSeekPrecision.EXACT, previewSeekPrecisionFor(PreviewSeekIntent.USER_EDIT))
        assertEquals(PreviewSeekPrecision.EXACT, previewSeekPrecisionFor(PreviewSeekIntent.AUDIO_RESYNC))
        assertEquals(PreviewSeekPrecision.CLOSEST_SYNC, previewSeekPrecisionFor(PreviewSeekIntent.PLAYLIST_REBUILD))
    }

    @Test
    fun resolvesFrameRateOnlyFromSelectedVideoTracks() {
        assertEquals(
            29.97f,
            resolveSelectedPreviewFrameRate(
                listOf(
                    PreviewTrackFrameRateCandidate(isVideo = false, isSelected = true, frameRate = 48_000f),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = false, frameRate = 30f),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = true, frameRate = -1f),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = true, frameRate = 29.97f),
                ),
            ),
        )
        assertNull(
            resolveSelectedPreviewFrameRate(
                listOf(
                    PreviewTrackFrameRateCandidate(isVideo = false, isSelected = true, frameRate = 30f),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = false, frameRate = 25f),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = true, frameRate = Float.NaN),
                    PreviewTrackFrameRateCandidate(isVideo = true, isSelected = true, frameRate = 300f),
                ),
            ),
        )
    }

    @Test
    fun stepsAdjacentFramesAtIntegerAndFractionalFrameRatesWithoutAccumulatedRounding() {
        assertEquals(
            42L,
            resolvePreviewFrameStepTargetMs(0L, 1_000L, 24f, PreviewFrameStepDirection.NEXT),
        )
        assertEquals(
            83L,
            resolvePreviewFrameStepTargetMs(42L, 1_000L, 24f, PreviewFrameStepDirection.NEXT),
        )
        assertEquals(
            0L,
            resolvePreviewFrameStepTargetMs(42L, 1_000L, 24f, PreviewFrameStepDirection.PREVIOUS),
        )
        assertEquals(
            67L,
            resolvePreviewFrameStepTargetMs(33L, 1_000L, 29.97f, PreviewFrameStepDirection.NEXT),
        )
        assertEquals(
            33L,
            resolvePreviewFrameStepTargetMs(17L, 1_000L, 59.94f, PreviewFrameStepDirection.NEXT),
        )
        assertEquals(
            42L,
            resolvePreviewFrameStepTargetMs(50L, 1_000L, 24f, PreviewFrameStepDirection.PREVIOUS),
        )
        assertEquals(
            83L,
            resolvePreviewFrameStepTargetMs(50L, 1_000L, 24f, PreviewFrameStepDirection.NEXT),
        )
    }

    @Test
    fun frameSteppingDoesNotCrossClipEdgesOrAcceptInvalidRates() {
        assertNull(resolvePreviewFrameStepTargetMs(0L, 1_000L, 24f, PreviewFrameStepDirection.PREVIOUS))
        assertNull(resolvePreviewFrameStepTargetMs(999L, 1_000L, 24f, PreviewFrameStepDirection.NEXT))
        assertNull(resolvePreviewFrameStepTargetMs(0L, 0L, 24f, PreviewFrameStepDirection.NEXT))
        assertNull(resolvePreviewFrameStepTargetMs(0L, 1_000L, 0f, PreviewFrameStepDirection.NEXT))
        assertNull(resolvePreviewFrameStepTargetMs(0L, 1_000L, Float.NaN, PreviewFrameStepDirection.NEXT))
        assertNull(resolvePreviewFrameStepTargetMs(0L, 1_000L, 300f, PreviewFrameStepDirection.NEXT))
    }

    @Test
    fun frameSteppingCrossesClipBoundariesUsingEachClipsOwnFrameRate() {
        val durations = listOf(1_000L, 1_000L)
        val frameRates = listOf(24f, 59.94f)

        assertEquals(
            PreviewFrameStepTarget(1, 0L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = frameRates,
                currentClipIndex = 0,
                currentPositionMs = 958L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertEquals(
            PreviewFrameStepTarget(0, 958L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = frameRates,
                currentClipIndex = 1,
                currentPositionMs = 0L,
                direction = PreviewFrameStepDirection.PREVIOUS,
            ),
        )
    }

    @Test
    fun frameSteppingUsesOriginalSourceFrameGridForNonAlignedTrimOffsets() {
        // The clip's initial seek presents its first source frame (125 ms),
        // so NEXT must move to the following frame (167 ms), not repeat it.
        assertEquals(
            67L,
            resolvePreviewFrameStepTargetMs(
                currentPositionMs = 0L,
                clipDurationMs = 1_000L,
                frameRate = 24f,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMs = 100L,
            ),
        )
        assertNull(
            resolvePreviewFrameStepTargetMs(
                currentPositionMs = 0L,
                clipDurationMs = 1_000L,
                frameRate = 24f,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMs = 100L,
            ),
        )
        assertEquals(
            67L,
            resolvePreviewFrameStepTargetMs(
                currentPositionMs = 25L,
                clipDurationMs = 1_000L,
                frameRate = 24f,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMs = 100L,
            ),
        )
        assertEquals(
            25L,
            resolvePreviewFrameStepTargetMs(
                currentPositionMs = 67L,
                clipDurationMs = 1_000L,
                frameRate = 24f,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMs = 100L,
            ),
        )
    }

    @Test
    fun frameSteppingAcrossTrimmedClipsUsesEachOriginalSourceOffset() {
        val durations = listOf(1_000L, 1_000L)
        val frameRates = listOf(24f, 30f)
        val sourceStarts = listOf(100L, 210L)

        assertEquals(
            PreviewFrameStepTarget(1, 23L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = frameRates,
                currentClipIndex = 0,
                currentPositionMs = 983L,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMsByClip = sourceStarts,
            ),
        )
        assertEquals(
            PreviewFrameStepTarget(0, 983L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = frameRates,
                currentClipIndex = 1,
                currentPositionMs = 0L,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMsByClip = sourceStarts,
            ),
        )
    }

    @Test
    fun frameSteppingRejectsTrimRangesWithoutAnySourceFrame() {
        assertNull(
            resolvePreviewFrameStepTargetMs(
                currentPositionMs = 0L,
                clipDurationMs = 10L,
                frameRate = 24f,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMs = 101L,
            ),
        )
        assertNull(
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = listOf(10L, 10L),
                frameRatesByClip = listOf(24f, 24f),
                currentClipIndex = 0,
                currentPositionMs = 0L,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMsByClip = listOf(101L, 202L),
            ),
        )
    }

    @Test
    fun frameSteppingStaysInsideClipUntilItsLastFrameAndRejectsUnknownAdjacentRates() {
        val durations = listOf(1_000L, 1_000L)
        assertEquals(
            PreviewFrameStepTarget(0, 83L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f, 59.94f),
                currentClipIndex = 0,
                currentPositionMs = 42L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertNull(
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f, null),
                currentClipIndex = 0,
                currentPositionMs = 958L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertNull(
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f),
                currentClipIndex = 0,
                currentPositionMs = 958L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertNull(
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f, 59.94f),
                currentClipIndex = 0,
                currentPositionMs = 0L,
                direction = PreviewFrameStepDirection.PREVIOUS,
            ),
        )
        // 958 ms is between frames at 59.94 fps, so the next frame is still
        // inside this one-second clip. The final frame is at approximately 984 ms.
        assertEquals(
            PreviewFrameStepTarget(1, 968L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f, 59.94f),
                currentClipIndex = 1,
                currentPositionMs = 958L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertNull(
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(24f, 59.94f),
                currentClipIndex = 1,
                currentPositionMs = 984L,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
    }

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
    fun batchedAutomaticVideoTransitionCallbacksDoNotForceAudioLaneReseeks() {
        assertEquals(
            false,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = true,
                mediaItemTransitionIsAutomatic = true,
            ),
        )
        assertEquals(
            false,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = true,
                mediaItemTransitionIsAutomatic = null,
            ),
        )
        assertEquals(
            false,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = null,
                mediaItemTransitionIsAutomatic = true,
            ),
        )
    }

    @Test
    fun anyExplicitVideoTimelineChangeForcesAudioLaneResynchronization() {
        assertEquals(
            true,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = false,
                mediaItemTransitionIsAutomatic = true,
            ),
        )
        assertEquals(
            true,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = true,
                mediaItemTransitionIsAutomatic = false,
            ),
        )
        assertEquals(
            true,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = false,
                mediaItemTransitionIsAutomatic = null,
            ),
        )
        assertEquals(
            true,
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = null,
                mediaItemTransitionIsAutomatic = false,
            ),
        )
    }

    @Test
    fun noMedia3TimelineCallbacksProduceNoAudioSyncDecision() {
        assertNull(
            shouldForceAudioLaneSeekForVideoEvents(
                positionDiscontinuityIsAutomatic = null,
                mediaItemTransitionIsAutomatic = null,
            ),
        )
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
    fun timelineAutoFollowYieldsToManualScrollOverrideWhilePlaying() {
        assertNull(
            calculateTimelineAutoFollowScrollTargetPx(
                currentScrollPx = 100,
                markerPositionPx = 900f,
                viewportWidthPx = 500,
                maxScrollPx = 1_200,
                isPlaying = true,
                isUserScrollOverride = true,
            ),
        )
    }

    @Test
    fun timelineAutoFollowOverrideRequiresHorizontalMovementBeyondTouchSlop() {
        assertTrue(shouldSuspendTimelineAutoFollowForDrag(deltaX = 24f, deltaY = 3f, touchSlopPx = 8f))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(deltaX = 8f, deltaY = 0f, touchSlopPx = 8f))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(deltaX = 4f, deltaY = 1f, touchSlopPx = 8f))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(deltaX = 3f, deltaY = 24f, touchSlopPx = 8f))
    }

    @Test
    fun timelineAutoFollowOverrideRejectsInvalidGestureMeasurements() {
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(Float.NaN, 0f, 8f))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(20f, Float.POSITIVE_INFINITY, 8f))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(20f, 0f, Float.NaN))
        assertFalse(shouldSuspendTimelineAutoFollowForDrag(20f, 0f, -1f))
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
        assertTrue(cappedTicks.size in 2..42)
        assertEquals(2_000_000L, cappedTicks.last().timeMs)
        assertEquals(320f, cappedTicks.last().offsetPx, 0.001f)
        assertTrue(cappedTicks.last().isMajor)
        assertTrue(
            cappedTicks.zipWithNext().all { (previous, next) ->
                next.offsetPx - previous.offsetPx >= TIMELINE_RULER_MIN_TICK_SPACING_DP - 0.001f
            },
        )

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
        assertTrue(ticks.size in 2..42)
        assertEquals(0L, ticks.first().timeMs)
        assertEquals(Long.MAX_VALUE, ticks.last().timeMs)
        assertTrue(
            ticks.zipWithNext().all { (previous, next) ->
                next.offsetPx - previous.offsetPx >= TIMELINE_RULER_MIN_TICK_SPACING_DP - 0.01f
            },
        )
    }


    @Test
    fun rulerTicksThinCompressedClipsAndPreserveMajorMarksAndProjectEnd() {
        val ticks = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(60_000L),
            clipWidthsPx = listOf(320f),
            gapPx = 6f,
        )

        assertEquals(0L, ticks.first().timeMs)
        assertEquals(60_000L, ticks.last().timeMs)
        assertTrue(ticks.size < 30)
        assertTrue(ticks.any { it.timeMs == 5_000L && it.isMajor })
        assertTrue(ticks.any { it.timeMs == 10_000L && it.isMajor })
        assertTrue(
            ticks.zipWithNext().all { (previous, next) ->
                next.offsetPx - previous.offsetPx >= TIMELINE_RULER_MIN_TICK_SPACING_DP - 0.001f
            },
        )
    }

    @Test
    fun rulerTickDensityFollowsRenderedWidthRatherThanOnlyProjectDuration() {
        val compressed = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(60_000L),
            clipWidthsPx = listOf(320f),
            gapPx = 6f,
        )
        val expanded = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(60_000L),
            clipWidthsPx = listOf(1_600f),
            gapPx = 6f,
        )

        assertTrue(compressed.size < expanded.size)
        assertEquals(60_000L, compressed.last().timeMs)
        assertEquals(60_000L, expanded.last().timeMs)
    }

    @Test
    fun invalidMinimumRulerTickSpacingLeavesTheBoundedTickSetUnfiltered() {
        val unfiltered = calculateTimelineRulerTicks(
            clipDurationsMs = listOf(60_000L),
            clipWidthsPx = listOf(320f),
            gapPx = 6f,
            minimumTickSpacingDp = 0f,
        )

        assertEquals(
            unfiltered,
            calculateTimelineRulerTicks(
                clipDurationsMs = listOf(60_000L),
                clipWidthsPx = listOf(320f),
                gapPx = 6f,
                minimumTickSpacingDp = Float.NaN,
            ),
        )
        assertEquals(
            unfiltered,
            calculateTimelineRulerTicks(
                clipDurationsMs = listOf(60_000L),
                clipWidthsPx = listOf(320f),
                gapPx = 6f,
                minimumTickSpacingDp = -1f,
            ),
        )
    }

    @Test
    fun rulerLabelsSkipCrowdedMajorTicksAndKeepTheEndWhenSpaceAllows() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 30f, true),
            TimelineRulerTick(10_000L, 60f, true),
            TimelineRulerTick(15_000L, 90f, true),
            TimelineRulerTick(20_000L, 120f, true),
        )

        assertEquals(
            listOf(0L, 10_000L, 20_000L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 37,
                contentWidthDp = 160f,
            ).map { it.timeMs },
        )
    }

    @Test
    fun rulerLabelsKeepOnlyOneLabelWhenTheWholeProjectIsCompressed() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 1f, true),
            TimelineRulerTick(10_000L, 2f, true),
        )

        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(ticks, labelWidthDp = 38, contentWidthDp = 100f).map { it.timeMs },
        )
    }

    @Test
    fun rulerLabelSelectionIgnoresMinorAndNonFiniteTicks() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(1_000L, 20f, false),
            TimelineRulerTick(5_000L, Float.NaN, true),
            TimelineRulerTick(10_000L, 60f, true),
            TimelineRulerTick(15_000L, Float.POSITIVE_INFINITY, true),
        )

        assertEquals(
            listOf(0L, 10_000L),
            selectTimelineRulerLabelTicks(ticks, labelWidthDp = 38, contentWidthDp = 160f).map { it.timeMs },
        )
    }

    @Test
    fun invalidRulerLabelSpacingUsesTheSharedGapWithoutAllowingCollisions() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 39f, true),
            TimelineRulerTick(10_000L, 58f, true),
        )

        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 38,
                contentWidthDp = 160f,
                minimumGapDp = Float.NaN,
            ).map { it.timeMs },
        )
        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 38,
                contentWidthDp = 160f,
                minimumGapDp = -1f,
            ).map { it.timeMs },
        )
        assertEquals(
            listOf(0L, 10_000L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 38,
                contentWidthDp = 160f,
                minimumGapDp = 0f,
            ).map { it.timeMs },
        )
    }

    @Test
    fun invalidRulerLabelWidthKeepsOnlyTheFirstMajorLabel() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 60f, true),
            TimelineRulerTick(10_000L, 120f, true),
        )

        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 0,
                contentWidthDp = 160f,
            ).map { it.timeMs },
        )
    }

    @Test
    fun invalidRulerContentWidthFallsBackToOneSafeLabel() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 60f, true),
            TimelineRulerTick(10_000L, 120f, true),
        )

        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 38,
                contentWidthDp = Float.NaN,
            ).map { it.timeMs },
        )
    }

    @Test
    fun rulerLabelSelectionAccountsForStartAndEndClampingBeforeCheckingCollisions() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 39f, true),
            TimelineRulerTick(10_000L, 78f, true),
            TimelineRulerTick(15_000L, 121f, true),
            TimelineRulerTick(20_000L, 160f, true),
        )

        val selected = selectTimelineRulerLabelTicks(
            ticks = ticks,
            labelWidthDp = 37,
            contentWidthDp = 160f,
        )

        assertEquals(listOf(0L, 10_000L, 15_000L), selected.map { it.timeMs })
        val renderedLefts = selected.map {
            timelineRulerLabelPlacement(it.offsetPx, 37, 160f).offsetDp
        }
        assertTrue(renderedLefts.zipWithNext().all { (left, next) -> next - left >= 39f })
    }

    @Test
    fun rulerLabelSelectionRetainsTheFinalEdgeLabelWhenItsRenderedBoundsFit() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 39f, true),
            TimelineRulerTick(10_000L, 78f, true),
            TimelineRulerTick(15_000L, 121f, true),
            TimelineRulerTick(20_000L, 200f, true),
        )

        assertEquals(
            listOf(0L, 10_000L, 15_000L, 20_000L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 37,
                contentWidthDp = 200f,
            ).map { it.timeMs },
        )
    }

    @Test
    fun rulerLabelSelectionReplacesCrowdedIntermediateLabelToKeepProjectEnd() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 100f, true),
            TimelineRulerTick(10_000L, 150f, true),
        )

        val selected = selectTimelineRulerLabelTicks(
            ticks = ticks,
            labelWidthDp = 60,
            contentWidthDp = 200f,
            minimumGapDp = 2f,
        )

        assertEquals(listOf(0L, 10_000L), selected.map { it.timeMs })
        val renderedLefts = selected.map {
            timelineRulerLabelPlacement(it.offsetPx, 60, 200f).offsetDp
        }
        assertTrue(renderedLefts.zipWithNext().all { (left, next) -> next - left >= 62f })
    }

    @Test
    fun rulerLabelSelectionKeepsFirstWhenProjectEndCannotFitWithoutOverlap() {
        val ticks = listOf(
            TimelineRulerTick(0L, 0f, true),
            TimelineRulerTick(5_000L, 50f, true),
            TimelineRulerTick(10_000L, 80f, true),
        )

        assertEquals(
            listOf(0L),
            selectTimelineRulerLabelTicks(
                ticks = ticks,
                labelWidthDp = 60,
                contentWidthDp = 200f,
                minimumGapDp = 2f,
            ).map { it.timeMs },
        )
    }

    @Test
    fun playbackProgressUsesFrameAlignedCadenceWhenPtsIndexIsAvailable() {
        assertEquals(33L, playbackProgressUpdateIntervalMs(isPlaying = true))
        assertEquals(16L, playbackProgressUpdateIntervalMs(isPlaying = true, hasPresentationTimestampIndex = true))
        assertEquals(100L, playbackProgressUpdateIntervalMs(isPlaying = false))
        assertEquals(100L, playbackProgressUpdateIntervalMs(isPlaying = false, hasPresentationTimestampIndex = true))
    }

    @Test
    fun previewCheckpointPersistsImmediatelyAndAtTheIntervalBoundary() {
        assertTrue(shouldPersistPreviewCheckpoint(nowElapsedMs = 1_500L, lastPersistedElapsedMs = null))
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = 1_999L, lastPersistedElapsedMs = 0L))
        assertTrue(shouldPersistPreviewCheckpoint(nowElapsedMs = 2_000L, lastPersistedElapsedMs = 0L))
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = 4_499L, lastPersistedElapsedMs = 2_500L))
        assertTrue(shouldPersistPreviewCheckpoint(nowElapsedMs = 4_500L, lastPersistedElapsedMs = 2_500L))
    }

    @Test
    fun previewCheckpointRejectsClockRegressionAndInvalidIntervals() {
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = 900L, lastPersistedElapsedMs = 1_000L))
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = -1L, lastPersistedElapsedMs = null))
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = 1_000L, lastPersistedElapsedMs = null, minimumIntervalMs = 0L))
        assertFalse(shouldPersistPreviewCheckpoint(nowElapsedMs = 1_000L, lastPersistedElapsedMs = -1L))
    }


    @Test
    fun variableFrameRateSteppingUsesActualIrregularPresentationTimestamps() {
        val timestampsUs = longArrayOf(0L, 33_333L, 66_667L, 100_000L, 166_667L)
        assertTrue(isValidVideoPresentationTimestampsUs(timestampsUs))
        assertEquals(
            100L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 67L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertEquals(
            33L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 67L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.PREVIOUS,
            ),
        )
        assertEquals(
            167L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 120L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.NEXT,
            ),
        )
        assertEquals(
            100L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 120L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.PREVIOUS,
            ),
        )
    }

    @Test
    fun continuousPlayheadUsesLatestPresentationTimestampNotAfterPlayerClock() {
        val timestampsUs = longArrayOf(
            1_000_000L, 1_033_333L, 1_066_667L, 1_100_000L, 1_166_667L,
        )
        assertEquals(
            0L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 0L,
                sourceStartMs = 1_000L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
            ),
        )
        assertEquals(
            33L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 34L,
                sourceStartMs = 1_000L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
            ),
        )
        assertEquals(
            67L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 80L,
                sourceStartMs = 1_000L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
            ),
        )
        // Past the last in-trim PTS, keep the marker on the last playable frame.
        assertEquals(
            167L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 199L,
                sourceStartMs = 1_000L,
                clipDurationMs = 200L,
                timestampsUs = timestampsUs,
            ),
        )
    }

    @Test
    fun continuousPlayheadHonoursNonAlignedTrimAndRejectsInvalidPositions() {
        val timestampsUs = longArrayOf(
            1_000_000L, 1_033_333L, 1_066_667L, 1_100_000L, 1_166_667L, 1_200_000L,
        )
        assertEquals(
            17L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 0L,
                sourceStartMs = 1_050L,
                clipDurationMs = 120L,
                timestampsUs = timestampsUs,
            ),
        )
        assertEquals(
            50L,
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 50L,
                sourceStartMs = 1_050L,
                clipDurationMs = 120L,
                timestampsUs = timestampsUs,
            ),
        )
        assertNull(
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = -1L,
                sourceStartMs = 1_050L,
                clipDurationMs = 120L,
                timestampsUs = timestampsUs,
            ),
        )
        assertNull(
            resolvePresentedPreviewFrameOffsetFromTimestampsMs(
                currentPositionMs = 0L,
                sourceStartMs = 1_050L,
                clipDurationMs = 120L,
                timestampsUs = longArrayOf(10L, 10L),
            ),
        )
    }

    @Test
    fun variableFrameRateSteppingHonoursTrimBoundsAndFirstPresentedFrame() {
        val timestampsUs = longArrayOf(
            0L, 33_333L, 66_667L, 100_000L, 166_667L, 200_000L,
        )
        assertEquals(
            50L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 0L,
                clipDurationMs = 130L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMs = 50L,
            ),
        )
        assertEquals(
            null,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 0L,
                clipDurationMs = 130L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMs = 50L,
            ),
        )
        assertEquals(
            50L,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 117L,
                clipDurationMs = 130L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMs = 50L,
            ),
        )
        assertEquals(
            null,
            resolvePreviewFrameStepTargetFromTimestampsMs(
                currentPositionMs = 117L,
                clipDurationMs = 130L,
                timestampsUs = timestampsUs,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMs = 50L,
            ),
        )
    }

    @Test
    fun variableFrameRateSteppingCrossesClipsUsingEachTrimmedPtsIndex() {
        val durations = listOf(100L, 100L)
        val timestampsByClip = listOf(
            longArrayOf(0L, 33_333L, 66_667L),
            longArrayOf(5_000L, 25_000L, 70_000L),
        )
        assertEquals(
            PreviewFrameStepTarget(1, 5L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(null, null),
                currentClipIndex = 0,
                currentPositionMs = 67L,
                direction = PreviewFrameStepDirection.NEXT,
                sourceStartMsByClip = listOf(0L, 0L),
                presentationTimestampsUsByClip = timestampsByClip,
            ),
        )
        assertEquals(
            PreviewFrameStepTarget(0, 67L),
            resolvePreviewFrameStepAcrossClips(
                clipDurationsMs = durations,
                frameRatesByClip = listOf(null, null),
                currentClipIndex = 1,
                currentPositionMs = 0L,
                direction = PreviewFrameStepDirection.PREVIOUS,
                sourceStartMsByClip = listOf(0L, 0L),
                presentationTimestampsUsByClip = timestampsByClip,
            ),
        )
    }

    @Test
    fun presentationTimestampIndexesRejectInvalidOrUnboundedSequences() {
        assertFalse(isValidVideoPresentationTimestampsUs(null))
        assertFalse(isValidVideoPresentationTimestampsUs(longArrayOf()))
        assertFalse(isValidVideoPresentationTimestampsUs(longArrayOf(0L, 33_333L, 33_333L)))
        assertFalse(isValidVideoPresentationTimestampsUs(longArrayOf(0L, 66_667L, 33_333L)))
        assertFalse(isValidVideoPresentationTimestampsUs(longArrayOf(-1L, 33_333L)))
        assertFalse(
            isValidVideoPresentationTimestampsUs(
                LongArray(MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP + 1) { it.toLong() },
            ),
        )
    }

    @Test
    fun presentationTimestampSamplesNormalizeDecodeOrderIntoUniquePresentationOrder() {
        val decodeOrderSamples = longArrayOf(
            0L, 100_000L, 33_333L, 66_667L, 133_333L, 100_000L, 166_667L,
        )
        val normalized = normalizeVideoPresentationTimestampsUs(decodeOrderSamples)

        assertArrayEquals(
            longArrayOf(0L, 33_333L, 66_667L, 100_000L, 133_333L, 166_667L),
            normalized,
        )
        assertTrue(isValidVideoPresentationTimestampsUs(normalized))

        // Normalization owns its result; later extractor-buffer reuse cannot mutate the index.
        decodeOrderSamples[0] = -1L
        assertArrayEquals(
            longArrayOf(0L, 33_333L, 66_667L, 100_000L, 133_333L, 166_667L),
            normalized,
        )
    }

    @Test
    fun presentationTimestampNormalizationRejectsMissingNegativeAndOversizedSamples() {
        assertNull(normalizeVideoPresentationTimestampsUs(null))
        assertNull(normalizeVideoPresentationTimestampsUs(longArrayOf()))
        assertNull(normalizeVideoPresentationTimestampsUs(longArrayOf(0L, -1L, 33_333L)))
        assertNull(
            normalizeVideoPresentationTimestampsUs(
                LongArray(MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP + 1) { it.toLong() },
            ),
        )
    }

    @Test
    fun presentationTimestampScanWaitsForTwoBoundarySyncsAndDecodeOrderTail() {
        var state = VideoPtsScanLookaheadState()
        state = nextVideoPtsScanLookaheadState(state, 120_000L, 100_000L, false)
        assertEquals(VideoPtsScanLookaheadState(), state)
        state = nextVideoPtsScanLookaheadState(state, 90_000L, 100_000L, true)
        assertEquals(0, state.endSyncSampleCount)
        state = nextVideoPtsScanLookaheadState(state, 110_000L, 100_000L, true)
        assertEquals(1, state.endSyncSampleCount)

        // A reordered B-frame can still carry an in-trim PTS after the first boundary sync.
        state = nextVideoPtsScanLookaheadState(state, 95_000L, 100_000L, false)
        assertEquals(0, state.samplesAfterLastEndSync)

        state = nextVideoPtsScanLookaheadState(state, 140_000L, 100_000L, true)
        assertEquals(VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT, state.endSyncSampleCount)
        assertFalse(shouldStopVideoPtsScan(state))

        repeat(VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES - 1) {
            state = nextVideoPtsScanLookaheadState(state, 96_000L, 100_000L, false)
        }
        assertEquals(VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES - 1, state.samplesAfterLastEndSync)
        assertFalse(shouldStopVideoPtsScan(state))

        state = nextVideoPtsScanLookaheadState(state, 97_000L, 100_000L, false)
        assertTrue(shouldStopVideoPtsScan(state))
    }

    @Test
    fun laterBoundarySyncRestartsTheBoundedDecodeOrderTail() {
        var state = VideoPtsScanLookaheadState(
            endSyncSampleCount = VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT,
            samplesAfterLastEndSync = 12,
        )
        state = nextVideoPtsScanLookaheadState(state, 160_000L, 100_000L, true)
        assertEquals(VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT, state.endSyncSampleCount)
        assertEquals(0, state.samplesAfterLastEndSync)
        assertFalse(shouldStopVideoPtsScan(state))

        repeat(VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES) {
            state = nextVideoPtsScanLookaheadState(state, 99_000L, 100_000L, false)
        }
        assertTrue(shouldStopVideoPtsScan(state))
    }

    @Test
    fun presentationTimestampCacheIsBoundedLruAndRetainsUnsupportedSources() {
        val first = VideoFrameTimestampCacheKey("content://video/1", 0L, 1_000L)
        val second = VideoFrameTimestampCacheKey("content://video/2", 0L, 1_000L)
        val third = VideoFrameTimestampCacheKey("content://video/3", 0L, 1_000L)
        val fourth = VideoFrameTimestampCacheKey("content://video/4", 0L, 1_000L)
        var cache = emptyMap<VideoFrameTimestampCacheKey, LongArray?>()

        cache = cacheVideoFrameTimestampIndex(cache, first, longArrayOf(0L, 40_000L), maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, second, null, maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, third, longArrayOf(0L, 33_333L), maxEntries = 3)
        cache = touchVideoFrameTimestampIndex(cache, first)
        cache = cacheVideoFrameTimestampIndex(cache, fourth, null, maxEntries = 3)

        assertEquals(listOf(third, first, fourth), cache.keys.toList())
        assertTrue(cache.containsKey(fourth))
        assertEquals(null, cache[fourth])
        assertFalse(cache.containsKey(second))
    }

    @Test
    fun cachingNewSourceRevisionReplacesOnlyTheSameTrim() {
        val oldRevision = VideoFrameTimestampCacheKey(
            "content://video/shared", 0L, 1_000L, sourceRevision = "size:100:modified:1",
        )
        val otherTrim = VideoFrameTimestampCacheKey(
            "content://video/shared", 1_000L, 2_000L, sourceRevision = "size:100:modified:1",
        )
        val otherSource = VideoFrameTimestampCacheKey(
            "content://video/other", 0L, 1_000L, sourceRevision = "size:100:modified:1",
        )
        val newRevision = oldRevision.copy(sourceRevision = "size:120:modified:2")
        var cache = emptyMap<VideoFrameTimestampCacheKey, LongArray?>()

        cache = cacheVideoFrameTimestampIndex(cache, oldRevision, longArrayOf(0L, 33_333L), maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, otherTrim, longArrayOf(1_000_000L, 1_033_333L), maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, otherSource, longArrayOf(0L, 40_000L), maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, newRevision, longArrayOf(0L, 34_000L), maxEntries = 3)

        assertFalse(cache.containsKey(oldRevision))
        assertTrue(cache.containsKey(newRevision))
        assertTrue(cache.containsKey(otherTrim))
        assertTrue(cache.containsKey(otherSource))
        assertEquals(listOf(otherTrim, otherSource, newRevision), cache.keys.toList())
    }

    @Test
    fun touchingCachedSourceRevisionPrunesSupersededEntriesAndMissingTouchIsNoOp() {
        val oldRevision = VideoFrameTimestampCacheKey(
            "content://video/shared", 0L, 1_000L, sourceRevision = "revision-old",
        )
        val otherSource = VideoFrameTimestampCacheKey("content://video/other", 0L, 1_000L)
        val currentRevision = oldRevision.copy(sourceRevision = "revision-current")
        val missingRevision = oldRevision.copy(sourceRevision = "revision-missing")
        var cache = emptyMap<VideoFrameTimestampCacheKey, LongArray?>()

        cache = cacheVideoFrameTimestampIndex(cache, oldRevision, longArrayOf(0L, 33_333L))
        cache = cacheVideoFrameTimestampIndex(cache, otherSource, longArrayOf(0L, 40_000L))
        cache = cacheVideoFrameTimestampIndex(cache, currentRevision, null)

        val touched = touchVideoFrameTimestampIndex(cache, currentRevision)
        assertFalse(touched.containsKey(oldRevision))
        assertTrue(touched.containsKey(currentRevision))
        assertNull(touched[currentRevision])
        assertEquals(listOf(otherSource, currentRevision), touched.keys.toList())

        val unchanged = touchVideoFrameTimestampIndex(touched, missingRevision)
        assertTrue(unchanged === touched)
    }

    @Test
    fun videoFrameTimestampRequestKeyIncludesUriTrimAndSourceRevision() {
        val original = VideoFrameTimestampCacheKey(
            uri = "content://media/video:one",
            sourceStartMs = 125L,
            sourceEndMs = 4_000L,
            sourceRevision = "size:120:modified:42",
        )

        assertEquals(videoFrameTimestampRequestKey(original), videoFrameTimestampRequestKey(original.copy()))
        assertFalse(videoFrameTimestampRequestKey(original) == videoFrameTimestampRequestKey(original.copy(sourceStartMs = 126L)))
        assertFalse(videoFrameTimestampRequestKey(original) == videoFrameTimestampRequestKey(original.copy(sourceEndMs = 4_001L)))
        assertFalse(videoFrameTimestampRequestKey(original) == videoFrameTimestampRequestKey(original.copy(sourceRevision = "size:121:modified:42")))
        assertFalse(videoFrameTimestampRequestKey(original) == videoFrameTimestampRequestKey(original.copy(uri = "content://media/video:two")))
        assertFalse(
            videoFrameTimestampRequestKey(original.copy(sourceRevision = null)) ==
                videoFrameTimestampRequestKey(original.copy(sourceRevision = "unversioned")),
        )
    }

    @Test
    fun presentationTimestampCacheOwnsSnapshotOfMutableDecoderArray() {
        val key = VideoFrameTimestampCacheKey("content://video/mutable", 0L, 1_000L)
        val decoderOutput = longArrayOf(0L, 33_333L, 66_667L)
        val cache = cacheVideoFrameTimestampIndex(emptyMap(), key, decoderOutput)
        val cached = cache[key]

        assertTrue(cached != null)
        assertFalse(cached === decoderOutput)
        assertTrue(cached!!.contentEquals(longArrayOf(0L, 33_333L, 66_667L)))

        // Reusing or clearing the caller-owned source array must not mutate the cached PTS index.
        decoderOutput[1] = 40_000L
        decoderOutput[2] = -1L
        assertTrue(cached.contentEquals(longArrayOf(0L, 33_333L, 66_667L)))
        assertTrue(isValidVideoPresentationTimestampsUs(cached))
    }

    @Test
    fun touchingUnsupportedTimestampIndexRefreshesRecencyWithoutAddingMissingKeys() {
        val first = VideoFrameTimestampCacheKey("content://video/1", 0L, 1_000L)
        val unsupported = VideoFrameTimestampCacheKey("content://video/2", 0L, 1_000L)
        val third = VideoFrameTimestampCacheKey("content://video/3", 0L, 1_000L)
        val missing = VideoFrameTimestampCacheKey("content://video/missing", 0L, 1_000L)
        var cache = emptyMap<VideoFrameTimestampCacheKey, LongArray?>()

        cache = cacheVideoFrameTimestampIndex(cache, first, longArrayOf(0L, 40_000L), maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, unsupported, null, maxEntries = 3)
        cache = cacheVideoFrameTimestampIndex(cache, third, longArrayOf(0L, 33_333L), maxEntries = 3)

        val touched = touchVideoFrameTimestampIndex(cache, unsupported)
        assertEquals(listOf(first, third, unsupported), touched.keys.toList())
        assertTrue(touched.containsKey(unsupported))
        assertNull(touched[unsupported])

        val unchanged = touchVideoFrameTimestampIndex(touched, missing)
        assertTrue(unchanged === touched)
        assertEquals(touched.keys.toList(), unchanged.keys.toList())
    }

    @Test
    fun presentationTimestampCacheKeySeparatesRevisionsForTheSameSourceRange() {
        val original = VideoFrameTimestampCacheKey(
            uri = "content://media/video/1",
            sourceStartMs = 1_000L,
            sourceEndMs = 6_000L,
            sourceRevision = "size=1024;modified=2000",
        )
        val unchanged = original.copy()
        val replacedBySize = original.copy(sourceRevision = "size=2048;modified=2000")
        val replacedByTimestamp = original.copy(sourceRevision = "size=1024;modified=3000")
        val unavailableRevision = original.copy(sourceRevision = null)

        assertEquals(original, unchanged)
        assertTrue(original != replacedBySize)
        assertTrue(original != replacedByTimestamp)
        assertTrue(original != unavailableRevision)
    }


}
