package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTracksTest {
    private fun clip(
        id: String,
        type: AudioTrackType = AudioTrackType.MUSIC,
        startMs: Long = 0L,
        sourceStartMs: Long = 0L,
        sourceEndMs: Long = 1_000L,
        sourceDurationMs: Long = sourceEndMs,
        volume: Float = 1f,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
    ) = AudioTrackClip(
        id = id,
        type = type,
        uri = "content://media/$id",
        name = "$id.wav",
        startMs = startMs,
        sourceStartMs = sourceStartMs,
        sourceEndMs = sourceEndMs,
        sourceDurationMs = sourceDurationMs,
        volume = volume,
        fadeInMs = fadeInMs,
        fadeOutMs = fadeOutMs,
    )

    @Test
    fun separateLanesMayOverlapButSameLaneMayNot() {
        val music = clip("music", AudioTrackType.MUSIC, 0L, sourceEndMs = 5_000L)
        val voice = clip("voice", AudioTrackType.VOICEOVER, 1_000L, sourceEndMs = 2_000L)
        assertTrue(validateAudioTrackClips(listOf(music, voice), 10_000L))
        assertFalse(
            validateAudioTrackClips(
                listOf(music, clip("music-2", AudioTrackType.MUSIC, 4_000L, sourceEndMs = 2_000L)),
                10_000L,
            ),
        )
    }

    @Test
    fun rejectsInvalidRangesVolumeFadesAndDuplicateIds() {
        assertFalse(validateAudioTrackClips(listOf(clip("bad", startMs = -1L)), 10_000L))
        assertFalse(validateAudioTrackClips(listOf(clip("bad", volume = 1.1f)), 10_000L))
        assertFalse(validateAudioTrackClips(listOf(clip("bad", fadeInMs = -1L)), 10_000L))
        val duplicate = clip("same")
        assertFalse(validateAudioTrackClips(listOf(duplicate, duplicate.copy(type = AudioTrackType.SFX)), 10_000L))
    }

    @Test
    fun insertionFindsFirstAvailableGapWithoutOverlappingLaneItems() {
        val existing = listOf(
            clip("first", startMs = 0L, sourceEndMs = 2_000L),
            clip("second", startMs = 5_000L, sourceEndMs = 2_000L),
        )
        assertEquals(
            2_000L,
            resolveAudioTrackInsertionStartMs(existing, AudioTrackType.MUSIC, 1_000L, 2_000L, 10_000L),
        )
        assertEquals(
            7_000L,
            resolveAudioTrackInsertionStartMs(existing, AudioTrackType.MUSIC, 6_000L, 2_000L, 10_000L),
        )
        assertNull(resolveAudioTrackInsertionStartMs(existing, AudioTrackType.MUSIC, 9_000L, 2_000L, 10_000L))
    }

    @Test
    fun trimCanExpandWithinOriginalSourceAndPreservesOtherAudioSettings() {
        val original = clip(
            "music",
            startMs = 1_000L,
            sourceStartMs = 1_000L,
            sourceEndMs = 3_000L,
            sourceDurationMs = 5_000L,
            volume = 0.6f,
            fadeInMs = 250L,
            fadeOutMs = 300L,
        )
        val trimmed = trimAudioTrackClip(listOf(original), "music", 0L, 4_000L, 10_000L)

        assertEquals(0L, trimmed?.single()?.sourceStartMs)
        assertEquals(4_000L, trimmed?.single()?.sourceEndMs)
        assertEquals(5_000L, trimmed?.single()?.sourceDurationMs)
        assertEquals(0.6f, trimmed?.single()?.volume ?: 0f, 0.0001f)
        assertEquals(250L, trimmed?.single()?.fadeInMs)
        assertEquals(300L, trimmed?.single()?.fadeOutMs)
        assertEquals(1_000L, original.sourceStartMs)
        assertEquals(3_000L, original.sourceEndMs)
    }

    @Test
    fun trimRejectsTooShortOutOfSourceAndSameLaneOverlap() {
        val first = clip("first", startMs = 0L, sourceEndMs = 2_000L, sourceDurationMs = 5_000L)
        val second = clip("second", startMs = 3_000L, sourceEndMs = 1_000L, sourceDurationMs = 1_000L)
        assertNull(trimAudioTrackClip(listOf(first), "first", 0L, 99L, 10_000L))
        assertNull(trimAudioTrackClip(listOf(first), "first", 0L, 6_000L, 10_000L))
        assertNull(trimAudioTrackClip(listOf(first, second), "first", 0L, 4_000L, 10_000L))
    }

    @Test
    fun moveFindsNextFreeSlotAndPreservesSourceRange() {
        val selected = clip("selected", startMs = 0L, sourceEndMs = 2_000L, sourceDurationMs = 8_000L)
        val blocker = clip("blocker", startMs = 3_000L, sourceEndMs = 2_000L)
        val moved = moveAudioTrackClip(listOf(selected, blocker), "selected", 2_500L, 10_000L)

        assertEquals(5_000L, moved?.first { it.id == "selected" }?.startMs)
        assertEquals(selected.sourceStartMs, moved?.first { it.id == "selected" }?.sourceStartMs)
        assertEquals(selected.sourceEndMs, moved?.first { it.id == "selected" }?.sourceEndMs)
        assertEquals(0L, selected.startMs)
    }

    @Test
    fun moveRejectsUnknownClipAndWhenNoFreeProjectTimeRemains() {
        val selected = clip("selected", startMs = 0L, sourceEndMs = 2_000L)
        val blocker = clip("blocker", startMs = 2_000L, sourceEndMs = 3_000L)
        assertNull(moveAudioTrackClip(listOf(selected, blocker), "missing", 5_000L, 5_000L))
        assertNull(moveAudioTrackClip(listOf(selected, blocker), "selected", 4_000L, 5_000L))
    }

    @Test
    fun timelineSegmentsFillLeadingInternalAndTrailingGaps() {
        val clips = listOf(
            clip("one", startMs = 2_000L, sourceEndMs = 1_000L),
            clip("two", startMs = 5_000L, sourceEndMs = 1_000L),
        )
        val segments = buildAudioTrackTimelineSegments(clips, AudioTrackType.MUSIC, 8_000L)
        assertEquals(
            listOf(0L to 2_000L, 2_000L to 3_000L, 3_000L to 5_000L, 5_000L to 6_000L, 6_000L to 8_000L),
            segments.map { it.startMs to it.endMs },
        )
        assertEquals(listOf(true, false, true, false, true), segments.map { it.clip == null })
    }

    @Test
    fun gainRespectsTrackOffsetVolumeAndFadeBoundaries() {
        val track = clip(
            "voice",
            type = AudioTrackType.VOICEOVER,
            startMs = 1_000L,
            sourceEndMs = 2_000L,
            volume = 0.5f,
            fadeInMs = 500L,
            fadeOutMs = 500L,
        )
        assertEquals(0f, audioTrackGainAtTimelinePosition(track, 999L), 0.0001f)
        assertEquals(0f, audioTrackGainAtTimelinePosition(track, 1_000L), 0.0001f)
        assertEquals(0.5f, audioTrackGainAtTimelinePosition(track, 1_500L), 0.0001f)
        assertEquals(0f, audioTrackGainAtTimelinePosition(track, 3_000L), 0.0001f)
    }

    @Test
    fun audioTrackPersistenceRoundTripsAndRejectsCorruptOrOutOfRangeData() {
        val expected = listOf(
            clip("music", AudioTrackType.MUSIC, 0L, sourceEndMs = 5_000L, volume = 0.75f, fadeInMs = 400L),
            clip("sfx", AudioTrackType.SFX, 2_000L, sourceEndMs = 500L),
        )
        val decoded = decodeAudioTrackClips(encodeAudioTrackClips(expected), 10_000L)
        assertEquals(expected, decoded)
        assertNull(decodeAudioTrackClips("[{\"id\":\"broken\"}]", 10_000L))
        assertNull(decodeAudioTrackClips("", 10_000L))
        assertNull(decodeAudioTrackClips(encodeAudioTrackClips(expected), 2_000L))
        assertEquals(emptyList<AudioTrackClip>(), decodeAudioTrackClips(null, 10_000L))
    }

    @Test
    fun sourceDurationSurvivesTrackSerializationAndAllowsLaterExpansion() {
        val original = clip(
            "music",
            sourceStartMs = 1_000L,
            sourceEndMs = 3_000L,
            sourceDurationMs = 8_000L,
        )
        val restored = decodeAudioTrackClips(encodeAudioTrackClips(listOf(original)), 10_000L)

        assertEquals(original, restored?.single())
        assertEquals(8_000L, restored?.single()?.sourceDurationMs)
    }

    @Test
    fun legacyTrackWithoutSourceDurationUsesItsSavedOutPoint() {
        val legacy = """[{"id":"legacy","type":"MUSIC","uri":"content://media/legacy","name":"Legacy","startMs":0,"sourceStartMs":500,"sourceEndMs":2500,"volume":1.0}]"""
        val restored = decodeAudioTrackClips(legacy, 5_000L)

        assertEquals(2_500L, restored?.single()?.sourceDurationMs)
        assertEquals(500L, restored?.single()?.sourceStartMs)
    }

    @Test
    fun insertionSkipsGapsShorterThanTheMinimumAudioSegment() {
        val existing = listOf(
            clip("first", startMs = 0L, sourceEndMs = 1_000L),
            clip("second", startMs = 1_050L, sourceEndMs = 1_000L),
        )
        assertEquals(
            AudioTrackInsertionSlot(2_050L, 500L),
            resolveAudioTrackInsertionSlot(existing, AudioTrackType.MUSIC, 1_000L, 500L, 5_000L),
        )
        assertNull(
            resolveAudioTrackInsertionSlot(
                emptyList(),
                AudioTrackType.MUSIC,
                950L,
                500L,
                1_049L,
            ),
        )
    }

    @Test
    fun insertionRejectsAudioSourcesShorterThanTheMinimumSegment() {
        assertNull(
            resolveAudioTrackInsertionSlot(
                emptyList(),
                AudioTrackType.MUSIC,
                0L,
                MIN_AUDIO_TRACK_CLIP_DURATION_MS - 1L,
                1_000L,
            ),
        )
        assertNull(
            resolveAudioTrackInsertionStartMs(
                emptyList(),
                AudioTrackType.MUSIC,
                0L,
                MIN_AUDIO_TRACK_CLIP_DURATION_MS - 1L,
                1_000L,
            ),
        )
    }

    @Test
    fun projectTrimDropsAudioRemaindersShorterThanTheMinimumSegment() {
        val original = clip("shortened", startMs = 900L, sourceEndMs = 500L)
        assertTrue(trimAudioTrackClipsToProjectDuration(listOf(original), 999L).isEmpty())
    }

    @Test
    fun timelineEndUsesSaturatingAddition() {
        assertEquals(Long.MAX_VALUE, addAudioTimelineTime(Long.MAX_VALUE - 5L, 10L))
    }

    @Test
    fun audioLaneSeekAndPositionStayOnProjectClockAcrossGaps() {
        val clips = listOf(
            clip("one", startMs = 2_000L, sourceEndMs = 1_000L),
            clip("two", startMs = 5_000L, sourceEndMs = 1_000L),
        )
        val segments = buildAudioTrackTimelineSegments(clips, AudioTrackType.MUSIC, 8_000L)
        assertEquals(AudioTrackSeekTarget(0, 1_500L), resolveAudioTrackSeekTarget(segments, 1_500L))
        assertEquals(AudioTrackSeekTarget(2, 500L), resolveAudioTrackSeekTarget(segments, 3_500L))
        assertEquals(3_500L, calculateAudioTrackTimelinePositionMs(segments, 2, 500L))
        assertEquals(AudioTrackSeekTarget(4, 1_999L), resolveAudioTrackSeekTarget(segments, 99_000L))
    }

    @Test
    fun videoDurationReductionShortensOrRemovesOverlayAudioNonDestructively() {
        val original = listOf(
            clip("kept", startMs = 2_000L, sourceEndMs = 4_000L, fadeOutMs = 1_500L),
            clip("removed", startMs = 8_000L, sourceEndMs = 1_000L),
        )
        val trimmed = trimAudioTrackClipsToProjectDuration(original, 5_000L)
        assertEquals(1, trimmed.size)
        assertEquals(3_000L, trimmed.single().durationMs)
        assertEquals(3_000L, trimmed.single().sourceEndMs)
        assertEquals(1_500L, trimmed.single().fadeOutMs)
        assertEquals(0, trimAudioTrackClipsToProjectDuration(original, 1_000L).size)
    }


    @Test
    fun insertionSlotTrimsLongSourceToTheFirstAvailableLaneGap() {
        val existing = listOf(
            clip("first", startMs = 0L, sourceEndMs = 2_000L),
            clip("second", startMs = 5_000L, sourceEndMs = 2_000L),
        )
        assertEquals(
            AudioTrackInsertionSlot(2_000L, 3_000L),
            resolveAudioTrackInsertionSlot(existing, AudioTrackType.MUSIC, 1_000L, 4_000L, 10_000L),
        )
        assertEquals(
            AudioTrackInsertionSlot(7_000L, 2_000L),
            resolveAudioTrackInsertionSlot(existing, AudioTrackType.MUSIC, 6_000L, 2_000L, 10_000L),
        )
        assertEquals(
            AudioTrackInsertionSlot(9_000L, 1_000L),
            resolveAudioTrackInsertionSlot(existing, AudioTrackType.MUSIC, 9_000L, 2_000L, 10_000L),
        )
    }

    @Test
    fun audioLaneResyncUsesToleranceAndCooldownToAvoidSeekThrashing() {
        assertFalse(shouldResyncAudioTrack(1_000L, 820L, 0, 0, 1, null, 0L))
        assertFalse(shouldResyncAudioTrack(1_000L, 821L, 0, 0, 1, null, 0L))
        assertTrue(shouldResyncAudioTrack(1_000L, 819L, 0, 0, 1, null, 0L))
        assertFalse(shouldResyncAudioTrack(1_000L, 700L, 0, 0, 1, 1_000L, 1_249L))
        assertTrue(shouldResyncAudioTrack(1_000L, 700L, 0, 0, 1, 1_000L, 1_250L))
        assertFalse(shouldResyncAudioTrack(1_000L, 1_000L, -1, 0, 1, 1_000L, 1_249L))
        assertTrue(shouldResyncAudioTrack(1_000L, 1_000L, -1, 0, 1, 1_000L, 1_250L))
        assertFalse(shouldResyncAudioTrack(1_000L, 0L, -1, 0, 0, null, 0L))
    }

    @Test
    fun audioLaneResyncDetectsWrongPlaylistItemAtAnExactSegmentBoundary() {
        val segments = listOf(
            AudioTrackTimelineSegment(0L, 1_000L, null),
            AudioTrackTimelineSegment(1_000L, 2_000L, null),
        )
        val expectedTarget = resolveAudioTrackSeekTarget(segments, 1_000L)

        assertEquals(AudioTrackSeekTarget(1, 0L), expectedTarget)
        assertTrue(
            shouldResyncAudioTrack(
                projectPositionMs = 1_000L,
                trackPositionMs = 1_000L,
                currentSegmentIndex = 0,
                expectedSegmentIndex = expectedTarget?.segmentIndex,
                segmentCount = segments.size,
                lastResyncAtMs = null,
                nowMs = 2_000L,
            ),
        )
        assertFalse(
            shouldResyncAudioTrack(
                projectPositionMs = 1_000L,
                trackPositionMs = 1_000L,
                currentSegmentIndex = 0,
                expectedSegmentIndex = expectedTarget?.segmentIndex,
                segmentCount = segments.size,
                lastResyncAtMs = 2_000L,
                nowMs = 2_249L,
                lastResyncSegmentIndex = expectedTarget?.segmentIndex,
            ),
        )
        assertTrue(
            shouldResyncAudioTrack(
                projectPositionMs = 1_000L,
                trackPositionMs = 1_000L,
                currentSegmentIndex = 0,
                expectedSegmentIndex = expectedTarget?.segmentIndex,
                segmentCount = segments.size,
                lastResyncAtMs = 2_000L,
                nowMs = 2_250L,
                lastResyncSegmentIndex = expectedTarget?.segmentIndex,
            ),
        )
    }

    @Test
    fun audioLaneResyncBypassesPreviousCooldownWhenTheTargetSegmentChanges() {
        assertTrue(
            shouldResyncAudioTrack(
                projectPositionMs = 2_000L,
                trackPositionMs = 2_000L,
                currentSegmentIndex = 0,
                expectedSegmentIndex = 2,
                segmentCount = 3,
                lastResyncAtMs = 1_000L,
                nowMs = 1_100L,
                lastResyncSegmentIndex = 1,
            ),
        )
        assertTrue(
            shouldResyncAudioTrack(
                projectPositionMs = 2_000L,
                trackPositionMs = 1_500L,
                currentSegmentIndex = 2,
                expectedSegmentIndex = 2,
                segmentCount = 3,
                lastResyncAtMs = 1_000L,
                nowMs = 1_100L,
                lastResyncSegmentIndex = 1,
            ),
        )
        assertFalse(
            shouldResyncAudioTrack(
                projectPositionMs = 2_000L,
                trackPositionMs = 1_500L,
                currentSegmentIndex = 2,
                expectedSegmentIndex = 2,
                segmentCount = 3,
                lastResyncAtMs = 1_000L,
                nowMs = 1_100L,
                lastResyncSegmentIndex = 2,
            ),
        )
    }

    @Test
    fun duplicatePreservesSourceEditsAndUsesFirstFreeSlotAfterSelectedClip() {
        val selected = clip(
            id = "music",
            startMs = 1_000L,
            sourceStartMs = 250L,
            sourceEndMs = 1_250L,
            sourceDurationMs = 2_000L,
            volume = 0.65f,
            fadeInMs = 100L,
            fadeOutMs = 200L,
        )
        val following = clip("following", startMs = 3_000L, sourceEndMs = 1_000L)
        val original = listOf(selected, following)

        val duplicated = duplicateAudioTrackClip(
            clips = original,
            clipId = "music",
            newClipId = "music-copy",
            projectDurationMs = 7_000L,
        ) ?: error("Expected the duplicate to fit in the first free gap")

        val copy = duplicated.first { it.id == "music-copy" }
        assertEquals(selected.copy(id = "music-copy", startMs = 2_000L), copy)
        assertEquals(selected, duplicated.first { it.id == "music" })
        assertEquals(original, listOf(selected, following))
        assertTrue(validateAudioTrackClips(duplicated, 7_000L))
    }

    @Test
    fun duplicateSkipsAGapThatIsTooShortAndUsesTheNextAvailableSlot() {
        val selected = clip("music", startMs = 0L, sourceEndMs = 1_000L)
        val blocker = clip("blocker", startMs = 1_500L, sourceEndMs = 1_000L)

        val duplicated = duplicateAudioTrackClip(
            clips = listOf(selected, blocker),
            clipId = "music",
            newClipId = "music-copy",
            projectDurationMs = 5_000L,
        ) ?: error("Expected a later free slot")

        assertEquals(2_500L, duplicated.first { it.id == "music-copy" }.startMs)
        assertTrue(validateAudioTrackClips(duplicated, 5_000L))
    }

    @Test
    fun duplicateRejectsMissingOrDuplicateIdsAndProjectsWithoutRoom() {
        val selected = clip("music", startMs = 4_000L, sourceEndMs = 1_000L)
        assertNull(duplicateAudioTrackClip(listOf(selected), "missing", "copy", 5_000L))
        assertNull(duplicateAudioTrackClip(listOf(selected), "music", "music", 5_000L))
        assertNull(duplicateAudioTrackClip(listOf(selected), "music", "copy", 5_000L))

        val fullProject = (0 until MAX_AUDIO_TRACK_CLIPS).map { index ->
            clip(
                id = "clip-$index",
                startMs = index * 1_000L,
                sourceEndMs = 1_000L,
                sourceDurationMs = 1_000L,
            )
        }
        assertNull(
            duplicateAudioTrackClip(
                clips = fullProject,
                clipId = "clip-0",
                newClipId = "clip-copy",
                projectDurationMs = MAX_AUDIO_TRACK_CLIPS * 1_000L,
            ),
        )
    }

    @Test
    fun splitPreservesContiguousSourceRangeAndOriginalFadeEnvelope() {
        val original = clip(
            id = "music",
            startMs = 1_000L,
            sourceStartMs = 2_000L,
            sourceEndMs = 5_000L,
            sourceDurationMs = 6_000L,
            volume = 0.6f,
            fadeInMs = 300L,
            fadeOutMs = 400L,
        )
        val split = splitAudioTrackClip(
            clips = listOf(original),
            clipId = original.id,
            splitTimelineMs = 2_500L,
            newClipId = "music-right",
            projectDurationMs = 10_000L,
        ) ?: error("Expected the flat section to be splittable")
        val left = split.first { it.id == "music" }
        val right = split.first { it.id == "music-right" }

        assertEquals(1_000L, left.startMs)
        assertEquals(2_000L, left.sourceStartMs)
        assertEquals(3_500L, left.sourceEndMs)
        assertEquals(2_500L, right.startMs)
        assertEquals(3_500L, right.sourceStartMs)
        assertEquals(5_000L, right.sourceEndMs)
        assertEquals(300L, left.fadeInMs)
        assertEquals(0L, left.fadeOutMs)
        assertEquals(0L, right.fadeInMs)
        assertEquals(400L, right.fadeOutMs)
        assertEquals(original.uri, right.uri)
        assertEquals(original.volume, right.volume, 0.0001f)
        assertTrue(validateAudioTrackClips(split, 10_000L))
        assertEquals(1_000L, original.startMs)
        assertEquals(2_000L, original.sourceStartMs)
        assertEquals(5_000L, original.sourceEndMs)

        for (positionMs in original.startMs until original.endMs) {
            val originalGain = audioTrackGainAtTimelinePosition(original, positionMs)
            val splitGain = split
                .sumOf { audioTrackGainAtTimelinePosition(it, positionMs).toDouble() }
                .toFloat()
            assertEquals("Gain changed at $positionMs ms", originalGain, splitGain, 0.0001f)
        }
    }

    @Test
    fun splitRejectsClipEdgesAndPositionsInsideFadeRamps() {
        val original = clip(
            id = "music",
            sourceEndMs = 3_000L,
            sourceDurationMs = 3_000L,
            fadeInMs = 300L,
            fadeOutMs = 400L,
        )
        assertNull(splitAudioTrackClip(listOf(original), "music", 0L, "right", 5_000L))
        assertNull(splitAudioTrackClip(listOf(original), "music", 200L, "right", 5_000L))
        assertNull(splitAudioTrackClip(listOf(original), "music", 2_700L, "right", 5_000L))
        assertNull(splitAudioTrackClip(listOf(original), "music", 3_000L, "right", 5_000L))
        assertNull(splitAudioTrackClip(listOf(original), "missing", 1_500L, "right", 5_000L))
        assertNull(splitAudioTrackClip(listOf(original), "music", 1_500L, "music", 5_000L))
    }

    @Test
    fun splitRejectsWhenAudioClipLimitIsAlreadyReached() {
        val fullLane = (0 until MAX_AUDIO_TRACK_CLIPS).map { index ->
            clip(
                id = "clip-$index",
                startMs = index * 1_000L,
                sourceEndMs = 1_000L,
                sourceDurationMs = 1_000L,
            )
        }
        assertNull(
            splitAudioTrackClip(
                clips = fullLane,
                clipId = "clip-0",
                splitTimelineMs = 500L,
                newClipId = "clip-right",
                projectDurationMs = MAX_AUDIO_TRACK_CLIPS * 1_000L,
            ),
        )
    }

}
