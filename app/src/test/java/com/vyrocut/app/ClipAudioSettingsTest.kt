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
    fun unityGainDoesNotCreateAnUnnecessaryTransformerProcessor() {
        assertTrue(createClipAudioProcessors(1f).isEmpty())
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

    private fun processPcm(samples: ShortArray, volume: Float): List<Int> {
        val processor = ClipGainAudioProcessor(volume)
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
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
