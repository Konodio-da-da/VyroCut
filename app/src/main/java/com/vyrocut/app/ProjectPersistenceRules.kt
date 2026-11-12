package com.vyrocut.app

/**
 * Decodes persisted undo/redo snapshots without turning corrupt entries into
 * empty projects. A valid empty project remains a legitimate history state.
 */
internal fun <T> decodeValidHistorySnapshots(
    rawSnapshots: List<String>,
    decode: (String) -> T?,
): List<T> = rawSnapshots.mapNotNull(decode).takeLast(50)

internal data class ProjectCanvasSettings(
    val aspectRatioPreset: String = "9:16",
    val backgroundPreset: String = "BLACK",
)

private const val PROJECT_CANVAS_SETTINGS_HEADER = "VYROCUT_CANVAS_V1"
private val PROJECT_CANVAS_ASPECT_PRESETS = setOf("9:16", "16:9", "1:1")
private val PROJECT_CANVAS_BACKGROUND_PRESETS = setOf("BLACK", "DARK", "WHITE")

internal fun encodeProjectCanvasSettings(settings: ProjectCanvasSettings): String {
    require(settings.aspectRatioPreset in PROJECT_CANVAS_ASPECT_PRESETS)
    require(settings.backgroundPreset in PROJECT_CANVAS_BACKGROUND_PRESETS)
    return PROJECT_CANVAS_SETTINGS_HEADER + "|" +
        settings.aspectRatioPreset + "|" + settings.backgroundPreset
}

internal fun decodeProjectCanvasSettings(raw: String?): ProjectCanvasSettings? {
    if (raw.isNullOrBlank()) return null
    val fields = raw.split('|')
    if (fields.size != 3 || fields[0] != PROJECT_CANVAS_SETTINGS_HEADER) return null
    if (fields[1] !in PROJECT_CANVAS_ASPECT_PRESETS) return null
    if (fields[2] !in PROJECT_CANVAS_BACKGROUND_PRESETS) return null
    return ProjectCanvasSettings(fields[1], fields[2])
}
