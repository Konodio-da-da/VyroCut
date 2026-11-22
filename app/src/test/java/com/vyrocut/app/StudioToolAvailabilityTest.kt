package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioToolAvailabilityTest {
    @Test
    fun effectsPanelExplainsThatPreviewAndExportAreUnchanged() {
        assertEquals(
            "Effects aren't available yet. This panel does not apply anything to preview or export.",
            unavailableStudioToolMessage("Effects"),
        )
    }

    @Test
    fun stickersPanelExplainsThatNoProjectOverlayIsCreated() {
        assertEquals(
            "Sticker overlays aren't available yet. This tool does not add anything to the project.",
            unavailableStudioToolMessage("Stickers"),
        )
    }

    @Test
    fun implementedToolsDoNotShowUnavailableWarnings() {
        listOf("Edit", "Audio", "Text", "Filters", "Ratio", "Canvas", "Adjust").forEach { tool ->
            assertNull("$tool should remain available", unavailableStudioToolMessage(tool))
        }
    }
}
