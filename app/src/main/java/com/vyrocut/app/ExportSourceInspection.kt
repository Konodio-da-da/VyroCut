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
        val hasVideoTrack = try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { extractor.release() }
        }
        ExportSourceMetadata(durationMs = durationMs, hasVideoTrack = hasVideoTrack)
    }
}
