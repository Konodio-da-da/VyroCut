package com.vyrocut.app

import android.net.Uri

/** Media selected by the user through Android's document picker. */
data class ImportedMedia(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?,
    val durationUs: Long? = null,
)

/** Stable timeline timebase: microseconds, matching Android media APIs. */
@JvmInline
value class TimelineTimeUs(val value: Long) {
    init { require(value >= 0L) { "Timeline time cannot be negative" } }
}

enum class TrackKind { VIDEO, AUDIO, IMAGE, TEXT, CAPTION }

data class TimelineClip(
    val id: String,
    val source: Uri,
    val trackKind: TrackKind,
    val timelineStart: TimelineTimeUs,
    val sourceStart: TimelineTimeUs,
    val sourceEnd: TimelineTimeUs,
) {
    init {
        require(sourceEnd.value > sourceStart.value) { "Clip source range must have positive duration" }
    }
    val durationUs: Long get() = sourceEnd.value - sourceStart.value
}
