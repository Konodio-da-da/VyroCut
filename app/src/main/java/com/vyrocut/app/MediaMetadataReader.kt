package com.vyrocut.app

import android.content.ContentResolver
import android.content.Context
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
}
