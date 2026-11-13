package com.vyrocut.app

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs

internal const val AUDIO_WAVEFORM_BUCKET_COUNT = 96
internal const val MAX_AUDIO_WAVEFORM_RANGE_MS = 15L * 60L * 1_000L

private const val AUDIO_WAVEFORM_CACHE_MAGIC = 0x56594357
private const val AUDIO_WAVEFORM_CACHE_VERSION = 1
private const val MAX_AUDIO_WAVEFORM_CACHE_FILES = 64

/** Fixed-size peak accumulator. It never retains decoded PCM samples. */
internal class AudioWaveformAccumulator(
    private val bucketCount: Int,
    private val startTimeUs: Long,
    private val endTimeUs: Long,
) {
    private val peaks: FloatArray

    init {
        require(bucketCount in 1..512)
        require(startTimeUs >= 0L && endTimeUs > startTimeUs)
        peaks = FloatArray(bucketCount)
    }

    fun addFrame(presentationTimeUs: Long, channelPeak: Float) {
        if (presentationTimeUs < startTimeUs || presentationTimeUs >= endTimeUs) return
        if (!channelPeak.isFinite()) return
        val offsetUs = presentationTimeUs - startTimeUs
        val durationUs = endTimeUs - startTimeUs
        val index = ((offsetUs.toDouble() / durationUs.toDouble()) * bucketCount)
            .toInt()
            .coerceIn(0, bucketCount - 1)
        peaks[index] = maxOf(peaks[index], abs(channelPeak).coerceIn(0f, 1f))
    }

    /** Normalizes all buckets to the strongest decoded peak; silence remains zero. */
    fun finish(): List<Float> {
        val maximum = peaks.maxOrNull() ?: 0f
        if (maximum <= 0.000001f) return peaks.map { 0f }
        return peaks.map { (it / maximum).coerceIn(0f, 1f) }
    }
}

internal fun audioWaveformCacheKey(clip: AudioTrackClip): String =
    audioWaveformCacheKey(clip.uri, clip.sourceStartMs, clip.sourceEndMs)

internal fun audioWaveformCacheKey(uri: String, sourceStartMs: Long, sourceEndMs: Long): String {
    val input = "waveform-v1|$uri|$sourceStartMs|$sourceEndMs"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}

/**
 * Decodes only the selected source range with the platform audio decoder.
 * The cache is disposable app-local data; projects and original media are untouched.
 */
internal fun decodeAudioWaveform(
    context: Context,
    uriString: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    bucketCount: Int = AUDIO_WAVEFORM_BUCKET_COUNT,
): List<Float>? {
    if (uriString.isBlank() || sourceStartMs < 0L ||
        sourceEndMs - sourceStartMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS ||
        sourceEndMs - sourceStartMs > MAX_AUDIO_WAVEFORM_RANGE_MS ||
        bucketCount !in 1..512
    ) return null

    val cacheKey = audioWaveformCacheKey(uriString, sourceStartMs, sourceEndMs)
    val cacheDirectory = File(context.cacheDir, "audio-waveforms")
    val cacheFile = File(cacheDirectory, "$cacheKey.bin")
    readWaveformCache(cacheFile, bucketCount)?.let { return it }

    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var decoderStarted = false
    try {
        extractor.setDataSource(context, Uri.parse(uriString), null)
        var audioTrackIndex = -1
        var inputFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = index
                inputFormat = candidate
                break
            }
        }
        if (audioTrackIndex < 0 || inputFormat == null) return null
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
        val startUs = sourceStartMs * 1_000L
        val endUs = sourceEndMs * 1_000L

        extractor.selectTrack(audioTrackIndex)
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()
        decoderStarted = true

        var outputFormat = inputFormat
        var inputFinished = false
        var outputFinished = false
        var idlePolls = 0
        val info = MediaCodec.BufferInfo()
        val accumulator = AudioWaveformAccumulator(bucketCount, startUs, endUs)

        while (!outputFinished && idlePolls < 5_000) {
            var madeProgress = false

            if (!inputFinished) {
                val inputIndex = decoder.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                        ?: throw IllegalStateException("Decoder input buffer was unavailable")
                    inputBuffer.clear()
                    val sampleTimeUs = extractor.sampleTime
                    if (sampleTimeUs < 0L || sampleTimeUs >= endUs) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            endUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputFinished = true
                    } else {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                endUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputFinished = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                sampleTimeUs,
                                extractor.sampleFlags,
                            )
                            extractor.advance()
                        }
                    }
                    madeProgress = true
                }
            }

            val outputIndex = decoder.dequeueOutputBuffer(info, 10_000L)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (info.size > 0 && outputBuffer != null) {
                        accumulatePcmFrames(outputBuffer, info, outputFormat, accumulator)
                    }
                    val reachedEnd = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (reachedEnd) outputFinished = true
                    madeProgress = true
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    outputFormat = decoder.outputFormat
                    madeProgress = true
                }
                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> madeProgress = true
            }

            idlePolls = if (madeProgress) 0 else idlePolls + 1
        }

        if (!outputFinished) return null
        val waveform = accumulator.finish()
        writeWaveformCache(cacheDirectory, cacheFile, waveform)
        return waveform
    } catch (_: Exception) {
        return null
    } finally {
        decoder?.let { codec ->
            if (decoderStarted) runCatching { codec.stop() }
            runCatching { codec.release() }
        }
        runCatching { extractor.release() }
    }
}

