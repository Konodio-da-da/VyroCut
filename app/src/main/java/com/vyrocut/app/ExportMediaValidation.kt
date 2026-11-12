package com.vyrocut.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileInputStream

/** Demuxes the complete exported file so a truncated tail is less likely to pass validation. */
internal fun verifyExportedMp4(
    context: Context,
    file: File,
    sourceUris: List<String>,
    expectedDurationMs: Long,
    expectedCanvasSize: ExportCanvasSize? = null,
) {
    check(file.isFile && file.length() > 0L) { "Transformer produced an empty MP4" }

    val extractor = MediaExtractor()
    val summary = try {
        extractor.setDataSource(file.absolutePath)
        val videoTracks = mutableSetOf<Int>()
        val audioTracks = mutableSetOf<Int>()
        var videoFormat: MediaFormat? = null

        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            when (format.getString(MediaFormat.KEY_MIME)?.substringBefore('/')) {
                "video" -> {
                    videoTracks += index
                    if (videoFormat == null) videoFormat = format
                }
                "audio" -> audioTracks += index
            }
        }
        check(videoTracks.isNotEmpty()) { "The exported MP4 contains no video track" }
        (videoTracks + audioTracks).forEach { trackIndex -> extractor.selectTrack(trackIndex) }

        var videoSamples = 0L
        var audioSamples = 0L
        var lastVideoSampleTimeUs = -1L
        if (extractor.sampleTime < 0L) extractor.advance()
        while (extractor.sampleTrackIndex >= 0) {
            val trackIndex = extractor.sampleTrackIndex
            if (trackIndex in videoTracks) {
                videoSamples++
                lastVideoSampleTimeUs = extractor.sampleTime
            } else if (trackIndex in audioTracks) {
                audioSamples++
            }
            if (!extractor.advance()) break
        }

        val declaredDurationUs = videoFormat
            ?.takeIf { it.containsKey(MediaFormat.KEY_DURATION) }
            ?.let { runCatching { it.getLong(MediaFormat.KEY_DURATION) }.getOrNull() }
            ?: 0L
        val videoWidthPx = videoFormat
            ?.takeIf { it.containsKey(MediaFormat.KEY_WIDTH) }
            ?.let { runCatching { it.getInteger(MediaFormat.KEY_WIDTH) }.getOrDefault(0) }
            ?: 0
        val videoHeightPx = videoFormat
            ?.takeIf { it.containsKey(MediaFormat.KEY_HEIGHT) }
            ?.let { runCatching { it.getInteger(MediaFormat.KEY_HEIGHT) }.getOrDefault(0) }
            ?: 0
        val measuredDurationMs = if (declaredDurationUs > 0L) {
            declaredDurationUs / 1_000L
        } else {
            (lastVideoSampleTimeUs / 1_000L).coerceAtLeast(0L)
        }
        ExportTrackSummary(videoSamples, audioSamples, measuredDurationMs, videoWidthPx, videoHeightPx)
    } finally {
        extractor.release()
    }

    val sourceHasAudio = sourceUris.any { sourceUri ->
        val source = MediaExtractor()
        try {
            source.setDataSource(context, Uri.parse(sourceUri), null)
            (0 until source.trackCount).any { index ->
                source.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
        } finally {
            source.release()
        }
    }

    validateExportTrackSummary(summary, expectedDurationMs, sourceHasAudio, expectedCanvasSize)?.let { message ->
        error(message)
    }

    // Demuxing proves that packets exist; decode real frames and PCM before
    // publishing so a structurally valid but undecodable MP4 is never exposed.
    expectedCanvasSize?.let { canvas ->
        verifyExportedVideoFrames(file, expectedDurationMs, canvas)
    }
    if (sourceHasAudio) {
        verifyExportedAudioSamples(file, expectedDurationMs)
    }
}

/** Publishes only a fully verified export; a failed copy removes its pending MediaStore row. */
internal fun publishExportToMovies(context: Context, file: File): Uri {
    var destination: Uri? = null
    try {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VyroCut")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        destination = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: error("Could not create a Movies library entry")

        context.contentResolver.openOutputStream(destination!!)?.use { output ->
            FileInputStream(file).use { input -> input.copyTo(output) }
        } ?: error("Could not write the exported video")

        val publishedRows = context.contentResolver.update(
            destination!!,
            ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
            null,
            null,
        )
        check(publishedRows > 0) { "Could not finish publishing the video" }
        return destination!!
    } catch (error: Throwable) {
        destination?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        throw error
    }
}
