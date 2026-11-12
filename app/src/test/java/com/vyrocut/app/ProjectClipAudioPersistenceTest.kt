package com.vyrocut.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectClipAudioPersistenceTest {
    @Test
    fun clipVolumeAndFadeSettingsSurviveProjectSerialization() {
        val original = EditClip(
            id = "clip-1",
            uri = "content://media/one",
            name = "Interview",
            sourceStartMs = 250L,
            sourceEndMs = 4_250L,
            audioVolume = 0.35f,
            fadeInMs = 750L,
            fadeOutMs = 1_250L,
        )

        val restored = decodeProjectStrict(encodeProject(listOf(original)))

        assertNotNull(restored)
        assertEquals(0.35f, restored!!.single().audioVolume, 0.0001f)
        assertEquals(750L, restored.single().fadeInMs)
        assertEquals(1_250L, restored.single().fadeOutMs)
        assertEquals(original.sourceStartMs, restored.single().sourceStartMs)
        assertEquals(original.sourceEndMs, restored.single().sourceEndMs)
    }

    @Test
    fun legacyClipWithoutVolumeOrFadesDefaultsToOriginalAudio() {
        val restored = decodeProjectStrict(
            """[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}]""",
        )

        assertNotNull(restored)
        assertEquals(1f, restored!!.single().audioVolume, 0f)
        assertEquals(0L, restored.single().fadeInMs)
        assertEquals(0L, restored.single().fadeOutMs)
    }

    @Test
    fun invalidPersistedClipVolumeOrFadeRejectsTheProjectInsteadOfSilentlyChangingIt() {
        val invalidVolume = decodeProjectStrict(
            """[{"id":"bad-volume","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"volume":1.5}]""",
        )
        val invalidFade = decodeProjectStrict(
            """[{"id":"bad-fade","uri":"content://media/bad","name":"Bad clip","start":0,"end":1000,"fadeInMs":-1}]""",
        )

        assertNull(invalidVolume)
        assertNull(invalidFade)
    }

    @Test
    fun versionFourBackupPreservesClipVolumeAndFades() {
        val original = EditClip(
            id = "clip-2",
            uri = "content://media/two",
            name = "Music",
            sourceStartMs = 0L,
            sourceEndMs = 8_000L,
            audioVolume = 0.2f,
            fadeInMs = 1_000L,
            fadeOutMs = 2_000L,
        )
        val backup = encodeProjectBackup(
            "Volume project",
            listOf(original),
            ProjectCanvasSettings("16:9", "DARK"),
        )
        val restored = decodeProjectBackup(backup)

        assertEquals("Volume project", restored.name)
        assertEquals(0.2f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(1_000L, restored.clips.single().fadeInMs)
        assertEquals(2_000L, restored.clips.single().fadeOutMs)
        assertEquals(ProjectCanvasSettings("16:9", "DARK"), restored.canvasSettings)
    }

    @Test
    fun versionThreeBackupWithClipVolumeRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":3,
            "projectName":"Version three backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000,"volume":0.25}],
            "canvasSettings":"VYROCUT_CANVAS_V1|16:9|DARK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Version three backup", restored.name)
        assertEquals(0.25f, restored.clips.single().audioVolume, 0.0001f)
        assertEquals(0L, restored.clips.single().fadeInMs)
        assertEquals(0L, restored.clips.single().fadeOutMs)
    }

    @Test
    fun versionTwoBackupWithoutClipVolumeRemainsCompatible() {
        val legacy = """{
            "format":"vyrocut-project",
            "version":2,
            "projectName":"Legacy backup",
            "clips":[{"id":"legacy","uri":"content://media/legacy","name":"Old clip","start":0,"end":1000}],
            "canvasSettings":"VYROCUT_CANVAS_V1|9:16|BLACK"
        }"""

        val restored = decodeProjectBackup(legacy)

        assertEquals("Legacy backup", restored.name)
        assertEquals(1f, restored.clips.single().audioVolume, 0f)
        assertEquals(0L, restored.clips.single().fadeInMs)
        assertEquals(0L, restored.clips.single().fadeOutMs)
        assertEquals(ProjectCanvasSettings("9:16", "BLACK"), restored.canvasSettings)
    }
}
