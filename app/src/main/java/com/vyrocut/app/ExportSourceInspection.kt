package com.vyrocut.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Re-opens every distinct source before rendering. This catches revoked SAF
 * permissions and providers that no longer expose a decodable video track.
 * Call from Dispatchers.IO because document providers may block on storage.
 */
internal fun inspectExportSources(
    context: Context,
    clips: List<ExportClipRequirement>,
): Map<String, ExportSourceMetadata> {
    return clips.map { it.uri }.distinct().associateWith { rawUri ->
        val uri = Uri.parse(rawUri)
        val durationMs = MediaMetadataReader.videoDurationMs(context, uri)
        val extractor = MediaExtractor()
        var videoWidthPx: Int? = null
        var videoHeightPx: Int? = null
        var videoRotationDegrees = 0
        val hasVideoTrack = try {
            extractor.setDataSource(context, uri, null)
            var foundVideoTrack = false
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    foundVideoTrack = true
                    if (videoWidthPx == null || videoHeightPx == null) {
                        videoWidthPx = runCatching { format.getInteger(MediaFormat.KEY_WIDTH) }.getOrNull()
                        videoHeightPx = runCatching { format.getInteger(MediaFormat.KEY_HEIGHT) }.getOrNull()
                        videoRotationDegrees = runCatching {
                            format.getInteger(MediaFormat.KEY_ROTATION)
                        }.getOrDefault(0)
                    }
                }
            }
            foundVideoTrack
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
        ExportSourceMetadata(
            durationMs = durationMs,
            hasVideoTrack = hasVideoTrack,
            videoWidthPx = videoWidthPx,
            videoHeightPx = videoHeightPx,
            videoRotationDegrees = videoRotationDegrees,
        )
    }
}