private fun accumulatePcmFrames(
    source: ByteBuffer,
    info: MediaCodec.BufferInfo,
    format: MediaFormat,
    accumulator: AudioWaveformAccumulator,
) {
    if (!format.containsKey(MediaFormat.KEY_SAMPLE_RATE) ||
        !format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
    ) return

    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    if (sampleRate <= 0 || channels <= 0) return

    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }
    val bytesPerSample = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT,
        AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> return
    }
    val bytesPerFrame = bytesPerSample * channels
    if (bytesPerFrame <= 0) return

    val pcm = source.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    val start = info.offset.coerceIn(0, pcm.capacity())
    val end = (info.offset.toLong() + info.size.toLong())
        .coerceIn(start.toLong(), pcm.capacity().toLong()).toInt()
    if (end <= start) return
    pcm.position(start)
    pcm.limit(end)

    val frameCount = pcm.remaining() / bytesPerFrame
    for (frame in 0 until frameCount) {
        var peak = 0f
        repeat(channels) {
            val sample = readPcmSample(pcm, encoding) ?: return
            peak = maxOf(peak, abs(sample))
        }
        val frameTimeUs = info.presentationTimeUs +
            (frame.toLong() * 1_000_000L / sampleRate.toLong())
        accumulator.addFrame(frameTimeUs, peak)
    }
}

private fun readPcmSample(buffer: ByteBuffer, encoding: Int): Float? = when (encoding) {
    AudioFormat.ENCODING_PCM_8BIT ->
        ((buffer.get().toInt() and 0xff) - 128) / 128f
    AudioFormat.ENCODING_PCM_16BIT ->
        buffer.short.toFloat() / 32_768f
    AudioFormat.ENCODING_PCM_FLOAT ->
        buffer.float.takeIf { it.isFinite() } ?: 0f
    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
        val low = buffer.get().toInt() and 0xff
        val middle = buffer.get().toInt() and 0xff
        val high = buffer.get().toInt() and 0xff
        val packed = low or (middle shl 8) or (high shl 16)
        val signed = if (packed and 0x800000 != 0) packed or -0x1000000 else packed
        signed / 8_388_608f
    }
    AudioFormat.ENCODING_PCM_32BIT ->
        (buffer.int.toDouble() / 2_147_483_648.0).toFloat()
    else -> null
}

private fun readWaveformCache(file: File, bucketCount: Int): List<Float>? = runCatching {
    if (!file.isFile || file.length() != 8L + bucketCount * 4L) return null
    DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
        require(input.readInt() == AUDIO_WAVEFORM_CACHE_MAGIC)
        require(input.readInt() == bucketCount)
        val values = List(bucketCount) {
            input.readFloat().also { value -> require(value.isFinite() && value in 0f..1f) }
        }
        require(input.read() == -1)
        values
    }
}.getOrNull()

private fun writeWaveformCache(directory: File, destination: File, values: List<Float>) {
    runCatching {
        if (!directory.exists()) directory.mkdirs()
        val temporary = File(directory, destination.name + ".tmp")
        DataOutputStream(BufferedOutputStream(FileOutputStream(temporary))).use { output ->
            output.writeInt(AUDIO_WAVEFORM_CACHE_MAGIC)
            output.writeInt(values.size)
            values.forEach(output::writeFloat)
        }
        if (!temporary.renameTo(destination)) {
            temporary.copyTo(destination, overwrite = true)
            temporary.delete()
        }
        directory.listFiles()
            ?.filter { it.isFile && it.extension == "bin" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_AUDIO_WAVEFORM_CACHE_FILES)
            ?.forEach { it.delete() }
    }
}

@androidx.compose.runtime.Composable
internal fun AudioWaveformStrip(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 24.dp,
) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val centerY = size.height / 2f
        drawLine(
            color = color.copy(alpha = 0.28f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.dp.toPx(),
        )
        if (amplitudes.isEmpty()) return@Canvas

        val bucketWidth = size.width / amplitudes.size.toFloat()
        val strokeWidth = (bucketWidth * 0.58f).coerceIn(1.dp.toPx(), 3.dp.toPx())
        amplitudes.forEachIndexed { index, amplitude ->
            val normalized = amplitude.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            val halfHeight = (normalized * size.height * 0.46f).coerceAtLeast(1.dp.toPx())
            val x = (index + 0.5f) * bucketWidth
            drawLine(
                color = color,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
