package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioWaveformTest {
    @Test
    fun waveformDecodeLoopStopsForCancellationCompletionAndIdleTimeout() {
        assertTrue(shouldContinueAudioWaveformDecode(true, false, 0))
        assertTrue(
            shouldContinueAudioWaveformDecode(
                isActive = true,
                outputFinished = false,
                idlePolls = MAX_AUDIO_WAVEFORM_IDLE_POLLS - 1,
            ),
        )
        assertFalse(shouldContinueAudioWaveformDecode(false, false, 0))
        assertFalse(shouldContinueAudioWaveformDecode(true, true, 0))
        assertFalse(
            shouldContinueAudioWaveformDecode(
                isActive = true,
                outputFinished = false,
                idlePolls = MAX_AUDIO_WAVEFORM_IDLE_POLLS,
            ),
        )
        assertFalse(
            shouldContinueAudioWaveformDecode(
                isActive = true,
                outputFinished = false,
                idlePolls = 2,
                maxIdlePolls = 2,
            ),
        )
        assertTrue(runCatching {
            shouldContinueAudioWaveformDecode(true, false, 0, maxIdlePolls = 0)
        }.isFailure)
    }

    @Test
    fun waveformDecodeIdleWatchdogResetsOnProgressAndDoesNotOverflow() {
        assertEquals(0, nextAudioWaveformIdlePollCount(17, madeProgress = true))
        assertEquals(18, nextAudioWaveformIdlePollCount(17, madeProgress = false))
        assertEquals(
            Int.MAX_VALUE,
            nextAudioWaveformIdlePollCount(Int.MAX_VALUE, madeProgress = false),
        )
        assertTrue(runCatching {
            nextAudioWaveformIdlePollCount(-1, madeProgress = false)
        }.isFailure)
    }

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
    fun memoryWaveformKeyMatchesUiLookupWhileDiskKeyTracksSourceRevision() {
        val clip = AudioTrackClip(
            id = "music-1",
            type = AudioTrackType.MUSIC,
            uri = "content://media/audio",
            name = "Music",
            startMs = 0L,
            sourceStartMs = 500L,
            sourceEndMs = 5_500L,
            sourceDurationMs = 10_000L,
        )
        val memoryKey = audioWaveformMemoryCacheKey(clip)

        assertEquals(audioWaveformCacheKey(clip), memoryKey)
        assertNotEquals(memoryKey, audioWaveformCacheKey(clip, "size=1024;modified=2000"))
        assertEquals("unversioned", audioWaveformSourceRevisionKey(null))
    }

    @Test
    fun waveformRefreshesWhenSourceRevisionChangesOrPreviousDecodeWasNotReady() {
        assertTrue(
            shouldDecodeAudioWaveform(
                hasCachedWaveform = false,
                cachedSourceRevisionKey = null,
                currentSourceRevisionKey = "size=100;modified=1",
                loadState = null,
            ),
        )
        assertFalse(
            shouldDecodeAudioWaveform(
                hasCachedWaveform = true,
                cachedSourceRevisionKey = "size=100;modified=1",
                currentSourceRevisionKey = "size=100;modified=1",
                loadState = AudioWaveformLoadState.READY,
            ),
        )
        assertTrue(
            shouldDecodeAudioWaveform(
                hasCachedWaveform = true,
                cachedSourceRevisionKey = "size=100;modified=1",
                currentSourceRevisionKey = "size=200;modified=2",
                loadState = AudioWaveformLoadState.READY,
            ),
        )
        assertTrue(
            shouldDecodeAudioWaveform(
                hasCachedWaveform = false,
                cachedSourceRevisionKey = "size=100;modified=1",
                currentSourceRevisionKey = "size=100;modified=1",
                loadState = AudioWaveformLoadState.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun waveformStatusDistinguishesLoadingUnavailableSilenceAndAudio() {
        assertEquals(
            "Loading waveform…",
            audioWaveformStatusLabel(AudioWaveformLoadState.LOADING, null),
        )
        assertEquals(
            "Waveform unavailable",
            audioWaveformStatusLabel(AudioWaveformLoadState.UNAVAILABLE, null),
        )
        assertEquals(
            "Silent audio",
            audioWaveformStatusLabel(AudioWaveformLoadState.READY, List(96) { 0f }),
        )
        assertNull(
            audioWaveformStatusLabel(AudioWaveformLoadState.READY, List(96) { 0.25f }),
        )
        assertNull(audioWaveformStatusLabel(null, null))
    }

    @Test
    fun waveformUiCacheDropsEntriesForRemovedAndTrimmedClipsAcrossAllLanes() {
        val retained = retainAudioWaveformUiCache(
            activeMemoryKeys = setOf("music-current", "voiceover-current"),
            waveforms = mapOf(
                "music-current" to listOf(0.25f, 0.75f),
                "voiceover-current" to listOf(0.5f),
                "music-old-trim" to listOf(1f),
                "deleted-sfx" to listOf(0.1f),
            ),
            sourceRevisions = mapOf(
                "music-current" to "size=100;modified=1",
                "voiceover-current" to "size=200;modified=2",
                "music-old-trim" to "size=100;modified=1",
            ),
            loadStates = mapOf(
                "music-current" to AudioWaveformLoadState.READY,
                "voiceover-current" to AudioWaveformLoadState.LOADING,
                "deleted-sfx" to AudioWaveformLoadState.UNAVAILABLE,
            ),
        )

        assertEquals(
            mapOf(
                "music-current" to listOf(0.25f, 0.75f),
                "voiceover-current" to listOf(0.5f),
            ),
            retained.waveforms,
        )
        assertEquals(
            mapOf(
                "music-current" to "size=100;modified=1",
                "voiceover-current" to "size=200;modified=2",
            ),
            retained.sourceRevisions,
        )
        assertEquals(
            mapOf(
                "music-current" to AudioWaveformLoadState.READY,
                "voiceover-current" to AudioWaveformLoadState.LOADING,
            ),
            retained.loadStates,
        )

        val emptyProject = retainAudioWaveformUiCache(
            activeMemoryKeys = emptySet(),
            waveforms = retained.waveforms,
            sourceRevisions = retained.sourceRevisions,
            loadStates = retained.loadStates,
        )
        assertTrue(emptyProject.waveforms.isEmpty())
        assertTrue(emptyProject.sourceRevisions.isEmpty())
        assertTrue(emptyProject.loadStates.isEmpty())
    }

    @Test
    fun waveformBarsOmitSilenceAndInvalidSamples() {
        assertNull(audioWaveformBarHalfHeightPx(0f, 100f, 1f))
        assertNull(audioWaveformBarHalfHeightPx(-0.5f, 100f, 1f))
        assertNull(audioWaveformBarHalfHeightPx(Float.NaN, 100f, 1f))
        assertNull(audioWaveformBarHalfHeightPx(Float.POSITIVE_INFINITY, 100f, 1f))
    }

    @Test
    fun waveformBarsScalePositivePeaksAndRespectCanvasBounds() {
        assertEquals(23f, audioWaveformBarHalfHeightPx(0.5f, 100f, 1f)!!, 0.0001f)
        assertEquals(46f, audioWaveformBarHalfHeightPx(2f, 100f, 1f)!!, 0.0001f)
        assertEquals(1f, audioWaveformBarHalfHeightPx(0.001f, 100f, 1f)!!, 0.0001f)
        assertEquals(5f, audioWaveformBarHalfHeightPx(1f, 10f, 8f)!!, 0.0001f)
    }

    @Test
    fun waveformBarsRejectInvalidCanvasGeometry() {
        assertNull(audioWaveformBarHalfHeightPx(0.5f, 0f, 1f))
        assertNull(audioWaveformBarHalfHeightPx(0.5f, Float.NaN, 1f))
        assertNull(audioWaveformBarHalfHeightPx(0.5f, 100f, -1f))
        assertNull(audioWaveformBarHalfHeightPx(0.5f, 100f, Float.POSITIVE_INFINITY))
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
    @Test
    fun staleWaveformTempCleanupUsesAgeAndOnlyMatchesOwnedTempFiles() {
        val nowMs = 100_000L
        val maxAgeMs = 60_000L

        assertTrue(
            isStaleAudioWaveformTempFile(
                fileName = "waveform-ab12.tmp",
                lastModifiedMs = 40_000L,
                nowMs = nowMs,
                maxAgeMs = maxAgeMs,
            ),
        )
        assertFalse(
            isStaleAudioWaveformTempFile(
                fileName = "waveform-active.tmp",
                lastModifiedMs = 40_001L,
                nowMs = nowMs,
                maxAgeMs = maxAgeMs,
            ),
        )
        assertFalse(
            isStaleAudioWaveformTempFile(
                fileName = "waveform-cache.bin",
                lastModifiedMs = 1L,
                nowMs = nowMs,
                maxAgeMs = maxAgeMs,
            ),
        )
        assertFalse(
            isStaleAudioWaveformTempFile(
                fileName = "other-process.tmp",
                lastModifiedMs = 1L,
                nowMs = nowMs,
                maxAgeMs = maxAgeMs,
            ),
        )
        assertFalse(
            isStaleAudioWaveformTempFile(
                fileName = "waveform-future.tmp",
                lastModifiedMs = nowMs + 1L,
                nowMs = nowMs,
                maxAgeMs = maxAgeMs,
            ),
        )
        assertFalse(
            isStaleAudioWaveformTempFile(
                fileName = "waveform-invalid-age.tmp",
                lastModifiedMs = 1L,
                nowMs = nowMs,
                maxAgeMs = 0L,
            ),
        )
    }


    @Test
    fun cacheEvictionRetainsRecentlyUsedWaveforms() {
        val entries = listOf(
            AudioWaveformCacheEntry("old.bin", 100L),
            AudioWaveformCacheEntry("recent.bin", 300L),
            AudioWaveformCacheEntry("middle.bin", 200L),
        )

        assertEquals(
            listOf("old.bin"),
            audioWaveformCacheFilesToEvict(entries, maxFiles = 2),
        )
    }

    @Test
    fun cacheEvictionUsesDeterministicTieBreaksAndHonorsTheLimit() {
        val entries = listOf(
            AudioWaveformCacheEntry("z.bin", 100L),
            AudioWaveformCacheEntry("a.bin", 100L),
            AudioWaveformCacheEntry("b.bin", 200L),
        )

        assertEquals(listOf("a.bin"), audioWaveformCacheFilesToEvict(entries, maxFiles = 2))
        assertTrue(audioWaveformCacheFilesToEvict(entries, maxFiles = 3).isEmpty())
        assertEquals(
            listOf("a.bin", "z.bin", "b.bin"),
            audioWaveformCacheFilesToEvict(entries, maxFiles = 0),
        )
    }

    @Test
    fun cacheEvictionRejectsNegativeLimits() {
        assertTrue(
            runCatching {
                audioWaveformCacheFilesToEvict(emptyList(), maxFiles = -1)
            }.isFailure,
        )
    }


    @Test
    fun cacheKeySeparatesSourceRevisionsForTheSameUriAndTrim() {
        val uri = "content://media/audio"
        val firstRevision = "size=1024;modified=2000"
        val updatedTimestamp = "size=1024;modified=3000"
        val updatedSize = "size=2048;modified=2000"

        val firstKey = audioWaveformCacheKey(uri, 0L, 5_000L, 96, firstRevision)
        assertEquals(firstKey, audioWaveformCacheKey(uri, 0L, 5_000L, 96, firstRevision))
        assertNotEquals(firstKey, audioWaveformCacheKey(uri, 0L, 5_000L, 96, updatedTimestamp))
        assertNotEquals(firstKey, audioWaveformCacheKey(uri, 0L, 5_000L, 96, updatedSize))
        assertNotEquals(
            audioWaveformCacheFileName(uri, 0L, 5_000L, 96, firstRevision),
            audioWaveformCacheFileName(uri, 0L, 5_000L, 96, updatedTimestamp),
        )
    }

    @Test
    fun sourceRevisionUsesAvailableMetadataAndFailsOpenWhenProviderMetadataIsMissing() {
        assertEquals(
            "size=1024;modified=2000",
            audioWaveformSourceRevision(sizeBytes = 1_024L, lastModifiedMs = 2_000L),
        )
        assertEquals(
            "size=1024;modified=?",
            audioWaveformSourceRevision(sizeBytes = 1_024L, lastModifiedMs = null),
        )
        assertEquals(
            "size=?;modified=2000",
            audioWaveformSourceRevision(sizeBytes = null, lastModifiedMs = 2_000L),
        )
        assertEquals(
            "size=0;modified=0",
            audioWaveformSourceRevision(sizeBytes = 0L, lastModifiedMs = 0L),
        )
        assertNull(audioWaveformSourceRevision(sizeBytes = null, lastModifiedMs = null))
        assertNull(audioWaveformSourceRevision(sizeBytes = -1L, lastModifiedMs = -1L))
        assertEquals("size=1024;modified=2000", mediaSourceRevision(1_024L, 2_000L))
        assertEquals("size=1024;modified=?", mediaSourceRevision(1_024L, null))
        assertEquals("size=?;modified=2000", mediaSourceRevision(null, 2_000L))
        assertNull(mediaSourceRevision(null, null))
    }

}
