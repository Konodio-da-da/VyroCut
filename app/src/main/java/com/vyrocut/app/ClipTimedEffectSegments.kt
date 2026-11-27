package com.vyrocut.app

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

internal const val MAX_CLIP_TIMED_EFFECT_SEGMENTS = 16

private val CLIP_TIMED_EFFECT_SEGMENT_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

/**
 * A non-destructive creative effect interval attached to one video clip.
 *
 * Times are clip-local milliseconds after the owning clip's source IN point.
 * The start is inclusive, the end is exclusive, and a null end follows the
 * clip's current OUT point. Segment IDs are scoped to their owning clip.
 */
internal data class ClipTimedEffectSegment(
    val id: String,
    val preset: ClipCreativeEffectPreset,
    val intensity: Int = DEFAULT_CREATIVE_EFFECT_INTENSITY,
    val startMs: Long = 0L,
    val endMs: Long? = null,
) {
    init {
        require(isValidClipTimedEffectSegmentId(id)) { "Timed effect segment ID is invalid" }
        require(preset != ClipCreativeEffectPreset.ORIGINAL) { "Original is not an effect segment" }
        require(intensity in MIN_CREATIVE_EFFECT_INTENSITY..MAX_CREATIVE_EFFECT_INTENSITY) {
            "Timed effect intensity must be between 0 and 100"
        }
        require(isValidClipTimedEffectSegmentTiming(startMs, endMs)) {
            "Timed effect segment interval is invalid"
        }
    }
}

internal data class ClipTimedEffectSegmentTimelineBounds(
    val startOffsetPx: Float,
    val widthPx: Float,
)

internal fun isValidClipTimedEffectSegmentId(id: String): Boolean =
    CLIP_TIMED_EFFECT_SEGMENT_ID_PATTERN.matches(id)

internal fun isValidClipTimedEffectSegmentTiming(startMs: Long, endMs: Long?): Boolean =
    startMs >= 0L && (endMs == null || endMs > startMs)

internal fun isValidClipTimedEffectSegment(
    segment: ClipTimedEffectSegment,
    clipDurationMs: Long,
): Boolean {
    if (clipDurationMs <= 0L) return false
    val effectiveEndMs = segment.endMs ?: clipDurationMs
    return isValidClipTimedEffectSegmentId(segment.id) &&
        segment.preset != ClipCreativeEffectPreset.ORIGINAL &&
        segment.intensity in MIN_CREATIVE_EFFECT_INTENSITY..MAX_CREATIVE_EFFECT_INTENSITY &&
        segment.startMs >= 0L &&
        effectiveEndMs > segment.startMs &&
        effectiveEndMs <= clipDurationMs
}

/** Empty lists remain valid for legacy projects and clips without effect tracks. */
internal fun validateClipTimedEffectSegments(
    segments: List<ClipTimedEffectSegment>,
    clipDurationMs: Long,
): Boolean {
    if (segments.isEmpty()) return true
    if (clipDurationMs <= 0L || segments.size > MAX_CLIP_TIMED_EFFECT_SEGMENTS) return false
    if (segments.map { it.id }.toSet().size != segments.size) return false
    return segments.all { isValidClipTimedEffectSegment(it, clipDurationMs) }
}

internal fun isClipTimedEffectSegmentActiveAtMs(
    segment: ClipTimedEffectSegment,
    positionMs: Long,
): Boolean =
    positionMs >= 0L &&
        positionMs >= segment.startMs &&
        (segment.endMs == null || positionMs < segment.endMs)

internal fun isClipTimedEffectSegmentActiveAtUs(
    segment: ClipTimedEffectSegment,
    presentationTimeUs: Long,
): Boolean =
    presentationTimeUs >= 0L &&
        isClipTimedEffectSegmentActiveAtMs(segment, presentationTimeUs / 1_000L)

