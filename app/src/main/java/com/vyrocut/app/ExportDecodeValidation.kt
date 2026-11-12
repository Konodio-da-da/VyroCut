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

private const val MAX_ALLOWED_AUDIO_GAP_US = 100_000L

internal data class DecodedAudioCoverage(
    val pcmBufferCount: Int,
    val firstPresentationTimeUs: Long,
    val lastPresentationTimeUs: Long,
    val decodedEndTimeUs: Long,
    val timestampsMonotonic: Boolean,
    val reachedEndOfStream: Boolean,
    val maximumGapUs: Long = 0L,
    val totalGapUs: Long = 0L,
)

/**
 * Validates decoded PCM coverage against the project timeline. A successful
 * demux or one initial PCM buffer is not enough: the decoder must reach EOS
 * and produce PCM through the expected end of the composition.
 */
internal fun validateDecodedAudioCoverage(
    coverage: DecodedAudioCoverage,
    expectedDurationMs: Long,
): String? {
    if (expectedDurationMs <= 0L) return "The project has no playable duration for audio verification"
    if (coverage.pcmBufferCount <= 0) {
        return "The exported MP4 audio track did not produce decodable PCM samples"
    }
    if (!coverage.timestampsMonotonic) {
        return "The exported MP4 audio decoder returned out-of-order PCM timestamps"
    }
    if (!coverage.reachedEndOfStream) {
        return "The exported MP4 audio track could not be decoded through its end"
    }
    if (coverage.maximumGapUs > MAX_ALLOWED_AUDIO_GAP_US) {
        return "The exported MP4 audio contains a gap longer than 100ms"
    }

    val expectedDurationUs = if (expectedDurationMs > Long.MAX_VALUE / 1_000L) {
        Long.MAX_VALUE
    } else {
        expectedDurationMs * 1_000L
    }
    val allowedTotalGapUs = maxOf(MAX_ALLOWED_AUDIO_GAP_US, expectedDurationUs / 200L)
    if (coverage.totalGapUs > allowedTotalGapUs) {
        return "The exported MP4 audio contains too much cumulative silence between decoded buffers"
    }

    // Audio codec priming/padding and the final PCM frame can differ slightly
    // from the video clock. Keep the allowance tight (0.1%, at least 250 ms).
    val toleranceUs = maxOf(250_000L, expectedDurationUs / 1_000L)
    val earliestAcceptedStartUs = -toleranceUs
    val latestAcceptedStartUs = toleranceUs
    if (coverage.firstPresentationTimeUs > latestAcceptedStartUs) {
        return "The exported MP4 audio starts after the project timeline"
    }
    if (coverage.firstPresentationTimeUs < earliestAcceptedStartUs) {
        return "The exported MP4 audio starts before the project timeline"
    }

    val earliestAcceptedEndUs = (expectedDurationUs - toleranceUs).coerceAtLeast(0L)
    val latestAcceptedEndUs = if (expectedDurationUs > Long.MAX_VALUE - toleranceUs) {
        Long.MAX_VALUE
    } else {
        expectedDurationUs + toleranceUs
    }
    if (coverage.decodedEndTimeUs < earliestAcceptedEndUs) {
        return "The exported MP4 audio ends before the project timeline"
    }
    if (coverage.decodedEndTimeUs > latestAcceptedEndUs) {
        return "The exported MP4 audio extends beyond the project timeline"
    }
    if (coverage.lastPresentationTimeUs < coverage.firstPresentationTimeUs) {
        return "The exported MP4 audio timestamps are invalid"
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
 * Decodes the complete exported audio track to EOS. PCM timestamps and the
 * byte count of each decoded buffer are used to measure its actual timeline
 * coverage, rather than accepting the first non-empty output buffer.
 */
internal fun verifyExportedAudioSamples(file: File, expectedDurationMs: Long) {
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
        val activeDecoder = MediaCodec.createDecoderByType(mime)
        decoder = activeDecoder
        activeDecoder.configure(format, null, null, 0)
        activeDecoder.start()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var idleIterations = 0
        var pcmBufferCount = 0
        var firstPresentationTimeUs = Long.MAX_VALUE
        var lastPresentationTimeUs = Long.MIN_VALUE
        var decodedEndTimeUs = Long.MIN_VALUE
        var timestampsMonotonic = true
        var previousDecodedEndTimeUs: Long? = null
        var maximumGapUs = 0L
        var totalGapUs = 0L

        while (!outputEnded) {
            var madeProgress = false
            if (!inputEnded) {
                val inputIndex = activeDecoder.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = activeDecoder.getInputBuffer(inputIndex)
                        ?: error("Could not access an audio decoder input buffer")
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        activeDecoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputEnded = true
                    } else {
                        val sampleTimeUs = extractor.sampleTime.coerceAtLeast(0L)
                        activeDecoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            sampleTimeUs,
                            extractor.sampleFlags,
                        )
                        extractor.advance()
                    }
                    madeProgress = true
                }
            }

            val outputIndex = activeDecoder.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputIndex >= 0 -> {
                    if (bufferInfo.size > 0) {
                        val outputFormat = activeDecoder.outputFormat
                        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val pcmEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        } else {
                            android.media.AudioFormat.ENCODING_PCM_16BIT
                        }
                        val bytesPerSample = when (pcmEncoding) {
                            android.media.AudioFormat.ENCODING_PCM_8BIT -> 1
                            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                            android.media.AudioFormat.ENCODING_PCM_FLOAT,
                            android.media.AudioFormat.ENCODING_PCM_32BIT -> 4
                            else -> 2
                        }
                        check(sampleRate > 0 && channelCount > 0) {
                            "The exported MP4 decoder returned an invalid PCM format"
                        }
                        val bytesPerFrame = bytesPerSample.toLong() * channelCount.toLong()
                        check(bufferInfo.size.toLong() % bytesPerFrame == 0L) {
                            "The exported MP4 audio decoder returned a partial PCM frame"
                        }
                        val decodedFrames = bufferInfo.size.toLong() / bytesPerFrame
                        check(decodedFrames > 0L) {
                            "The exported MP4 audio decoder returned an incomplete PCM frame"
                        }

                        val presentationTimeUs = bufferInfo.presentationTimeUs
                        if (lastPresentationTimeUs != Long.MIN_VALUE &&
                            presentationTimeUs < lastPresentationTimeUs
                        ) {
                            timestampsMonotonic = false
                        }
                        firstPresentationTimeUs = minOf(firstPresentationTimeUs, presentationTimeUs)
                        lastPresentationTimeUs = presentationTimeUs

                        val bufferDurationUs = decodedFrames * 1_000_000L / sampleRate.toLong()
                        val bufferEndUs = if (presentationTimeUs > Long.MAX_VALUE - bufferDurationUs) {
                            Long.MAX_VALUE
                        } else {
                            presentationTimeUs + bufferDurationUs
                        }
                        val previousEndUs = previousDecodedEndTimeUs
                        if (previousEndUs != null && presentationTimeUs > previousEndUs) {
                            val gapUs = presentationTimeUs - previousEndUs
                            maximumGapUs = maxOf(maximumGapUs, gapUs)
                            totalGapUs = if (gapUs > Long.MAX_VALUE - totalGapUs) {
                                Long.MAX_VALUE
                            } else {
                                totalGapUs + gapUs
                            }
                        }
                        previousDecodedEndTimeUs = maxOf(previousEndUs ?: bufferEndUs, bufferEndUs)
                        decodedEndTimeUs = maxOf(decodedEndTimeUs, bufferEndUs)
                        pcmBufferCount++
                    }
                    outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    activeDecoder.releaseOutputBuffer(outputIndex, false)
                    madeProgress = true
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Reading outputFormat on the next PCM buffer captures the negotiated PCM layout.
                    madeProgress = true
                }
            }

            if (madeProgress) {
                idleIterations = 0
            } else {
                idleIterations++
                check(idleIterations < 1_000) {
                    "The exported MP4 audio decoder stopped making progress"
                }
            }
        }

        val coverage = DecodedAudioCoverage(
            pcmBufferCount = pcmBufferCount,
            firstPresentationTimeUs = firstPresentationTimeUs,
            lastPresentationTimeUs = lastPresentationTimeUs,
            decodedEndTimeUs = decodedEndTimeUs,
            timestampsMonotonic = timestampsMonotonic,
            reachedEndOfStream = outputEnded,
            maximumGapUs = maximumGapUs,
            totalGapUs = totalGapUs,
        )
        validateDecodedAudioCoverage(coverage, expectedDurationMs)?.let(::error)
    } finally {
        runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        extractor.release()
    }
}
