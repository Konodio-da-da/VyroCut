package com.vyrocut.app

/**
 * Explains tools whose UI is present but whose project/rendering pipeline is not.
 * Keep these tools explicit until real preview, persistence and export support exists.
 */
internal fun unavailableStudioToolMessage(tool: String): String? = when (tool) {
    "Effects" -> "Effects aren't available yet. This panel does not apply anything to preview or export."
    "Stickers" -> "Sticker overlays aren't available yet. This tool does not add anything to the project."
    else -> null
}
