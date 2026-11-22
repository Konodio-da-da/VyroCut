package com.vyrocut.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class VyroCutDesignSystemTest {
    @Test
    fun sharedPaletteMatchesTheApprovedDesignReference() {
        assertEquals(Color(0xFF131313), VyroCutPalette.Background)
        assertEquals(Color(0xFF1C1B1B), VyroCutPalette.Surface)
        assertEquals(Color(0xFF0FE8B8), VyroCutPalette.Accent)
        assertEquals(Color(0xFF00E5FF), VyroCutPalette.AccentSecondary)
        assertEquals(Color(0xFFE5EDEA), VyroCutPalette.TextPrimary)
        assertEquals(Color(0xFF9BAAA5), VyroCutPalette.TextMuted)
    }

    @Test
    fun materialColorSchemeUsesTheSharedPalette() {
        val scheme = vyroCutColorScheme()

        assertEquals(VyroCutPalette.Accent, scheme.primary)
        assertEquals(VyroCutPalette.AccentSecondary, scheme.secondary)
        assertEquals(VyroCutPalette.Background, scheme.background)
        assertEquals(VyroCutPalette.Surface, scheme.surface)
        assertEquals(VyroCutPalette.TextPrimary, scheme.onSurface)
        assertEquals(VyroCutPalette.Danger, scheme.error)
    }
}
