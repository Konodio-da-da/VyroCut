package com.vyrocut.app

import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFailureDiagnosticsTest {
    @Test
    fun detectsStorageFailureInNestedMedia3Cause() {
        val error = IllegalStateException("Transformer failed", IOException("No space left on device"))

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.STORAGE, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("free storage"))
    }

    @Test
    fun detectsPermissionFailureWithoutEchoingProviderDetails() {
        val error = SecurityException("Permission denied for content://private/source")

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.SOURCE_PERMISSION, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("Re-import or relink"))
        assertFalse(diagnostic.userMessage.contains("content://"))
    }

    @Test
    fun identifiesMissingSourceDuringPreparationOrRendering() {
        val diagnostic = diagnoseExportFailure(
            ExportFailureStage.RENDERING,
            FileNotFoundException("/storage/emulated/0/private/video.mp4"),
        )

        assertEquals(ExportFailureCategory.SOURCE_UNAVAILABLE, diagnostic.category)
        assertFalse(diagnostic.userMessage.contains("/storage/"))
    }

    @Test
    fun identifiesEncoderFailuresFromWrappedExceptionNames() {
        val error = IllegalStateException("Export failed", TestEncoderException("Codec initialization failed"))

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.UNSUPPORTED_ENCODER, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("lower resolution or bitrate"))
    }

    @Test
    fun identifiesInvalidOutputOnlyDuringVerification() {
        val error = IllegalStateException("The exported MP4 contains no video track")

        val verifying = diagnoseExportFailure(ExportFailureStage.VERIFYING_OR_PUBLISHING, error)
        val rendering = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.INVALID_OUTPUT, verifying.category)
        assertTrue(verifying.userMessage.contains("was not published"))
        assertEquals(ExportFailureCategory.UNKNOWN, rendering.category)
    }

    @Test
    fun unknownFailuresUseStageSpecificSafeGuidance() {
        val preparation = diagnoseExportFailure(
            ExportFailureStage.PREPARATION,
            IllegalStateException("provider returned opaque failure"),
        )
        val rendering = diagnoseExportFailure(
            ExportFailureStage.RENDERING,
            IllegalStateException("provider returned opaque failure"),
        )
        val publishing = diagnoseExportFailure(
            ExportFailureStage.VERIFYING_OR_PUBLISHING,
            IllegalStateException("provider returned opaque failure"),
        )

        assertEquals(ExportFailureCategory.UNKNOWN, preparation.category)
        assertEquals(ExportFailureCategory.UNKNOWN, rendering.category)
        assertEquals(ExportFailureCategory.UNKNOWN, publishing.category)
        assertTrue(preparation.userMessage.contains("source clips"))
        assertTrue(rendering.userMessage.contains("device encoder"))
        assertTrue(publishing.userMessage.contains("Movies"))
    }

    @Test
    fun decoderFailuresAreNotMisclassifiedAsEncoderFailures() {
        val error = IllegalStateException("Video decoder failed to initialize")

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.UNSUPPORTED_DECODER, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("could not decode"))
        assertFalse(diagnostic.userMessage.contains("lower resolution or bitrate"))
    }

    @Test
    fun genericCodecFailureDoesNotGuessWhetherEncoderOrDecoderFailed() {
        val diagnostic = diagnoseExportFailure(
            ExportFailureStage.RENDERING,
            IllegalStateException("Codec initialization failed"),
        )

        assertEquals(ExportFailureCategory.UNSUPPORTED_CODEC, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("media codecs"))
    }

    @Test
    fun decoderFailureDuringOutputVerificationIsReportedAsInvalidOutput() {
        val diagnostic = diagnoseExportFailure(
            ExportFailureStage.VERIFYING_OR_PUBLISHING,
            IllegalStateException("Could not decode exported video"),
        )

        assertEquals(ExportFailureCategory.INVALID_OUTPUT, diagnostic.category)
        assertTrue(diagnostic.userMessage.contains("was not published"))
    }

    @Test
    fun causeChainCycleDoesNotPreventFindingNestedStorageFailure() {
        val outer = IllegalStateException("Transformer failed")
        val nested = IOException("No space left on device")
        outer.initCause(nested)
        nested.initCause(outer)

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, outer)

        assertEquals(ExportFailureCategory.STORAGE, diagnostic.category)
    }

    @Test
    fun causeChainInspectionStopsAtEightThrowables() {
        var error: Throwable = SecurityException("Permission denied")
        repeat(8) { index ->
            error = IllegalStateException("Wrapper $index", error)
        }

        val diagnostic = diagnoseExportFailure(ExportFailureStage.RENDERING, error)

        assertEquals(ExportFailureCategory.UNKNOWN, diagnostic.category)
    }

    private class TestEncoderException(message: String) : Exception(message)
}