/** Maps a clip-local effect interval to a future effect-track row. */
internal fun calculateClipTimedEffectSegmentTimelineBounds(
    trackWidthPx: Float,
    clipDurationMs: Long,
    segment: ClipTimedEffectSegment,
): ClipTimedEffectSegmentTimelineBounds? {
    if (!trackWidthPx.isFinite() || trackWidthPx <= 0f ||
        !isValidClipTimedEffectSegment(segment, clipDurationMs)
    ) return null
    val endMs = segment.endMs ?: clipDurationMs
    val start = trackWidthPx * (segment.startMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    val end = trackWidthPx * (endMs.toDouble() / clipDurationMs.toDouble()).toFloat()
    return ClipTimedEffectSegmentTimelineBounds(
        startOffsetPx = start.coerceIn(0f, trackWidthPx),
        widthPx = (end - start).coerceIn(0f, trackWidthPx - start).coerceAtLeast(1f),
    )
}

/**
 * Splits one effect interval at the same clip-local boundary used by video,
 * text and sticker edits. IDs remain stable because they are scoped per clip.
 */
internal fun splitClipTimedEffectSegmentAt(
    segment: ClipTimedEffectSegment,
    splitPositionMs: Long,
    clipDurationMs: Long,
): Pair<ClipTimedEffectSegment?, ClipTimedEffectSegment?> {
    if (!isValidClipTimedEffectSegment(segment, clipDurationMs)) return null to null
    val splitMs = splitPositionMs.coerceIn(0L, clipDurationMs)
    val startMs = segment.startMs
    val endMs = segment.endMs ?: clipDurationMs

    val leftEndMs = min(endMs, splitMs)
    val left = if (leftEndMs > startMs) {
        segment.copy(endMs = leftEndMs)
    } else {
        null
    }

    val rightStartMs = max(startMs, splitMs)
    val right = if (endMs > rightStartMs) {
        segment.copy(
            startMs = rightStartMs - splitMs,
            endMs = if (segment.endMs == null && endMs == clipDurationMs) null else endMs - splitMs,
        )
    } else {
        null
    }
    return left to right
}

/** Splits all effect intervals without changing their presets, intensity or ordering. */
internal fun splitClipTimedEffectSegmentsAt(
    segments: List<ClipTimedEffectSegment>,
    splitPositionMs: Long,
    clipDurationMs: Long,
): Pair<List<ClipTimedEffectSegment>, List<ClipTimedEffectSegment>> {
    if (segments.isEmpty() || clipDurationMs <= 0L) {
        return emptyList<ClipTimedEffectSegment>() to emptyList()
    }
    if (!validateClipTimedEffectSegments(segments, clipDurationMs)) {
        return emptyList<ClipTimedEffectSegment>() to emptyList()
    }
    val left = mutableListOf<ClipTimedEffectSegment>()
    val right = mutableListOf<ClipTimedEffectSegment>()
    segments.forEach { segment ->
        val (leftSegment, rightSegment) = splitClipTimedEffectSegmentAt(
            segment = segment,
            splitPositionMs = splitPositionMs,
            clipDurationMs = clipDurationMs,
        )
        leftSegment?.let(left::add)
        rightSegment?.let(right::add)
    }
    return left to right
}

/**
 * Appends one validated effect interval without mutating the caller's list.
 * Invalid existing data, duplicate IDs, clip-bound violations, and the per-clip
 * segment cap all fail closed so editing cannot create an unpersistable project.
 */
internal fun appendClipTimedEffectSegment(
    segments: List<ClipTimedEffectSegment>,
    clipDurationMs: Long,
    segment: ClipTimedEffectSegment,
): List<ClipTimedEffectSegment>? {
    if (!validateClipTimedEffectSegments(segments, clipDurationMs) ||
        segments.size >= MAX_CLIP_TIMED_EFFECT_SEGMENTS ||
        segments.any { it.id == segment.id } ||
        !isValidClipTimedEffectSegment(segment, clipDurationMs)
    ) return null
    return segments + segment
}

/**
 * Updates one selected effect's strength without changing its preset or interval.
 * Invalid tracks, missing IDs, and out-of-range values fail closed.
 */
internal fun updateClipTimedEffectSegmentIntensity(
    segments: List<ClipTimedEffectSegment>,
    segmentId: String,
    clipDurationMs: Long,
    intensity: Int,
): List<ClipTimedEffectSegment>? {
    if (!validateClipTimedEffectSegments(segments, clipDurationMs) ||
        intensity !in MIN_CREATIVE_EFFECT_INTENSITY..MAX_CREATIVE_EFFECT_INTENSITY
    ) return null

    val index = segments.indexOfFirst { it.id == segmentId }
    if (index < 0) return null
    val selected = segments[index]
    if (selected.intensity == intensity) return segments

    return segments.toMutableList().apply {
        this[index] = selected.copy(intensity = intensity)
    }
}

/**
 * Keeps effect timing anchored to source time when a clip's source IN/OUT
 * points change. Intervals outside the retained source range are discarded.
 */

internal enum class ClipTimedEffectSegmentEditMode {
    MOVE,
    TRIM_START,
    TRIM_END,
}

internal const val MIN_CLIP_TIMED_EFFECT_SEGMENT_EDIT_DURATION_MS = 100L

private fun addClipTimedEffectEditDelta(
    value: Long,
    deltaMs: Long,
    minimum: Long,
    maximum: Long,
): Long {
    val candidate = when {
        deltaMs > 0L && value > Long.MAX_VALUE - deltaMs -> Long.MAX_VALUE
        deltaMs < 0L && value < Long.MIN_VALUE - deltaMs -> Long.MIN_VALUE
        else -> value + deltaMs
    }
    return candidate.coerceIn(minimum, maximum)
}

/**
 * Applies one clip-local move or edge trim to a timed effect segment.
 * Bounded intervals keep their duration when moved; a null end remains attached
 * to the owning clip's OUT point. Trims enforce a 100 ms minimum interval.
 */
internal fun editClipTimedEffectSegment(
    segments: List<ClipTimedEffectSegment>,
    segmentId: String,
    clipDurationMs: Long,
    mode: ClipTimedEffectSegmentEditMode,
    deltaMs: Long,
): List<ClipTimedEffectSegment>? {
    if (!validateClipTimedEffectSegments(segments, clipDurationMs)) return null
    val index = segments.indexOfFirst { it.id == segmentId }
    if (index < 0) return null
    if (deltaMs == 0L) return segments

    val selected = segments[index]
    val effectiveEndMs = selected.endMs ?: clipDurationMs
    val currentDurationMs = effectiveEndMs - selected.startMs
    val updated = when (mode) {
        ClipTimedEffectSegmentEditMode.MOVE -> {
            val maximumStartMs = if (selected.endMs == null) {
                clipDurationMs - 1L
            } else {
                clipDurationMs - currentDurationMs
            }
            val nextStartMs = addClipTimedEffectEditDelta(
                value = selected.startMs,
                deltaMs = deltaMs,
                minimum = 0L,
                maximum = maximumStartMs,
            )
            if (selected.endMs == null) {
                selected.copy(startMs = nextStartMs)
            } else {
                selected.copy(
                    startMs = nextStartMs,
                    endMs = nextStartMs + currentDurationMs,
                )
            }
        }
        ClipTimedEffectSegmentEditMode.TRIM_START -> {
            if (currentDurationMs < MIN_CLIP_TIMED_EFFECT_SEGMENT_EDIT_DURATION_MS) return null
            val maximumStartMs = effectiveEndMs - MIN_CLIP_TIMED_EFFECT_SEGMENT_EDIT_DURATION_MS
            val nextStartMs = addClipTimedEffectEditDelta(
                value = selected.startMs,
                deltaMs = deltaMs,
                minimum = 0L,
                maximum = maximumStartMs,
            )
            selected.copy(startMs = nextStartMs)
        }
        ClipTimedEffectSegmentEditMode.TRIM_END -> {
            if (clipDurationMs - selected.startMs < MIN_CLIP_TIMED_EFFECT_SEGMENT_EDIT_DURATION_MS) return null
            val minimumEndMs = selected.startMs + MIN_CLIP_TIMED_EFFECT_SEGMENT_EDIT_DURATION_MS
            val nextEndMs = addClipTimedEffectEditDelta(
                value = effectiveEndMs,
                deltaMs = deltaMs,
                minimum = minimumEndMs,
                maximum = clipDurationMs,
            )
            selected.copy(
                endMs = if (selected.endMs == null && nextEndMs == clipDurationMs) null else nextEndMs,
            )
        }
    }

    return segments.toMutableList().apply { this[index] = updated }
        .takeIf { validateClipTimedEffectSegments(it, clipDurationMs) }
}

/** Converts horizontal pointer movement into a bounded clip-local time delta. */
internal fun calculateClipTimedEffectDragDeltaMs(
    deltaPx: Float,
    trackWidthPx: Float,
    clipDurationMs: Long,
): Long? {
    if (!deltaPx.isFinite() || !trackWidthPx.isFinite() ||
        trackWidthPx <= 0f || clipDurationMs <= 0L
    ) return null
    val fraction = (deltaPx.toDouble() / trackWidthPx.toDouble()).coerceIn(-1.0, 1.0)
    return (fraction * clipDurationMs.toDouble()).roundToLong()
}

internal fun rebaseClipTimedEffectSegmentForTrim(
    segment: ClipTimedEffectSegment?,
    oldSourceStartMs: Long,
    oldSourceEndMs: Long,
    newSourceStartMs: Long,
    newSourceEndMs: Long,
): ClipTimedEffectSegment? {
    if (segment == null || oldSourceStartMs < 0L || newSourceStartMs < 0L ||
        oldSourceEndMs <= oldSourceStartMs || newSourceEndMs <= newSourceStartMs
    ) return null
    val oldDurationMs = oldSourceEndMs - oldSourceStartMs
    if (!isValidClipTimedEffectSegment(segment, oldDurationMs)) return null
    val segmentEndRelativeMs = segment.endMs ?: oldDurationMs
    if (segment.startMs > Long.MAX_VALUE - oldSourceStartMs ||
        segmentEndRelativeMs > Long.MAX_VALUE - oldSourceStartMs
    ) return null

    val absoluteStartMs = oldSourceStartMs + segment.startMs
    val absoluteEndMs = oldSourceStartMs + segmentEndRelativeMs
    val intersectionStartMs = max(absoluteStartMs, newSourceStartMs)
    val intersectionEndMs = min(absoluteEndMs, newSourceEndMs)
    if (intersectionEndMs <= intersectionStartMs) return null

    return segment.copy(
        startMs = intersectionStartMs - newSourceStartMs,
        endMs = if (segment.endMs == null && intersectionEndMs == newSourceEndMs) {
            null
        } else {
            intersectionEndMs - newSourceStartMs
        },
    )
}

/** Rebases every effect interval independently after a non-destructive source trim. */
internal fun rebaseClipTimedEffectSegmentsForTrim(
    segments: List<ClipTimedEffectSegment>,
    oldSourceStartMs: Long,
    oldSourceEndMs: Long,
    newSourceStartMs: Long,
    newSourceEndMs: Long,
): List<ClipTimedEffectSegment> {
    if (segments.isEmpty() || oldSourceEndMs <= oldSourceStartMs) return emptyList()
    if (!validateClipTimedEffectSegments(segments, oldSourceEndMs - oldSourceStartMs)) return emptyList()
    return segments.mapNotNull { segment ->
        rebaseClipTimedEffectSegmentForTrim(
            segment = segment,
            oldSourceStartMs = oldSourceStartMs,
            oldSourceEndMs = oldSourceEndMs,
            newSourceStartMs = newSourceStartMs,
            newSourceEndMs = newSourceEndMs,
        )
    }
}

/** Returns active intervals in deterministic order for preview/export effect resolution. */
internal fun activeClipTimedEffectSegmentsAtMs(
    segments: List<ClipTimedEffectSegment>,
    positionMs: Long,
    clipDurationMs: Long,
): List<ClipTimedEffectSegment> {
    if (positionMs < 0L || positionMs >= clipDurationMs ||
        !validateClipTimedEffectSegments(segments, clipDurationMs)
    ) return emptyList()
    return segments
        .filter { isClipTimedEffectSegmentActiveAtMs(it, positionMs) }
        .sortedWith(compareBy<ClipTimedEffectSegment>({ it.startMs }, { it.id }))
}

/**
 * Resolves a clip-local Vignette interval from Media3's monotonic project /
 * composition timestamp. Both ExoPlayer's video graph and Transformer feed
 * frame timestamps on that shared timeline, so the owning clip's start must
 * be removed before evaluating its non-destructive local interval.
 *
 * Overlapping Vignette segments use the last active segment in stable
 * start-time/ID order; a zero-intensity segment disables an earlier overlap.
 */
internal fun activeClipTimedVignetteIntensityAtUs(
    segments: List<ClipTimedEffectSegment>,
    presentationTimeUs: Long,
    clipDurationMs: Long,
    clipPresentationStartMs: Long = 0L,
): Float {
    if (presentationTimeUs < 0L || clipPresentationStartMs < 0L ||
        clipPresentationStartMs > Long.MAX_VALUE / 1_000L
    ) return 0f
    val clipPresentationStartUs = clipPresentationStartMs * 1_000L
    if (presentationTimeUs < clipPresentationStartUs) return 0f
    val clipLocalTimeUs = presentationTimeUs - clipPresentationStartUs
    return activeClipTimedEffectSegmentsAtMs(
        segments = segments,
        positionMs = clipLocalTimeUs / 1_000L,
        clipDurationMs = clipDurationMs,
    ).lastOrNull { it.preset == ClipCreativeEffectPreset.VIGNETTE }
        ?.intensity
        ?.div(100f)
        ?: 0f
}

/**
 * Validates the complete persisted track before filtering to presets that have
 * a frame-time renderer shared by preview and Transformer export.
 *
 * Returning null distinguishes invalid project data from a valid track that
 * currently contains no timed-renderable presets.
 */
internal fun supportedClipTimedEffectSegmentsOrNull(
    segments: List<ClipTimedEffectSegment>,
    clipDurationMs: Long,
): List<ClipTimedEffectSegment>? {
    if (!validateClipTimedEffectSegments(segments, clipDurationMs)) return null
    return segments.filter { isClipTimedCreativeEffectPresetSupported(it.preset) }
}

/** Pure effect descriptions for the active, frame-time-renderable intervals. */
internal fun createClipTimedEffectSpecsAtMs(
    segments: List<ClipTimedEffectSegment>,
    positionMs: Long,
    clipDurationMs: Long,
): List<ClipVisualEffectSpec> {
    val supportedSegments = supportedClipTimedEffectSegmentsOrNull(segments, clipDurationMs)
        ?: return emptyList()
    return activeClipTimedEffectSegmentsAtMs(
        supportedSegments,
        positionMs,
        clipDurationMs,
    ).flatMap { segment ->
        createClipVisualEffectSpecs(
            ClipVisualAdjustments(
                creativeEffectPreset = segment.preset,
                creativeEffectIntensity = segment.intensity,
            ),
        )
    }
}

internal fun decodeClipTimedEffectSegment(
    id: String?,
    presetId: String?,
    intensity: Int,
    startMs: Long,
    endMs: Long? = null,
): ClipTimedEffectSegment? {
    if (!isValidClipTimedEffectSegmentId(id.orEmpty()) ||
        intensity !in MIN_CREATIVE_EFFECT_INTENSITY..MAX_CREATIVE_EFFECT_INTENSITY ||
        !isValidClipTimedEffectSegmentTiming(startMs, endMs)
    ) return null
    val preset = clipCreativeEffectPresetFromId(presetId) ?: return null
    if (preset == ClipCreativeEffectPreset.ORIGINAL) return null
    return runCatching {
        ClipTimedEffectSegment(
            id = id.orEmpty(),
            preset = preset,
            intensity = intensity,
            startMs = startMs,
            endMs = endMs,
        )
    }.getOrNull()
}
