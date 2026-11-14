package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportCanvasEffectsTest {
    @Test
    fun landscapeSourceUsesTopAndBottomBarsOnPortraitCanvas() {
        assertEquals(
            LetterboxContentRect(0f, 656.25f, 1_080f, 1_263.75f),
            calculateLetterboxContentRect(1_080, 1_920, 1_920, 1_080),
        )
    }

    @Test
    fun portraitSourceUsesLeftAndRightBarsOnLandscapeCanvas() {
        assertEquals(
            LetterboxContentRect(656.25f, 0f, 1_263.75f, 1_080f),
            calculateLetterboxContentRect(1_920, 1_080, 1_080, 1_920),
        )
    }

    @Test
    fun quarterTurnRotationSwapsSourceDimensionsBeforeFitting() {
        assertEquals(
            LetterboxContentRect(0f, 0f, 1_080f, 1_920f),
            calculateLetterboxContentRect(1_080, 1_920, 1_920, 1_080, 90),
        )
        assertEquals(
            LetterboxContentRect(0f, 0f, 1_080f, 1_920f),
            calculateLetterboxContentRect(1_080, 1_920, 1_920, 1_080, -90),
        )
    }

    @Test
    fun matchingAspectRatiosFillTheCanvasWithoutLetterboxBars() {
        assertEquals(
            LetterboxContentRect(0f, 0f, 1_920f, 1_080f),
            calculateLetterboxContentRect(1_920, 1_080, 1_920, 1_080),
        )
    }

    @Test
    fun invalidCanvasOrSourceDimensionsAreRejected() {
        assertNull(calculateLetterboxContentRect(0, 1_080, 1_920, 1_080))
        assertNull(calculateLetterboxContentRect(1_920, 1_080, 0, 1_080))
    }

    @Test
    fun samplesBothLetterboxBarsAwayFromTheirEdges() {
        assertEquals(
            listOf(
                LetterboxPixelProbe(540, 328),
                LetterboxPixelProbe(540, 1_592),
            ),
            letterboxBackgroundProbePoints(1_080, 1_920, 1_920, 1_080),
        )
    }

    @Test
    fun samplesBothPillarboxBarsAwayFromTheirEdges() {
        assertEquals(
            listOf(
                LetterboxPixelProbe(328, 540),
                LetterboxPixelProbe(1_592, 540),
            ),
            letterboxBackgroundProbePoints(1_920, 1_080, 1_080, 1_920),
        )
    }

    @Test
    fun producesNoBackgroundProbesWhenSourceFillsCanvas() {
        assertEquals(
            emptyList<LetterboxPixelProbe>(),
            letterboxBackgroundProbePoints(1_920, 1_080, 1_920, 1_080),
        )
    }

    @Test
    fun acceptsSmallCompressionColourShiftInBackgroundPixels() {
        assertNull(
            validateLetterboxBackgroundPixels(
                actualPixelsArgb = listOf(0xFF242726.toInt(), 0xFF1E2221.toInt()),
                expectedArgb = 0xFF202322.toInt(),
            ),
        )
    }

    @Test
    fun rejectsBackgroundPixelsThatDoNotMatchTheSelectedPreset() {
        assertEquals(
            "The exported MP4 canvas background pixels do not match the selected background",
            validateLetterboxBackgroundPixels(
                actualPixelsArgb = listOf(0xFFFFFFFF.toInt()),
                expectedArgb = 0xFF202322.toInt(),
            ),
        )
    }

    @Test
    fun mapsBackgroundPresetsToStableArgbValues() {
        assertEquals(0xFF000000.toInt(), exportCanvasBackgroundArgbForPreset("BLACK"))
        assertEquals(0xFF202322.toInt(), exportCanvasBackgroundArgbForPreset("DARK"))
        assertEquals(0xFFFFFFFF.toInt(), exportCanvasBackgroundArgbForPreset("WHITE"))
        assertEquals(0xFF000000.toInt(), exportCanvasBackgroundArgbForPreset("unknown"))
    }
}
