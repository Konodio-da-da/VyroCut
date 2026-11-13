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
        assertEquals(listOf(false, true, false, true, false), segments.map { it.clip == null })
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

}
