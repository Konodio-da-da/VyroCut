package com.vyrocut.app

/**
 * A position in the playback window of one clip in the ordered project timeline.
 * [positionMs] is relative to the clip's trimmed start, matching ExoPlayer.seekTo(index, positionMs).
 */
internal data class TimelineSeekTarget(
    val clipIndex: Int,
    val positionMs: Long,
)

/** Maps a clip-relative player position to an absolute position in the concatenated timeline. */
internal fun calculateTimelinePositionMs(
    clipDurationsMs: List<Long>,
    currentClipIndex: Int,
    currentClipPositionMs: Long,
): Long {
    if (currentClipIndex !in clipDurationsMs.indices) return 0L

    var elapsed = 0L
    for (index in 0 until currentClipIndex) {
        elapsed = addWithoutOverflow(elapsed, clipDurationsMs[index].coerceAtLeast(0L))
    }

    val currentDuration = clipDurationsMs[currentClipIndex].coerceAtLeast(0L)
    return addWithoutOverflow(elapsed, currentClipPositionMs.coerceIn(0L, currentDuration))
}

/**
 * Resolves an absolute timeline seek position into a playlist item and a clip-relative position.
 * Exact internal boundaries select the following clip. The project end selects the final
 * playable clip's last millisecond so a drag to the end does not jump back to its beginning.
 */
internal fun resolveTimelineSeekTarget(
    clipDurationsMs: List<Long>,
    timelinePositionMs: Long,
): TimelineSeekTarget? {
    val lastPlayableIndex = clipDurationsMs.indexOfLast { it > 0L }
    if (lastPlayableIndex < 0) return null

    var totalDuration = 0L
    clipDurationsMs.forEach { duration ->
        totalDuration = addWithoutOverflow(totalDuration, duration.coerceAtLeast(0L))
    }
    val requestedPosition = timelinePositionMs.coerceAtLeast(0L)

    if (requestedPosition >= totalDuration) {
        return TimelineSeekTarget(
            clipIndex = lastPlayableIndex,
            positionMs = (clipDurationsMs[lastPlayableIndex] - 1L).coerceAtLeast(0L),
        )
    }

    var remaining = requestedPosition
    clipDurationsMs.forEachIndexed { index, rawDuration ->
        val duration = rawDuration.coerceAtLeast(0L)
        if (duration == 0L) return@forEachIndexed
        if (remaining < duration) {
            return TimelineSeekTarget(index, remaining)
        }
        remaining -= duration
    }

    // Defensive fallback for saturated duration sums.
    return TimelineSeekTarget(
        clipIndex = lastPlayableIndex,
        positionMs = (clipDurationsMs[lastPlayableIndex] - 1L).coerceAtLeast(0L),
    )
}

private fun addWithoutOverflow(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
