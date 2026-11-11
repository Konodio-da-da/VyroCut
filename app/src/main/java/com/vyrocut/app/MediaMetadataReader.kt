package com.vyrocut.app

import android.content.ContentResolver
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
}
