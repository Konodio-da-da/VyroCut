package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectPersistenceRulesTest {
    @Test
    fun projectCanvasSettingsRoundTripEverySupportedPreset() {
        val ratios = listOf("9:16", "16:9", "1:1")
        val backgrounds = listOf("BLACK", "DARK", "WHITE")

        ratios.forEach { ratio ->
            backgrounds.forEach { background ->
                val settings = ProjectCanvasSettings(ratio, background)
                assertEquals(settings, decodeProjectCanvasSettings(encodeProjectCanvasSettings(settings)))
            }
        }
    }

    @Test
    fun projectCanvasSettingsRejectUnknownVersionsAndPresets() {
        assertEquals(null, decodeProjectCanvasSettings(null))
        assertEquals(null, decodeProjectCanvasSettings("VYROCUT_CANVAS_V2|9:16|BLACK"))
        assertEquals(null, decodeProjectCanvasSettings("VYROCUT_CANVAS_V1|4:3|BLACK"))
        assertEquals(null, decodeProjectCanvasSettings("VYROCUT_CANVAS_V1|9:16|BLUE"))
        assertEquals(null, decodeProjectCanvasSettings("VYROCUT_CANVAS_V1|9:16"))
    }

    @Test
    fun skipsCorruptSnapshotsWithoutReplacingThemWithEmptyProjects() {
        val decoded = decodeValidHistorySnapshots(
            rawSnapshots = listOf("[]", "broken", "[1]"),
            decode = { raw ->
                when (raw) {
                    "[]" -> emptyList<Int>()
                    "[1]" -> listOf(1)
                    else -> null
                }
            },
        )

        assertEquals(listOf(emptyList<Int>(), listOf(1)), decoded)
    }

    @Test
    fun keepsOnlyTheNewestFiftyValidSnapshots() {
        val rawSnapshots = (0..54).map(Int::toString) + "corrupt"

        val decoded = decodeValidHistorySnapshots(rawSnapshots) { it.toIntOrNull() }

        assertEquals((5..54).toList(), decoded)
    }

    @Test
    fun corruptOnlyHistoryProducesNoUndoEntries() {
        val decoded = decodeValidHistorySnapshots(listOf("bad-1", "bad-2")) {
            it.toIntOrNull()
        }

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun audioTrackRecoveryJournalRoundTripsAndValidatesProjectIdentity() {
        val tracks = listOf(
            AudioTrackClip(
                id = "voice-1",
                type = AudioTrackType.VOICEOVER,
                uri = "content://media/voice-1",
                name = "夜の街 🎙️.wav",
                startMs = 1_000L,
                sourceStartMs = 250L,
                sourceEndMs = 1_250L,
                sourceDurationMs = 2_000L,
                volume = 0.7f,
                fadeInMs = 100L,
                fadeOutMs = 150L,
            ),
        )
        val encodedTracks = encodeAudioTrackClips(tracks)
        val journal = encodeAudioTrackRecoveryJournal("project-1", encodedTracks, 1_800_000_000_000L)

        assertEquals(tracks, decodeAudioTrackRecoveryJournal(journal, "project-1", 5_000L))
        assertEquals(null, decodeAudioTrackRecoveryJournal(journal, "project-2", 5_000L))
    }

    @Test
    fun audioTrackRecoveryJournalRejectsTamperingAndOutOfRangeTracks() {
        val tracks = listOf(
            AudioTrackClip(
                id = "music-1",
                type = AudioTrackType.MUSIC,
                uri = "content://media/music-1",
                name = "Music.wav",
                startMs = 0L,
                sourceStartMs = 0L,
                sourceEndMs = 2_000L,
                sourceDurationMs = 2_000L,
            ),
        )
        val journal = encodeAudioTrackRecoveryJournal(
            "project-1",
            encodeAudioTrackClips(tracks),
            1_800_000_000_000L,
        )
        val tampered = journal.dropLast(1) + if (journal.last() == '0') "1" else "0"

        assertEquals(null, decodeAudioTrackRecoveryJournal(tampered, "project-1", 5_000L))
        assertEquals(null, decodeAudioTrackRecoveryJournal(journal, "project-1", 1_000L))
    }

    @Test
    fun audioTrackRecoveryDecoderStillAcceptsLegacyRawJson() {
        val tracks = listOf(
            AudioTrackClip(
                id = "sfx-1",
                type = AudioTrackType.SFX,
                uri = "content://media/sfx-1",
                name = "Impact.wav",
                startMs = 500L,
                sourceStartMs = 100L,
                sourceEndMs = 600L,
                sourceDurationMs = 1_000L,
            ),
        )
        val legacyJson = encodeAudioTrackClips(tracks)

        assertEquals(tracks, decodeAudioTrackRecoveryJournal(legacyJson, "project-1", 2_000L))
    }

    @Test
    fun projectAndAudioRecoveryJournalsShareOneCheckpointTimestamp() {
        val projectJson = """[{"id":"clip-1","start":0,"end":1200}]"""
        val audioJson = "[]"
        val savedAtMs = 1_800_000_000_000L
        val bundle = createProjectRecoveryJournalBundle(
            projectId = "project-1",
            projectName = "Shared checkpoint",
            projectJson = projectJson,
            audioTracksJson = audioJson,
            savedAtMs = savedAtMs,
        )

        val projectSnapshot = ProjectRecoveryJournal.decode(bundle.projectJournal, "project-1")
        val audioSnapshot = ProjectRecoveryJournal.decode(bundle.audioTracksJournal, "project-1")

        assertEquals(savedAtMs, projectSnapshot?.savedAtMs)
        assertEquals(savedAtMs, audioSnapshot?.savedAtMs)
        assertEquals(projectJson, projectSnapshot?.projectJson)
        assertEquals("Shared checkpoint", projectSnapshot?.projectName)
        assertEquals(audioJson, audioSnapshot?.projectJson)
    }

    @Test
    fun projectRecoveryJournalBundleRejectsBlankProjectIdsAndInvalidTimestamps() {
        assertEquals(
            null,
            runCatching {
                createProjectRecoveryJournalBundle(" ", "Project", "[]", "[]", 1L)
            }.getOrNull(),
        )
        assertEquals(
            null,
            runCatching {
                createProjectRecoveryJournalBundle("project-1", "Project", "[]", "[]", 0L)
            }.getOrNull(),
        )
    }


    @Test
    fun projectRecoveryRestoresAudioFromTheMatchingCheckpointTimestamp() {
        val savedAtMs = 1_800_000_000_000L
        val projectSnapshot = recoveryProjectSnapshot(savedAtMs)
        val checkpointTracks = listOf(recoveryAudioTrack("checkpoint-music", 500L))
        val currentTracks = listOf(recoveryAudioTrack("newer-music", 1_500L))
        val audioJournal = encodeAudioTrackRecoveryJournal(
            projectId = "project-1",
            audioTracksJson = encodeAudioTrackClips(checkpointTracks),
            savedAtMs = savedAtMs,
        )

        assertEquals(
            checkpointTracks,
            audioTracksForProjectRecovery(
                projectSnapshot,
                audioJournal,
                currentTracks,
                projectDurationMs = 5_000L,
            ),
        )
    }

    @Test
    fun projectRecoveryDoesNotMixAudioFromADifferentCheckpointTimestamp() {
        val projectSnapshot = recoveryProjectSnapshot(1_800_000_000_000L)
        val currentTracks = listOf(recoveryAudioTrack("current-music", 1_000L))
        val audioJournal = encodeAudioTrackRecoveryJournal(
            projectId = "project-1",
            audioTracksJson = encodeAudioTrackClips(
                listOf(recoveryAudioTrack("later-music", 2_000L)),
            ),
            savedAtMs = 1_800_000_000_001L,
        )

        assertEquals(
            currentTracks,
            audioTracksForProjectRecovery(
                projectSnapshot,
                audioJournal,
                currentTracks,
                projectDurationMs = 5_000L,
            ),
        )
    }

    @Test
    fun projectRecoveryFallsBackWhenAudioJournalIsCorruptOrBelongsToAnotherProject() {
        val projectSnapshot = recoveryProjectSnapshot(1_800_000_000_000L)
        val currentTracks = listOf(recoveryAudioTrack("current-music", 750L))
        val validForeignJournal = encodeAudioTrackRecoveryJournal(
            projectId = "project-2",
            audioTracksJson = encodeAudioTrackClips(
                listOf(recoveryAudioTrack("foreign-music", 0L)),
            ),
            savedAtMs = projectSnapshot.savedAtMs,
        )
        val tamperedJournal = encodeAudioTrackRecoveryJournal(
            projectId = "project-1",
            audioTracksJson = encodeAudioTrackClips(
                listOf(recoveryAudioTrack("tampered-music", 0L)),
            ),
            savedAtMs = projectSnapshot.savedAtMs,
        ).let { it.dropLast(1) + if (it.last() == '0') "1" else "0" }

        listOf(tamperedJournal, validForeignJournal).forEach { journal ->
            assertEquals(
                currentTracks,
                audioTracksForProjectRecovery(
                    projectSnapshot,
                    journal,
                    currentTracks,
                    projectDurationMs = 5_000L,
                ),
            )
        }
    }

    @Test
    fun projectDeletionClearsSavedPreviewPositionAndAllOtherProjectOwnedPreferences() {
        assertEquals(
            listOf(
                "project_project-1",
                "project_project-1_undo",
                "project_project-1_redo",
                "project_project-1_recovery",
                "project_project-1_recovery_dismissed",
                "project_project-1_audio_tracks",
                "project_project-1_audio_tracks_recovery",
                "project_project-1_canvas_settings",
                "project_project-1_preview_clip_id",
                "project_project-1_preview_source_time_ms",
            ),
            projectPreferenceKeysForDeletion("project-1"),
        )
    }

    @Test
    fun projectDeletionKeyListRejectsBlankIdsAndDoesNotIncludeGlobalPreferences() {
        assertEquals(
            false,
            runCatching { projectPreferenceKeysForDeletion(" ") }.isSuccess,
        )
        val keys = projectPreferenceKeysForDeletion("project-1")
        assertEquals(false, "active_project_id" in keys)
        assertEquals(false, "project_project-2" in keys)
    }

    private fun recoveryProjectSnapshot(savedAtMs: Long): ProjectRecoverySnapshot {
        val journal = ProjectRecoveryJournal.encode(
            projectId = "project-1",
            projectName = "Project",
            savedAtMs = savedAtMs,
            projectJson = "[]",
        )
        return requireNotNull(ProjectRecoveryJournal.decode(journal, "project-1"))
    }

    private fun recoveryAudioTrack(id: String, startMs: Long) = AudioTrackClip(
        id = id,
        type = AudioTrackType.MUSIC,
        uri = "content://media/$id",
        name = id,
        startMs = startMs,
        sourceStartMs = 0L,
        sourceEndMs = 1_000L,
        sourceDurationMs = 1_000L,
    )
}
