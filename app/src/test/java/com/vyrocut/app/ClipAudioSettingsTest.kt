package com.vyrocut.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class ClipAudioSettingsTest {
    @Test
    fun missingStoredVolumeDefaultsToUnityAndValidValuesRoundTrip() {
        assertEquals(1f, decodeClipAudioVolume(null)!!, 0f)
        assertEquals(0f, decodeClipAudioVolume(0.0)!!, 0f)
        assertEquals(0.35f, decodeClipAudioVolume(0.35)!!, 0.0001f)
        assertEquals(1f, decodeClipAudioVolume(1.0)!!, 0f)
    }

    @Test
    fun rejectsNonFiniteAndOutOfRangeStoredVolumes() {
        assertEquals(null, decodeClipAudioVolume(Double.NaN))
        assertEquals(null, decodeClipAudioVolume(Double.POSITIVE_INFINITY))
        assertEquals(null, decodeClipAudioVolume(-0.01))
        assertEquals(null, decodeClipAudioVolume(1.01))
        assertFalse(isValidClipAudioVolume(Float.NaN))
        assertFalse(isValidClipAudioVolume(Float.POSITIVE_INFINITY))
        assertTrue(isValidClipAudioVolume(0f))
        assertTrue(isValidClipAudioVolume(1f))
    }

    @Test
    fun reportsClipVolumeAsAWholePercent() {
        assertEquals(0, clipAudioVolumePercent(0f))
        assertEquals(35, clipAudioVolumePercent(0.35f))
        assertEquals(100, clipAudioVolumePercent(1f))
    }

    @Test
    fun fadeEnvelopeReachesSilenceAtBothBoundaries() {
        assertEquals(0f, clipAudioFadeGainAtMs(0L, 1_000L, 200L, 300L), 0f)
        assertEquals(1f, clipAudioFadeGainAtMs(200L, 1_000L, 200L, 300L), 0f)
        assertEquals(1f, clipAudioFadeGainAtMs(700L, 1_000L, 200L, 300L), 0f)
        assertEquals(0f, clipAudioFadeGainAtMs(1_000L, 1_000L, 200L, 300L), 0f)
    }

    @Test
    fun overlappingFadesUseTheLowerRampAndRemainBounded() {
        assertEquals(0f, clipAudioFadeGainAtMs(0L, 1_000L, 800L, 800L), 0f)
        assertEquals(0.625f, clipAudioFadeGainAtMs(500L, 1_000L, 800L, 800L), 0.0001f)
        assertEquals(0f, clipAudioFadeGainAtMs(1_000L, 1_000L, 800L, 800L), 0f)
    }

    @Test
    fun playbackGainCombinesClipVolumeAndFadeEnvelope() {
        assertEquals(0.25f, clipAudioPlaybackGain(0.5f, 500L, 1_000L, 1_000L, 0L), 0.0001f)
    }

    @Test
    fun frameDomainFadeDoesNotQuantizeNonIntegerMillisecondSamplePositions() {
        val frame220 = clipAudioFadeGainAtFrame(
            framePosition = 220L,
            sampleRateHz = 44_100,
            clipDurationMs = 1_000L,
            fadeInMs = 10L,
            fadeOutMs = 0L,
        )
        val frame221 = clipAudioFadeGainAtFrame(
            framePosition = 221L,
            sampleRateHz = 44_100,
            clipDurationMs = 1_000L,
            fadeInMs = 10L,
            fadeOutMs = 0L,
        )

        assertEquals(220.0 / 441.0, frame220.toDouble(), 0.000001)
        assertEquals(221.0 / 441.0, frame221.toDouble(), 0.000001)
        assertTrue(frame221 > frame220)
    }

    @Test
    fun frameDomainFadeOutUsesTheExactDecodedFramePosition() {
        assertEquals(
            1f,
            clipAudioFadeGainAtFrame(47_520L, 48_000, 1_000L, 0L, 10L),
            0.000001f,
        )
        assertEquals(
            0.5f,
            clipAudioFadeGainAtFrame(47_760L, 48_000, 1_000L, 0L, 10L),
            0.000001f,
        )
        assertEquals(
            1.0 / 480.0,
            clipAudioFadeGainAtFrame(47_999L, 48_000, 1_000L, 0L, 10L).toDouble(),
            0.000001,
        )
    }

    @Test
    fun pcmFadeUsesSmoothFrameDomainGainAt44100Hz() {
        val output = processPcm(
            samples = ShortArray(222) { 1_000 },
            volume = 1f,
            sampleRateHz = 44_100,
            channelCount = 1,
            clipDurationMs = 1_000L,
            fadeInMs = 10L,
        )

        assertEquals(499, output[220])
        assertEquals(501, output[221])
    }

    @Test
    fun frameDomainFadeRejectsInvalidSampleRates() {
        try {
            clipAudioFadeGainAtFrame(0L, 0, 1_000L, 100L, 0L)
            throw AssertionError("A zero sample rate cannot define an audio fade")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun rejectsNegativeFadeDurationsAndInvalidClipDuration() {
        assertFalse(isValidClipAudioFades(-1L, 0L))
        assertFalse(isValidClipAudioFades(0L, -1L))
        try {
            clipAudioFadeGainAtMs(0L, 0L, 0L, 0L)
            throw AssertionError("Zero-duration clips cannot be faded")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun unityGainWithoutFadesDoesNotCreateAnUnnecessaryTransformerProcessor() {
        assertTrue(createClipAudioProcessors(1f).isEmpty())
    }

    @Test
    fun fadeAtUnityGainStillCreatesTheTransformerProcessorChain() {
        assertEquals(2, createClipAudioProcessors(1f, 1_000L, 250L, 0L).size)
    }

    @Test
    fun reducedAndMutedGainUseThePcmConversionAndGainProcessors() {
        assertEquals(2, createClipAudioProcessors(0.5f).size)
        assertEquals(2, createClipAudioProcessors(0f).size)
    }

    @Test
    fun appliesLinearGainToEveryInterleavedPcmSample() {
        val output = processPcm(shortArrayOf(1_000, -1_000, 2_000, -2_000), 0.5f)
        assertEquals(listOf(500, -500, 1_000, -1_000), output)
    }

    @Test
    fun zeroGainProducesSilenceWithoutChangingBufferLength() {
        val output = processPcm(shortArrayOf(12_000, -12_000, 32767, -32768), 0f)
        assertEquals(listOf(0, 0, 0, 0), output)
    }

    @Test
    fun appliesFadeInAtIndividualPcmFrameBoundaries() {
        val output = processPcm(
            samples = shortArrayOf(1_000, 1_000, 1_000, 1_000),
            volume = 1f,
            sampleRateHz = 1_000,
            channelCount = 1,
            clipDurationMs = 4L,
            fadeInMs = 4L,
        )
        assertEquals(listOf(0, 250, 500, 750), output)
    }

    @Test
    fun appliesFadeOutAtIndividualPcmFrameBoundaries() {
        val output = processPcm(
            samples = shortArrayOf(1_000, 1_000, 1_000, 1_000),
            volume = 1f,
            sampleRateHz = 1_000,
            channelCount = 1,
            clipDurationMs = 4L,
            fadeOutMs = 4L,
        )
        assertEquals(listOf(1_000, 750, 500, 250), output)
    }

    @Test
    fun fadesKeepTheOriginalPcmBufferLength() {
        val output = processPcm(
            samples = shortArrayOf(10_000, -10_000, 10_000, -10_000),
            volume = 0.5f,
            sampleRateHz = 1_000,
            channelCount = 2,
            clipDurationMs = 2L,
            fadeInMs = 2L,
        )
        assertEquals(listOf(0, 0, 2_500, -2_500), output)
    }

    @Test
    fun unityProcessorIsInactiveAndUnsupportedPcmEncodingIsRejected() {
        val unity = ClipGainAudioProcessor(1f)
        val unityFormat = unity.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        assertFalse(unity.isActive())
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, unityFormat)

        val reduced = ClipGainAudioProcessor(0.5f)
        try {
            reduced.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_FLOAT))
            throw AssertionError("Float PCM must be converted before clip gain")
        } catch (_: AudioProcessor.UnhandledAudioFormatException) {
            // The production processor chain converts to 16-bit PCM first.
        }
    }

    @Test
    fun visualAdjustmentValuesAreBoundedAndLegacyDefaultsAreNeutral() {
        assertTrue(isValidClipVisualAdjustments(0, 0, 0))
        assertTrue(isValidClipVisualAdjustments(-100, 100, -100))
        assertFalse(isValidClipVisualAdjustments(-101, 0, 0))
        assertFalse(isValidClipVisualAdjustments(0, 101, 0))
        assertFalse(isValidClipVisualAdjustments(0, 0, 101))
        assertEquals(ClipVisualAdjustments(), decodeClipVisualAdjustments(0, 0, 0))
        assertEquals(null, decodeClipVisualAdjustments(101, 0, 0))
    }

    @Test
    fun describesOnlyTheRequestedColourEffectsInStablePreviewAndExportOrder() {
        assertTrue(createClipVisualEffectSpecs(ClipVisualAdjustments()).isEmpty())

        val specs = createClipVisualEffectSpecs(
            ClipVisualAdjustments(brightness = 35, contrast = -20, saturation = 55),
        )

        assertEquals(
            listOf(
                ClipVisualEffectSpec(ClipVisualEffectType.BRIGHTNESS, 0.35f),
                ClipVisualEffectSpec(ClipVisualEffectType.CONTRAST, -0.2f),
                ClipVisualEffectSpec(ClipVisualEffectType.SATURATION, 55f),
            ),
            specs,
        )
    }

    private fun processPcm(
        samples: ShortArray,
        volume: Float,
        sampleRateHz: Int = 48_000,
        channelCount: Int = 2,
        clipDurationMs: Long = 0L,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
    ): List<Int> {
        val processor = ClipGainAudioProcessor(volume, clipDurationMs, fadeInMs, fadeOutMs)
        processor.configure(AudioProcessor.AudioFormat(sampleRateHz, channelCount, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach(input::putShort)
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        val result = buildList {
            while (output.hasRemaining()) add(output.short.toInt())
        }
        processor.reset()
        return result
    }
}
