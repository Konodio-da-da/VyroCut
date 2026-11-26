package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipTimedEffectSegmentsTest {
    @Test
    fun timedEffectUsesInclusiveStartAndExclusiveEndInMillisecondsAndMicroseconds() {
        val segment = ClipTimedEffectSegment(
            id = "fx-vignette",
            preset = ClipCreativeEffectPreset.VIGNETTE,
            intensity = 40,
            startMs = 1_000L,
            endMs = 2_500L,
        )

        assertFalse(isClipTimedEffectSegmentActiveAtMs(segment, 999L))
        assertTrue(isClipTimedEffectSegmentActiveAtMs(segment, 1_000L))
        assertTrue(isClipTimedEffectSegmentActiveAtMs(segment, 2_499L))
        assertFalse(isClipTimedEffectSegmentActiveAtMs(segment, 2_500L))
        assertFalse(isClipTimedEffectSegmentActiveAtUs(segment, -1L))
        assertTrue(isClipTimedEffectSegmentActiveAtUs(segment, 1_500_000L))
        assertFalse(isClipTimedEffectSegmentActiveAtUs(segment, 2_500_000L))
    }

    @Test
    fun activeEffectSpecsAreResolvedInStableOrderAndRespectZeroIntensity() {
        val segments = listOf(
            ClipTimedEffectSegment("b-warm", ClipCreativeEffectPreset.WARM, 25, 0L, 4_000L),
            ClipTimedEffectSegment("a-vignette", ClipCreativeEffectPreset.VIGNETTE, 40, 0L, 4_000L),
            ClipTimedEffectSegment("c-sepia", ClipCreativeEffectPreset.SEPIA, 0, 0L, 4_000L),
        )

        assertEquals(
            listOf(
                ClipVisualEffectSpec(ClipVisualEffectType.VIGNETTE, 0.4f),
                ClipVisualEffectSpec(ClipVisualEffectType.WARM_TINT, 0.25f),
            ),
            createClipTimedEffectSpecsAtMs(segments, 1_000L, 5_000L),
        )
        assertEquals(emptyList<ClipVisualEffectSpec>(), createClipTimedEffectSpecsAtMs(segments, 4_000L, 5_000L))
        assertEquals(emptyList<ClipVisualEffectSpec>(), createClipTimedEffectSpecsAtMs(segments, 5_000L, 5_000L))
    }

    @Test
    fun timedVignetteIntensityUsesClipLocalFrameTimestampAndLastActiveSegmentWins() {
        val segments = listOf(
            ClipTimedEffectSegment("base", ClipCreativeEffectPreset.VIGNETTE, 25, 0L, 5_000L),
            ClipTimedEffectSegment("later", ClipCreativeEffectPreset.VIGNETTE, 70, 1_250L, 2_500L),
            ClipTimedEffectSegment("warm", ClipCreativeEffectPreset.WARM, 90, 1_500L, 4_000L),
            ClipTimedEffectSegment("disabled", ClipCreativeEffectPreset.VIGNETTE, 0, 3_000L, 3_500L),
        )

        assertEquals(0f, activeClipTimedVignetteIntensityAtUs(segments, -1L, 6_000L), 0.0001f)
        assertEquals(0.25f, activeClipTimedVignetteIntensityAtUs(segments, 999_999L, 6_000L), 0.0001f)
        assertEquals(0.70f, activeClipTimedVignetteIntensityAtUs(segments, 1_250_000L, 6_000L), 0.0001f)
        assertEquals(0.70f, activeClipTimedVignetteIntensityAtUs(segments, 2_499_999L, 6_000L), 0.0001f)
        assertEquals(0.25f, activeClipTimedVignetteIntensityAtUs(segments, 2_500_000L, 6_000L), 0.0001f)
        assertEquals(0f, activeClipTimedVignetteIntensityAtUs(segments, 3_000_000L, 6_000L), 0.0001f)
        assertEquals(0.25f, activeClipTimedVignetteIntensityAtUs(segments, 3_500_000L, 6_000L), 0.0001f)
        assertEquals(0.25f, activeClipTimedVignetteIntensityAtUs(segments, 4_999_999L, 6_000L), 0.0001f)
        assertEquals(0f, activeClipTimedVignetteIntensityAtUs(segments, 5_000_000L, 6_000L), 0.0001f)
        assertEquals(0f, activeClipTimedVignetteIntensityAtUs(segments, 6_000_000L, 6_000L), 0.0001f)
    }

    @Test
    fun timedVignetteIntensitySubtractsTheOwningClipProjectTimelineStart() {
        val segments = listOf(
            ClipTimedEffectSegment("later-clip-vignette", ClipCreativeEffectPreset.VIGNETTE, 40, 500L, 1_500L),
        )

        assertEquals(
            0f,
            activeClipTimedVignetteIntensityAtUs(segments, 9_999_999L, 2_000L, 10_000L),
            0.0001f,
        )
        assertEquals(
            0f,
            activeClipTimedVignetteIntensityAtUs(segments, 10_499_999L, 2_000L, 10_000L),
            0.0001f,
        )
        assertEquals(
            0.4f,
            activeClipTimedVignetteIntensityAtUs(segments, 10_500_000L, 2_000L, 10_000L),
            0.0001f,
        )
        assertEquals(
            0.4f,
            activeClipTimedVignetteIntensityAtUs(segments, 11_499_999L, 2_000L, 10_000L),
            0.0001f,
        )
        assertEquals(
            0f,
            activeClipTimedVignetteIntensityAtUs(segments, 11_500_000L, 2_000L, 10_000L),
            0.0001f,
        )
        assertEquals(
            0f,
            activeClipTimedVignetteIntensityAtUs(segments, 10_500_000L, 2_000L, Long.MAX_VALUE),
            0.0001f,
        )
    }

    @Test
    fun splitPreservesIntervalsAcrossBothVideoHalvesAndKeepsClipEndSentinel() {
        val segments = listOf(
            ClipTimedEffectSegment("before", ClipCreativeEffectPreset.VIGNETTE, 40, 0L, 2_000L),
            ClipTimedEffectSegment("cross", ClipCreativeEffectPreset.WARM, 60, 1_000L, 6_000L),
            ClipTimedEffectSegment("after", ClipCreativeEffectPreset.SEPIA, 75, 4_000L, null),
        )

        val (left, right) = splitClipTimedEffectSegmentsAt(
            segments = segments,
            splitPositionMs = 3_000L,
            clipDurationMs = 8_000L,
        )

        assertEquals(
            listOf(
                segments[0],
                segments[1].copy(endMs = 3_000L),
            ),
            left,
        )
        assertEquals(
            listOf(
                segments[1].copy(startMs = 0L, endMs = 3_000L),
                segments[2].copy(startMs = 1_000L, endMs = null),
            ),
            right,
        )
    }

    @Test
    fun trimRebasesOnlyTheRetainedSourceIntersectionAndPreservesFullClipEnd() {
        val segments = listOf(
            ClipTimedEffectSegment("head", ClipCreativeEffectPreset.VIGNETTE, 40, 0L, 5_000L),
            ClipTimedEffectSegment("tail", ClipCreativeEffectPreset.SEPIA, 75, 7_000L, 10_000L),
            ClipTimedEffectSegment("whole", ClipCreativeEffectPreset.WARM, 20, 0L, null),
            ClipTimedEffectSegment("outside", ClipCreativeEffectPreset.COOL, 50, 1_000L, 2_000L),
        )

        assertEquals(
            listOf(
                segments[0].copy(startMs = 0L, endMs = 2_000L),
                segments[1].copy(startMs = 4_000L, endMs = 5_000L),
                segments[2].copy(startMs = 0L, endMs = null),
            ),
            rebaseClipTimedEffectSegmentsForTrim(
                segments = segments,
                oldSourceStartMs = 1_000L,
                oldSourceEndMs = 11_000L,
                newSourceStartMs = 4_000L,
                newSourceEndMs = 9_000L,
            ),
        )
    }

    @Test
    fun invalidIntervalsIdsPresetsIntensityAndClipBoundsAreRejected() {
        assertFalse(isValidClipTimedEffectSegmentId(""))
        assertFalse(isValidClipTimedEffectSegmentId("bad/id"))
        assertFalse(isValidClipTimedEffectSegmentTiming(-1L, 1_000L))
        assertFalse(isValidClipTimedEffectSegmentTiming(1_000L, 1_000L))
        assertTrue(isValidClipTimedEffectSegmentTiming(0L, null))

        assertNull(decodeClipTimedEffectSegment("fx", "future-effect", 50, 0L, 1_000L))
        assertNull(decodeClipTimedEffectSegment("fx", "original", 50, 0L, 1_000L))
        assertNull(decodeClipTimedEffectSegment("fx", "vignette", 101, 0L, 1_000L))
        assertNull(decodeClipTimedEffectSegment("fx", "vignette", 50, -1L, 1_000L))

        val segment = ClipTimedEffectSegment("fx", ClipCreativeEffectPreset.VIGNETTE, 50, 0L, 1_000L)
        assertTrue(validateClipTimedEffectSegments(listOf(segment), 1_000L))
        assertFalse(validateClipTimedEffectSegments(listOf(segment), 999L))
        assertFalse(validateClipTimedEffectSegments(listOf(segment, segment), 1_000L))
        assertFalse(validateClipTimedEffectSegments((0..MAX_CLIP_TIMED_EFFECT_SEGMENTS).map { index ->
            ClipTimedEffectSegment("fx-$index", ClipCreativeEffectPreset.VIGNETTE, 50, 0L, 1_000L)
        }, 1_000L))
        assertFalse(validateClipTimedEffectSegments(listOf(segment), 0L))
    }

    @Test
    fun timelineBoundsMapClipLocalIntervalToTrackWidth() {
        val segment = ClipTimedEffectSegment(
            "fx-vignette",
            ClipCreativeEffectPreset.VIGNETTE,
            40,
            2_000L,
            7_000L,
        )

        val bounds = calculateClipTimedEffectSegmentTimelineBounds(100f, 10_000L, segment)

        assertEquals(20f, bounds!!.startOffsetPx, 0.001f)
        assertEquals(50f, bounds.widthPx, 0.001f)
        assertNull(calculateClipTimedEffectSegmentTimelineBounds(Float.NaN, 10_000L, segment))
        assertNull(calculateClipTimedEffectSegmentTimelineBounds(100f, 0L, segment))
    }

    @Test
    fun projectAndVersionFifteenBackupRoundTripTimedSegmentsAndKeepLegacyClipsCompatible() {
        val segment = ClipTimedEffectSegment(
            id = "fx-vignette",
            preset = ClipCreativeEffectPreset.VIGNETTE,
            intensity = 65,
            startMs = 1_250L,
            endMs = 4_750L,
        )
        val clip = EditClip(
            id = "video",
            uri = "content://media/video",
            name = "Timed effects",
            sourceStartMs = 0L,
            sourceEndMs = 6_000L,
            timedEffectSegments = listOf(segment),
        )

        assertEquals(listOf(clip), decodeProjectStrict(encodeProject(listOf(clip))))
        val backup = encodeProjectBackup("Timed effects", listOf(clip), ProjectCanvasSettings())
        assertEquals(15, org.json.JSONObject(backup).getInt("version"))
        assertEquals(listOf(segment), decodeProjectBackup(backup).clips.single().timedEffectSegments)

        val legacyBackup = """{
            "format":"vyrocut-project",
            "version":15,
            "projectName":"Legacy timed effects",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Legacy","start":0,"end":5000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK",
            "audioTracks":[]
        }"""
        assertEquals(
            emptyList<ClipTimedEffectSegment>(),
            decodeProjectBackup(legacyBackup).clips.single().timedEffectSegments,
        )

        val snapshot = ProjectEditSnapshot(listOf(clip), emptyList())
        assertEquals(snapshot, decodeProjectEditSnapshot(encodeProjectEditSnapshot(snapshot)))
    }

    @Test
    fun malformedTimedEffectSegmentPayloadRejectsTheProjectInsteadOfSilentlyDroppingIt() {
        val malformed = """[{
            "id":"video",
            "uri":"content://media/video",
            "name":"Invalid timed effect",
            "start":0,
            "end":5000,
            "timedEffectSegments":[{"id":"fx","presetId":"future-effect","intensity":50,"startMs":0,"endMs":1000}]
        }]"""

        assertNull(decodeProjectStrict(malformed))
    }
}
