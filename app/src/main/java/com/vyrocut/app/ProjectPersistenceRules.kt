package com.vyrocut.app

/**
 * Decodes persisted undo/redo snapshots without turning corrupt entries into
 * empty projects. A valid empty project remains a legitimate history state.
 */
internal fun <T> decodeValidHistorySnapshots(
    rawSnapshots: List<String>,
    decode: (String) -> T?,
): List<T> = rawSnapshots.mapNotNull(decode).takeLast(50)
