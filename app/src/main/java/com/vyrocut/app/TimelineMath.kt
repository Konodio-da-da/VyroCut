package com.vyrocut.app

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

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

/**
 * Combines the reasons reported by Media3's position-discontinuity and
 * media-item-transition callbacks for one onEvents batch.
 *
 * A natural video transition can be reported through both callbacks; it should
 * not restart continuous gap-filled audio playlists. If either callback reports
 * an explicit seek or playlist edit, all audio lanes must be realigned.
 * Null means neither callback contributed a timeline change.
 */
internal fun shouldForceAudioLaneSeekForVideoEvents(
    positionDiscontinuityIsAutomatic: Boolean?,
    mediaItemTransitionIsAutomatic: Boolean?,
): Boolean? {
    val reasons = listOfNotNull(
        positionDiscontinuityIsAutomatic,
        mediaItemTransitionIsAutomatic,
    )
    return reasons.takeIf { it.isNotEmpty() }?.any { isAutomatic -> !isAutomatic }
}

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

/**
 * Selects major ruler labels whose estimated text bounds do not overlap.
 *
 * Tick marks remain available at every interval; this helper only controls
 * which timecode labels are drawn. The first major label is retained, and the
 * final major label is retained when it has enough room after the previous one.
 * Offsets use the same coordinate unit as TimelineRulerTick.offsetPx.
 */
internal fun selectTimelineRulerLabelTicks(
    ticks: List<TimelineRulerTick>,
    minimumSpacing: Float,
): List<TimelineRulerTick> {
    val candidates = ticks.asSequence()
        .filter { it.isMajor && it.offsetPx.isFinite() }
        .sortedBy { it.timeMs }
        .toList()
    if (candidates.size <= 1) return candidates
    if (!minimumSpacing.isFinite() || minimumSpacing <= 0f) return candidates

    val selected = mutableListOf(candidates.first())
    candidates.subList(1, candidates.lastIndex).forEach { candidate ->
        val previous = selected.last()
        if (
            candidate.offsetPx >= previous.offsetPx &&
            candidate.offsetPx - previous.offsetPx >= minimumSpacing
        ) {
            selected += candidate
        }
    }

    val lastCandidate = candidates.last()
    val previous = selected.last()
    if (
        lastCandidate.timeMs != previous.timeMs &&
        lastCandidate.offsetPx >= previous.offsetPx &&
        lastCandidate.offsetPx - previous.offsetPx >= minimumSpacing
    ) {
        selected += lastCandidate
    }
    return selected
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

/** Captures a clip-relative player position as an absolute source timestamp. */
internal fun createSavedPlaybackPosition(
    clip: ClipPlaybackRange?,
    clipPositionMs: Long,
): SavedPlaybackPosition? {
    if (clip == null || clip.id.isBlank()) return null
    if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) return null
    val relativePositionMs = clipPositionMs.coerceIn(0L, clip.durationMs - 1L)
    val sourceTimeMs = addWithoutOverflow(clip.sourceStartMs, relativePositionMs)
    if (sourceTimeMs >= clip.sourceEndMs) return null
    return SavedPlaybackPosition(clip.id, sourceTimeMs)
}

/** Rejects checkpoints whose stable clip ID or source timestamp is not in this edit list. */
internal fun validateSavedPlaybackPosition(
    clips: List<ClipPlaybackRange>,
    savedPosition: SavedPlaybackPosition?,
): SavedPlaybackPosition? {
    val saved = savedPosition ?: return null
    if (saved.clipId.isBlank() || saved.sourceTimeMs < 0L) return null
    val clip = clips.singleOrNull { it.id == saved.clipId } ?: return null
    if (clip.sourceStartMs < 0L || clip.sourceEndMs <= clip.sourceStartMs) return null
    return saved.takeIf { it.sourceTimeMs >= clip.sourceStartMs && it.sourceTimeMs < clip.sourceEndMs }
}

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
 * Suspends timeline auto-follow only after a deliberate horizontal drag crosses
 * touch slop. Vertical gestures and sub-slop movement must not take ownership.
 */
internal fun shouldSuspendTimelineAutoFollowForDrag(
    deltaX: Float,
    deltaY: Float,
    touchSlopPx: Float,
): Boolean {
    if (!deltaX.isFinite() || !deltaY.isFinite() || !touchSlopPx.isFinite() || touchSlopPx < 0f) {
        return false
    }
    return kotlin.math.abs(deltaX) > touchSlopPx &&
        kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
}

