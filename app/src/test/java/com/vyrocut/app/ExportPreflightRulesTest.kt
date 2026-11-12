package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportPreflightRulesTest {
    private val source = ExportSourceMetadata(durationMs = 12_000L, hasVideoTrack = true)
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
}
