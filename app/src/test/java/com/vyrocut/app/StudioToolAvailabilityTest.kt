package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudioToolAvailabilityTest {
    @Test
    fun implementedHueEffectDoesNotShowAnUnavailableWarning() {
        assertNull(unavailableStudioToolMessage("Effects"))
    }

    @Test
    fun implementedStickerOverlaysDoNotShowAnUnavailableWarning() {
        assertNull(unavailableStudioToolMessage("Stickers"))
    }

    @Test
    fun implementedToolsDoNotShowUnavailableWarnings() {
        listOf("Edit", "Audio", "Text", "Stickers", "Filters", "Ratio", "Canvas", "Adjust", "Effects").forEach { tool ->
            assertNull("$tool should remain available", unavailableStudioToolMessage(tool))
        }
    }
}
