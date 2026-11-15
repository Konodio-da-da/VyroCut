package com.vyrocut.app

import java.io.File
import java.nio.file.Files

internal const val DEFAULT_STALE_EXPORT_RETENTION_MS = 24L * 60L * 60L * 1_000L

private val VYROCUT_TEMP_EXPORT_NAME = Regex("VyroCut_[0-9]{10,17}\\.mp4")

internal data class ExportTemporaryCleanupResult(
    val deletedFiles: Int,
    val deletedBytes: Long,
)

/** Pure age/name rule for the app-private temporary files created by Transformer. */
internal fun isStaleExportTemporaryFile(
    fileName: String,
    lastModifiedMs: Long,
    nowMs: Long,
    retentionMs: Long = DEFAULT_STALE_EXPORT_RETENTION_MS,
): Boolean {
    if (!VYROCUT_TEMP_EXPORT_NAME.matches(fileName)) return false
    if (lastModifiedMs <= 0L || nowMs <= 0L || retentionMs <= 0L) return false
    if (lastModifiedMs > nowMs || nowMs < retentionMs) return false
    return lastModifiedMs <= nowMs - retentionMs
}

/**
 * Removes only old, direct-child Transformer MP4 leftovers in the app-private
 * VyroCut export directory. Published MediaStore videos live elsewhere.
 *
 * The current export is explicitly protected. Unknown timestamps, future-dated
 * files, symlinks, nested files, and files with any other name are retained.
 */
internal fun cleanupStaleExportTemporaryFiles(
    directory: File,
    nowMs: Long = System.currentTimeMillis(),
    protectedFile: File? = null,
    retentionMs: Long = DEFAULT_STALE_EXPORT_RETENTION_MS,
): ExportTemporaryCleanupResult {
    if (!directory.isDirectory || nowMs <= 0L || retentionMs <= 0L || nowMs < retentionMs) {
        return ExportTemporaryCleanupResult(0, 0L)
    }

    val canonicalDirectory = runCatching { directory.canonicalFile }.getOrNull()
        ?: return ExportTemporaryCleanupResult(0, 0L)
    val protectedPath = if (protectedFile == null) {
        null
    } else {
        runCatching { protectedFile.canonicalPath }.getOrNull()
            ?: return ExportTemporaryCleanupResult(0, 0L)
    }

    var deletedFiles = 0
    var deletedBytes = 0L
    directory.listFiles().orEmpty().forEach { candidate ->
        val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull()
            ?: return@forEach
        if (canonicalCandidate.parentFile != canonicalDirectory) return@forEach
        if (canonicalCandidate.path == protectedPath) return@forEach
        if (runCatching { Files.isSymbolicLink(candidate.toPath()) }.getOrDefault(true)) return@forEach
        if (!candidate.isFile) return@forEach
        if (!isStaleExportTemporaryFile(candidate.name, candidate.lastModified(), nowMs, retentionMs)) {
            return@forEach
        }

        val size = candidate.length().coerceAtLeast(0L)
        if (runCatching { candidate.delete() }.getOrDefault(false)) {
            deletedFiles++
            deletedBytes = if (size > Long.MAX_VALUE - deletedBytes) {
                Long.MAX_VALUE
            } else {
                deletedBytes + size
            }
        }
    }
    return ExportTemporaryCleanupResult(deletedFiles, deletedBytes)
}
