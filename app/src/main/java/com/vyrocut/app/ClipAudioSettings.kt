package com.vyrocut.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal const val DEFAULT_CLIP_AUDIO_VOLUME = 1f

/** Stored clip gain is linear and intentionally limited to silence..unity. */
internal fun decodeClipAudioVolume(raw: Double?): Float? {
    if (raw == null) return DEFAULT_CLIP_AUDIO_VOLUME
    if (!raw.isFinite() || raw < 0.0 || raw > 1.0) return null
    return raw.toFloat()
}

internal fun isValidClipAudioVolume(volume: Float): Boolean =
    volume.isFinite() && volume in 0f..1f

internal fun clipAudioVolumePercent(volume: Float): Int =
    (volume.coerceIn(0f, 1f) * 100f).roundToInt()

/**
 * Applies per-clip linear gain to 16-bit PCM. Transformer runs ToInt16PcmAudioProcessor
 * first so float, 8-bit, 24-bit and 32-bit decoded PCM are normalized before gain.
 */
@OptIn(UnstableApi::class)
internal class ClipGainAudioProcessor(
    private val volume: Float,
) : BaseAudioProcessor() {
    init {
        require(isValidClipAudioVolume(volume)) { "Clip volume must be between 0 and 1" }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return if (volume == 1f) AudioProcessor.AudioFormat.NOT_SET else inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        check(byteCount % 2 == 0) { "Clip gain received a partial 16-bit PCM sample" }
        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(byteCount)
        while (input.hasRemaining()) {
            val scaled = (input.short.toFloat() * volume)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(scaled.toShort())
        }
        output.flip()
    }
}

/** Builds the per-item Transformer processor chain. Unity gain needs no audio work. */
@OptIn(UnstableApi::class)
internal fun createClipAudioProcessors(volume: Float): List<AudioProcessor> {
    require(isValidClipAudioVolume(volume)) { "Clip volume must be between 0 and 1" }
    if (volume == 1f) return emptyList()
    return listOf(ToInt16PcmAudioProcessor(), ClipGainAudioProcessor(volume))
}
