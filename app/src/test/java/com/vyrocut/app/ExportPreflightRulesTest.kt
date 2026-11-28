package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPreflightRulesTest {
    private val source = ExportSourceMetadata(
        durationMs = 12_000L,
        hasVideoTrack = true,
        videoWidthPx = 1_920,
        videoHeightPx = 1_080,
    )
    private val clip = ExportClipRequirement(
        id = "clip-a",
        uri = "content://media/source-a",
        name = "Source A",
        sourceStartMs = 1_000L,
        sourceEndMs = 5_000L,
    )

    @Test
    fun acceptsValidClipsThatReuseOneReadableSource() {
        val second = clip.copy(
            id = "clip-b",
            sourceStartMs = 5_000L,
            sourceEndMs = 9_000L,
        )
        assertNull(
            validateExportPreflight(
                listOf(clip, second),
                mapOf(clip.uri to source),
            ),
        )
    }

    @Test
    fun rejectsAnEmptyProject() {
        assertEquals(
            "The project has no clips to export",
            validateExportPreflight(emptyList(), emptyMap()),
        )
    }

    @Test
    fun rejectsBlankAndDuplicateClipIds() {
        assertEquals(
            "Source A has no stable clip ID",
            validateExportPreflight(listOf(clip.copy(id = " ")), mapOf(clip.uri to source)),
        )
        assertEquals(
            "The project contains duplicate clip IDs",
            validateExportPreflight(listOf(clip, clip.copy(name = "Duplicate")), mapOf(clip.uri to source)),
        )
    }

    @Test
    fun rejectsMissingOrInvalidSourceRanges() {
        assertEquals(
            "Source A has no source video",
            validateExportPreflight(listOf(clip.copy(uri = "")), emptyMap()),
        )
        assertEquals(
            "Source A has an invalid source trim range",
            validateExportPreflight(listOf(clip.copy(sourceStartMs = -1L)), mapOf(clip.uri to source)),
        )
        assertEquals(
            "Source A has an invalid source trim range",
            validateExportPreflight(listOf(clip.copy(sourceEndMs = clip.sourceStartMs)), mapOf(clip.uri to source)),
        )
    }

    @Test
    fun rejectsSourcesThatCannotBeInspected() {
        assertEquals(
            "Could not inspect the source for Source A",
            validateExportPreflight(listOf(clip), emptyMap()),
        )
        assertEquals(
            "Could not read the duration of Source A's source video",
            validateExportPreflight(
                listOf(clip),
                mapOf(clip.uri to ExportSourceMetadata(durationMs = null, hasVideoTrack = true)),
            ),
        )
    }

    @Test
    fun requiresSourceDimensionsOnlyWhenACustomCanvasBackgroundIsSelected() {
        val sourceWithoutDimensions = source.copy(videoWidthPx = null, videoHeightPx = null)
        assertNull(validateExportPreflight(listOf(clip), mapOf(clip.uri to sourceWithoutDimensions)))
        assertEquals(
            "Source A's video dimensions are unavailable for the selected canvas background",
            validateExportPreflight(
                listOf(clip),
                mapOf(clip.uri to sourceWithoutDimensions),
                requireVideoDimensions = true,
            ),
        )
    }

    @Test
    fun acceptsReadableSourceDimensionsForCustomCanvasBackgrounds() {
        assertNull(
            validateExportPreflight(
                listOf(clip),
                mapOf(clip.uri to source),
                requireVideoDimensions = true,
            ),
        )
    }

    @Test
    fun rejectsSourcesWithoutVideoTracks() {
        assertEquals(
            "Source A's source has no readable video track",
            validateExportPreflight(
                listOf(clip),
                mapOf(clip.uri to ExportSourceMetadata(durationMs = 12_000L, hasVideoTrack = false)),
            ),
        )
    }

    @Test
    fun rejectsTrimsThatExtendPastTheCurrentSourceDuration() {
        assertEquals(
            "Source A's trim extends beyond its source video",
            validateExportPreflight(
                listOf(clip.copy(sourceEndMs = 13_000L)),
                mapOf(clip.uri to source),
            ),
        )
    }

    @Test
    fun rejectsTimelineDurationOverflow() {
        val veryLong = clip.copy(sourceStartMs = 0L, sourceEndMs = Long.MAX_VALUE)
        val another = veryLong.copy(id = "clip-b")
        assertEquals(
            "The project duration is too long to export safely",
            validateExportPreflight(
                listOf(veryLong, another),
                mapOf(clip.uri to ExportSourceMetadata(Long.MAX_VALUE, true)),
            ),
        )
    }

    @Test
    fun exportSourceUrisAreDeduplicatedInFirstUseOrder() {
        assertEquals(
            listOf("content://video/a", "content://audio/b", "content://video/c"),
            distinctExportSourceUris(
                listOf(
                    "content://video/a",
                    "content://audio/b",
                    "content://video/a",
                    "content://video/c",
                    "content://audio/b",
                ),
            ),
        )
        assertTrue(distinctExportSourceUris(emptyList()).isEmpty())
    }

    @Test
    fun recognizesRealAudioTrackMimeTypesAndRejectsVideoOnlySources() {
        assertTrue(hasAudioTrackMimeType(listOf("video/avc", "audio/mp4")))
        assertTrue(hasAudioTrackMimeType(listOf(" AUDIO/AAC ")))
        assertFalse(hasAudioTrackMimeType(listOf("video/mp4", "application/octet-stream", null)))
        assertFalse(hasAudioTrackMimeType(listOf("audio/")))
        assertFalse(hasAudioTrackMimeType(emptyList()))
    }
}
