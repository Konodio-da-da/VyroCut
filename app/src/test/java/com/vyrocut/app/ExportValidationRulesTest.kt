package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportValidationRulesTest {
    @Test
    fun acceptsVideoAndAudioSamplesWhenSourceContainsAudio() {
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(videoSampleCount = 120L, audioSampleCount = 480L, videoDurationMs = 4_000L),
                expectedDurationMs = 4_000L,
                sourceHasAudio = true,
            ),
        )
    }

    @Test
    fun acceptsVideoOnlyExportWhenSourcesHaveNoAudio() {
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(videoSampleCount = 24L, audioSampleCount = 0L, videoDurationMs = 1_000L),
                expectedDurationMs = 1_000L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun rejectsMissingVideoSamples() {
        assertEquals(
            "The exported MP4 contains no readable video samples",
            validateExportTrackSummary(
                ExportTrackSummary(0L, 100L, 1_000L),
                expectedDurationMs = 1_000L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun rejectsMissingAudioSamplesWhenSourceHasAudio() {
        assertEquals(
            "The source clips contain audio, but the exported MP4 has no readable audio samples",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 1_000L),
                expectedDurationMs = 1_000L,
                sourceHasAudio = true,
            ),
        )
    }

    @Test
    fun rejectsMateriallyTruncatedOrExtendedExports() {
        assertEquals(
            "Export duration 2_000ms does not match the 4_000ms timeline",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 2_000L),
                expectedDurationMs = 4_000L,
                sourceHasAudio = false,
            ),
        )
        assertEquals(
            "Export duration 5_000ms does not match the 4_000ms timeline",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 5_000L),
                expectedDurationMs = 4_000L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun rejectsProjectsWithoutPositiveDuration() {
        assertEquals(
            "The project has no playable duration",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 1_000L),
                expectedDurationMs = 0L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun allowsSmallFrameBoundaryDurationDifferences() {
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 3_950L),
                expectedDurationMs = 4_000L,
                sourceHasAudio = false,
            ),
        )
    }
}
