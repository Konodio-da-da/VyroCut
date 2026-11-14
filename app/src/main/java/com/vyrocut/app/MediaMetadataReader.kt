package com.vyrocut.app

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns

object MediaMetadataReader {
    fun displayName(contentResolver: ContentResolver, uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameColumn >= 0 && cursor.moveToFirst()) return cursor.getString(nameColumn)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Imported media"
    }

    fun mimeType(contentResolver: ContentResolver, uri: Uri): String? =
        contentResolver.getType(uri)

    /**
     * Reads duration without retaining a native retriever. Call from Dispatchers.IO:
     * document providers may perform disk or cloud-backed work while opening a URI.
     */
    fun videoDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Reads the source video's nominal frame rate for cross-clip frame stepping.
     * Capture metadata is preferred because it can retain fractional rates such
     * as 23.976/29.97; the extractor track format is a fallback for providers
     * that do not expose capture-frame-rate metadata. Call from Dispatchers.IO.
     */
    fun videoFrameRate(context: Context, uri: Uri): Float? {
        val retriever = MediaMetadataRetriever()
        val captureRate = try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
        if (captureRate != null && isValidPreviewFrameRate(captureRate)) return captureRate

        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            var resolvedRate: Float? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true) continue
                val candidate = runCatching {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                }.getOrNull()
                if (candidate != null && isValidPreviewFrameRate(candidate)) {
                    resolvedRate = candidate
                    break
                }
            }
            resolvedRate
        } catch (_: Exception) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }
}
