package com.vyrocut.app

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.File

/**
 * Chooses three spread-out timestamps so an MP4 is checked at its beginning,
 * middle, and end rather than only trusting container track metadata.
 */
internal fun exportFrameProbePositionsMs(expectedDurationMs: Long): List<Long> {
    if (expectedDurationMs <= 0L) return emptyList()
    return listOf(
        0L,
        expectedDurationMs / 2L,
        (expectedDurationMs - 1L).coerceAtLeast(0L),
    ).distinct()
}

/** Pure frame contract shared by runtime verification and local unit tests. */
internal fun validateDecodedExportFrameDimensions(
    actualWidthPx: Int,
    actualHeightPx: Int,
    expectedCanvasSize: ExportCanvasSize,
): String? {
    if (actualWidthPx <= 0 || actualHeightPx <= 0) {
        return "The exported MP4 contains an unreadable video frame"
    }
    if (actualWidthPx != expectedCanvasSize.widthPx || actualHeightPx != expectedCanvasSize.heightPx) {
        return "Decoded frame ${actualWidthPx}x${actualHeightPx} does not match selected ${expectedCanvasSize.widthPx}x${expectedCanvasSize.heightPx} canvas"
    }
    return null
}

/**
 * Uses the platform decoder to render real frames from the completed MP4.
 * MediaExtractor sample counts alone do not prove that a decoder can render
 * the output. Frames are decoded sequentially and recycled immediately to
 * keep peak memory bounded on minSdk 29 devices.
 */
internal fun verifyExportedVideoFrames(
    file: File,
    expectedDurationMs: Long,
    expectedCanvasSize: ExportCanvasSize,
) {
    val probes = exportFrameProbePositionsMs(expectedDurationMs)
    check(probes.isNotEmpty()) { "The project has no playable duration to decode" }

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        probes.forEach { positionMs ->
            val timeUs = if (positionMs > Long.MAX_VALUE / 1_000L) {
                Long.MAX_VALUE
            } else {
                positionMs * 1_000L
            }
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("Could not decode an MP4 video frame near ${positionMs}ms")
            try {
                validateDecodedExportFrameDimensions(
                    actualWidthPx = frame.width,
                    actualHeightPx = frame.height,
                    expectedCanvasSize = expectedCanvasSize,
                )?.let(::error)
            } finally {
                frame.recycle()
            }
        }
    } finally {
        retriever.release()
    }
}

/**
 * Decodes the first available audio output buffer. This catches files that
 * contain an audio track and demuxable packets but cannot produce PCM.
 */
internal fun verifyExportedAudioSamples(file: File) {
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
        extractor.setDataSource(file.absolutePath)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val candidate = extractor.getTrackFormat(index)
            if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                audioTrackIndex = index
                audioFormat = candidate
                break
            }
        }
        val format = audioFormat ?: error("The exported MP4 has no audio track to decode")
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: error("The exported MP4 audio track has no codec MIME type")

        extractor.selectTrack(audioTrackIndex)
        decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var decodedPcm = false
        var outputEnded = false
        var attempts = 0

        while (!decodedPcm && !outputEnded && attempts++ < 1_000) {
            if (!inputEnded) {
                val inputIndex = decoder.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex)
                        ?: error("Could not access an audio decoder input buffer")
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
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
            }

            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            if (outputIndex >= 0) {
                decodedPcm = bufferInfo.size > 0
                outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                decoder.releaseOutputBuffer(outputIndex, false)
            }
        }

        check(decodedPcm) { "The exported MP4 audio track did not produce decodable PCM samples" }
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        extractor.release()
    }
}
