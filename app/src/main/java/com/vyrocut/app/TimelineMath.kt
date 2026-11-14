package com.vyrocut.app

/**
 * A position in the playback window of one clip in the ordered project timeline.
 * [positionMs] is relative to the clip's trimmed start, matching ExoPlayer.seekTo(index, positionMs).
 */
internal data class TimelineSeekTarget(
    val clipIndex: Int,
    val positionMs: Long,
)

internal data class PlayerTimelinePosition(
    val clipIndex: Int,
    val clipPositionMs: Long,
    val timelinePositionMs: Long,
)

/** Normalizes a Media3 position and maps it to the editor's project-wide scrubber. */
internal fun resolvePlayerTimelinePosition(
    clipDurationsMs: List<Long>,
    clipIndex: Int,
    playerPositionMs: Long,
): PlayerTimelinePosition? {
    if (clipIndex !in clipDurationsMs.indices) return null
    val durationMs = clipDurationsMs[clipIndex].coerceAtLeast(0L)
    if (durationMs == 0L) return null
    val clipPositionMs = playerPositionMs.coerceIn(0L, durationMs)
    return PlayerTimelinePosition(
        clipIndex = clipIndex,
        clipPositionMs = clipPositionMs,
        timelinePositionMs = calculateTimelinePositionMs(clipDurationsMs, clipIndex, clipPositionMs),
    )
}

/** Treat a restored final playable frame as the end so Play restarts the full project. */
internal fun isTimelinePositionAtEnd(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0L && positionMs >= (durationMs - 1L).coerceAtLeast(0L)

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

/** A valid source-time interval for one non-destructive clip segment. */
internal data class ClipRange(val startMs: Long, val endMs: Long)

/** Returns the left and right source ranges in playback order, or null for an invalid split. */
internal fun splitClipRange(
    sourceStartMs: Long,
    sourceEndMs: Long,
    splitPositionMs: Long,
): Pair<ClipRange, ClipRange>? {
    if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs) return null
    if (splitPositionMs <= sourceStartMs || splitPositionMs >= sourceEndMs) return null
    return ClipRange(sourceStartMs, splitPositionMs) to ClipRange(splitPositionMs, sourceEndMs)
}

/**
 * Splits at the active Media3 item's current position. The player position is
 * relative to the clipped item, while the edit decision list stores source time.
 */
internal fun splitClipRangeAtPlaybackPosition(
    sourceStartMs: Long,
    sourceEndMs: Long,
    playbackPositionMs: Long,
): Pair<ClipRange, ClipRange>? {
    if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs) return null
    val sourceDurationMs = sourceEndMs - sourceStartMs
    if (playbackPositionMs <= 0L || playbackPositionMs >= sourceDurationMs) return null
    return splitClipRange(
        sourceStartMs = sourceStartMs,
        sourceEndMs = sourceEndMs,
        splitPositionMs = sourceStartMs + playbackPositionMs,
    )
}

internal const val MIN_CLIP_DURATION_MS = 100L

/**
 * A saved source range may only be edited when the original media duration is
 * readable and still contains the complete range. If a provider now reports a
 * shorter source, silently widening the trim limit would permit an invalid edit.
 */
internal fun isClipRangeWithinSourceDuration(
    sourceDurationMs: Long?,
    sourceStartMs: Long,
    sourceEndMs: Long,
): Boolean {
    val durationMs = sourceDurationMs ?: return false
    return durationMs > 0L &&
        sourceStartMs >= 0L &&
        sourceEndMs > sourceStartMs &&
        sourceEndMs <= durationMs
}

/**
 * Clamps a two-handle trim selection to the original source and rejects segments
 * shorter than [minimumDurationMs]. The source file itself is never modified.
 */
