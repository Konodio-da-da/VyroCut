package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportDecodeValidationTest {
    @Test
    fun acceptsAudioDecodedThroughProjectEndAndEos() {
        assertNull(
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(
                    pcmBufferCount = 188,
                    firstPresentationTimeUs = 0L,
                    lastPresentationTimeUs = 3_978_666L,
                    decodedEndTimeUs = 4_000_000L,
                    timestampsMonotonic = true,
                    reachedEndOfStream = true,
                ),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsAudioWithoutDecodedPcm() {
        assertEquals(
            "The exported MP4 audio track did not produce decodable PCM samples",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(0, Long.MAX_VALUE, Long.MIN_VALUE, Long.MIN_VALUE, true, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsAudioDecoderThatDidNotReachEndOfStream() {
        assertEquals(
            "The exported MP4 audio track could not be decoded through its end",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(20, 0L, 500_000L, 600_000L, true, false),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsAudioThatEndsBeforeTheProjectTimeline() {
        assertEquals(
            "The exported MP4 audio ends before the project timeline",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(100, 0L, 2_900_000L, 3_000_000L, true, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsAudioThatStartsAfterTheProjectTimeline() {
        assertEquals(
            "The exported MP4 audio starts after the project timeline",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(100, 500_000L, 3_900_000L, 4_000_000L, true, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsOutOfOrderDecodedAudioTimestamps() {
        assertEquals(
            "The exported MP4 audio decoder returned out-of-order PCM timestamps",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(100, 0L, 3_000_000L, 4_000_000L, false, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsAudioThatExtendsMateriallyPastTheProjectTimeline() {
        assertEquals(
            "The exported MP4 audio extends beyond the project timeline",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(100, 0L, 4_900_000L, 5_000_000L, true, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }

    @Test
    fun rejectsProjectsWithoutPositiveDuration() {
        assertEquals(
            "The project has no playable duration for audio verification",
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(1, 0L, 0L, 1L, true, true),
                expectedDurationMs = 0L,
            ),
        )
    }

    @Test
    fun allowsSmallCodecPrimingAndPaddingDifferences() {
        assertNull(
            validateDecodedAudioCoverage(
                DecodedAudioCoverage(188, -20_000L, 3_950_000L, 3_980_000L, true, true),
                expectedDurationMs = 4_000L,
            ),
        )
    }
}