/**
 * Keeps a playing playhead inside a safe horizontal viewport band without
 * taking control away from the user while paused or after a manual timeline
 * drag. Manual scrolling suppresses auto-follow until playback is restarted
 * or the user seeks directly on the timeline.
 */
internal fun calculateTimelineAutoFollowScrollTargetPx(
    currentScrollPx: Int,
    markerPositionPx: Float,
    viewportWidthPx: Int,
    maxScrollPx: Int,
    isPlaying: Boolean,
    isUserScrollOverride: Boolean = false,
): Int? {
    if (
        !isPlaying ||
        isUserScrollOverride ||
        viewportWidthPx <= 0 ||
        maxScrollPx <= 0 ||
        !markerPositionPx.isFinite()
    ) {
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

/** Why the editor is seeking the preview playlist. */
internal enum class PreviewSeekIntent {
    USER_EDIT,
    AUDIO_RESYNC,
    PLAYLIST_REBUILD,
}

internal enum class PreviewSeekPrecision {
    EXACT,
    CLOSEST_SYNC,
}

/** User seeks should land on the requested timestamp; playlist reconstruction uses the faster closest-sync policy. */
internal fun previewSeekPrecisionFor(intent: PreviewSeekIntent): PreviewSeekPrecision =
    when (intent) {
        PreviewSeekIntent.USER_EDIT, PreviewSeekIntent.AUDIO_RESYNC -> PreviewSeekPrecision.EXACT
        PreviewSeekIntent.PLAYLIST_REBUILD -> PreviewSeekPrecision.CLOSEST_SYNC
    }

/** Metadata from one selected or unselected Media3 track, kept pure for JVM policy tests. */
internal data class PreviewTrackFrameRateCandidate(
    val isVideo: Boolean,
    val isSelected: Boolean,
    val frameRate: Float,
)

/** Returns the first usable selected video-track frame rate; unknown or implausible rates are ignored. */
internal const val MIN_PREVIEW_FRAME_RATE = 1f
internal const val MAX_PREVIEW_FRAME_RATE = 240f

internal fun isValidPreviewFrameRate(frameRate: Float): Boolean =
    frameRate.isFinite() && frameRate in MIN_PREVIEW_FRAME_RATE..MAX_PREVIEW_FRAME_RATE

internal fun resolveSelectedPreviewFrameRate(
    candidates: List<PreviewTrackFrameRateCandidate>,
): Float? = candidates.firstOrNull { candidate ->
    candidate.isVideo && candidate.isSelected && isValidPreviewFrameRate(candidate.frameRate)
}?.frameRate

internal enum class PreviewFrameStepDirection { PREVIOUS, NEXT }

/** Inclusive source-frame index bounds intersecting one clip's source trim. */
private data class PreviewFrameIndexRange(
    val firstFrameIndex: Long,
    val lastFrameIndex: Long,
)

/**
 * Returns the source-frame indices whose presentation timestamps fall inside
 * [sourceStartMs, sourceStartMs + clipDurationMs). The source offset matters:
 * a non-destructive trim can begin between two frames, so clip-relative zero
 * is not necessarily a frame boundary.
 */
private fun resolvePreviewFrameIndexRange(
    sourceStartMs: Long,
    clipDurationMs: Long,
    frameRate: Float,
): PreviewFrameIndexRange? {
    if (sourceStartMs < 0L || clipDurationMs <= 0L ||
        sourceStartMs > Long.MAX_VALUE - clipDurationMs ||
        !isValidPreviewFrameRate(frameRate)
    ) return null

    val sourceEndMs = sourceStartMs + clipDurationMs
    val framesPerSecond = frameRate.toDouble()
    val firstFramePosition = sourceStartMs.toDouble() * framesPerSecond / 1_000.0
    val endFramePosition = sourceEndMs.toDouble() * framesPerSecond / 1_000.0

    // Subtract a tiny epsilon so an exact frame boundary remains inclusive at
    // the trim start and exclusive at the trim end despite floating-point noise.
    val firstFrameIndex = ceil(firstFramePosition - 1e-9).toLong().coerceAtLeast(0L)
    val endExclusiveFrameIndex = ceil(endFramePosition - 1e-9).toLong()
    val lastFrameIndex = endExclusiveFrameIndex - 1L
    if (lastFrameIndex < firstFrameIndex) return null

    return PreviewFrameIndexRange(firstFrameIndex, lastFrameIndex)
}

private fun previewFrameTimestampMs(frameIndex: Long, frameRate: Float): Long =
    (frameIndex.toDouble() * 1_000.0 / frameRate.toDouble()).roundToLong()

private fun previewFrameOffsetMs(
    frameIndex: Long,
    sourceStartMs: Long,
    clipDurationMs: Long,
    frameRate: Float,
): Long = (previewFrameTimestampMs(frameIndex, frameRate) - sourceStartMs)
    .coerceIn(0L, clipDurationMs - 1L)

private fun resolveFirstPreviewFrameOffsetMs(
    sourceStartMs: Long,
    clipDurationMs: Long,
    frameRate: Float,
): Long? {
    val range = resolvePreviewFrameIndexRange(sourceStartMs, clipDurationMs, frameRate) ?: return null
    return previewFrameOffsetMs(range.firstFrameIndex, sourceStartMs, clipDurationMs, frameRate)
}

private fun resolveLastPreviewFrameOffsetMs(
    sourceStartMs: Long,
    clipDurationMs: Long,
    frameRate: Float,
): Long? {
    val range = resolvePreviewFrameIndexRange(sourceStartMs, clipDurationMs, frameRate) ?: return null
    return previewFrameOffsetMs(range.lastFrameIndex, sourceStartMs, clipDurationMs, frameRate)
}

/**
 * Resolves one adjacent source frame inside the active clipped playlist item.
 *
 * Media3 reports positions relative to the clipped item, but frame timestamps
 * remain anchored to the original source. [sourceStartMs] therefore participates
 * in frame-index calculation and the result is converted back to clip-relative
 * time only after the target frame has been validated against the source range.
 */
internal fun resolvePreviewFrameStepTargetMs(
    currentPositionMs: Long,
    clipDurationMs: Long,
    frameRate: Float,
    direction: PreviewFrameStepDirection,
    sourceStartMs: Long = 0L,
): Long? {
    val range = resolvePreviewFrameIndexRange(sourceStartMs, clipDurationMs, frameRate) ?: return null
    val currentMs = currentPositionMs.coerceIn(0L, clipDurationMs - 1L)
    val firstFrameOffsetMs = previewFrameOffsetMs(
        range.firstFrameIndex,
        sourceStartMs,
        clipDurationMs,
        frameRate,
    )

    // A seek to the clipped item's local zero presents its first available
    // source frame, even when that frame's source timestamp is after the trim IN.
    // Treat that frame as current so NEXT advances rather than showing it twice.
    if (currentMs < firstFrameOffsetMs) {
        if (direction == PreviewFrameStepDirection.PREVIOUS) return null
        val nextFrameIndex = range.firstFrameIndex + 1L
        if (nextFrameIndex > range.lastFrameIndex) return null
        return previewFrameOffsetMs(nextFrameIndex, sourceStartMs, clipDurationMs, frameRate)
    }

    val currentSourceMs = sourceStartMs + currentMs
    val framePosition = currentSourceMs.toDouble() * frameRate.toDouble() / 1_000.0
    val nearestFrameIndex = framePosition.roundToLong()
    val nearestFrameMs = previewFrameTimestampMs(nearestFrameIndex, frameRate)
    val isOnFrameBoundary = abs(nearestFrameMs - currentSourceMs) <= 1L

    val targetFrameIndex = when (direction) {
        PreviewFrameStepDirection.PREVIOUS -> {
            if (isOnFrameBoundary) nearestFrameIndex - 1L else ceil(framePosition).toLong() - 1L
        }
        PreviewFrameStepDirection.NEXT -> {
            if (isOnFrameBoundary) nearestFrameIndex + 1L else floor(framePosition).toLong() + 1L
        }
    }
    if (targetFrameIndex !in range.firstFrameIndex..range.lastFrameIndex) return null

    val targetMs = previewFrameOffsetMs(targetFrameIndex, sourceStartMs, clipDurationMs, frameRate)
    return targetMs.takeIf { it != currentMs }
}


internal const val MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP = 120_000
internal const val MAX_CACHED_VIDEO_FRAME_TIMESTAMP_INDEXES = 3
private const val PREVIEW_FRAME_TIMESTAMP_MATCH_TOLERANCE_US = 1_000L

/** A frame timestamp index belongs to one exact non-destructive source trim. */
internal data class VideoFrameTimestampCacheKey(
    val uri: String,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val sourceRevision: String? = null,
)

/**
 * Stable, collision-resistant key for sharing one bounded MediaExtractor scan
 * across playback prefetch and manual frame-step requests. Length-prefixing
 * string fields keeps arbitrary content URIs/revisions unambiguous.
 */
internal fun videoFrameTimestampRequestKey(key: VideoFrameTimestampCacheKey): String = buildString {
    append("video-pts-v1:")
    append(key.uri.length).append(':').append(key.uri)
    append(':').append(key.sourceStartMs)
    append(':').append(key.sourceEndMs)
    append(':')
    val revision = key.sourceRevision
    if (revision == null) {
        append("-1:")
    } else {
        append(revision.length).append(':').append(revision)
    }
}

/** Rejects partial, empty, oversized, negative, duplicate, or out-of-order PTS indexes. */
internal fun isValidVideoPresentationTimestampsUs(timestampsUs: LongArray?): Boolean {
    if (timestampsUs == null || timestampsUs.isEmpty() ||
        timestampsUs.size > MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP
    ) return false
    var previousUs = -1L
    for (timestampUs in timestampsUs) {
        if (timestampUs < 0L || timestampUs <= previousUs) return false
        previousUs = timestampUs
    }
    return true
}

/**
 * Converts extractor sample-order timestamps into a unique presentation-order index.
 *
 * Compressed samples may be enumerated in decode order, so B-frame PTS values can move
 * backwards even though the final display timeline is strictly increasing.
 */
internal fun normalizeVideoPresentationTimestampsUs(sampleTimestampsUs: LongArray?): LongArray? {
    if (sampleTimestampsUs == null || sampleTimestampsUs.isEmpty() ||
        sampleTimestampsUs.size > MAX_VIDEO_FRAME_TIMESTAMPS_PER_CLIP ||
        sampleTimestampsUs.any { it < 0L }
    ) return null

    val sorted = sampleTimestampsUs.copyOf()
    sorted.sort()
    var uniqueCount = 0
    for (timestampUs in sorted) {
        if (uniqueCount == 0 || sorted[uniqueCount - 1] != timestampUs) {
            sorted[uniqueCount++] = timestampUs
        }
    }
    return sorted.copyOf(uniqueCount)
        .takeIf(::isValidVideoPresentationTimestampsUs)
}

/**
 * A sync sample is a decode-order boundary, not proof that every earlier-presented
 * B-frame has already been emitted. After the second sync sample at/after trim OUT,
 * keep reading a bounded tail so reordered samples that follow that sync sample
 * can still enter the presentation index. A later qualifying sync sample restarts
 * the tail. The total extractor sample cap remains the final fail-closed bound.
 *
 * This is a conservative codec-independent heuristic, not a guarantee for every
 * open-GOP or unusually deep-reordering stream; those still need device validation.
 */
internal const val VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT = 2
internal const val VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES = 64

internal data class VideoPtsScanLookaheadState(
    val endSyncSampleCount: Int = 0,
    val samplesAfterLastEndSync: Int = 0,
)

internal fun nextVideoPtsScanLookaheadState(
    currentState: VideoPtsScanLookaheadState,
    sampleTimeUs: Long,
    endUs: Long,
    isSyncSample: Boolean,
): VideoPtsScanLookaheadState {
    require(currentState.endSyncSampleCount >= 0)
    require(currentState.samplesAfterLastEndSync >= 0)

    if (sampleTimeUs >= endUs && isSyncSample) {
        val nextSyncCount = if (
            currentState.endSyncSampleCount >= VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT
        ) {
            VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT
        } else {
            currentState.endSyncSampleCount + 1
        }
        return VideoPtsScanLookaheadState(
            endSyncSampleCount = nextSyncCount,
            samplesAfterLastEndSync = 0,
        )
    }

    if (currentState.endSyncSampleCount < VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT ||
        currentState.samplesAfterLastEndSync >= VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES
    ) return currentState

    return currentState.copy(
        samplesAfterLastEndSync = currentState.samplesAfterLastEndSync + 1,
    )
}

internal fun shouldStopVideoPtsScan(state: VideoPtsScanLookaheadState): Boolean =
    state.endSyncSampleCount >= VIDEO_PTS_END_SYNC_LOOKAHEAD_COUNT &&
        state.samplesAfterLastEndSync >= VIDEO_PTS_POST_SYNC_DECODE_ORDER_LOOKAHEAD_SAMPLES

/**
 * Small insertion-ordered LRU for decoded clip PTS indexes; null values cache unsupported sources.
 *
 * The cache stores its own snapshot of a valid [LongArray]. Kotlin arrays are mutable, so retaining
 * the caller's instance would let a later decoder/test mutation silently corrupt a cached index.
 */
private fun VideoFrameTimestampCacheKey.hasSameSourceRangeAs(
    other: VideoFrameTimestampCacheKey,
): Boolean =
    uri == other.uri &&
        sourceStartMs == other.sourceStartMs &&
        sourceEndMs == other.sourceEndMs

/**
 * Keeps only the requested source revision for one exact URI/trim tuple.
 *
 * The continuous playhead resolves cached indexes by URI and trim. Retaining
 * multiple revisions for that tuple would make the selected index depend on
 * insertion order, so successful writes/accesses prune superseded revisions.
 */
private fun LinkedHashMap<VideoFrameTimestampCacheKey, LongArray?>.removeSupersededVideoFrameTimestampIndexes(
    key: VideoFrameTimestampCacheKey,
) {
    val supersededKeys = keys.filter { candidate ->
        candidate.hasSameSourceRangeAs(key) && candidate.sourceRevision != key.sourceRevision
    }
    supersededKeys.forEach { remove(it) }
}

/** Inserts an independent snapshot of a valid source PTS index. */
internal fun cacheVideoFrameTimestampIndex(
    cache: Map<VideoFrameTimestampCacheKey, LongArray?>,
    key: VideoFrameTimestampCacheKey,
    timestampsUs: LongArray?,
    maxEntries: Int = MAX_CACHED_VIDEO_FRAME_TIMESTAMP_INDEXES,
): Map<VideoFrameTimestampCacheKey, LongArray?> {
    require(maxEntries > 0)
    val updated = LinkedHashMap(cache)
    updated.removeSupersededVideoFrameTimestampIndexes(key)
    updated.remove(key)
    val validSnapshot = timestampsUs
        ?.takeIf(::isValidVideoPresentationTimestampsUs)
        ?.copyOf()
    updated[key] = validSnapshot
    while (updated.size > maxEntries) {
        updated.remove(updated.keys.first())
    }
    return updated
}

/**
 * Refreshes a cached index after a successful lookup. Null values are valid
 * negative-cache entries. A hit also removes older revisions for the same
 * URI/trim; a missing key remains a strict no-op.
 */
internal fun touchVideoFrameTimestampIndex(
    cache: Map<VideoFrameTimestampCacheKey, LongArray?>,
    key: VideoFrameTimestampCacheKey,
): Map<VideoFrameTimestampCacheKey, LongArray?> {
    if (!cache.containsKey(key)) return cache
    val hasSupersededRevision = cache.keys.any { candidate ->
        candidate.hasSameSourceRangeAs(key) && candidate.sourceRevision != key.sourceRevision
    }
    if (!hasSupersededRevision && cache.keys.lastOrNull() == key) return cache

    val updated = LinkedHashMap(cache)
    updated.removeSupersededVideoFrameTimestampIndexes(key)
    val value = updated.remove(key)
    updated[key] = value
    return updated
}

private data class VideoFrameTimestampRange(
    val firstIndex: Int,
    val endExclusiveIndex: Int,
)

private fun lowerBoundVideoFrameTimestampUs(
    timestampsUs: LongArray,
    targetUs: Long,
    fromIndex: Int = 0,
    endExclusiveIndex: Int = timestampsUs.size,
): Int {
    var low = fromIndex
    var high = endExclusiveIndex
    while (low < high) {
        val middle = (low + high) ushr 1
        if (timestampsUs[middle] < targetUs) low = middle + 1 else high = middle
    }
    return low
}

private fun resolveVideoFrameTimestampRange(
    timestampsUs: LongArray,
    sourceStartMs: Long,
    clipDurationMs: Long,
): VideoFrameTimestampRange? {
    if (!isValidVideoPresentationTimestampsUs(timestampsUs) ||
        sourceStartMs < 0L || clipDurationMs <= 0L ||
        sourceStartMs > Long.MAX_VALUE - clipDurationMs
    ) return null
    val sourceEndMs = sourceStartMs + clipDurationMs
    if (sourceEndMs > Long.MAX_VALUE / 1_000L) return null
    val firstIndex = lowerBoundVideoFrameTimestampUs(timestampsUs, sourceStartMs * 1_000L)
    val endExclusiveIndex = lowerBoundVideoFrameTimestampUs(timestampsUs, sourceEndMs * 1_000L)
    return VideoFrameTimestampRange(firstIndex, endExclusiveIndex)
        .takeIf { it.firstIndex < it.endExclusiveIndex }
}

private fun videoFrameTimestampOffsetMs(
    timestampUs: Long,
    sourceStartMs: Long,
    clipDurationMs: Long,
): Long {
    val roundedSourceMs = (timestampUs.toDouble() / 1_000.0).roundToLong()
    return (roundedSourceMs - sourceStartMs).coerceIn(0L, clipDurationMs - 1L)
}

internal fun resolveFirstPreviewFrameOffsetFromTimestampsMs(
    sourceStartMs: Long,
    clipDurationMs: Long,
    timestampsUs: LongArray,
): Long? {
    val range = resolveVideoFrameTimestampRange(timestampsUs, sourceStartMs, clipDurationMs) ?: return null
    return videoFrameTimestampOffsetMs(timestampsUs[range.firstIndex], sourceStartMs, clipDurationMs)
}

internal fun resolveLastPreviewFrameOffsetFromTimestampsMs(
    sourceStartMs: Long,
    clipDurationMs: Long,
    timestampsUs: LongArray,
): Long? {
    val range = resolveVideoFrameTimestampRange(timestampsUs, sourceStartMs, clipDurationMs) ?: return null
    return videoFrameTimestampOffsetMs(timestampsUs[range.endExclusiveIndex - 1], sourceStartMs, clipDurationMs)
}

/**
 * Maps Media3's clip-relative playback clock to the start of the frame whose PTS
 * is currently at or immediately before that clock position.
 *
 * This is a presentation-timeline approximation for the editor playhead, not a
 * decoded-frame callback. Media3 remains the playback clock; audio synchronization
 * and persisted preview checkpoints continue to use the unquantized player position.
 */
internal fun resolvePresentedPreviewFrameOffsetFromTimestampsMs(
    currentPositionMs: Long,
    sourceStartMs: Long,
    clipDurationMs: Long,
    timestampsUs: LongArray,
): Long? {
    if (currentPositionMs < 0L || sourceStartMs < 0L || clipDurationMs <= 0L ||
        sourceStartMs > Long.MAX_VALUE - clipDurationMs
    ) return null

    val range = resolveVideoFrameTimestampRange(timestampsUs, sourceStartMs, clipDurationMs)
        ?: return null
    val boundedPositionMs = currentPositionMs.coerceIn(0L, clipDurationMs - 1L)
    val sourcePositionMs = sourceStartMs + boundedPositionMs
    if (sourcePositionMs > Long.MAX_VALUE / 1_000L) return null

    val targetUs = sourcePositionMs * 1_000L
    val insertionIndex = lowerBoundVideoFrameTimestampUs(
        timestampsUs,
        targetUs,
        range.firstIndex,
        range.endExclusiveIndex,
    )
    val presentedIndex = when {
        insertionIndex < range.endExclusiveIndex &&
            timestampsUs[insertionIndex] == targetUs -> insertionIndex
        insertionIndex > range.firstIndex -> insertionIndex - 1
        else -> range.firstIndex
    }
    return videoFrameTimestampOffsetMs(
        timestampsUs[presentedIndex],
        sourceStartMs,
        clipDurationMs,
    )
}

/**
 * Steps through actual MediaExtractor presentation timestamps. The input index
 * is scoped to the clip's original source range; Media3 seek positions remain
 * clip-relative milliseconds, so timestamps are rounded only at the final step.
 */
internal fun resolvePreviewFrameStepTargetFromTimestampsMs(
    currentPositionMs: Long,
    clipDurationMs: Long,
    timestampsUs: LongArray,
    direction: PreviewFrameStepDirection,
    sourceStartMs: Long = 0L,
): Long? {
    val range = resolveVideoFrameTimestampRange(timestampsUs, sourceStartMs, clipDurationMs) ?: return null
    val currentMs = currentPositionMs.coerceIn(0L, clipDurationMs - 1L)
    val firstFrameOffsetMs = videoFrameTimestampOffsetMs(
        timestampsUs[range.firstIndex],
        sourceStartMs,
        clipDurationMs,
    )

    // Seeking to clip-local zero presents the first source frame at/after trim IN.
    // Treat it as current so NEXT does not show that same frame a second time.
    if (currentMs < firstFrameOffsetMs) {
        if (direction == PreviewFrameStepDirection.PREVIOUS) return null
        val nextIndex = range.firstIndex + 1
        if (nextIndex >= range.endExclusiveIndex) return null
        return videoFrameTimestampOffsetMs(timestampsUs[nextIndex], sourceStartMs, clipDurationMs)
    }

    if (sourceStartMs > Long.MAX_VALUE - currentMs ||
        sourceStartMs + currentMs > Long.MAX_VALUE / 1_000L
    ) return null
    val currentSourceUs = (sourceStartMs + currentMs) * 1_000L
    val insertionIndex = lowerBoundVideoFrameTimestampUs(
        timestampsUs,
        currentSourceUs,
        range.firstIndex,
        range.endExclusiveIndex,
    )
    val onFrameIndex = when {
        insertionIndex < range.endExclusiveIndex &&
            kotlin.math.abs(timestampsUs[insertionIndex] - currentSourceUs) <= PREVIEW_FRAME_TIMESTAMP_MATCH_TOLERANCE_US ->
            insertionIndex
        insertionIndex > range.firstIndex &&
            kotlin.math.abs(timestampsUs[insertionIndex - 1] - currentSourceUs) <= PREVIEW_FRAME_TIMESTAMP_MATCH_TOLERANCE_US ->
            insertionIndex - 1
        else -> -1
    }
    val targetIndex = when (direction) {
        PreviewFrameStepDirection.PREVIOUS -> if (onFrameIndex >= 0) onFrameIndex - 1 else insertionIndex - 1
        PreviewFrameStepDirection.NEXT -> if (onFrameIndex >= 0) onFrameIndex + 1 else insertionIndex
    }
    if (targetIndex !in range.firstIndex until range.endExclusiveIndex) return null
    return videoFrameTimestampOffsetMs(
        timestampsUs[targetIndex],
        sourceStartMs,
        clipDurationMs,
    ).takeIf { it != currentMs }
}

internal data class PreviewFrameStepTarget(
    val clipIndex: Int,
    val positionMs: Long,
)

/**
 * Steps one decoded-frame position across an ordered playlist. Each clip's
 * frame grid is anchored to its original source timestamps, not to the trimmed
 * playlist item's local zero. This keeps frame navigation aligned when a
 * non-destructive source IN point falls between frames or adjacent clips use
 * different frame rates.
 */
internal fun resolvePreviewFrameStepAcrossClips(
    clipDurationsMs: List<Long>,
    frameRatesByClip: List<Float?>,
    currentClipIndex: Int,
    currentPositionMs: Long,
    direction: PreviewFrameStepDirection,
    sourceStartMsByClip: List<Long> = emptyList(),
    presentationTimestampsUsByClip: List<LongArray?> = emptyList(),
): PreviewFrameStepTarget? {
    if (clipDurationsMs.isEmpty() ||
        frameRatesByClip.size != clipDurationsMs.size ||
        currentClipIndex !in clipDurationsMs.indices ||
        (sourceStartMsByClip.isNotEmpty() && sourceStartMsByClip.size != clipDurationsMs.size) ||
        (presentationTimestampsUsByClip.isNotEmpty() &&
            presentationTimestampsUsByClip.size != clipDurationsMs.size)
    ) return null

    val sourceStarts = sourceStartMsByClip.ifEmpty { List(clipDurationsMs.size) { 0L } }
    val timestampIndexes = presentationTimestampsUsByClip.ifEmpty { List(clipDurationsMs.size) { null } }
    val currentDurationMs = clipDurationsMs[currentClipIndex]
    val currentSourceStartMs = sourceStarts[currentClipIndex]
    val currentFrameRate = frameRatesByClip[currentClipIndex]
    val currentTimestamps = timestampIndexes[currentClipIndex]
        ?.takeIf(::isValidVideoPresentationTimestampsUs)

    val currentTargetMs = currentTimestamps?.let { timestamps ->
        resolvePreviewFrameStepTargetFromTimestampsMs(
            currentPositionMs = currentPositionMs,
            clipDurationMs = currentDurationMs,
            timestampsUs = timestamps,
            direction = direction,
            sourceStartMs = currentSourceStartMs,
        )
    } ?: currentFrameRate?.let { frameRate ->
        resolvePreviewFrameStepTargetMs(
            currentPositionMs = currentPositionMs,
            clipDurationMs = currentDurationMs,
            frameRate = frameRate,
            direction = direction,
            sourceStartMs = currentSourceStartMs,
        )
    }
    if (currentTargetMs != null) return PreviewFrameStepTarget(currentClipIndex, currentTargetMs)
    if (currentTimestamps == null &&
        (currentFrameRate == null ||
            resolvePreviewFrameIndexRange(currentSourceStartMs, currentDurationMs, currentFrameRate) == null)
    ) return null

    val adjacentClipIndex = currentClipIndex + when (direction) {
        PreviewFrameStepDirection.PREVIOUS -> -1
        PreviewFrameStepDirection.NEXT -> 1
    }
    if (adjacentClipIndex !in clipDurationsMs.indices) return null

    val adjacentDurationMs = clipDurationsMs[adjacentClipIndex]
    val adjacentSourceStartMs = sourceStarts[adjacentClipIndex]
    val adjacentFrameRate = frameRatesByClip[adjacentClipIndex]
    val adjacentTimestamps = timestampIndexes[adjacentClipIndex]
        ?.takeIf(::isValidVideoPresentationTimestampsUs)

    val adjacentPositionMs = when (direction) {
        PreviewFrameStepDirection.PREVIOUS -> adjacentTimestamps?.let { timestamps ->
            resolveLastPreviewFrameOffsetFromTimestampsMs(
                sourceStartMs = adjacentSourceStartMs,
                clipDurationMs = adjacentDurationMs,
                timestampsUs = timestamps,
            )
        } ?: adjacentFrameRate?.let { frameRate ->
            resolveLastPreviewFrameOffsetMs(
                sourceStartMs = adjacentSourceStartMs,
                clipDurationMs = adjacentDurationMs,
                frameRate = frameRate,
            )
        } ?: return null
        PreviewFrameStepDirection.NEXT -> adjacentTimestamps?.let { timestamps ->
            resolveFirstPreviewFrameOffsetFromTimestampsMs(
                sourceStartMs = adjacentSourceStartMs,
                clipDurationMs = adjacentDurationMs,
                timestampsUs = timestamps,
            )
        } ?: adjacentFrameRate?.let { frameRate ->
            resolveFirstPreviewFrameOffsetMs(
                sourceStartMs = adjacentSourceStartMs,
                clipDurationMs = adjacentDurationMs,
                frameRate = frameRate,
            )
        } ?: return null
    }
    return PreviewFrameStepTarget(adjacentClipIndex, adjacentPositionMs)
}

/** Target cadence for keeping the editor playhead visually aligned with Media3. */
internal const val PLAYBACK_PROGRESS_ACTIVE_INTERVAL_MS = 33L
internal const val PLAYBACK_PROGRESS_FRAME_ALIGNED_INTERVAL_MS = 16L
internal const val PLAYBACK_PROGRESS_IDLE_INTERVAL_MS = 100L
internal const val PREVIEW_CHECKPOINT_INTERVAL_MS = 2_000L

/**
 * Uses 30 Hz for ordinary playback and up to 60 Hz when an actual source PTS
 * index is available to frame-quantize the playhead. Paused sampling remains
 * lightweight. This controls UI sampling only; Media3 remains the playback clock.
 */
internal fun playbackProgressUpdateIntervalMs(
    isPlaying: Boolean,
    hasPresentationTimestampIndex: Boolean = false,
): Long = when {
    !isPlaying -> PLAYBACK_PROGRESS_IDLE_INTERVAL_MS
    hasPresentationTimestampIndex -> PLAYBACK_PROGRESS_FRAME_ALIGNED_INTERVAL_MS
    else -> PLAYBACK_PROGRESS_ACTIVE_INTERVAL_MS
}

/**
 * Throttles preview checkpoint writes using monotonic elapsed realtime rather
 * than wall-clock time, which can jump when the user or network changes the clock.
 * A null previous timestamp means the first active sample should be persisted.
 */
internal fun shouldPersistPreviewCheckpoint(
    nowElapsedMs: Long,
    lastPersistedElapsedMs: Long?,
    minimumIntervalMs: Long = PREVIEW_CHECKPOINT_INTERVAL_MS,
): Boolean {
    if (nowElapsedMs < 0L || minimumIntervalMs <= 0L) return false
    val lastPersisted = lastPersistedElapsedMs ?: return true
    if (lastPersisted < 0L || nowElapsedMs < lastPersisted) return false
    return nowElapsedMs - lastPersisted >= minimumIntervalMs
}
