package com.vyrocut.app

import org.json.JSONArray
import org.json.JSONObject

internal enum class AudioTrackType {
    MUSIC,
    SFX,
    VOICEOVER,
}

internal const val MAX_AUDIO_TRACK_CLIPS = 24
internal const val MIN_AUDIO_TRACK_CLIP_DURATION_MS = 100L

/**
 * A non-destructive audio segment positioned on the project timeline.
 * Source trim points are relative to the original audio file; startMs is
 * relative to the complete video project.
 */
internal data class AudioTrackClip(
    val id: String,
    val type: AudioTrackType,
    val uri: String,
    val name: String,
    val startMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    /** Full readable source duration, retained so trims can be expanded again. */
    val sourceDurationMs: Long = sourceEndMs,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
) {
    val durationMs: Long
        get() = (sourceEndMs - sourceStartMs).coerceAtLeast(0L)

    val endMs: Long
        get() = addAudioTimelineTime(startMs, durationMs)
}

internal data class AudioTrackTimelineSegment(
    val startMs: Long,
    val endMs: Long,
    val clip: AudioTrackClip?,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Saturating addition keeps malformed or extremely long projects from wrapping. */
internal fun addAudioTimelineTime(leftMs: Long, rightMs: Long): Long {
    val left = leftMs.coerceAtLeast(0L)
    val right = rightMs.coerceAtLeast(0L)
    return if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
}

internal fun isValidAudioTrackClip(clip: AudioTrackClip, projectDurationMs: Long): Boolean =
    clip.id.isNotBlank() &&
        clip.uri.isNotBlank() &&
        clip.name.isNotBlank() &&
        clip.startMs >= 0L &&
        clip.sourceStartMs >= 0L &&
        clip.sourceEndMs > clip.sourceStartMs &&
        clip.sourceDurationMs >= clip.sourceEndMs &&
        clip.sourceDurationMs > 0L &&
        clip.durationMs >= MIN_AUDIO_TRACK_CLIP_DURATION_MS &&
        clip.endMs > clip.startMs &&
        clip.endMs <= projectDurationMs &&
        clip.volume.isFinite() &&
        clip.volume in 0f..1f &&
        clip.fadeInMs >= 0L &&
        clip.fadeOutMs >= 0L

/**
 * Each lane is sequential in this first audio-track milestone. Different lanes
 * may overlap, allowing music, effects and voiceover to mix concurrently.
 */
internal fun validateAudioTrackClips(
    clips: List<AudioTrackClip>,
    projectDurationMs: Long,
): Boolean {
    if (projectDurationMs <= 0L || clips.size > MAX_AUDIO_TRACK_CLIPS) return clips.isEmpty()
    if (clips.map { it.id }.toSet().size != clips.size) return false
    if (clips.any { !isValidAudioTrackClip(it, projectDurationMs) }) return false

    return AudioTrackType.values().all { type ->
        val ordered = clips.filter { it.type == type }.sortedBy { it.startMs }
        ordered.zipWithNext().all { (left, right) -> right.startMs >= left.endMs }
    }
}

/**
 * Finds the first free interval at or after the requested playhead on one lane.
 * Returns null when the source cannot fit without overlapping an existing item.
 */

internal data class AudioTrackInsertionSlot(
    val startMs: Long,
    val durationMs: Long,
)

/**
 * Chooses the first free interval at or after the playhead and trims the source
 * to that interval when necessary. This lets a long imported file fit the
 * remaining project time without overlapping another clip on the same lane.
 */
internal fun resolveAudioTrackInsertionSlot(
    clips: List<AudioTrackClip>,
    type: AudioTrackType,
    requestedStartMs: Long,
    sourceDurationMs: Long,
    projectDurationMs: Long,
): AudioTrackInsertionSlot? {
    if (projectDurationMs <= 0L || sourceDurationMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS) return null
    var candidate = requestedStartMs.coerceIn(0L, projectDurationMs)
    val ordered = clips.filter { it.type == type }.sortedBy { it.startMs }

    for (existing in ordered) {
        if (existing.endMs <= candidate) continue
        if (existing.startMs > candidate) {
            val gapMs = existing.startMs - candidate
            val fitMs = minOf(sourceDurationMs, gapMs)
            if (fitMs >= MIN_AUDIO_TRACK_CLIP_DURATION_MS) {
                return AudioTrackInsertionSlot(candidate, fitMs)
            }
        }
        candidate = maxOf(candidate, existing.endMs)
        if (candidate >= projectDurationMs) return null
    }
    val remainingMs = projectDurationMs - candidate
    val fitMs = minOf(sourceDurationMs, remainingMs)
    return if (fitMs >= MIN_AUDIO_TRACK_CLIP_DURATION_MS) {
        AudioTrackInsertionSlot(candidate, fitMs)
    } else {
        null
    }
}

internal fun resolveAudioTrackInsertionStartMs(
    clips: List<AudioTrackClip>,
    type: AudioTrackType,
    requestedStartMs: Long,
    clipDurationMs: Long,
    projectDurationMs: Long,
): Long? {
    if (projectDurationMs <= 0L ||
        clipDurationMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS ||
        clipDurationMs > projectDurationMs
    ) return null
    var candidate = requestedStartMs.coerceIn(0L, projectDurationMs)
    val ordered = clips.filter { it.type == type }.sortedBy { it.startMs }

    for (existing in ordered) {
        if (existing.endMs <= candidate) continue
        val proposedEnd = addAudioTimelineTime(candidate, clipDurationMs)
        if (proposedEnd <= existing.startMs) return candidate
        candidate = existing.endMs
        if (candidate > projectDurationMs - clipDurationMs) return null
    }
    return candidate.takeIf { it <= projectDurationMs - clipDurationMs }
}

/**
 * Changes source in/out points without touching the original file. A trim may
 * expand back to any range inside the source duration captured at import time.
 * The edit is rejected if it would overlap another clip on the same lane.
 */
internal fun trimAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    val selected = clips.firstOrNull { it.id == clipId } ?: return null
    if (sourceStartMs < 0L ||
        sourceEndMs > selected.sourceDurationMs ||
        sourceEndMs - sourceStartMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS
    ) return null

    val updated = clips.map { clip ->
        if (clip.id == clipId) clip.copy(
            sourceStartMs = sourceStartMs,
            sourceEndMs = sourceEndMs,
            fadeInMs = clip.fadeInMs.coerceAtMost(sourceEndMs - sourceStartMs),
            fadeOutMs = clip.fadeOutMs.coerceAtMost(sourceEndMs - sourceStartMs),
        ) else clip
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

/**
 * Splits one audio edit-decision-list item at an absolute project time.
 *
 * The left item keeps the original ID and fade-in; the right item receives
 * [newClipId] and keeps the original fade-out. Splits inside an active fade
 * ramp are rejected because the current fade model cannot represent a
 * non-zero starting gain on a newly split item without changing the sound.
 * Both items continue to reference the same untouched source URI.
 */
/**
 * Trims one audio clip and ripples only later clips on the same lane by the
 * resulting duration delta. Shortening pulls later clips earlier; extending
 * pushes them later. Existing gaps and all other lanes remain unchanged.
 *
 * The edit is atomic: if the source range is invalid or any shifted clip would
 * leave the project, no updated list is returned.
 */
internal fun rippleTrimAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    sourceStartMs: Long,
    sourceEndMs: Long,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (!validateAudioTrackClips(clips, projectDurationMs)) return null
    val selected = clips.singleOrNull { it.id == clipId } ?: return null
    if (sourceStartMs < 0L ||
        sourceEndMs > selected.sourceDurationMs ||
        sourceEndMs - sourceStartMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS
    ) return null

    val nextDurationMs = sourceEndMs - sourceStartMs
    val durationDeltaMs = nextDurationMs - selected.durationMs
    val updated = clips.map { clip ->
        when {
            clip.id == selected.id -> clip.copy(
                sourceStartMs = sourceStartMs,
                sourceEndMs = sourceEndMs,
                fadeInMs = clip.fadeInMs.coerceAtMost(nextDurationMs),
                fadeOutMs = clip.fadeOutMs.coerceAtMost(nextDurationMs),
            )
            clip.type == selected.type && clip.startMs >= selected.endMs -> {
                val nextStartMs = if (durationDeltaMs >= 0L) {
                    addAudioTimelineTime(clip.startMs, durationDeltaMs)
                } else {
                    clip.startMs - (-durationDeltaMs)
                }
                clip.copy(startMs = nextStartMs)
            }
            else -> clip
        }
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

internal fun splitAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    splitTimelineMs: Long,
    newClipId: String,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (!validateAudioTrackClips(clips, projectDurationMs) ||
        clips.size >= MAX_AUDIO_TRACK_CLIPS ||
        newClipId.isBlank() ||
        clips.any { it.id == newClipId }
    ) return null

    val selectedIndex = clips.indexOfFirst { it.id == clipId }
    if (selectedIndex < 0) return null
    val selected = clips[selectedIndex]
    if (splitTimelineMs <= selected.startMs || splitTimelineMs >= selected.endMs) return null

    val splitOffsetMs = splitTimelineMs - selected.startMs
    val rightDurationMs = selected.durationMs - splitOffsetMs
    if (splitOffsetMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS ||
        rightDurationMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS
    ) return null

    val lastFadeInMs = selected.fadeInMs
    val firstFadeOutMs = selected.durationMs - selected.fadeOutMs
    if (splitOffsetMs < lastFadeInMs || splitOffsetMs > firstFadeOutMs) return null

    // The validated source range guarantees this addition remains <= sourceEndMs.
    val splitSourceMs = selected.sourceStartMs + splitOffsetMs
    if (splitSourceMs <= selected.sourceStartMs || splitSourceMs >= selected.sourceEndMs) return null

    val left = selected.copy(
        sourceEndMs = splitSourceMs,
        fadeOutMs = 0L,
    )
    val right = selected.copy(
        id = newClipId,
        startMs = splitTimelineMs,
        sourceStartMs = splitSourceMs,
        fadeInMs = 0L,
    )
    val updated = clips.toMutableList().apply {
        this[selectedIndex] = left
        add(selectedIndex + 1, right)
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

/**
 * Moves one lane item to the first non-overlapping slot at or after the requested
 * project time. Its source trim, volume and fades remain unchanged.
 */
internal fun moveAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    requestedStartMs: Long,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (!validateAudioTrackClips(clips, projectDurationMs)) return null
    val selected = clips.firstOrNull { it.id == clipId } ?: return null
    val others = clips.filterNot { it.id == clipId }
    val nextStartMs = resolveAudioTrackInsertionStartMs(
        clips = others,
        type = selected.type,
        requestedStartMs = requestedStartMs,
        clipDurationMs = selected.durationMs,
        projectDurationMs = projectDurationMs,
    ) ?: return null
    val updated = clips.map { clip ->
        if (clip.id == clipId) clip.copy(startMs = nextStartMs) else clip
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

/**
 * Duplicates an audio edit-decision-list item into the first free slot at or
 * after the selected clip's end on the same lane. The new item keeps the
 * original source range, volume and fades, but receives an independent ID.
 */
internal fun duplicateAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    newClipId: String,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (!validateAudioTrackClips(clips, projectDurationMs) ||
        clips.size >= MAX_AUDIO_TRACK_CLIPS ||
        newClipId.isBlank() ||
        clips.any { it.id == newClipId }
    ) return null

    val selectedIndex = clips.indexOfFirst { it.id == clipId }
    if (selectedIndex < 0) return null
    val selected = clips[selectedIndex]
    val otherClips = clips.filterNot { it.id == clipId }
    val duplicateStartMs = resolveAudioTrackInsertionStartMs(
        clips = otherClips,
        type = selected.type,
        requestedStartMs = selected.endMs,
        clipDurationMs = selected.durationMs,
        projectDurationMs = projectDurationMs,
    ) ?: return null

    val duplicate = selected.copy(id = newClipId, startMs = duplicateStartMs)
    val updated = clips.toMutableList().apply {
        add(selectedIndex + 1, duplicate)
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

/**
 * Removes one audio clip and closes its duration on the same lane.
 *
 * Only later clips on the selected lane move left. Clips before it, clips on
 * other lanes, source trims, volume and fades remain unchanged. Any gaps on
 * either side of the removed clip are combined rather than erased.
 */
internal fun rippleDeleteAudioTrackClip(
    clips: List<AudioTrackClip>,
    clipId: String,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (!validateAudioTrackClips(clips, projectDurationMs)) return null
    val selected = clips.singleOrNull { it.id == clipId } ?: return null
    val shiftMs = selected.durationMs
    val updated = clips.filterNot { it.id == selected.id }.map { clip ->
        if (clip.type == selected.type && clip.startMs >= selected.endMs) {
            clip.copy(startMs = clip.startMs - shiftMs)
        } else {
            clip
        }
    }
    return updated.takeIf { validateAudioTrackClips(it, projectDurationMs) }
}

/**
 * Produces a gap-filled sequence for one lane. Gaps become silent media items
 * in preview/export, so every lane has the same project-wide clock.
 */
internal fun buildAudioTrackTimelineSegments(
    clips: List<AudioTrackClip>,
    type: AudioTrackType,
    projectDurationMs: Long,
): List<AudioTrackTimelineSegment> {
    if (projectDurationMs <= 0L) return emptyList()
    val ordered = clips.filter { it.type == type }.sortedBy { it.startMs }
    if (ordered.isEmpty() || !validateAudioTrackClips(clips, projectDurationMs)) return emptyList()

    val segments = mutableListOf<AudioTrackTimelineSegment>()
    var cursorMs = 0L
    for (clip in ordered) {
        if (clip.startMs > cursorMs) {
            segments += AudioTrackTimelineSegment(cursorMs, clip.startMs, null)
        }
        segments += AudioTrackTimelineSegment(clip.startMs, clip.endMs, clip)
        cursorMs = clip.endMs
    }
    if (cursorMs < projectDurationMs) {
        segments += AudioTrackTimelineSegment(cursorMs, projectDurationMs, null)
    }
    return segments
}

/** Returns the clip's volume/fade gain at an absolute project timeline position. */
internal fun audioTrackGainAtTimelinePosition(
    clip: AudioTrackClip,
    timelinePositionMs: Long,
): Float {
    if (timelinePositionMs < clip.startMs || timelinePositionMs >= clip.endMs) return 0f
    return clipAudioPlaybackGain(
        volume = clip.volume,
        positionMs = timelinePositionMs - clip.startMs,
        clipDurationMs = clip.durationMs,
        fadeInMs = clip.fadeInMs,
        fadeOutMs = clip.fadeOutMs,
    )
}


internal const val AUDIO_TRACK_SYNC_TOLERANCE_MS = 180L
internal const val AUDIO_TRACK_RESYNC_COOLDOWN_MS = 250L

/** Avoids repeatedly seeking an audio decoder while it is still catching up. */
internal fun shouldResyncAudioTrack(
    projectPositionMs: Long,
    trackPositionMs: Long,
    currentSegmentIndex: Int,
    expectedSegmentIndex: Int?,
    segmentCount: Int,
    lastResyncAtMs: Long?,
    nowMs: Long,
    lastResyncSegmentIndex: Int? = null,
): Boolean {
    if (segmentCount <= 0) return false
    val expectedSegment = expectedSegmentIndex?.takeIf { it in 0 until segmentCount } ?: return false
    val isOnExpectedSegment = currentSegmentIndex == expectedSegment
    if (isOnExpectedSegment) {
        val projectPosition = projectPositionMs.coerceAtLeast(0L)
        val trackPosition = trackPositionMs.coerceAtLeast(0L)
        val driftMs = if (projectPosition >= trackPosition) {
            projectPosition - trackPosition
        } else {
            trackPosition - projectPosition
        }
        if (driftMs <= AUDIO_TRACK_SYNC_TOLERANCE_MS) return false
    }
    // A new target segment usually means the user crossed or sought across a
    // clip boundary. Do not carry the previous segment's cooldown into that
    // discontinuity; the same target remains throttled below while Media3 settles.
    if (lastResyncSegmentIndex != null && lastResyncSegmentIndex != expectedSegment) {
        return true
    }
    // A wrong playlist item is a sync error even when its local clock happens
    // to equal project time (for example, the previous item at an exact boundary).
    val lastResync = lastResyncAtMs ?: return true
    val elapsedSinceResync = (nowMs - lastResync).coerceAtLeast(0L)
    return elapsedSinceResync >= AUDIO_TRACK_RESYNC_COOLDOWN_MS
}

internal data class AudioTrackSeekTarget(
    val segmentIndex: Int,
    val positionMs: Long,
)

/** Maps project time to the lane playlist, whose silence items preserve global timing. */
internal fun resolveAudioTrackSeekTarget(
    segments: List<AudioTrackTimelineSegment>,
    projectPositionMs: Long,
): AudioTrackSeekTarget? {
    if (segments.isEmpty()) return null
    val totalDurationMs = segments.fold(0L) { total, segment ->
        addAudioTimelineTime(total, segment.durationMs)
    }
    val target = projectPositionMs.coerceIn(0L, (totalDurationMs - 1L).coerceAtLeast(0L))
    var elapsedMs = 0L
    segments.forEachIndexed { index, segment ->
        if (target < addAudioTimelineTime(elapsedMs, segment.durationMs)) {
            return AudioTrackSeekTarget(index, (target - elapsedMs).coerceAtLeast(0L))
        }
        elapsedMs = addAudioTimelineTime(elapsedMs, segment.durationMs)
    }
    val last = segments.lastIndex
    return AudioTrackSeekTarget(last, (segments[last].durationMs - 1L).coerceAtLeast(0L))
}

/**
 * Resolves every audio lane to the same canonical project-clock position.
 * Empty lanes are omitted; gap-filled lanes return a seek target inside their
 * silence item so their playlist clock remains aligned with the video.
 */
internal fun resolveAudioTrackSeekTargets(
    segmentsByType: Map<AudioTrackType, List<AudioTrackTimelineSegment>>,
    projectPositionMs: Long,
): Map<AudioTrackType, AudioTrackSeekTarget> =
    AudioTrackType.values().mapNotNull { type ->
        resolveAudioTrackSeekTarget(segmentsByType[type].orEmpty(), projectPositionMs)
            ?.let { target -> type to target }
    }.toMap()

internal data class SynchronizedPreviewSeekTarget(
    val videoTarget: TimelineSeekTarget,
    val projectPositionMs: Long,
    val audioTargets: Map<AudioTrackType, AudioTrackSeekTarget>,
)

/**
 * Maps one requested project time to the video playlist and all prepared audio
 * lane playlists. At the project end, the video's final playable millisecond
 * becomes the canonical time used by every audio lane as well.
 */
internal fun resolveSynchronizedPreviewSeekTarget(
    clipDurationsMs: List<Long>,
    audioSegmentsByType: Map<AudioTrackType, List<AudioTrackTimelineSegment>>,
    requestedProjectPositionMs: Long,
): SynchronizedPreviewSeekTarget? {
    val videoTarget = resolveTimelineSeekTarget(clipDurationsMs, requestedProjectPositionMs) ?: return null
    val projectPositionMs = calculateTimelinePositionMs(
        clipDurationsMs,
        videoTarget.clipIndex,
        videoTarget.positionMs,
    ).coerceIn(0L, calculateTimelineDurationMs(clipDurationsMs))
    return SynchronizedPreviewSeekTarget(
        videoTarget = videoTarget,
        projectPositionMs = projectPositionMs,
        audioTargets = resolveAudioTrackSeekTargets(audioSegmentsByType, projectPositionMs),
    )
}

/** Reconstructs project time from the active lane playlist item and its local position. */
internal fun calculateAudioTrackTimelinePositionMs(
    segments: List<AudioTrackTimelineSegment>,
    segmentIndex: Int,
    segmentPositionMs: Long,
): Long {
    if (segmentIndex !in segments.indices) return 0L
    var elapsedMs = 0L
    for (index in 0 until segmentIndex) {
        elapsedMs = addAudioTimelineTime(elapsedMs, segments[index].durationMs)
    }
    return addAudioTimelineTime(
        elapsedMs,
        segmentPositionMs.coerceIn(0L, segments[segmentIndex].durationMs),
    )
}

/** Shortens or removes overlay audio that no longer fits after a video edit. */
internal fun trimAudioTrackClipsToProjectDuration(
    clips: List<AudioTrackClip>,
    projectDurationMs: Long,
): List<AudioTrackClip> {
    if (projectDurationMs <= 0L) return emptyList()
    return clips.mapNotNull { clip ->
        if (clip.startMs >= projectDurationMs) return@mapNotNull null
        val availableMs = projectDurationMs - clip.startMs
        if (availableMs < MIN_AUDIO_TRACK_CLIP_DURATION_MS) return@mapNotNull null
        if (clip.durationMs <= availableMs) {
            clip
        } else {
            val nextSourceEnd = addAudioTimelineTime(clip.sourceStartMs, availableMs)
            clip.copy(
                sourceEndMs = nextSourceEnd,
                fadeInMs = clip.fadeInMs.coerceAtMost(availableMs),
                fadeOutMs = clip.fadeOutMs.coerceAtMost(availableMs),
            )
        }
    }
}

internal fun encodeAudioTrackClips(clips: List<AudioTrackClip>): String {
    val array = JSONArray()
    clips.forEach { clip ->
        array.put(JSONObject().apply {
            put("id", clip.id)
            put("type", clip.type.name)
            put("uri", clip.uri)
            put("name", clip.name)
            put("startMs", clip.startMs)
            put("sourceStartMs", clip.sourceStartMs)
            put("sourceEndMs", clip.sourceEndMs)
            put("sourceDurationMs", clip.sourceDurationMs)
            put("volume", clip.volume.toDouble())
            put("fadeInMs", clip.fadeInMs)
            put("fadeOutMs", clip.fadeOutMs)
        })
    }
    return array.toString()
}

/** Null means corrupt/invalid data; an absent value is a valid empty track set. */
internal fun decodeAudioTrackClips(
    raw: String?,
    projectDurationMs: Long,
): List<AudioTrackClip>? {
    if (raw == null) return emptyList()
    if (raw.isBlank()) return null
    return runCatching {
        val array = JSONArray(raw)
        require(array.length() <= MAX_AUDIO_TRACK_CLIPS)
        val clips = buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val volume = item.optDouble("volume", Double.NaN)
                require(volume.isFinite())
                add(
                    AudioTrackClip(
                        id = item.getString("id"),
                        type = AudioTrackType.valueOf(item.getString("type")),
                        uri = item.getString("uri"),
                        name = item.getString("name"),
                        startMs = item.getLong("startMs"),
                        sourceStartMs = item.getLong("sourceStartMs"),
                        sourceEndMs = item.getLong("sourceEndMs"),
                        sourceDurationMs = item.optLong("sourceDurationMs", item.getLong("sourceEndMs")),
                        volume = volume.toFloat(),
                        fadeInMs = item.optLong("fadeInMs", 0L),
                        fadeOutMs = item.optLong("fadeOutMs", 0L),
                    ),
                )
            }
        }
        require(validateAudioTrackClips(clips, projectDurationMs))
        clips
    }.getOrNull()
}