internal fun clampTrimRange(
    sourceDurationMs: Long,
    requestedStartMs: Long,
    requestedEndMs: Long,
    minimumDurationMs: Long = MIN_CLIP_DURATION_MS,
): ClipRange? {
    if (sourceDurationMs <= 0L || minimumDurationMs <= 0L) return null
    val startMs = requestedStartMs.coerceIn(0L, sourceDurationMs)
    val endMs = requestedEndMs.coerceIn(0L, sourceDurationMs)
    if (endMs - startMs < minimumDurationMs) return null
    return ClipRange(startMs, endMs)
}

/** Trims one edge without mutating the source file or allowing an empty segment. */
internal fun trimClipRange(
    sourceStartMs: Long,
    sourceEndMs: Long,
    trimAmountMs: Long,
    fromStart: Boolean,
): ClipRange? {
    if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs || trimAmountMs <= 0L) return null
    val durationMs = sourceEndMs - sourceStartMs
    if (trimAmountMs >= durationMs) return null
    return if (fromStart) {
        ClipRange(sourceStartMs + trimAmountMs, sourceEndMs)
    } else {
        ClipRange(sourceStartMs, sourceEndMs - trimAmountMs)
    }
}

/** Moves an item to a new timeline index without mutating the original list. */
/**
 * Maps the project playhead to the horizontal pixel offset of the filmstrip.
 * Exact clip boundaries begin at the next playable clip, matching timeline seeking.
 * Zero-duration items keep their visual slot but do not consume playback time.
 */
internal fun calculateTimelinePlayheadOffsetPx(
    clipDurationsMs: List<Long>,
    clipWidthsPx: List<Float>,
    playheadPositionMs: Long,
    gapPx: Float,
): Float {
    val lastPlayableIndex = clipDurationsMs.indexOfLast { it > 0L }
    if (lastPlayableIndex < 0) return 0f

    val safeGap = if (gapPx.isFinite()) gapPx.coerceAtLeast(0f) else 0f
    fun widthAt(index: Int): Float =
        clipWidthsPx.getOrNull(index)?.let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } ?: 0f

    val totalDurationMs = calculateTimelineDurationMs(clipDurationsMs)
    if (playheadPositionMs.coerceAtLeast(0L) >= totalDurationMs) {
        var endOffset = 0f
        for (index in 0..lastPlayableIndex) {
            endOffset += widthAt(index)
            if (index < lastPlayableIndex) endOffset += safeGap
        }
        return endOffset
    }

    var remainingMs = playheadPositionMs.coerceAtLeast(0L)
    var offsetPx = 0f
    for (index in 0..lastPlayableIndex) {
        val durationMs = clipDurationsMs[index].coerceAtLeast(0L)
        val widthPx = widthAt(index)
        if (durationMs > 0L && remainingMs < durationMs) {
            val progress = remainingMs.toDouble() / durationMs.toDouble()
            return offsetPx + widthPx * progress.toFloat()
        }
        if (durationMs > 0L) remainingMs -= durationMs
        offsetPx += widthPx
        if (index < lastPlayableIndex) offsetPx += safeGap
    }
    return offsetPx
}

internal data class TimelineRulerTick(
    val timeMs: Long,
    val offsetPx: Float,
    val isMajor: Boolean,
)

/**
 * Builds ruler ticks from absolute project time rather than restarting a local
 * second counter for each clip. Fractional clip durations therefore keep ruler
 * labels aligned with the same time-to-pixel mapping used by the playhead.
 *
 * Very long projects increase the tick interval so the ruler never creates an
 * unbounded number of Compose children.
 */
