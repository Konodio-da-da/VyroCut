package com.vyrocut.app

import org.json.JSONArray
import org.json.JSONObject

internal enum class AudioTrackType {
    MUSIC,
    SFX,
    VOICEOVER,
}

internal const val MAX_AUDIO_TRACK_CLIPS = 24

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
        clip.durationMs > 0L &&
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
    if (projectDurationMs <= 0L || sourceDurationMs <= 0L) return null
    var candidate = requestedStartMs.coerceIn(0L, projectDurationMs)
    val ordered = clips.filter { it.type == type }.sortedBy { it.startMs }

    for (existing in ordered) {
        if (existing.endMs <= candidate) continue
        if (existing.startMs > candidate) {
            val gapMs = existing.startMs - candidate
            val fitMs = minOf(sourceDurationMs, gapMs)
            if (fitMs > 0L) return AudioTrackInsertionSlot(candidate, fitMs)
        }
        candidate = maxOf(candidate, existing.endMs)
        if (candidate >= projectDurationMs) return null
    }
    val remainingMs = projectDurationMs - candidate
    val fitMs = minOf(sourceDurationMs, remainingMs)
    return if (fitMs > 0L) AudioTrackInsertionSlot(candidate, fitMs) else null
}

internal fun resolveAudioTrackInsertionStartMs(
    clips: List<AudioTrackClip>,
    type: AudioTrackType,
    requestedStartMs: Long,
    clipDurationMs: Long,
    projectDurationMs: Long,
): Long? {
    if (projectDurationMs <= 0L || clipDurationMs <= 0L || clipDurationMs > projectDurationMs) return null
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
        if (availableMs <= 0L) return@mapNotNull null
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
    if (raw.isNullOrBlank()) return emptyList()
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
