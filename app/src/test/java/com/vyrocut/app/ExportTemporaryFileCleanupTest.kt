package com.vyrocut.app

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTemporaryFileCleanupTest {
    private val nowMs = 2_000_000_000_000L
    private val retentionMs = DEFAULT_STALE_EXPORT_RETENTION_MS

    @Test
    fun staleRuleRequiresGeneratedNameKnownTimestampAndFullRetentionPeriod() {
        val generatedName = "VyroCut_1999999999999.mp4"
        assertTrue(
            isStaleExportTemporaryFile(
                generatedName,
                lastModifiedMs = nowMs - retentionMs,
                nowMs = nowMs,
                retentionMs = retentionMs,
            ),
        )
        assertFalse(
            isStaleExportTemporaryFile(
                generatedName,
                lastModifiedMs = nowMs - retentionMs + 1L,
                nowMs = nowMs,
                retentionMs = retentionMs,
            ),
        )
        assertFalse(isStaleExportTemporaryFile(generatedName, nowMs + 1L, nowMs, retentionMs))
        assertFalse(isStaleExportTemporaryFile(generatedName, 0L, nowMs, retentionMs))
        assertFalse(isStaleExportTemporaryFile("holiday.mp4", 1L, nowMs, retentionMs))
        assertFalse(isStaleExportTemporaryFile("VyroCut_latest.mp4", 1L, nowMs, retentionMs))
        assertFalse(isStaleExportTemporaryFile(generatedName, 1L, nowMs, 0L))
    }

    @Test
    fun cleanupDeletesOnlyOldGeneratedDirectChildrenAndProtectsCurrentExport() {
        val directory = Files.createTempDirectory("vyrocut-export-cleanup").toFile()
        try {
            val stale = File(directory, "VyroCut_1999999999999.mp4").apply {
                writeBytes(byteArrayOf(1, 2, 3, 4))
                assertTrue(setLastModified(nowMs - retentionMs))
            }
            val staleBytes = stale.length()
            val recent = File(directory, "VyroCut_1999999999998.mp4").apply {
                writeBytes(byteArrayOf(5, 6))
                assertTrue(setLastModified(nowMs - retentionMs + 1L))
            }
            val protected = File(directory, "VyroCut_1999999999997.mp4").apply {
                writeBytes(byteArrayOf(7, 8, 9))
                assertTrue(setLastModified(nowMs - retentionMs))
            }
            val unrelated = File(directory, "holiday.mp4").apply {
                writeBytes(byteArrayOf(10))
                assertTrue(setLastModified(nowMs - retentionMs))
            }
            val matchingDirectory = File(directory, "VyroCut_1999999999996.mp4").apply {
                assertTrue(mkdir())
                assertTrue(setLastModified(nowMs - retentionMs))
            }

            val result = cleanupStaleExportTemporaryFiles(
                directory = directory,
                nowMs = nowMs,
                protectedFile = protected,
                retentionMs = retentionMs,
            )

            assertEquals(1, result.deletedFiles)
            assertEquals(staleBytes, result.deletedBytes)
            assertFalse(stale.exists())
            assertTrue(recent.isFile)
            assertTrue(protected.isFile)
            assertTrue(unrelated.isFile)
            assertTrue(matchingDirectory.isDirectory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cleanupFailsClosedForMissingDirectoryOrInvalidClock() {
        val directory = Files.createTempDirectory("vyrocut-export-cleanup-invalid").toFile()
        try {
            assertEquals(
                ExportTemporaryCleanupResult(0, 0L),
                cleanupStaleExportTemporaryFiles(File(directory, "missing"), nowMs),
            )
            assertEquals(
                ExportTemporaryCleanupResult(0, 0L),
                cleanupStaleExportTemporaryFiles(directory, nowMs = 0L),
            )
            assertEquals(
                ExportTemporaryCleanupResult(0, 0L),
                cleanupStaleExportTemporaryFiles(directory, nowMs = nowMs, retentionMs = -1L),
            )
        } finally {
            directory.deleteRecursively()
        }
    }
}
