package com.vyrocut.app

/**
 * A replacement video is compatible only when the existing non-destructive
 * source window still fits inside it. The caller keeps the original in/out
 * points rather than silently changing the edit.
 */
internal fun isClipSourceCompatible(
    sourceStartMs: Long,
    sourceEndMs: Long,
    replacementDurationMs: Long,
): Boolean {
    if (sourceStartMs < 0L || sourceEndMs <= sourceStartMs || replacementDurationMs <= 0L) {
        return false
    }
    return sourceEndMs <= replacementDurationMs
}
