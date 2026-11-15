package com.vyrocut.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal enum class ProjectRecoveryFileKind(val suffix: String) {
    PROJECT("project"),
    AUDIO("audio"),
}

/** A second app-private recovery copy stored outside SharedPreferences. */
internal object ProjectRecoveryFileStore {
    private const val MAX_JOURNAL_BYTES = 16L * 1024L * 1024L
    private val safeProjectId = Regex("[A-Za-z0-9_-]{1,128}")

    @Synchronized
    fun write(directory: File, projectId: String, kind: ProjectRecoveryFileKind, journal: String): Boolean {
        if (!isSafeProjectId(projectId) || deletedMarker(directory, projectId).exists()) return false
        val snapshot = ProjectRecoveryJournal.decode(journal, projectId) ?: return false
        if (kind == ProjectRecoveryFileKind.AUDIO && snapshot.projectName != "Audio tracks") return false
        val bytes = journal.toByteArray(Charsets.UTF_8)
        if (bytes.isEmpty() || bytes.size.toLong() > MAX_JOURNAL_BYTES) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        if (!directory.isDirectory) return false
        val target = fileFor(directory, projectId, kind)
        val existing = read(directory, projectId, kind)?.let { ProjectRecoveryJournal.decode(it, projectId) }
        if (existing != null && existing.savedAtMs > snapshot.savedAtMs) return false

        val temporary = runCatching { File.createTempFile(target.name + ".", ".tmp", directory) }.getOrNull()
            ?: return false
        return try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            target.isFile && target.length() == bytes.size.toLong()
        } catch (_: Exception) {
            false
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    fun read(directory: File, projectId: String, kind: ProjectRecoveryFileKind): String? {
        if (!isSafeProjectId(projectId) || !directory.isDirectory || deletedMarker(directory, projectId).exists()) return null
        val file = fileFor(directory, projectId, kind)
        if (!file.isFile || file.length() !in 1L..MAX_JOURNAL_BYTES) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val snapshot = ProjectRecoveryJournal.decode(raw, projectId) ?: return null
        if (kind == ProjectRecoveryFileKind.AUDIO && snapshot.projectName != "Audio tracks") return null
        return raw
    }

    @Synchronized
    fun deleteProject(directory: File, projectId: String): Boolean {
        if (!isSafeProjectId(projectId)) return false
        if (!directory.exists() && !directory.mkdirs()) return false
        if (!directory.isDirectory) return false
        val marker = deletedMarker(directory, projectId)
        if (!runCatching { marker.createNewFile() || marker.isFile }.getOrDefault(false)) return false
        return ProjectRecoveryFileKind.values().all { kind ->
            val file = fileFor(directory, projectId, kind)
            !file.exists() || file.delete()
        }
    }

    private fun isSafeProjectId(projectId: String): Boolean = safeProjectId.matches(projectId)
    private fun fileFor(directory: File, projectId: String, kind: ProjectRecoveryFileKind): File =
        File(directory, "vyrocut_${projectId}_${kind.suffix}.journal")
    private fun deletedMarker(directory: File, projectId: String): File =
        File(directory, "vyrocut_${projectId}.deleted")
}

/** Chooses the newest valid recovery checkpoint from independent storage copies. */
internal fun newestProjectRecoverySnapshot(projectId: String, candidates: Iterable<String?>): ProjectRecoverySnapshot? =
    candidates.mapNotNull { ProjectRecoveryJournal.decode(it, projectId) }.maxByOrNull { it.savedAtMs }

/** Selects only an audio journal saved atomically with the requested video checkpoint. */
internal fun matchingAudioRecoveryJournal(projectId: String, savedAtMs: Long, candidates: Iterable<String?>): String? {
    if (projectId.isBlank() || savedAtMs <= 0L) return null
    return candidates.firstOrNull { raw ->
        ProjectRecoveryJournal.decode(raw, projectId)?.let { it.projectName == "Audio tracks" && it.savedAtMs == savedAtMs } == true
    }
}

/** Monotonic even when saves share a wall-clock millisecond or the clock moves backwards. */
internal fun nextProjectRecoveryTimestampMs(nowMs: Long, previousSavedAtMs: Long?): Long {
    val now = nowMs.coerceAtLeast(1L)
    val previous = previousSavedAtMs?.takeIf { it > 0L } ?: 0L
    return if (previous >= now && previous < Long.MAX_VALUE) previous + 1L else maxOf(now, previous)
}