internal fun calculateTimelineRulerTicks(
    clipDurationsMs: List<Long>,
    clipWidthsPx: List<Float>,
    gapPx: Float,
    tickIntervalMs: Long = 1_000L,
    majorIntervalMs: Long = 5_000L,
): List<TimelineRulerTick> {
    if (tickIntervalMs <= 0L || majorIntervalMs <= 0L) return emptyList()
    val totalDurationMs = calculateTimelineDurationMs(clipDurationsMs)
    if (totalDurationMs <= 0L) return emptyList()

    val requestedTickCount = totalDurationMs / tickIntervalMs
    val intervalMultiplier = if (requestedTickCount <= MAX_TIMELINE_RULER_TICKS) {
        1L
    } else {
        requestedTickCount / MAX_TIMELINE_RULER_TICKS +
            if (requestedTickCount % MAX_TIMELINE_RULER_TICKS == 0L) 0L else 1L
    }
    // When the multiplier is greater than one, requestedTickCount is also
    // greater than the cap, so this product cannot exceed totalDurationMs.
    val effectiveIntervalMs = tickIntervalMs * intervalMultiplier

    fun createTick(timeMs: Long) = TimelineRulerTick(
        timeMs = timeMs,
        offsetPx = calculateTimelinePlayheadOffsetPx(
            clipDurationsMs = clipDurationsMs,
            clipWidthsPx = clipWidthsPx,
            playheadPositionMs = timeMs,
            gapPx = gapPx,
        ),
        isMajor = timeMs % majorIntervalMs == 0L,
    )

    val ticks = mutableListOf<TimelineRulerTick>()
    var timeMs = 0L
    while (timeMs <= totalDurationMs && ticks.size < MAX_TIMELINE_RULER_TICKS) {
        ticks += createTick(timeMs)
        if (totalDurationMs - timeMs < effectiveIntervalMs) break
        timeMs += effectiveIntervalMs
    }

    // Keep the project-end marker even when the regular interval does not land
    // on it. Replacing the final regular tick preserves the strict child cap.
    if (ticks.lastOrNull()?.timeMs != totalDurationMs) {
        val endTick = createTick(totalDurationMs)
        if (ticks.size < MAX_TIMELINE_RULER_TICKS) {
            ticks += endTick
        } else {
            ticks[ticks.lastIndex] = endTick
        }
    }
    return ticks
}

private const val MAX_TIMELINE_RULER_TICKS = 2_000

internal fun <T> moveTimelineItem(items: List<T>, fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return items
    return items.toMutableList().apply {
        val moved = removeAt(fromIndex)
        add(toIndex, moved)
    }
}

internal data class ClipPlaybackRange(
    val id: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
) {
    val durationMs: Long get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)
}

internal data class PlaybackRestoreTarget(
    val clipIndex: Int,
    val positionMs: Long,
)

/** Saved preview location uses source time so it survives trim and reorder edits. */
internal data class SavedPlaybackPosition(
    val clipId: String,
    val sourceTimeMs: Long,
)

/**
 * Restores a project's last saved preview location. A saved source time outside a
 * newly-trimmed range is clamped to the nearest playable millisecond.
 */
internal fun resolveSavedPlaybackTarget(
    nextClips: List<ClipPlaybackRange>,
    savedPosition: SavedPlaybackPosition?,
    fallbackClipIndex: Int = 0,
): PlaybackRestoreTarget? {
    if (nextClips.isEmpty()) return null

    val savedIndex = savedPosition?.let { saved ->
        nextClips.indexOfFirst { it.id == saved.clipId }
    } ?: -1
    if (savedIndex >= 0 && savedPosition != null) {
        val clip = nextClips[savedIndex]
        val maximumPositionMs = (clip.durationMs - 1L).coerceAtLeast(0L)
        val relativePositionMs = (
            savedPosition.sourceTimeMs.coerceAtLeast(0L) -
                clip.sourceStartMs.coerceAtLeast(0L)
            ).coerceIn(0L, maximumPositionMs)
        return PlaybackRestoreTarget(savedIndex, relativePositionMs)
    }

    return PlaybackRestoreTarget(fallbackClipIndex.coerceIn(0, nextClips.lastIndex), 0L)
}

