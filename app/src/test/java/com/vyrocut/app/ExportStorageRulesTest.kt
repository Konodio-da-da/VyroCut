package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportStorageRulesTest {
    private val estimate = estimateExportFileSize(
        durationMs = 60_000L,
        requestedVideoBitrateBps = 6_000_000,
        requestedAudioBitrateBps = 128_000,
        automaticVideoBitrateBps = 6_000_000,
    )!!

    @Test
    fun estimatesManualVideoAndAudioBitratesWithContainerAllowance() {
        assertEquals(48_258_000L, estimate.estimatedBytes)
        assertFalse(estimate.usesAutomaticVideoBitrate)
        assertFalse(estimate.usesAutomaticAudioBitrate)
    }

    @Test
    fun autoUsesExplicitPlanningAssumptionsAndMarksThemAsEstimates() {
        val automatic = estimateExportFileSize(
            durationMs = 60_000L,
            requestedVideoBitrateBps = null,
            requestedAudioBitrateBps = null,
            automaticVideoBitrateBps = 6_220_800,
        )!!

        assertEquals(6_220_800, automatic.videoBitrateBps)
        assertEquals(DEFAULT_AUTOMATIC_AUDIO_BITRATE_ESTIMATE_BPS, automatic.audioBitrateBps)
        assertTrue(automatic.usesAutomaticVideoBitrate)
        assertTrue(automatic.usesAutomaticAudioBitrate)
        assertTrue(formatExportSizeMiB(automatic.estimatedBytes).startsWith("~"))
    }

    @Test
    fun invalidDurationsAndBitrateAssumptionsDoNotProduceAnEstimate() {
        assertNull(
            estimateExportFileSize(
                durationMs = 0L,
                requestedVideoBitrateBps = 1_000_000,
                requestedAudioBitrateBps = 128_000,
                automaticVideoBitrateBps = 6_000_000,
            ),
        )
        assertNull(
            estimateExportFileSize(
                durationMs = 1_000L,
                requestedVideoBitrateBps = null,
                requestedAudioBitrateBps = null,
                automaticVideoBitrateBps = null,
            ),
        )
        assertNull(
            estimateExportFileSize(
                durationMs = 1_000L,
                requestedVideoBitrateBps = -1,
                requestedAudioBitrateBps = 128_000,
                automaticVideoBitrateBps = 6_000_000,
            ),
        )
    }

    @Test
    fun tinyEstimatesRoundUpAndHugeDurationsSaturateSafely() {
        val tiny = estimateExportFileSize(
            durationMs = 1L,
            requestedVideoBitrateBps = 1,
            requestedAudioBitrateBps = 1,
            automaticVideoBitrateBps = 1,
        )!!
        assertEquals(2L, tiny.estimatedBytes)

        val huge = estimateExportFileSize(
            durationMs = Long.MAX_VALUE,
            requestedVideoBitrateBps = Int.MAX_VALUE,
            requestedAudioBitrateBps = Int.MAX_VALUE,
            automaticVideoBitrateBps = Int.MAX_VALUE,
        )!!
        assertEquals(Long.MAX_VALUE, huge.estimatedBytes)
    }

    @Test
    fun sameVolumePreflightBudgetsForTemporaryAndPublishedCopies() {
        val required = estimate.estimatedBytes * 2L + EXPORT_SAME_VOLUME_STORAGE_RESERVE_BYTES
        val result = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(
                temporaryAvailableBytes = required,
                moviesAvailableBytes = required,
                sameVolume = true,
            ),
        )

        assertEquals(ExportStoragePreflightStatus.SUFFICIENT, result.status)
        assertEquals(ExportStorageScope.TEMPORARY_AND_MOVIES, result.scope)
        assertEquals(required, result.requiredBytes)
    }

    @Test
    fun sameVolumePreflightRejectsSpaceThatCannotHoldBothCopies() {
        val required = estimate.estimatedBytes * 2L + EXPORT_SAME_VOLUME_STORAGE_RESERVE_BYTES
        val result = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(
                temporaryAvailableBytes = required - 1L,
                moviesAvailableBytes = required,
                sameVolume = true,
            ),
        )

        assertEquals(ExportStoragePreflightStatus.INSUFFICIENT, result.status)
        assertTrue(describeExportStoragePreflight(result).contains("Not enough free space"))
    }

    @Test
    fun separateVolumesAreCheckedIndependently() {
        val temporaryRequired = estimate.estimatedBytes + EXPORT_TEMPORARY_STORAGE_RESERVE_BYTES
        val moviesRequired = estimate.estimatedBytes + EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES
        val result = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(
                temporaryAvailableBytes = temporaryRequired,
                moviesAvailableBytes = moviesRequired,
                sameVolume = false,
            ),
        )

        assertEquals(ExportStoragePreflightStatus.SUFFICIENT, result.status)
    }

    @Test
    fun separateVolumePreflightIdentifiesTheInsufficientDestination() {
        val temporaryRequired = estimate.estimatedBytes + EXPORT_TEMPORARY_STORAGE_RESERVE_BYTES
        val moviesRequired = estimate.estimatedBytes + EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES
        val result = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(
                temporaryAvailableBytes = temporaryRequired,
                moviesAvailableBytes = moviesRequired - 1L,
                sameVolume = false,
            ),
        )

        assertEquals(ExportStoragePreflightStatus.INSUFFICIENT, result.status)
        assertEquals(ExportStorageScope.MOVIES_PUBLICATION, result.scope)
    }

    @Test
    fun knownInsufficientVolumeIsReportedEvenWhenAnotherCapacityIsUnknown() {
        val missingTemporaryCapacity = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(temporaryAvailableBytes = null, moviesAvailableBytes = 10L, sameVolume = false),
        )
        val unknownTemporaryCapacity = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(
                temporaryAvailableBytes = null,
                moviesAvailableBytes = estimate.estimatedBytes + EXPORT_PUBLICATION_STORAGE_RESERVE_BYTES,
                sameVolume = false,
            ),
        )
        val unknownVolume = evaluateExportStoragePreflight(
            estimate,
            ExportStorageAvailability(temporaryAvailableBytes = 10L, moviesAvailableBytes = 10L, sameVolume = null),
        )

        assertEquals(ExportStoragePreflightStatus.INSUFFICIENT, missingTemporaryCapacity.status)
        assertEquals(ExportStorageScope.MOVIES_PUBLICATION, missingTemporaryCapacity.scope)
        assertEquals(ExportStoragePreflightStatus.UNKNOWN, unknownTemporaryCapacity.status)
        assertEquals(ExportStoragePreflightStatus.UNKNOWN, unknownVolume.status)
    }

    @Test
    fun storageRootMatchingUsesPathBoundaries() {
        assertTrue(isPathWithinStorageRoot("/storage/emulated/0", "/storage/emulated/0/"))
        assertTrue(isPathWithinStorageRoot("/storage/emulated/0/Android/data/app/files", "/storage/emulated/0"))
        assertFalse(isPathWithinStorageRoot("/storage/emulated/01/Movies", "/storage/emulated/0"))
        assertFalse(isPathWithinStorageRoot("/data/user/0/app/files", "/storage/emulated/0"))
    }
}
