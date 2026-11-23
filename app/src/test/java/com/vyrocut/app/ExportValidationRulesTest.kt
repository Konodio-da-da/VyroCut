package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportValidationRulesTest {
    @Test
    fun probesBeginningMiddleAndEndOfEveryClipWithoutDuplicateTimestamps() {
        assertEquals(listOf(0L, 2_000L, 3_999L), exportFrameProbePositionsMs(listOf(4_000L)))
        assertEquals(listOf(0L), exportFrameProbePositionsMs(listOf(1L)))
        assertEquals(emptyList<Long>(), exportFrameProbePositionsMs(emptyList()))
        assertEquals(emptyList<Long>(), exportFrameProbePositionsMs(listOf(0L)))
        assertEquals(emptyList<Long>(), exportFrameProbePositionsMs(listOf(-1L)))
    }

    @Test
    fun probesEachClipInProjectTimelineOrder() {
        assertEquals(
            listOf(0L, 1_000L, 1_999L, 2_000L, 2_500L, 2_999L, 3_000L, 4_500L, 5_999L),
            exportFrameProbePositionsMs(listOf(2_000L, 1_000L, 3_000L)),
        )
    }

    @Test
    fun rejectsClipProbeTimelinesThatOverflowLongMilliseconds() {
        assertEquals(
            emptyList<Long>(),
            exportFrameProbePositionsMs(listOf(Long.MAX_VALUE, 1L)),
        )
    }

    @Test
    fun acceptsDecodedFramesWithTheSelectedCanvasDimensions() {
        val canvas = exportCanvasSizeForPreset("9:16")
        assertNull(validateDecodedExportFrameDimensions(1_080, 1_920, canvas))
    }

    @Test
    fun rejectsDecodedFramesWithInvalidOrUnexpectedDimensions() {
        val canvas = exportCanvasSizeForPreset("9:16")
        assertEquals(
            "The exported MP4 contains an unreadable video frame",
            validateDecodedExportFrameDimensions(0, 1_920, canvas),
        )
        assertEquals(
            "Decoded frame 1920x1080 does not match selected 1080x1920 canvas",
            validateDecodedExportFrameDimensions(1_920, 1_080, canvas),
        )
    }

    @Test
    fun acceptsMp4DimensionsMatchingTheSelectedCanvasPreset() {
        val canvas = exportCanvasSizeForPreset("16:9")
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(120L, 480L, 4_000L, canvas.widthPx, canvas.heightPx),
                expectedDurationMs = 4_000L,
                sourceHasAudio = true,
                expectedCanvasSize = canvas,
            ),
        )
    }

    @Test
    fun rejectsMp4DimensionsThatDoNotMatchTheSelectedCanvasPreset() {
        val canvas = exportCanvasSizeForPreset("9:16")
        assertEquals(
            "Export canvas 1920x1080 does not match selected 1080x1920 canvas",
            validateExportTrackSummary(
                ExportTrackSummary(120L, 480L, 4_000L, 1_920, 1_080),
                expectedDurationMs = 4_000L,
                sourceHasAudio = true,
                expectedCanvasSize = canvas,
            ),
        )
    }

    @Test
    fun mapsSupportedCanvasPresetsToFixedOutputDimensions() {
        assertEquals(ExportCanvasSize(1_080, 1_920), exportCanvasSizeForPreset("9:16"))
        assertEquals(ExportCanvasSize(1_920, 1_080), exportCanvasSizeForPreset("16:9"))
        assertEquals(ExportCanvasSize(1_080, 1_080), exportCanvasSizeForPreset("1:1"))
    }

    @Test
    fun acceptsVideoAndAudioSamplesWhenSourceContainsAudio() {
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(videoSampleCount = 120L, audioSampleCount = 480L, videoDurationMs = 4_000L, videoWidthPx = 1_920, videoHeightPx = 1_080, videoMimeType = "video/avc", audioMimeType = "audio/mp4a-latm"),
                expectedDurationMs = 4_000L,
                sourceHasAudio = true,
            ),
        )
    }

    @Test
    fun acceptsVideoOnlyExportWhenSourcesHaveNoAudio() {
        assertNull(
            validateExportTrackSummary(
                ExportTrackSummary(videoSampleCount = 24L, audioSampleCount = 0L, videoDurationMs = 1_000L, videoWidthPx = 1_920, videoHeightPx = 1_080, videoMimeType = "video/avc"),
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
                ExportTrackSummary(24L, 0L, 1_000L, 1_920, 1_080, "video/avc", "audio/mp4a-latm"),
                expectedDurationMs = 1_000L,
                sourceHasAudio = true,
            ),
        )
    }

    @Test
    fun rejectsMateriallyTruncatedOrExtendedExports() {
        assertEquals(
            "Export duration 2000ms does not match the 4000ms timeline",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 2_000L, 1_920, 1_080, "video/avc"),
                expectedDurationMs = 4_000L,
                sourceHasAudio = false,
            ),
        )
        assertEquals(
            "Export duration 5000ms does not match the 4000ms timeline",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 5_000L, 1_920, 1_080, "video/avc"),
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
                ExportTrackSummary(24L, 0L, 3_950L, 1_920, 1_080, "video/avc"),
                expectedDurationMs = 4_000L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun rejectsMissingOrNonPositiveEncodedVideoDimensions() {
        assertEquals(
            "The exported MP4 has invalid video dimensions",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 1_000L, 0, 1_080),
                expectedDurationMs = 1_000L,
                sourceHasAudio = false,
            ),
        )
        assertEquals(
            "The exported MP4 has invalid video dimensions",
            validateExportTrackSummary(
                ExportTrackSummary(24L, 0L, 1_000L, 1_920, -1),
                expectedDurationMs = 1_000L,
                sourceHasAudio = false,
            ),
        )
    }

    @Test
    fun formatsVerifiedOutputUsingActualMetadataAndFingerprintedByteCount() {
        val summary = ExportTrackSummary(
            videoSampleCount = 240L,
            audioSampleCount = 480L,
            videoDurationMs = 10_000L,
            videoWidthPx = 1_920,
            videoHeightPx = 1_080,
            videoMimeType = "video/avc",
            audioMimeType = "audio/mp4a-latm",
        )

        assertEquals(
            "1920 × 1080 • 00:10 • 1.0 MiB actual • H.264 + AAC",
            formatVerifiedExportOutputSummary(summary, 1_048_576L),
        )
    }

    @Test
    fun formatsVideoOnlyOutputWithoutInventingAnAudioCodec() {
        val summary = ExportTrackSummary(
            videoSampleCount = 24L,
            audioSampleCount = 0L,
            videoDurationMs = 3_661_000L,
            videoWidthPx = 1_280,
            videoHeightPx = 720,
            videoMimeType = "video/hevc",
            audioMimeType = "audio/mp4a-latm",
        )

        assertEquals(
            "1280 × 720 • 1:01:01 • 2.0 MiB actual • HEVC",
            formatVerifiedExportOutputSummary(summary, 2_097_152L),
        )
    }

    @Test
    fun rejectsIncompleteMeasuredOutputMetadataInsteadOfShowingAnEstimateAsActual() {
        val valid = ExportTrackSummary(
            videoSampleCount = 24L,
            audioSampleCount = 0L,
            videoDurationMs = 1_000L,
            videoWidthPx = 1_920,
            videoHeightPx = 1_080,
            videoMimeType = "video/avc",
        )

        assertNull(formatVerifiedExportOutputSummary(valid.copy(videoWidthPx = 0), 1_048_576L))
        assertNull(formatVerifiedExportOutputSummary(valid.copy(videoDurationMs = 0L), 1_048_576L))
        assertNull(formatVerifiedExportOutputSummary(valid, 0L))
        assertNull(formatActualExportFileSize(-1L))
        assertNull(formatVerifiedExportDuration(0L))
    }

    @Test
    fun preservesUnknownCodecNamesWithoutGuessingAStandardCodec() {
        val summary = ExportTrackSummary(
            videoSampleCount = 24L,
            audioSampleCount = 0L,
            videoDurationMs = 1_000L,
            videoWidthPx = 640,
            videoHeightPx = 360,
            videoMimeType = "video/x-custom",
        )

        assertEquals(
            "640 × 360 • 00:01 • 1.0 MiB actual • X-CUSTOM",
            formatVerifiedExportOutputSummary(summary, 1_048_576L),
        )
    }

}
