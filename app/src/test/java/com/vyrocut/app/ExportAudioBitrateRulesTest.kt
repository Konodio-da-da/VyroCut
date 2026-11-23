package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportAudioBitrateRulesTest {
    @Test
    fun missingOrUnknownPreferencesPreserveAutomaticEncoderSelection() {
        assertEquals(ExportAudioBitratePreset.AUTO, ExportAudioBitratePreset.fromId(null))
        assertEquals(ExportAudioBitratePreset.AUTO, ExportAudioBitratePreset.fromId(""))
        assertEquals(ExportAudioBitratePreset.AUTO, ExportAudioBitratePreset.fromId("lossless"))
        assertNull(exportRequestedAudioBitrateBps(ExportAudioBitratePreset.AUTO))
    }

    @Test
    fun restoresEverySupportedManualAudioBitratePreset() {
        assertEquals(ExportAudioBitratePreset.SPEECH, ExportAudioBitratePreset.fromId("96"))
        assertEquals(ExportAudioBitratePreset.STANDARD, ExportAudioBitratePreset.fromId("128"))
        assertEquals(ExportAudioBitratePreset.HIGH, ExportAudioBitratePreset.fromId("192"))
        assertEquals(ExportAudioBitratePreset.VERY_HIGH, ExportAudioBitratePreset.fromId("256"))
    }

    @Test
    fun manualPresetsRequestTheirExactBitratesInBitsPerSecond() {
        assertEquals(96_000, exportRequestedAudioBitrateBps(ExportAudioBitratePreset.SPEECH))
        assertEquals(128_000, exportRequestedAudioBitrateBps(ExportAudioBitratePreset.STANDARD))
        assertEquals(192_000, exportRequestedAudioBitrateBps(ExportAudioBitratePreset.HIGH))
        assertEquals(256_000, exportRequestedAudioBitrateBps(ExportAudioBitratePreset.VERY_HIGH))
    }

    @Test
    fun autoDoesNotOverrideMedia3AudioEncoderDefaults() {
        assertNull(exportRequestedAudioBitrateBps(ExportAudioBitratePreset.AUTO))
        assertEquals(
            "Auto keeps Media3's default audio encoder bitrate.",
            exportAudioBitrateSummary(ExportAudioBitratePreset.AUTO),
        )
    }

    @Test
    fun summariesDescribeRequestedTargetsRatherThanGuaranteedOutput() {
        assertEquals(
            "Requested target: 96 kbps • device encoder may adjust to a supported value.",
            exportAudioBitrateSummary(ExportAudioBitratePreset.SPEECH),
        )
        assertEquals(
            "Requested target: 128 kbps • device encoder may adjust to a supported value.",
            exportAudioBitrateSummary(ExportAudioBitratePreset.STANDARD),
        )
        assertEquals(
            "Requested target: 192 kbps • device encoder may adjust to a supported value.",
            exportAudioBitrateSummary(ExportAudioBitratePreset.HIGH),
        )
        assertEquals(
            "Requested target: 256 kbps • device encoder may adjust to a supported value.",
            exportAudioBitrateSummary(ExportAudioBitratePreset.VERY_HIGH),
        )
    }
}
