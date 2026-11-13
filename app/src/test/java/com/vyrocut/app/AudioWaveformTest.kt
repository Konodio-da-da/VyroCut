package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformTest {
    @Test
    fun accumulatorNormalizesPeaksIntoSourceTimeBuckets() {
        val waveform = AudioWaveformAccumulator(4, 0L, 4_000_000L)
        waveform.addFrame(0L, 0.25f)
        waveform.addFrame(999_999L, 0.5f)
        waveform.addFrame(1_000_000L, 1f)
        waveform.addFrame(3_000_000L, 0.25f)

        assertEquals(listOf(0.5f, 1f, 0f, 0.25f), waveform.finish())
    }

    @Test
    fun accumulatorIgnoresFramesOutsideTheRequestedSourceRange() {
        val waveform = AudioWaveformAccumulator(3, 1_000_000L, 4_000_000L)
        waveform.addFrame(999_999L, 1f)
        waveform.addFrame(4_000_000L, 1f)
        waveform.addFrame(2_000_000L, 0.5f)

        assertEquals(listOf(0f, 1f, 0f), waveform.finish())
    }

    @Test
    fun accumulatorKeepsDigitalSilenceAtZero() {
        val waveform = AudioWaveformAccumulator(8, 0L, 8_000_000L)
        repeat(8) { index ->
            waveform.addFrame(index * 1_000_000L, 0f)
        }

        assertEquals(List(8) { 0f }, waveform.finish())
    }

    @Test
    fun accumulatorIgnoresNonFiniteAndClampsOutOfRangePeaks() {
        val waveform = AudioWaveformAccumulator(2, 0L, 2_000_000L)
        waveform.addFrame(0L, Float.NaN)
        waveform.addFrame(100_000L, 2f)
        waveform.addFrame(1_000_000L, -0.5f)

        assertEquals(listOf(1f, 0.5f), waveform.finish())
    }

    @Test
    fun accumulatorRejectsInvalidRangesAndBucketCounts() {
        assertEquals(
            false,
            runCatching { AudioWaveformAccumulator(0, 0L, 1_000_000L) }.isSuccess,
        )
        assertEquals(
            false,
            runCatching { AudioWaveformAccumulator(8, 2_000_000L, 1_000_000L) }.isSuccess,
        )
    }

    @Test
    fun cacheKeySeparatesWaveformBucketResolutions() {
        val uri = "content://media/track"
        val defaultResolution = audioWaveformCacheKey(uri, 0L, 5_000L)
        val explicitDefault = audioWaveformCacheKey(uri, 0L, 5_000L, bucketCount = 96)
        val compactResolution = audioWaveformCacheKey(uri, 0L, 5_000L, bucketCount = 48)

        assertEquals(defaultResolution, explicitDefault)
        assertNotEquals(defaultResolution, compactResolution)
    }

    @Test
    fun cacheFileNameSeparatesRequestedWaveformResolutions() {
        val uri = "content://media/track"
        val compact = audioWaveformCacheFileName(uri, 0L, 5_000L, bucketCount = 48)
        val detailed = audioWaveformCacheFileName(uri, 0L, 5_000L, bucketCount = 96)

        assertNotEquals(compact, detailed)
        assertEquals(true, compact.endsWith(".bin"))
        assertEquals(true, detailed.endsWith(".bin"))
    }

    @Test
    fun cacheKeyRejectsUnsupportedBucketResolution() {
        assertEquals(
            false,
            runCatching {
                audioWaveformCacheKey("content://media/track", 0L, 5_000L, bucketCount = 0)
            }.isSuccess,
        )
    }

    @Test
    fun cacheKeyIsStableAndChangesWithSourceTrimRange() {
        val first = audioWaveformCacheKey("content://media/track", 0L, 5_000L)
        val repeated = audioWaveformCacheKey("content://media/track", 0L, 5_000L)
        val trimmed = audioWaveformCacheKey("content://media/track", 1_000L, 5_000L)

        assertEquals(first, repeated)
        assertNotEquals(first, trimmed)
    }

    @Test
    fun cachePayloadRoundTripsNormalizedPeaks() {
        val expected = listOf(0f, 0.125f, 0.5f, 1f)
        val encoded = encodeAudioWaveformCache(expected)

        assertEquals(16 + expected.size * 4, encoded.size)
        assertEquals(expected, decodeAudioWaveformCache(encoded, expected.size))
    }

    @Test
    fun cachePayloadRejectsTruncationTrailingBytesAndWrongResolution() {
        val encoded = encodeAudioWaveformCache(listOf(0.25f, 0.75f))

        assertNull(decodeAudioWaveformCache(encoded.copyOf(encoded.size - 1), 2))
        assertNull(decodeAudioWaveformCache(encoded + byteArrayOf(0), 2))
        assertNull(decodeAudioWaveformCache(encoded, 1))

        val wrongMagic = encoded.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        assertNull(decodeAudioWaveformCache(wrongMagic, 2))

        val wrongVersion = encoded.copyOf().apply { this[7] = (this[7].toInt() xor 1).toByte() }
        assertNull(decodeAudioWaveformCache(wrongVersion, 2))
    }

    @Test
    fun cachePayloadChecksumRejectsCorruptionEvenWhenPeakRemainsInRange() {
        val encoded = encodeAudioWaveformCache(listOf(0.25f, 0.75f))
        encoded[16] = (encoded[16].toInt() xor 1).toByte()

        assertNull(decodeAudioWaveformCache(encoded, 2))
    }

    @Test
    fun cacheEncoderRejectsEmptyNonFiniteAndOutOfRangePeaks() {
        assertTrue(runCatching { encodeAudioWaveformCache(emptyList()) }.isFailure)
        assertTrue(runCatching { encodeAudioWaveformCache(listOf(Float.NaN)) }.isFailure)
        assertTrue(runCatching { encodeAudioWaveformCache(listOf(1.1f)) }.isFailure)
    }
}
