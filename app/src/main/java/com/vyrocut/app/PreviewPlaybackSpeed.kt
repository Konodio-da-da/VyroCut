package com.vyrocut.app

internal val PREVIEW_PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** Advances to the next supported preview rate; unknown values safely return to normal speed. */
internal fun nextPreviewPlaybackSpeed(currentSpeed: Float): Float {
    val currentIndex = PREVIEW_PLAYBACK_SPEEDS.indexOf(currentSpeed)
    if (currentIndex < 0) return 1f
    return PREVIEW_PLAYBACK_SPEEDS[(currentIndex + 1) % PREVIEW_PLAYBACK_SPEEDS.size]
}

/** Compact label used by the editor transport control. */
internal fun previewPlaybackSpeedLabel(speed: Float): String {
    val value = speed.toString().removeSuffix(".0")
    return "${value}×"
}
