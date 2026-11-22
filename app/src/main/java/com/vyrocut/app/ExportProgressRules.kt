package com.vyrocut.app

internal enum class ExportUiStage(val label: String) {
    IDLE("Ready"),
    PREPARING("Preparing export"),
    RENDERING("Rendering MP4"),
    VERIFYING("Verifying and saving"),
    COMPLETE("Export complete"),
    FAILED("Export failed"),
    CANCELLED("Export cancelled"),
}

/**
 * Accepts only valid Media3 percentage values and prevents a delayed callback
 * from making the visible progress move backwards.
 */
internal fun nextExportProgressPercent(current: Int?, reported: Int): Int? {
    if (reported !in 0..100) return current
    return maxOf(current ?: 0, reported)
}
