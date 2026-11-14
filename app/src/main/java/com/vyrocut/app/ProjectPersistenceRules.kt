package com.vyrocut.app

/**
 * Decodes persisted undo/redo snapshots without turning corrupt entries into
 * empty projects. A valid empty project remains a legitimate history state.
 */
internal fun <T> decodeValidHistorySnapshots(
    rawSnapshots: List<String>,
    decode: (String) -> T?,
): List<T> = rawSnapshots.mapNotNull(decode).takeLast(50)

internal data class ProjectCanvasSettings(
    val aspectRatioPreset: String = "9:16",
    val backgroundPreset: String = "BLACK",
)

private const val PROJECT_CANVAS_SETTINGS_HEADER = "VYROCUT_CANVAS_V1"
private val PROJECT_CANVAS_ASPECT_PRESETS = setOf("9:16", "16:9", "1:1")
private val PROJECT_CANVAS_BACKGROUND_PRESETS = setOf("BLACK", "DARK", "WHITE")

internal fun encodeProjectCanvasSettings(settings: ProjectCanvasSettings): String {
    require(settings.aspectRatioPreset in PROJECT_CANVAS_ASPECT_PRESETS)
    require(settings.backgroundPreset in PROJECT_CANVAS_BACKGROUND_PRESETS)
    return PROJECT_CANVAS_SETTINGS_HEADER + "|" +
        settings.aspectRatioPreset + "|" + settings.backgroundPreset
}

internal fun decodeProjectCanvasSettings(raw: String?): ProjectCanvasSettings? {
    if (raw.isNullOrBlank()) return null
    val fields = raw.split('|')
    if (fields.size != 3 || fields[0] != PROJECT_CANVAS_SETTINGS_HEADER) return null
    if (fields[1] !in PROJECT_CANVAS_ASPECT_PRESETS) return null
    if (fields[2] !in PROJECT_CANVAS_BACKGROUND_PRESETS) return null
    return ProjectCanvasSettings(fields[1], fields[2])
}


/** A complete editor history entry. Null audioTracks identifies pre-v12 clip-only snapshots. */
internal data class ProjectEditSnapshot(
    val clips: List<EditClip>,
    val audioTracks: List<AudioTrackClip>?,
)

/** Restores audio from a complete snapshot, or safely preserves legacy audio within the restored project length. */
internal fun audioTracksForHistorySnapshot(
    snapshot: ProjectEditSnapshot,
    currentAudioTracks: List<AudioTrackClip>,
): List<AudioTrackClip> {
    val projectDurationMs = calculateTimelineDurationMs(snapshot.clips.map { it.durationMs })
    val savedAudioTracks = snapshot.audioTracks
    return if (savedAudioTracks != null && validateAudioTrackClips(savedAudioTracks, projectDurationMs)) {
        savedAudioTracks
    } else {
        trimAudioTrackClipsToProjectDuration(currentAudioTracks, projectDurationMs)
    }
}

/**
 * Wraps the independently recoverable audio-lane payload in the same versioned,
 * checksummed journal used for project clips. Older releases stored raw JSON in
 * the recovery key, so the decoder below deliberately keeps that format readable.
 */
internal fun encodeAudioTrackRecoveryJournal(
    projectId: String,
    audioTracksJson: String,
    savedAtMs: Long,
): String = ProjectRecoveryJournal.encode(
    projectId = projectId,
    projectName = "Audio tracks",
    savedAtMs = savedAtMs,
    projectJson = audioTracksJson,
)

internal fun decodeAudioTrackRecoveryJournal(
    raw: String?,
    projectId: String,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (raw.isNullOrBlank()) return null

    // Backward compatibility: earlier versions stored plain audio-track JSON.
    decodeAudioTrackClips(raw, projectDurationMs)?.let { return it }

    val snapshot = ProjectRecoveryJournal.decode(raw, projectId) ?: return null
    return decodeAudioTrackClips(snapshot.projectJson, projectDurationMs)
}

/**
 * Recovery checkpoints for the video edit list and its independent audio lanes.
 * Both journals share one timestamp so callers can persist the complete project
 * state in a single SharedPreferences editor transaction.
 */
internal data class ProjectRecoveryJournalBundle(
    val projectJournal: String,
    val audioTracksJournal: String,
)

internal fun createProjectRecoveryJournalBundle(
    projectId: String,
    projectName: String,
    projectJson: String,
    audioTracksJson: String,
    savedAtMs: Long,
): ProjectRecoveryJournalBundle {
    require(projectId.isNotBlank())
    require(savedAtMs > 0L)
    return ProjectRecoveryJournalBundle(
        projectJournal = ProjectRecoveryJournal.encode(
            projectId = projectId,
            projectName = projectName,
            savedAtMs = savedAtMs,
            projectJson = projectJson,
        ),
        audioTracksJournal = encodeAudioTrackRecoveryJournal(
            projectId = projectId,
            audioTracksJson = audioTracksJson,
            savedAtMs = savedAtMs,
        ),
    )
}


/**
 * Restores audio lanes from the same save instant as a video recovery checkpoint.
 * If an older or independently updated audio journal cannot be matched safely,
 * preserve the current audio state and trim it to the recovered video duration.
 */
internal fun audioTracksForProjectRecovery(
    projectSnapshot: ProjectRecoverySnapshot,
    rawAudioRecoveryJournal: String?,
    currentAudioTracks: List<AudioTrackClip>,
    projectDurationMs: Long,
): List<AudioTrackClip> {
    val checkpointAudioTracks = ProjectRecoveryJournal
        .decode(rawAudioRecoveryJournal, projectSnapshot.projectId)
        ?.takeIf {
            it.projectName == "Audio tracks" && it.savedAtMs == projectSnapshot.savedAtMs
        }
        ?.let { decodeAudioTrackClips(it.projectJson, projectDurationMs) }

    return checkpointAudioTracks
        ?: trimAudioTrackClipsToProjectDuration(currentAudioTracks, projectDurationMs)
}
