package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
