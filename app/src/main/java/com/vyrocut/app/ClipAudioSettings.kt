package com.vyrocut.app

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.ToInt16PcmAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
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

/** Fade durations are stored in milliseconds and may overlap for short clips. */
internal fun isValidClipAudioFades(fadeInMs: Long, fadeOutMs: Long): Boolean =
    fadeInMs >= 0L && fadeOutMs >= 0L

/**
 * Returns a linear fade envelope. When fades overlap, the lower of the two
 * ramps wins, guaranteeing silence at both clip boundaries without a gain boost.
 */
internal fun clipAudioFadeGainAtMs(
    positionMs: Long,
    clipDurationMs: Long,
    fadeInMs: Long,
    fadeOutMs: Long,
): Float {
    require(clipDurationMs > 0L) { "Clip duration must be positive for audio fades" }
    require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }
    val position = positionMs.coerceIn(0L, clipDurationMs)
    val fadeInGain = if (fadeInMs == 0L) {
        1.0
    } else {
        (position.toDouble() / fadeInMs.toDouble()).coerceIn(0.0, 1.0)
    }
    val fadeOutGain = if (fadeOutMs == 0L) {
        1.0
    } else {
        ((clipDurationMs - position).toDouble() / fadeOutMs.toDouble()).coerceIn(0.0, 1.0)
    }
    return min(fadeInGain, fadeOutGain).toFloat()
}

/**
 * Computes the fade envelope directly in decoded PCM frames. Keeping the
 * cursor in the sample-rate domain avoids the 1 ms stair-stepping that occurs
 * when 44.1/48 kHz frames are repeatedly rounded down to integer milliseconds.
 */
internal fun clipAudioFadeGainAtFrame(
    framePosition: Long,
    sampleRateHz: Int,
    clipDurationMs: Long,
    fadeInMs: Long,
    fadeOutMs: Long,
): Float {
    require(sampleRateHz > 0) { "Sample rate must be positive for audio fades" }
    require(clipDurationMs > 0L) { "Clip duration must be positive for audio fades" }
    require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }

    val sampleRate = sampleRateHz.toDouble()
    val durationFrames = clipDurationMs.toDouble() * sampleRate / 1_000.0
    val positionFrames = framePosition.coerceAtLeast(0L).toDouble().coerceIn(0.0, durationFrames)
    val fadeInFrames = fadeInMs.toDouble() * sampleRate / 1_000.0
    val fadeOutFrames = fadeOutMs.toDouble() * sampleRate / 1_000.0
    val fadeInGain = if (fadeInMs == 0L) {
        1.0
    } else {
        (positionFrames / fadeInFrames).coerceIn(0.0, 1.0)
    }
    val fadeOutGain = if (fadeOutMs == 0L) {
        1.0
    } else {
        ((durationFrames - positionFrames) / fadeOutFrames).coerceIn(0.0, 1.0)
    }
    return min(fadeInGain, fadeOutGain).toFloat()
}

internal fun clipAudioPlaybackGain(
    volume: Float,
    positionMs: Long,
    clipDurationMs: Long,
    fadeInMs: Long,
    fadeOutMs: Long,
): Float {
    require(isValidClipAudioVolume(volume)) { "Clip volume must be between 0 and 1" }
    return volume * clipAudioFadeGainAtMs(positionMs, clipDurationMs, fadeInMs, fadeOutMs)
}

/**
 * Applies per-clip gain and fade envelopes to 16-bit PCM. Transformer runs
 * ToInt16PcmAudioProcessor first so decoded PCM formats are normalized.
 * The frame cursor is local to this EditedMediaItem, so every clip starts at 0.
 */
@OptIn(UnstableApi::class)
internal class ClipGainAudioProcessor(
    private val volume: Float,
    private val clipDurationMs: Long = 0L,
    private val fadeInMs: Long = 0L,
    private val fadeOutMs: Long = 0L,
) : BaseAudioProcessor() {
    private var sampleRateHz = 0
    private var channelCount = 0
    private var framePosition = 0L

    init {
        require(isValidClipAudioVolume(volume)) { "Clip volume must be between 0 and 1" }
        require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }
        require((fadeInMs == 0L && fadeOutMs == 0L) || clipDurationMs > 0L) {
            "A positive clip duration is required when fades are enabled"
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        return if (volume == 1f && fadeInMs == 0L && fadeOutMs == 0L) {
            AudioProcessor.AudioFormat.NOT_SET
        } else {
            inputAudioFormat
        }
    }

    override fun onFlush() {
        framePosition = 0L
    }

    override fun onReset() {
        sampleRateHz = 0
        channelCount = 0
        framePosition = 0L
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytesPerFrame = channelCount * 2
        check(sampleRateHz > 0 && bytesPerFrame > 0) { "Clip gain audio processor was not configured" }
        val byteCount = inputBuffer.remaining()
        check(byteCount % bytesPerFrame == 0) { "Clip gain received an incomplete PCM frame" }

        val input = inputBuffer.order(ByteOrder.nativeOrder())
        val output = replaceOutputBuffer(byteCount)
        while (input.hasRemaining()) {
            val envelope = if (fadeInMs == 0L && fadeOutMs == 0L) {
                1f
            } else {
                clipAudioFadeGainAtFrame(
                    framePosition = framePosition,
                    sampleRateHz = sampleRateHz,
                    clipDurationMs = clipDurationMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs,
                )
            }
            val combinedGain = volume * envelope
            repeat(channelCount) {
                val scaled = (input.short.toFloat() * combinedGain)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.putShort(scaled.toShort())
            }
            if (framePosition < Long.MAX_VALUE) framePosition += 1L
        }
        output.flip()
    }

}

/** Builds the per-item Transformer processor chain. Unity gain without fades needs no work. */
@OptIn(UnstableApi::class)
internal fun createClipAudioProcessors(
    volume: Float,
    clipDurationMs: Long = 0L,
    fadeInMs: Long = 0L,
    fadeOutMs: Long = 0L,
): List<AudioProcessor> {
    require(isValidClipAudioVolume(volume)) { "Clip volume must be between 0 and 1" }
    require(isValidClipAudioFades(fadeInMs, fadeOutMs)) { "Clip fade durations cannot be negative" }
    if (volume == 1f && fadeInMs == 0L && fadeOutMs == 0L) return emptyList()
    return listOf(
        ToInt16PcmAudioProcessor(),
        ClipGainAudioProcessor(volume, clipDurationMs, fadeInMs, fadeOutMs),
    )
}