/**
 * Restores the current source-time playhead after rebuilding a project's playlist.
 * Stable clip IDs let reorder/trim edits keep the same visual moment. If the active
 * clip was removed (for example, deleted or split), playback falls back to the
 * caller's selected index at the beginning of that segment.
 */
internal fun resolvePlaybackRestoreTarget(
    previousClips: List<ClipPlaybackRange>,
    nextClips: List<ClipPlaybackRange>,
    currentClipId: String?,
    currentClipPositionMs: Long,
    fallbackClipIndex: Int,
    preferredClipId: String? = null,
): PlaybackRestoreTarget? {
    if (nextClips.isEmpty()) return null

    // Import and duplicate operations deliberately select a newly-created clip.
    // Honor that selection instead of retaining the old player's still-valid ID.
    val preferredIndex = preferredClipId?.let { id ->
        nextClips.indexOfFirst { it.id == id }
    } ?: -1
    if (preferredIndex >= 0 && preferredClipId != currentClipId) {
        return PlaybackRestoreTarget(preferredIndex, 0L)
    }

    val previousClip = previousClips.firstOrNull { it.id == currentClipId }
    val nextIndex = previousClip?.let { old ->
        nextClips.indexOfFirst { it.id == old.id }
    } ?: -1

    if (previousClip != null && nextIndex >= 0) {
        val nextClip = nextClips[nextIndex]
        val sourcePositionMs = addWithoutOverflow(
            previousClip.sourceStartMs.coerceAtLeast(0L),
            currentClipPositionMs.coerceAtLeast(0L),
        )
        val maximumPositionMs = (nextClip.durationMs - 1L).coerceAtLeast(0L)
        val relativePositionMs = (sourcePositionMs - nextClip.sourceStartMs.coerceAtLeast(0L))
            .coerceIn(0L, maximumPositionMs)
        return PlaybackRestoreTarget(nextIndex, relativePositionMs)
    }

    return PlaybackRestoreTarget(fallbackClipIndex.coerceIn(0, nextClips.lastIndex), 0L)
}


/**
 * Maps a horizontal timeline pixel offset to a project-wide Media3 seek target.
 * Clip widths represent the visible filmstrip (not source duration), so each
 * playable clip is mapped proportionally. Gaps snap to the next clip boundary;
 * zero-duration visual slots do not consume playback time. [offsetPx] is in
 * scrollable-content coordinates: pointer input on a child inside horizontalScroll
 * already includes the scroll transform, so callers must not add ScrollState.value.
 */
internal fun resolveTimelineSeekTargetAtOffsetPx(
    clipDurationsMs: List<Long>,
    clipWidthsPx: List<Float>,
    offsetPx: Float,
    gapPx: Float,
): TimelineSeekTarget? {
    if (clipDurationsMs.none { it > 0L }) return null

    val safeOffset = if (offsetPx.isFinite()) offsetPx.coerceAtLeast(0f) else 0f
    val safeGap = if (gapPx.isFinite()) gapPx.coerceAtLeast(0f) else 0f
    fun widthAt(index: Int): Float =
        clipWidthsPx.getOrNull(index)?.let { if (it.isFinite()) it.coerceAtLeast(0f) else 0f } ?: 0f

    var cursorPx = 0f
    var elapsedMs = 0L
    clipDurationsMs.forEachIndexed { index, rawDuration ->
        val durationMs = rawDuration.coerceAtLeast(0L)
        val widthPx = widthAt(index)
        val clipEndPx = cursorPx + widthPx

        if (safeOffset <= clipEndPx) {
            val progress = if (widthPx > 0f) {
                ((safeOffset - cursorPx) / widthPx).coerceIn(0f, 1f)
            } else {
                0f
            }
            val withinClipMs = (durationMs.toDouble() * progress.toDouble()).toLong()
            return resolveTimelineSeekTarget(
                clipDurationsMs,
                addWithoutOverflow(elapsedMs, withinClipMs),
            )
        }

        cursorPx = clipEndPx
        elapsedMs = addWithoutOverflow(elapsedMs, durationMs)
        if (index < clipDurationsMs.lastIndex) {
            val gapEndPx = cursorPx + safeGap
            if (safeOffset <= gapEndPx) {
                return resolveTimelineSeekTarget(clipDurationsMs, elapsedMs)
            }
            cursorPx = gapEndPx
        }
    }

    return resolveTimelineSeekTarget(
        clipDurationsMs,
        calculateTimelineDurationMs(clipDurationsMs),
    )
}

