package com.vyrocut.app

internal val PREVIEW_PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Keeps persisted preview settings on a known Media3 rate; corrupt or stale values use normal speed. */
internal fun normalizePreviewPlaybackSpeed(speed: Float?): Float =
    speed?.takeIf { it.isFinite() && it in PREVIEW_PLAYBACK_SPEEDS } ?: 1f

/** Stable project-scoped key so switching projects restores each preview rate independently. */
internal fun projectPreviewPlaybackSpeedPreferenceKey(projectId: String): String {
    require(projectId.isNotBlank()) { "Project ID cannot be blank" }
    return "project_${projectId}_preview_playback_speed"
}

/** Advances to the next supported preview rate; unknown values safely return to normal speed. */
internal fun nextPreviewPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = PREVIEW_PLAYBACK_SPEEDS.indexOf(currentSpeed)
    if (currentIndex < 0) return 1f
    return PREVIEW_PLAYBACK_SPEEDS[(currentIndex + 1) % PREVIEW_PLAYBACK_SPEEDS.size]
}

/** Compact label used by the editor transport control. */
internal fun previewPlaybackSpeedLabel(speed: Float): String {
    val value = normalizePreviewPlaybackSpeed(speed).toString().removeSuffix(".0")
    return "${value}×"
}