/**
 * Moves the project playhead by a signed delta and resolves the result to a
 * Media3 playlist item. Seeking is project-wide, so crossing a clip boundary
 * naturally lands in the adjacent item. The final position stays on a playable
 * millisecond rather than seeking beyond the playlist.
 */
internal fun resolveTimelineSeekTargetAfterDelta(
    clipDurationsMs: List<Long>,
    currentTimelinePositionMs: Long,
    deltaMs: Long,
): TimelineSeekTarget? {
    val totalDurationMs = calculateTimelineDurationMs(clipDurationsMs)
    if (totalDurationMs <= 0L) return null

    val current = currentTimelinePositionMs.coerceIn(0L, totalDurationMs - 1L)
    val requested = if (deltaMs >= 0L) {
        addWithoutOverflow(current, deltaMs)
    } else {
        val magnitude = if (deltaMs == Long.MIN_VALUE) Long.MAX_VALUE else -deltaMs
        (current - magnitude).coerceAtLeast(0L)
    }
    return resolveTimelineSeekTarget(
        clipDurationsMs,
        requested.coerceIn(0L, totalDurationMs - 1L),
    )
}

/** Computes total timeline length without overflowing when adding clip durations. */
internal fun calculateTimelineDurationMs(clipDurationsMs: List<Long>): Long =
    clipDurationsMs.fold(0L) { total, duration ->
        addWithoutOverflow(total, duration.coerceAtLeast(0L))
    }

private fun addWithoutOverflow(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right


/** Appends an item and returns the index that should become selected. */
internal data class TimelineAppendResult<T>(
    val items: List<T>,
    val selectedIndex: Int,
)

internal fun <T> appendTimelineItemAndSelect(
    items: List<T>,
    item: T,
): TimelineAppendResult<T> = TimelineAppendResult(items + item, items.size)

internal fun <T> duplicateTimelineItemAfter(
    items: List<T>,
    index: Int,
    duplicate: (T) -> T,
): TimelineAppendResult<T>? {
    if (index !in items.indices) return null
    val selectedIndex = index + 1
    val copy = duplicate(items[index])
    return TimelineAppendResult(
        items = items.toMutableList().apply { add(selectedIndex, copy) },
        selectedIndex = selectedIndex,
    )
}


/**
 * Keeps a playing playhead inside a safe horizontal viewport band without
 * taking control away from the user while paused. Returns null when no scroll
 * is needed. The target is expressed in ScrollState pixels.
 */
internal fun calculateTimelineAutoFollowScrollTargetPx(
    currentScrollPx: Int,
    markerPositionPx: Float,
    viewportWidthPx: Int,
    maxScrollPx: Int,
    isPlaying: Boolean,
): Int? {
    if (!isPlaying || viewportWidthPx <= 0 || maxScrollPx <= 0 || !markerPositionPx.isFinite()) {
        return null
    }

    val current = currentScrollPx.coerceIn(0, maxScrollPx)
    val leftBoundaryPx = current + viewportWidthPx * 0.20f
    val rightBoundaryPx = current + viewportWidthPx * 0.80f
    if (markerPositionPx in leftBoundaryPx..rightBoundaryPx) return null

    val target = (markerPositionPx - viewportWidthPx * 0.35f)
        .toInt()
        .coerceIn(0, maxScrollPx)
    return target.takeIf { it != current }
}
